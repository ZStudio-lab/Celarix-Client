package com.mnchacks;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.joml.Quaternionf;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Entity ESP. Draws a translucent fill + outline around nearby entities,
 * rendered with the depth test forced to GL_ALWAYS so boxes show through
 * walls. Colors indicate the entity type.
 *
 * <p>A name + distance label is drawn above each box, also through walls.</p>
 */
public final class EspModule {

	private static final double MAX_DISTANCE = 128.0D;

	private static final int[] PLAYER_COLOR = {255, 60, 60};    // red
	private static final int[] FRIENDLY_COLOR = {200, 120, 255}; // purple (teammates)
	private static final int[] FRIEND_COLOR = {90, 255, 130};     // green (friends)
	private static final int[] HOSTILE_COLOR = {255, 160, 0};   // orange
	private static final int[] PASSIVE_COLOR = {80, 255, 80};   // green
	private static final int[] ITEM_COLOR = {60, 220, 255};     // cyan
	private static final int[] OTHER_COLOR = {200, 200, 255};   // pale blue
	private static final int[] INVIS_COLOR = {255, 255, 255};   // white (invisible)

	private static final int LINE_ALPHA = 230;

	/** Whether name/distance labels are drawn above boxes. */
	private static final boolean SHOW_LABELS = true;
	/** Label text scale (0.025 is the vanilla nametag size). */
	private static final float LABEL_SCALE = 0.025F;

