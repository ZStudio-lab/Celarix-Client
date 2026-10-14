package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Bow Aimbot — automatic projectile aiming. While you draw a bow, it picks
 * a target with the same decisions as the aimbot (players first, closest one,
 * never friends/teammates/spectators), points the bow straight at it and
 * solves the arrow arc: an iterative simulation of the real arrow physics
 * (charge curve, drag 0.99/tick, gravity 0.05/tick) finds the pitch that puts
 * the arrow on the target at its distance. Horizontal velocity of the target
 * is led a little, so strafing targets still get hit.
 */
public final class BowAimbotModule {

	private static final double RANGE = 64.0D;
	/** Don't fight the aim before the bow is meaningfully drawn. */
	private static final float MIN_PULL = 0.35F;

	private static boolean enabled = false;

	private BowAimbotModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BowAimbotModule::tick);
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Bow Aimbot: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		ItemStack held = player.getMainHandStack();
		if (held.getItem() != Items.BOW || !player.isUsingItem()) {
			return;
		}
		// Vanilla pull-progress curve: f = t/20, f = (f*f + 2f)/3, clamped to 1.
		float t = player.getItemUseTime() / 20.0F;
		float pull = (t * t + 2.0F * t) / 3.0F;
		if (pull > 1.0F) {
			pull = 1.0F;
		}
		if (pull < MIN_PULL) {
			return;
		}
		double speed = pull * 3.0D;

		Entity target = acquire(client, player);
		if (target == null) {
			return;
		}

		Vec3d eye = player.getEyePos();
		// Lead the target's horizontal movement a little; vertical velocity is
		// jump noise, same as the aimbot.
		Vec3d velocity = target.getVelocity();
		Vec3d lead = new Vec3d(velocity.x * 4.0D, 0.0D, velocity.z * 4.0D);
		if (lead.lengthSquared() > 4.0D) {
			lead = lead.normalize().multiply(2.0D);
		}
		Vec3d aim = target.getEyePos().add(lead);

		double dx = aim.x - eye.x;
		double dy = aim.y - eye.y;
		double dz = aim.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 0.5D) {
			return;
		}

		// Yaw: straight at the target.
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);

		// Pitch: solved against the arrow simulation.
		double pitch = -(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI));
		Vec3d flatDir = new Vec3d(dx / horizontal, 0.0D, dz / horizontal);
		for (int i = 0; i < 30; i++) {
			double hitY = simulateHeightAt(client, eye, flatDir, pitch, speed, horizontal);
			if (Double.isNaN(hitY)) {
				break;
			}
			double error = hitY - aim.y;
			if (Math.abs(error) < 0.05D) {
				break;
			}
			// Arc below the target -> aim higher (pitch decreases).
			pitch += Math.toDegrees(Math.atan2(error, horizontal));
			pitch = MathHelper.clamp(pitch, -89.0D, 89.0D);
		}
		player.setPitch((float) pitch);
	}

	/**
	 * Simulates the arrow (drag 0.99, gravity 0.05 per tick) and returns its
	 * height at the target's horizontal distance - or NaN when terrain blocks
	 * the shot or the arrow lands first.
	 */
	private static double simulateHeightAt(MinecraftClient client, Vec3d eye, Vec3d flatDir,
										   double pitch, double speed, double horizontalDist) {
		double radians = Math.toRadians(pitch);
		double cos = Math.cos(radians);
		Vec3d velocity = new Vec3d(flatDir.x * cos, -Math.sin(radians), flatDir.z * cos)
				.multiply(speed);
		Vec3d pos = eye;
		for (int i = 0; i < 120; i++) {
			Vec3d next = pos.add(velocity);
			double startX = pos.x - eye.x;
			double startZ = pos.z - eye.z;
			double nextX = next.x - eye.x;
			double nextZ = next.z - eye.z;
			double startHorizontal = Math.sqrt(startX * startX + startZ * startZ);
			double nextHorizontal = Math.sqrt(nextX * nextX + nextZ * nextZ);
			if (nextHorizontal >= horizontalDist && nextHorizontal > startHorizontal) {
				double fraction = (horizontalDist - startHorizontal) / (nextHorizontal - startHorizontal);
				return pos.y + (next.y - pos.y) * fraction;
			}
			if (!client.world.getBlockState(BlockPos.ofFloored(next)).isAir()) {
				return Double.NaN;
			}
			pos = next;
			velocity = velocity.multiply(0.99D).subtract(0.0D, 0.05D, 0.0D);
		}
		return Double.NaN;
	}

	/** Same target decisions as the aimbot: players first, closest one. */
	private static Entity acquire(MinecraftClient client, ClientPlayerEntity player) {
		List<Entity> candidates = new ArrayList<>(EspModule.getTrackedTargets());
		if (candidates.isEmpty()) {
			Box searchBox = player.getBoundingBox().expand(RANGE);
			candidates.addAll(client.world.getEntitiesByClass(Entity.class, searchBox,
					e -> Teams.isEnemy(e, player)));
		}
		Entity bestPlayer = null;
		double bestPlayerDist = Double.MAX_VALUE;
		Entity bestMob = null;
		double bestMobDist = Double.MAX_VALUE;
		for (Entity entity : candidates) {
			if (entity == player || !entity.isAlive() || entity.isRemoved()
					|| !Teams.isEnemy(entity, player)
					|| entity.squaredDistanceTo(player) > RANGE * RANGE) {
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
		return bestPlayer != null ? bestPlayer : bestMob;
	}
}
