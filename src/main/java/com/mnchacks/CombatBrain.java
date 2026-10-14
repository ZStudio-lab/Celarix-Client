package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Combat targeting brain — the "Technoblade mode" decision layer shared by
 * PVP mode and the Bed Wars fighter.
 *
 * <p><b>Sticky lock:</b> once a target is acquired it is followed relentlessly
 * (through walls, past render distance via world memory) until it dies or the
 * pursuit window expires.</p>
 *
 * <p><b>Threat retarget:</b> the instant we take damage, whoever hit us becomes
 * the top-priority target for a few seconds — melee attackers are detected by
 * proximity at the moment of the hit, shooters by projectile ownership. The
 * last attacker always wins the lock.</p>
 *
 * <p><b>Swarm fighting (1v3/1v5):</b> when several enemies are close, targets
 * are scored by distance + health, so the bot focus-fires the closest and
 * weakest enemy first — killing swarm members fast instead of spreading
 * damage. Living enemy health is client-visible, so this is real data.</p>
 */
public final class CombatBrain {

	/** An attacker must be this close when we take damage to count as the hitter. */
	private static final double THREAT_LOCK_RADIUS = 7.0D;
	/** Projectile owners this close count as attackers (arrows point home). */
	private static final double ARROW_LOCK_RADIUS = 40.0D;
	/** How far a locked target is chased past detection range. */
	public static final double PURSUIT_RADIUS = 32.0D;
	/** Attacker priority lasts this many ticks (5s of "you hit me first"). */
	private static final int THREAT_PRIORITY_TICKS = 100;
	/** How long a lost lock is chased through memory before being dropped. */
	private static final int PURSUIT_MEMORY_TICKS = 300;

	private static UUID lockedId = null;
	private static UUID threatId = null;
	private static int threatTicks = 0;
	private static int lastHurtTime = 0;
	/** Ticks spent chasing a locked target that is currently out of sight. */
	private static int lostTicks = 0;
	private static String lockedName = null;

	private CombatBrain() {
	}

	public static void reset() {
		lockedId = null;
		threatId = null;
		threatTicks = 0;
		lastHurtTime = 0;
		lostTicks = 0;
		lockedName = null;
	}

	/** Call every tick: watches for fresh damage and retargets the attacker. */
	public static void onTick(MinecraftClient client, ClientPlayerEntity player) {
		if (threatTicks > 0) {
			threatTicks--;
		}
		boolean hurtNow = player.hurtTime > 0;
		if (hurtNow && lastHurtTime == 0) {
			onDamaged(client, player);
		}
		lastHurtTime = player.hurtTime;
	}

	/**
	 * Picks the entity to fight right now, or null while pursuing a lost lock
	 * (see {@link #isPursuing()}).
	 */
	public static Entity acquireTarget(MinecraftClient client, ClientPlayerEntity player,
									   double detectRadius, double pursuitRadius) {
		// 1) Whoever attacked us last owns the lock while the priority lasts.
		if (threatId != null && threatTicks > 0) {
			Entity threat = findAlive(client, player, threatId, pursuitRadius);
			if (threat != null) {
				lockedId = threat.getUuid();
				lockedName = threat.getName().getString();
				lostTicks = 0;
				return threat;
			}
		}

		// 2) Sticky lock: stay on the current target.
		if (lockedId != null) {
			Entity locked = findAlive(client, player, lockedId, pursuitRadius);
			if (locked != null) {
				lostTicks = 0;
				return locked;
			}
			// Out of sight: keep the lock alive through world memory for a while.
			if (awarenessKnows(lockedId) && ++lostTicks < PURSUIT_MEMORY_TICKS) {
				return null;
			}
		}

		// 3) Acquire the best new target: close AND weak first.
		Entity best = bestScored(client, player, detectRadius);
		if (best != null) {
			lockedId = best.getUuid();
			lockedName = best.getName().getString();
			lostTicks = 0;
			threatTicks = 0;
		} else {
			lockedId = null;
		}
		return best;
	}

	/** True while chasing a locked target that is currently out of sight. */
	public static boolean isPursuing() {
		return lockedId != null && lostTicks > 0 && lostTicks < PURSUIT_MEMORY_TICKS;
	}

	/** Last-known position of the pursued target, or null. */
	public static Vec3d chaseGoal() {
		if (lockedId == null) {
			return null;
		}
		for (Awareness.Tracked tracked : Awareness.knownEntities()) {
			if (lockedId.equals(tracked.id)) {
				return tracked.pos;
			}
		}
		return null;
	}

	/** Display name of the locked target, even when it is out of sight. */
	public static String lockedName() {
		return lockedName;
	}

	/** How many enemies are around the player right now (for the 1vN readout). */
	public static int countEnemies(MinecraftClient client, ClientPlayerEntity player, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		int count = 0;
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e != player && e.isAlive() && Teams.isEnemy(e, player))) {
			count++;
		}
		return count;
	}

	// --- Internals ---------------------------------------------------------

	private static void onDamaged(MinecraftClient client, ClientPlayerEntity player) {
		// Arrows first: the shooter is identifiable even at 40 blocks.
		Entity shooter = projectileAttacker(client, player);
		if (shooter != null) {
			setThreat(shooter);
			return;
		}
		// Melee: whoever is in hitting range at the moment of the hit.
		Entity attacker = nearestEnemyWithin(client, player, THREAT_LOCK_RADIUS);
		if (attacker != null) {
			setThreat(attacker);
		}
	}

	private static void setThreat(Entity attacker) {
		threatId = attacker.getUuid();
		threatTicks = THREAT_PRIORITY_TICKS;
		lockedId = threatId;
		lockedName = attacker.getName().getString();
		lostTicks = 0;
	}

	private static Entity projectileAttacker(MinecraftClient client, ClientPlayerEntity player) {
		Box box = player.getBoundingBox().expand(ARROW_LOCK_RADIUS);
		for (ProjectileEntity projectile : client.world.getEntitiesByClass(ProjectileEntity.class, box,
				p -> p.isAlive() && p.squaredDistanceTo(player) <= ARROW_LOCK_RADIUS * ARROW_LOCK_RADIUS)) {
			Entity owner = projectile.getOwner();
			if (owner != null && owner != player && Teams.isEnemy(owner, player)) {
				return owner;
			}
		}
		return null;
	}

	private static Entity nearestEnemyWithin(MinecraftClient client, ClientPlayerEntity player, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		Entity best = null;
		double bestSq = radius * radius;
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e != player && e.isAlive() && Teams.isEnemy(e, player))) {
			double distSq = entity.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = entity;
			}
		}
		return best;
	}

	private static Entity findAlive(MinecraftClient client, ClientPlayerEntity player, UUID id, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e.isAlive() && id.equals(e.getUuid()))) {
			if (Teams.isEnemy(entity, player)) {
				return entity;
			}
		}
		return null;
	}

	/** Score = distance + health x1.5 — kills the closest, weakest enemy first. */
	private static Entity bestScored(MinecraftClient client, ClientPlayerEntity player, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e != player && e.isAlive() && Teams.isEnemy(e, player))) {
			double health = entity instanceof LivingEntity living ? living.getHealth() : 20.0D;
			double score = Math.sqrt(entity.squaredDistanceTo(player)) + health * 1.5D;
			if (score < bestScore) {
				bestScore = score;
				best = entity;
			}
		}
		return best;
	}

	private static boolean awarenessKnows(UUID id) {
		for (Awareness.Tracked tracked : Awareness.knownEntities()) {
			if (id.equals(tracked.id)) {
				return true;
			}
		}
		return false;
	}
}