	/**
	 * See-through line pipeline: the vanilla {@code core/position_color} shader
	 * with the depth test disabled, so ESP outlines are visible through walls.
	 */
	private static final RenderPipeline ESP_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder()
					.withLocation(Identifier.of("celarix", "esp_lines"))
					.withVertexShader("core/position_color")
					.withFragmentShader("core/position_color")
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.withDepthWrite(false)
					.withBlend(BlendFunction.TRANSLUCENT)
					.withCull(false)
					.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
					.build());

	/** The render layer the ESP outlines are emitted into. */
	private static final RenderLayer ESP_LAYER = RenderLayer.of(
			"celarix_esp", 16384, ESP_PIPELINE,
			RenderLayer.MultiPhaseParameters.builder()
					.lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.0D)))
					.build(false));

	private static boolean enabled = false;
	/** Number of targets drawn in the last frame (shown on the HUD). */
	public static volatile int lastTargetCount = 0;
	/**
	 * Latest snapshot of nearby tracked entities (through walls, out to
	 * {@link #MAX_DISTANCE}). Kept fresh every frame so other modules, notably
	 * the aimbot, can use the ESP's awareness even when ESP rendering is off.
	 */
	private static volatile List<Entity> tracked = Collections.emptyList();

	/** True while the orchestrators is the reason ESP is on. */
	private static boolean forced = false;
	/** Whether ESP was already on before PVP forced it. */
	private static boolean enabledBeforeForce = false;

	private EspModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** Automatic enable/disable used by the orchestrators. */
	public static void setForced(boolean on) {
		if (on) {
			if (!forced) {
				forced = true;
				enabledBeforeForce = enabled;
				enabled = true;
			}
		} else if (forced) {
			forced = false;
			if (!enabledBeforeForce) {
				enabled = false;
			}
		}
	}

	/** Entities the ESP currently sees (through walls). Never null. */
	public static List<Entity> getTrackedTargets() {
		return tracked;
	}

	public static void uiToggle() {
		enabled = !enabled;
		lastTargetCount = 0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.literal("ESP: " + (enabled ? "ON" : "OFF")), true);
		}
	}
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (Keybinds.espToggle.wasPressed()) {
				enabled = !enabled;
				lastTargetCount = 0;
				if (client.player != null) {
					client.player.sendMessage(Text.literal("ESP: " + (enabled ? "ON" : "OFF")), true);
				}
			}
		});
	}

	public static void renderWorld(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			tracked = Collections.emptyList();
			return;
		}

		Vec3d cam = context.camera().getPos();
		float delta = context.tickCounter().getTickProgress(true);

		// Collect targets in range. Done every frame (even when rendering is
		// off) so getTrackedTargets() is always a fresh snapshot for the aimbot.
		List<Box> boxes = new ArrayList<>();
		List<int[]> colors = new ArrayList<>();
		List<Entity> targets = new ArrayList<>();
		int enemyCount = 0;
		double maxSq = MAX_DISTANCE * MAX_DISTANCE;
		Box searchBox = new Box(
				cam.x - MAX_DISTANCE, cam.y - MAX_DISTANCE, cam.z - MAX_DISTANCE,
				cam.x + MAX_DISTANCE, cam.y + MAX_DISTANCE, cam.z + MAX_DISTANCE);

		for (Entity entity : client.world.getEntitiesByClass(Entity.class, searchBox, e -> !e.isRemoved())) {
			if (entity == client.getCameraEntity()) {
				continue;
			}
			if (entity.squaredDistanceTo(cam.x, cam.y, cam.z) > maxSq) {
				continue;
			}
			int[] color = colorFor(entity, player);
			if (color == null) {
				continue;
			}

			// Interpolate position so the box follows smooth movement.
			double ix = lerpPos(entity, entity.lastX, entity.getX(), delta);
			double iy = lerpPos(entity, entity.lastY, entity.getY(), delta);
			double iz = lerpPos(entity, entity.lastZ, entity.getZ(), delta);

			Box box = entity.getBoundingBox()
					.expand(0.05D)
					.offset(ix - entity.getX(), iy - entity.getY(), iz - entity.getZ());

			boxes.add(box);
			colors.add(color);
			targets.add(entity);
			if (Teams.isEnemy(entity, player)) {
				enemyCount++;
			}
		}

		lastTargetCount = enemyCount;
		tracked = targets;

		if (!enabled) {
			return;
		}

		// 1.21.5 renders world geometry with the camera rotation inside the
		// shader's model-view uniform, which is popped back to identity by
		// the time the END event fires. Vertices emitted here would get no
		// camera rotation, so the boxes "swim" with the view instead of
		// sticking to their entities. Baking the full view transform
		// (rotation + -camera translation) into the vertices fixes that.
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(new Matrix4f()
				.rotation(context.camera().getRotation().conjugate(new Quaternionf())));
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f matrix = matrices.peek().getPositionMatrix();

		// See-through outline layer (depth test disabled by the pipeline).
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer buffer = consumers.getBuffer(ESP_LAYER);

		// Outline pass (no fill - clean box outlines only).
		for (int i = 0; i < boxes.size(); i++) {
			lineBox(buffer, matrix, boxes.get(i), colors.get(i), LINE_ALPHA);
		}
		// Anchor the tracers at the world point that projects to the top-centre
		// of the screen, so the lines always join in one fixed spot above the
		// player instead of at the camera (which wanders as you look around).
		Vec3d tracerOrigin = TracersModule.isEnabled() ? tracerOrigin(context, matrices, cam) : null;
		if (TracersModule.isEnabled()) {
			for (int i = 0; i < boxes.size(); i++) {
				Box box = boxes.get(i);
				edge(buffer, matrix,
						(float) tracerOrigin.x,
						(float) tracerOrigin.y,
						(float) tracerOrigin.z,
						(float) ((box.minX + box.maxX) / 2.0D),
						(float) ((box.minY + box.maxY) / 2.0D),
						(float) ((box.minZ + box.maxZ) / 2.0D),
						colors.get(i), LINE_ALPHA);
			}
		}
				// Utility overlays: breadcrumbs, trajectories, holes, search hits.
		if (BreadcrumbsModule.isEnabled()) {
			var points = BreadcrumbsModule.getPoints();
			Vec3d prev = null;
			for (Vec3d point : points) {
				if (prev != null) {
					edge(buffer, matrix,
							(float) prev.x, (float) prev.y, (float) prev.z,
							(float) point.x, (float) point.y, (float) point.z,
							new int[]{70, 200, 255}, 160);
				}
				prev = point;
			}
		}
		if (TrajectoriesModule.isEnabled()) {
			var points = TrajectoriesModule.compute(client, player);
			Vec3d prev = null;
			for (Vec3d point : points) {
				if (prev != null) {
					edge(buffer, matrix,
							(float) prev.x, (float) prev.y, (float) prev.z,
							(float) point.x, (float) point.y, (float) point.z,
							new int[]{255, 200, 60}, 220);
				}
				prev = point;
			}
		}
		if (HoleEspModule.isEnabled()) {
			for (BlockPos hole : HoleEspModule.getHoles()) {
				lineBox(buffer, matrix,
						new Box(Vec3d.of(hole.down(2)), Vec3d.of(hole.up(1))),
						new int[]{140, 140, 255}, LINE_ALPHA);
			}
		}
		if (ChestEspModule.isEnabled()) {
			List<Box> chestBoxes = ChestEspModule.getBoxes();
			List<int[]> chestColors = ChestEspModule.getColors();
			for (int i = 0; i < chestBoxes.size(); i++) {
				lineBox(buffer, matrix, chestBoxes.get(i), chestColors.get(i), LINE_ALPHA);
			}
		}
		if (NewChunksModule.isEnabled()) {
			// One bright ring + four corner pillars per freshly loaded chunk,
			// at eye level, so the border is visible wherever you are.
			int[] color = {60, 255, 140};
			float ringY = (float) Math.floor(cam.y);
			for (ChunkPos pos : NewChunksModule.getChunks()) {
				float x1 = pos.getStartX();
				float z1 = pos.getStartZ();
				float x2 = x1 + 16;
				float z2 = z1 + 16;
				edge(buffer, matrix, x1, ringY, z1, x2, ringY, z1, color, LINE_ALPHA);
				edge(buffer, matrix, x2, ringY, z1, x2, ringY, z2, color, LINE_ALPHA);
				edge(buffer, matrix, x2, ringY, z2, x1, ringY, z2, color, LINE_ALPHA);
				edge(buffer, matrix, x1, ringY, z2, x1, ringY, z1, color, LINE_ALPHA);
				edge(buffer, matrix, x1, ringY, z1, x1, ringY + 8, z1, color, LINE_ALPHA);
				edge(buffer, matrix, x2, ringY, z1, x2, ringY + 8, z1, color, LINE_ALPHA);
				edge(buffer, matrix, x2, ringY, z2, x2, ringY + 8, z2, color, LINE_ALPHA);
				edge(buffer, matrix, x1, ringY, z2, x1, ringY + 8, z2, color, LINE_ALPHA);
			}
		}
		if (SearchModule.isEnabled()) {
			for (Box box : SearchModule.getFound()) {
				lineBox(buffer, matrix, box,
						new int[]{255, 170, 40}, LINE_ALPHA);
			}
		}
		if (SHOW_LABELS) {
			drawLabels(client, context, matrices, boxes, targets, cam);
		}

		// One flush AFTER everything (boxes, tracers, utility overlays and
		// labels) - flushing before the labels left them pending in the
		// buffer, so they drifted a frame behind the boxes.
		consumers.draw();
	}

	/**
	 * World point that projects to the top-centre of the screen (just above
	 * the player's head area): unprojects the anchor pixel through the inverse
	 * projection-view matrix and walks a fixed distance along that ray.
	 */
	private static Vec3d tracerOrigin(WorldRenderContext context, MatrixStack matrices, Vec3d cam) {
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			int scaledWidth = client.getWindow().getScaledWidth();
			int scaledHeight = client.getWindow().getScaledHeight();
			float ndcX = 2.0F * (scaledWidth / 2.0F) / scaledWidth - 1.0F;
			float ndcY = 1.0F - 2.0F * 6.0F / scaledHeight;

			Matrix4f combined = new Matrix4f(context.projectionMatrix());
			combined.mul(matrices.peek().getPositionMatrix());
			Matrix4f inverse = new Matrix4f(combined).invert();

			Vector4f near = new Vector4f(ndcX, ndcY, -1.0F, 1.0F);
			Vector4f far = new Vector4f(ndcX, ndcY, 1.0F, 1.0F);
			inverse.transform(near);
			near.div(near.w);
			inverse.transform(far);
			far.div(far.w);

			Vec3d direction = new Vec3d(far.x - near.x, far.y - near.y, far.z - near.z);
			if (direction.lengthSquared() < 1.0E-9D) {
				return cam;
			}
			// A fixed distance along the anchor ray - any point on it projects
			// to the same screen pixel, so all tracer lines join there.
			return cam.add(direction.normalize().multiply(3.0D));
		} catch (Exception error) {
			return cam;
		}
	}

	/** Name (+ distance) labels drawn above each box, billboarded to the camera. */
	private static void drawLabels(MinecraftClient client, WorldRenderContext context, MatrixStack matrices,
								   List<Box> boxes, List<Entity> targets, Vec3d cam) {
		TextRenderer textRenderer = client.textRenderer;
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

		for (int i = 0; i < boxes.size(); i++) {
			Entity entity = targets.get(i);
			Box box = boxes.get(i);

			int distance = (int) Math.round(Math.sqrt(entity.squaredDistanceTo(cam.x, cam.y, cam.z)));

			// If the game is already rendering a nametag for this entity, don't
			// duplicate the name - only add the distance the game doesn't show.
			Text label;
			if (gameShowsName(client, context, entity, cam)) {
				label = Text.literal(distance + "m").formatted(Formatting.GRAY);
			} else {
				label = Text.literal(entity.getName().getString())
						.formatted(entity instanceof PlayerEntity ? Formatting.RED : Formatting.WHITE)
						.append(Text.literal(" " + distance + "m").formatted(Formatting.GRAY));
			}

			matrices.push();
			matrices.translate((box.minX + box.maxX) / 2.0D, box.maxY + 0.4D, (box.minZ + box.maxZ) / 2.0D);
			matrices.multiply(context.camera().getRotation());
			matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

			float halfWidth = textRenderer.getWidth(label) / 2.0F;
			textRenderer.draw(label, -halfWidth, 0.0F, 0xFFFFFFFF, true,
					matrices.peek().getPositionMatrix(), consumers,
					TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
			matrices.pop();
		}
		consumers.draw();
	}

	/**
	 * Mirrors the vanilla nametag visibility rules so the ESP can avoid drawing a
	 * duplicate name when the game already shows one. Based on
	 * {@code EntityRenderer.hasLabel}, {@code LivingEntityRenderer.hasLabel},
	 * {@code MobEntityRenderer.hasLabel}, {@code ArmorStandEntityRenderer.hasLabel}
	 * and the frustum cull in {@code EntityRenderer.shouldRender}.
	 */
	private static boolean gameShowsName(MinecraftClient client, WorldRenderContext context, Entity entity, Vec3d cam) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return false;
		}

		// The game only draws a nametag for an entity it actually renders this frame.
		if (!passesFrustum(context, entity, cam)) {
			return false;
		}

		double d = entity.squaredDistanceTo(cam.x, cam.y, cam.z);

		// Non-living entities use the base EntityRenderer.hasLabel rule.
		if (!(entity instanceof LivingEntity)) {
			boolean named = entity.shouldRenderName()
					|| (entity.hasCustomName() && entity == client.targetedEntity);
			return named && d <= 4096.0D;
		}

		LivingEntity living = (LivingEntity) entity;

		// Armor stands have their own rule.
		if (entity instanceof ArmorStandEntity) {
			float standRange = living.isSneaky() ? 32.0F : 64.0F;
			return d < (double) (standRange * standRange) && entity.isCustomNameVisible();
		}

		// LivingEntityRenderer.hasLabel.
		boolean shows;
		float range = living.isSneaky() ? 32.0F : 64.0F;
		if (d >= (double) (range * range)) {
			shows = false;
		} else {
			boolean visible = !living.isInvisibleTo(player);
			AbstractTeam team = living != player ? living.getScoreboardTeam() : null;
			if (team != null) {
				AbstractTeam ownTeam = player.getScoreboardTeam();
				switch (team.getNameTagVisibilityRule()) {
					case ALWAYS:
						shows = visible;
						break;
					case NEVER:
						shows = false;
						break;
					case HIDE_FOR_OTHER_TEAMS:
						shows = ownTeam == null ? visible
								: team.isEqual(ownTeam) && (team.shouldShowFriendlyInvisibles() || visible);
						break;
					case HIDE_FOR_OWN_TEAM:
						shows = ownTeam == null ? visible : !team.isEqual(ownTeam) && visible;
						break;
					default:
						shows = true;
						break;
				}
			} else {
				shows = MinecraftClient.isHudEnabled()
						&& living != client.getCameraEntity()
						&& visible
						&& !living.hasPassengers();
			}
		}

		// Mobs additionally need a visible custom name.
		if (shows && entity instanceof MobEntity) {
			shows = entity.shouldRenderName()
					|| (entity.hasCustomName() && entity == client.targetedEntity);
		}
		return shows;
	}

	private static boolean passesFrustum(WorldRenderContext context, Entity entity, Vec3d cam) {
		if (!entity.shouldRender(entity.getX() - cam.x, entity.getY() - cam.y, entity.getZ() - cam.z)) {
			return false;
		}
		Frustum frustum = context.frustum();
		if (frustum == null) {
			return true;
		}
		// 1.21+ removed getVisibilityBoundingBox()/ignoreCameraFrustum; the
		// vanilla renderer now culls against the plain bounding box.
		Box box = entity.getBoundingBox().expand(0.5D);
		if (box.isNaN() || box.getAverageSideLength() == 0.0D) {
			box = new Box(entity.getX() - 2.0D, entity.getY() - 2.0D, entity.getZ() - 2.0D,
					entity.getX() + 2.0D, entity.getY() + 2.0D, entity.getZ() + 2.0D);
		}
		return frustum.isVisible(box);
	}

	private static int[] colorFor(Entity entity, PlayerEntity player) {
		if (entity instanceof PlayerEntity) {
			return Teams.isTeammate(entity, player) ? FRIENDLY_COLOR : PLAYER_COLOR;
		}
		if (entity instanceof Monster) {
			return HOSTILE_COLOR;
		}
		if (entity instanceof AnimalEntity || entity instanceof WaterCreatureEntity
				|| entity instanceof AmbientEntity || entity instanceof MerchantEntity
				|| entity instanceof GolemEntity) {
			return PASSIVE_COLOR;
		}
		if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) {
			return ITEM_COLOR;
		}
		if (entity instanceof ArmorStandEntity || entity instanceof EndCrystalEntity
				|| entity instanceof ProjectileEntity) {
			return OTHER_COLOR;
		}
		return null;
	}

	private static double lerpPos(Entity entity, double prev, double current, float delta) {
		// Don't stretch boxes across teleports.
		if ((current - prev) * (current - prev) > 64.0D) {
			return current;
		}
		return MathHelper.lerp(delta, prev, current);
	}

	private static void lineBox(VertexConsumer buffer, Matrix4f matrix, Box box, int[] color, int alpha) {
		float x0 = (float) box.minX;
		float y0 = (float) box.minY;
		float z0 = (float) box.minZ;
		float x1 = (float) box.maxX;
		float y1 = (float) box.maxY;
		float z1 = (float) box.maxZ;

		edge(buffer, matrix, x0, y0, z0, x1, y0, z0, color, alpha);
		edge(buffer, matrix, x0, y0, z0, x0, y1, z0, color, alpha);
		edge(buffer, matrix, x0, y0, z0, x0, y0, z1, color, alpha);
		edge(buffer, matrix, x1, y0, z0, x1, y1, z0, color, alpha);
		edge(buffer, matrix, x1, y0, z0, x1, y0, z1, color, alpha);
		edge(buffer, matrix, x0, y1, z0, x1, y1, z0, color, alpha);
		edge(buffer, matrix, x0, y1, z0, x0, y1, z1, color, alpha);
		edge(buffer, matrix, x0, y0, z1, x1, y0, z1, color, alpha);
		edge(buffer, matrix, x0, y0, z1, x0, y1, z1, color, alpha);
		edge(buffer, matrix, x1, y1, z0, x1, y1, z1, color, alpha);
		edge(buffer, matrix, x0, y1, z1, x1, y1, z1, color, alpha);
		edge(buffer, matrix, x1, y0, z1, x1, y1, z1, color, alpha);
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix,
							 float ax, float ay, float az,
							 float bx, float by, float bz,
							 float cx, float cy, float cz,
							 float dx, float dy, float dz,
							 int[] color, int alpha) {
		vertex(buffer, matrix, ax, ay, az, color, alpha);
		vertex(buffer, matrix, bx, by, bz, color, alpha);
		vertex(buffer, matrix, cx, cy, cz, color, alpha);
		vertex(buffer, matrix, dx, dy, dz, color, alpha);
	}

	private static void edge(VertexConsumer buffer, Matrix4f matrix,
							 float ax, float ay, float az,
							 float bx, float by, float bz,
							 int[] color, int alpha) {
		vertex(buffer, matrix, ax, ay, az, color, alpha);
		vertex(buffer, matrix, bx, by, bz, color, alpha);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f matrix,
							   float x, float y, float z, int[] color, int alpha) {
		buffer.vertex(matrix, x, y, z).color(color[0], color[1], color[2], alpha);
	}
}
