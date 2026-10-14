package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SpawnerBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent world memory — the closest a client mod can get to "omniscient".
 *
 * <p>The client only ever receives entities within the server's tracking range
 * and blocks for loaded chunks. This class cannot exceed that, but it remembers
 * everything it <em>has</em> been told and keeps it around after it leaves
 * view: player/mob last-known positions, every bed, chest and spawner it has
 * seen, incoming projectiles/TNT, and the parsed scoreboard sidebar. Other
 * modules (ESP, aimbot, Bed Wars fighter) read from here so they act on the
 * most complete picture available.</p>
 */
public final class Awareness {

	/** How long (ticks) a target stays in memory after leaving tracking range. */
	private static final int STALE_TICKS = 600; // 30s
	/** Horizontal radius (blocks) scanned for beds/chests/spawners. */
	private static final int BLOCK_SCAN = 24;
	/** Vertical radius of the block scan. */
	private static final int BLOCK_SCAN_Y = 6;
	/** Frames between block scans. */
	private static final int BLOCK_SCAN_INTERVAL = 20;

	/** A remembered entity. Mutable; only touched on the client thread. */
	public static final class Tracked {
		public final UUID id;
		public final boolean player;
		public String name;
		public Vec3d pos;
		public Vec3d velocity = Vec3d.ZERO;
		public int lastSeen;
		public boolean alive = true;
		public boolean teammate;
		public boolean visible;
		/** Shopkeepers and other fixed interactables: remembered forever, never ghosts. */
		public boolean sticky;
		/** True for merchants (shopkeepers) — usable interactables. */
		public boolean merchant;

		Tracked(UUID id, String name, boolean player) {
			this.id = id;
			this.name = name;
			this.player = player;
		}
	}

	/** An incoming projectile or primed TNT heading roughly at the player. */
	public static final class Threat {
		public final String name;
		public final Vec3d pos;
		public final Vec3d velocity;
		public final double distance;

		Threat(String name, Vec3d pos, Vec3d velocity, double distance) {
			this.name = name;
			this.pos = pos;
			this.velocity = velocity;
			this.distance = distance;
		}
	}

	private static final Map<UUID, Tracked> entities = new HashMap<>();
	private static final Map<BlockPos, int[]> beds = new HashMap<>();
	private static final Set<BlockPos> chests = new HashSet<>();
	private static final Set<BlockPos> spawners = new HashSet<>();
	private static final List<Threat> threats = new ArrayList<>();
	private static final List<String> sidebarLines = new ArrayList<>();

	private static int tick = 0;
	private static int scanCooldown = 0;
	private static String sidebarTitle = "";

