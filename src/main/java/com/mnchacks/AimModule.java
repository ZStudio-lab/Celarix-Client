package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Aimbot with layered target decisions.
 *
 * <p>Targeting is fed by the ESP's entity tracking (see
 * {@link EspModule#getTrackedTargets()}), which sees through walls and over a
 * wide range. Every candidate passes the same gate: never yourself, never
 * spectators, never scoreboard teammates, never name-coloured teammates, and
 * never anyone on the Friends list (middle-click a player to toggle that).
 * Among the survivors the lock prefers <b>players over mobs</b> and takes the
 * <b>closest</b> one - the crosshair only breaks near-ties. The lock is
 * <b>sticky</b>: once acquired the target is kept - even behind you or behind
 * a wall - until it dies, leaves range, or the module is toggled off, so the
 * aim never thrashes between two targets mid-fight.</p>
 *
 * <p>Turning looks natural: a short reaction delay on new targets, an eased
 * turn with a slight sway, a per-tick turn cap and a small per-lock wobble.</p>
 */
public final class AimModule {

	/** Default max distance to acquire and keep a target (GUI slider 16-128). */
	private static final double DEFAULT_RANGE = 64.0D;
	private static final double MIN_RANGE = 16.0D;
	private static final double MAX_RANGE = 128.0D;

	// --- Human-likeness / detection hardening -----------------------------

	/** Ticks to "notice" a new target before starting to turn (reaction time). */
	private static final int REACTION_TICKS = 3;
	/** Fraction of the remaining angle applied per tick (the easing curve). */
	private static final float TURN_SPEED = 0.38F;
	/** Slight sway on the easing so the turn is not a constant ratio. */
	private static final float TURN_SPEED_SWAY = 0.05F;
	/** Absolute cap on degrees turned per tick - fast flicks, still smooth. */
	private static final float MAX_TURN_PER_TICK = 40.0F;
	/** Below this angle the remaining delta is applied as-is (clean converge). */
	private static final float CONVERGE_ANGLE = 1.2F;
	/** How much of the target's per-tick velocity the aim leads by. */
	private static final double LEAD_TICKS = 2.0D;
	/** Tiny stable per-lock wobble, in blocks (rolled per target, not per tick). */
	private static final double AIM_SCATTER = 0.05D;

	private static final Random RANDOM = new Random();

	private static boolean enabled = false;
	/** Max distance to acquire and keep a target (GUI slider). */
	private static double range = DEFAULT_RANGE;
	/** The target we are currently tracking, kept between ticks. */
	private static Entity target = null;
	/** Ticks the current target has been held (drives the reaction delay). */
	private static int targetTicks = 0;
	/** Eased aim point - absorbs target jitter so the crosshair glides. */
	private static Vec3d smoothedPoint = null;
	/** Which target the smoothing trail belongs to. */
	private static java.util.UUID smoothedFor = null;
	/** Tiny stable wobble rolled once per lock (never re-rolled per tick). */
	private static Vec3d scatter = Vec3d.ZERO;

	/** True while the orchestrators is the reason the aimbot is on. */
	private static boolean forced = false;
	/** Whether the aimbot was already on before PVP forced it. */
	private static boolean enabledBeforeForce = false;

	private AimModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static double getRange() {
		return range;
	}

	public static void setRange(double value) {
		range = MathHelper.clamp(value, MIN_RANGE, MAX_RANGE);
	}

	public static Entity getTarget() {
		return target;
	}

	/**
	 * Automatic enable/disable used by the orchestrators. Restores the previous
	 * on/off state when the assist is no longer needed.
	 */
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
				target = null;
				targetTicks = 0;
			}
		}
	}

	public static void uiToggle() {
		enabled = !enabled;
		target = null;
		targetTicks = 0;
		smoothedPoint = null;
		smoothedFor = null;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.literal("Aimbot: " + (enabled ? "ON" : "OFF")), true);
		}
	}
	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AimModule::tick);
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.aimToggle.wasPressed()) {
			enabled = !enabled;
			target = null;
			targetTicks = 0;
			if (client.player != null) {
				client.player.sendMessage(Text.literal("Aimbot: " + (enabled ? "ON" : "OFF")), true);
			}
		}

		if (!enabled) {
			target = null;
			targetTicks = 0;
			return;
		}

		ClientPlayerEntity player = client.player;
		// Don't fight the player while a screen (chat, inventory...) is open.
		if (player == null || client.world == null || client.currentScreen != null || player.isSpectator()) {
			target = null;
			targetTicks = 0;
			return;
		}

		// Keep the current lock as long as it is still valid, even if the target
		// is now behind us or behind a wall. Only look for a new one when we have
		// no usable target.
		if (!isValid(client, player, target)) {
			Entity best = acquire(client, player);
			if (best == null) {
				target = null;
				targetTicks = 0;
				return;
			}
			target = best;
			targetTicks = 0;
		}
		targetTicks++;

		// Simulate human reaction time before engaging a new target.
		if (targetTicks < REACTION_TICKS) {
			return;
		}

		Vec3d aim = aimPoint(target);
		if (!target.getUuid().equals(smoothedFor)) {
			// New lock: restart the easing trail, roll a fresh tiny scatter.
			smoothedFor = target.getUuid();
			smoothedPoint = aim;
			scatter = new Vec3d(
					(RANDOM.nextDouble() - 0.5D) * 2.0D * AIM_SCATTER,
					(RANDOM.nextDouble() - 0.5D) * 2.0D * AIM_SCATTER,
					(RANDOM.nextDouble() - 0.5D) * 2.0D * AIM_SCATTER);
		}
		aim = aim.add(scatter);
		// Ease the aim point itself so sudden target jabs never yank the
		// crosshair - this is what removes the laggy catch-up wobble.
		smoothedPoint = smoothedPoint.add(aim.subtract(smoothedPoint).multiply(0.5D));

		rotateToward(player, smoothedPoint);
	}

	/** A target stays valid regardless of direction/line-of-sight, until it dies or leaves range. */
	private static boolean isValid(MinecraftClient client, ClientPlayerEntity player, Entity entity) {
		if (entity == null || entity == player || entity == client.getCameraEntity()) {
			return false;
		}
		if (!entity.isAlive() || entity.isRemoved()) {
			return false;
		}
		if (!isCandidate(entity, player)) {
			return false;
		}
		return entity.squaredDistanceTo(player) <= range * range;
	}

	/**
	 * Decides who to lock next. The gate ({@code isCandidate}) already threw
	 * out ourselves, spectators, teammates and friends; among the survivors:
	 *
	 * <ol>
	 *   <li>players always win over mobs</li>
	 *   <li>the closest candidate wins (crosshair angle only breaks ties)</li>
	 * </ol>
	 *
	 * <p>Uses the ESP's tracked entity list so targets are seen through
	 * walls; falls back to a world scan if the ESP has no data yet.</p>
	 */
	private static Entity acquire(MinecraftClient client, ClientPlayerEntity player) {
		List<Entity> candidates = new ArrayList<>(EspModule.getTrackedTargets());
		if (candidates.isEmpty()) {
			Box searchBox = player.getBoundingBox().expand(range);
			candidates.addAll(client.world.getEntitiesByClass(Entity.class, searchBox,
					e -> isCandidate(e, player)));
		}

		Entity bestPlayer = null;
		double bestPlayerDist = Double.MAX_VALUE;
		Entity bestMob = null;
		double bestMobDist = Double.MAX_VALUE;

		for (Entity entity : candidates) {
			if (!isValid(client, player, entity)) {
				continue;
			}
			double distSq = entity.squaredDistanceTo(player);
			if (entity instanceof PlayerEntity) {
				if (distSq < bestPlayerDist) {
					bestPlayer = entity;
					bestPlayerDist = distSq;
				}
			} else if (distSq < bestMobDist) {
				bestMob = entity;
				bestMobDist = distSq;
			}
		}

		// Players first, closest of them; hostile mobs only while no player
		// is anywhere in range.
		return bestPlayer != null ? bestPlayer : bestMob;
	}

	private static boolean isCandidate(Entity entity, PlayerEntity self) {
		// Never target teammates (or ourselves).
		return Teams.isEnemy(entity, self);
	}

	/** Aim point: the target's head (eye level), leading horizontal movement only. */
	private static Vec3d aimPoint(Entity entity) {
		// Vertical velocity is just gravity noise (jumps, falls, knockback) -
		// leading by it threw the aim above the head on every jump. Lead
		// horizontally only, capped so knockback spikes cannot slingshot it.
		Vec3d velocity = entity.getVelocity();
		Vec3d lead = new Vec3d(velocity.x * LEAD_TICKS, 0.0D, velocity.z * LEAD_TICKS);
		if (lead.lengthSquared() > 1.0D) {
			lead = lead.normalize().multiply(1.0D);
		}
		// Head lock: eye level tracks the head through sneaking and size changes.
		return entity.getEyePos().add(lead);
	}

	/**
	 * Turns toward the point with a smooth easing curve: fast on large angles,
	 * gently converging on small ones - never overshooting, never micro-shaking.
	 */
	private static void rotateToward(ClientPlayerEntity player, Vec3d point) {
		Vec3d delta = point.subtract(player.getEyePos());

		float targetYaw = (float) (MathHelper.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
		float targetPitch = (float) (MathHelper.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))
				* (180.0D / Math.PI));
		targetPitch = -targetPitch; // Minecraft pitch: negative = looking up

		float yawDelta = MathHelper.wrapDegrees(targetYaw - player.getYaw());
		float pitchDelta = MathHelper.wrapDegrees(targetPitch - player.getPitch());

		// Constant easing with a whisper of sway - no per-tick speed lottery.
		float strength = TURN_SPEED + (RANDOM.nextFloat() - 0.5F) * 2.0F * TURN_SPEED_SWAY;

		float yawStep = yawDelta * strength;
		float pitchStep = pitchDelta * strength;

		// Cap only the big flicks; close range converges exactly so the
		// crosshair settles on the target instead of orbiting it.
		if (Math.abs(yawDelta) <= CONVERGE_ANGLE) {
			yawStep = yawDelta;
		} else {
			yawStep = MathHelper.clamp(yawStep, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
		}
		if (Math.abs(pitchDelta) <= CONVERGE_ANGLE) {
			pitchStep = pitchDelta;
		} else {
			pitchStep = MathHelper.clamp(pitchStep, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
		}

		player.setYaw(player.getYaw() + yawStep);
		player.setPitch(MathHelper.clamp(player.getPitch() + pitchStep, -90.0F, 90.0F));
	}
}