	private Awareness() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Awareness::update);
	}

	/** Clears all memory (world change / disconnect). */
	public static void reset() {
		entities.clear();
		beds.clear();
		chests.clear();
		spawners.clear();
		threats.clear();
		sidebarLines.clear();
		sidebarTitle = "";
		tick = 0;
		scanCooldown = 0;
	}

	private static void update(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || player.isSpectator()) {
			return;
		}
		tick++;

		updateEntities(client, player);

		if (scanCooldown-- <= 0) {
			scanBlocks(client, player);
			scanCooldown = BLOCK_SCAN_INTERVAL;
		}

		updateThreats(client, player);
		updateSidebar(client);
	}

	private static void updateEntities(MinecraftClient client, ClientPlayerEntity player) {
		Set<UUID> seen = new HashSet<>();
		Box box = player.getBoundingBox().expand(256.0D);
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box, e -> !e.isRemoved())) {
			if (entity == player) {
				continue;
			}
			boolean isPlayer = entity instanceof PlayerEntity;
			UUID id = entity.getUuid();
			seen.add(id);

			Tracked tracked = entities.get(id);
			if (tracked == null) {
				tracked = new Tracked(id, entity.getName().getString(), isPlayer);
				entities.put(id, tracked);
			}
			tracked.name = entity.getName().getString();
			tracked.pos = entity.getPos();
			tracked.velocity = entity.getVelocity();
			tracked.alive = entity.isAlive();
			tracked.visible = true;
			tracked.lastSeen = tick;
			tracked.teammate = isPlayer && Teams.isTeammate(entity, player);
			// Usables (shopkeepers) are static: remember them permanently and
			// never let them degrade into ghosts.
			tracked.merchant = entity instanceof MerchantEntity;
			if (tracked.merchant) {
				tracked.sticky = true;
			}
		}

		Iterator<Map.Entry<UUID, Tracked>> it = entities.entrySet().iterator();
		while (it.hasNext()) {
			Tracked tracked = it.next().getValue();
			if (!seen.contains(tracked.id)) {
				tracked.visible = false;
			}
			if (!tracked.alive || (!tracked.sticky && tick - tracked.lastSeen > STALE_TICKS)) {
				it.remove();
			}
		}
	}

	private static void scanBlocks(MinecraftClient client, ClientPlayerEntity player) {
		BlockPos origin = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(origin.add(-BLOCK_SCAN, -BLOCK_SCAN_Y, -BLOCK_SCAN),
				origin.add(BLOCK_SCAN, BLOCK_SCAN_Y, BLOCK_SCAN))) {
			BlockState state = client.world.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock bed) {
				beds.put(pos.toImmutable(), dyeRgb(bed.getColor()));
			} else if (beds.containsKey(pos)) {
				// A remembered bed is no longer here — it was broken.
				beds.remove(pos);
			}
			if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) {
				chests.add(pos.toImmutable());
			} else if (chests.contains(pos)) {
				// A remembered chest is gone (looted/broken) — stop showing it.
				chests.remove(pos);
			}
			if (state.getBlock() instanceof SpawnerBlock) {
				spawners.add(pos.toImmutable());
			}
		}
	}

	private static void updateThreats(MinecraftClient client, ClientPlayerEntity player) {
		threats.clear();
		Box box = player.getBoundingBox().expand(40.0D);
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e instanceof ProjectileEntity || e instanceof TntEntity)) {
			Vec3d pos = entity.getPos();
			Vec3d velocity = entity.getVelocity();
			double speed = velocity.length();
			Vec3d toPlayer = player.getPos().add(0.0D, player.getHeight() * 0.5D, 0.0D).subtract(pos);
			double distance = toPlayer.length();
			if (distance < 0.5D || speed < 0.15D) {
				continue;
			}
			double aim = velocity.normalize().dotProduct(toPlayer.normalize());
			if (aim > 0.45D) {
				threats.add(new Threat(entity.getName().getString(), pos, velocity, distance));
			}
		}
		threats.sort((a, b) -> Double.compare(a.distance, b.distance));
	}

	private static void updateSidebar(MinecraftClient client) {
		sidebarLines.clear();
		Scoreboard scoreboard = client.world.getScoreboard();
		ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
		if (objective == null) {
			sidebarTitle = "";
			return;
		}
		sidebarTitle = objective.getDisplayName().getString();
		List<ScoreboardEntry> entries = new ArrayList<>(scoreboard.getScoreboardEntries(objective));
		entries.sort((a, b) -> Integer.compare(b.value(), a.value()));
		for (ScoreboardEntry entry : entries) {
			if (entry.hidden()) {
				continue;
			}
			sidebarLines.add(entry.name().getString());
			if (sidebarLines.size() >= 20) {
				break;
			}
		}
	}

	private static int[] dyeRgb(DyeColor color) {
		// 1.21.5 dropped getColorComponents() (the float[] form); the entity
		// colour is still available as a packed ARGB int.
		int rgb = color.getEntityColor();
		return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
	}

	// --- Queries -----------------------------------------------------------

	/** Every remembered enemy player (visible or last-known). */
	public static List<Tracked> enemies() {
		List<Tracked> out = new ArrayList<>();
		for (Tracked tracked : entities.values()) {
			if (tracked.player && !tracked.teammate) {
				out.add(tracked);
			}
		}
		return out;
	}

	public static List<Tracked> enemyPlayers() {
		return enemies();
	}

	/** Remembered enemy players no longer in the client world (ghosts). */
	public static List<Tracked> ghosts() {
		List<Tracked> out = new ArrayList<>();
		for (Tracked tracked : entities.values()) {
			if (tracked.player && !tracked.teammate && !tracked.visible) {
				out.add(tracked);
			}
		}
		return out;
	}

	public static int enemyCount() {
		int count = 0;
		for (Tracked tracked : entities.values()) {
			if (tracked.player && !tracked.teammate) {
				count++;
			}
		}
		return count;
	}

	public static int ghostCount() {
		int count = 0;
		for (Tracked tracked : entities.values()) {
			if (tracked.player && !tracked.teammate && !tracked.visible) {
				count++;
			}
		}
		return count;
	}

	/** Closest remembered enemy to a point (visible or ghost), or null. */
	public static Tracked nearestEnemy(Vec3d from) {
		Tracked best = null;
		double bestSq = Double.MAX_VALUE;
		for (Tracked tracked : entities.values()) {
			if (!tracked.player || tracked.teammate) {
				continue;
			}
			double distSq = tracked.pos.squaredDistanceTo(from);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = tracked;
			}
		}
		return best;
	}

	/**
	 * Nearest remembered shopkeeper (usable), anywhere it was ever seen.
	 * Merchants are static, so their last-known spot stays valid forever —
	 * this is how the Bed Wars fighter can walk back to a shop it found once.
	 */
	public static Tracked nearestKnownMerchant(Vec3d from) {
		Tracked best = null;
		double bestSq = Double.MAX_VALUE;
		for (Tracked tracked : entities.values()) {
			if (!tracked.merchant || !tracked.alive) {
				continue;
			}
			double distSq = tracked.pos.squaredDistanceTo(from);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = tracked;
			}
		}
		return best;
	}

	public static List<Tracked> knownEntities() {
		return new ArrayList<>(entities.values());
	}

	public static Map<BlockPos, int[]> getBeds() {
		return beds;
	}

	public static Set<BlockPos> getChests() {
		return chests;
	}

	public static Set<BlockPos> getSpawners() {
		return spawners;
	}

	public static List<Threat> getThreats() {
		return threats;
	}

	public static List<String> getSidebarLines() {
		return sidebarLines;
	}

	public static String getSidebarTitle() {
		return sidebarTitle;
	}
}
