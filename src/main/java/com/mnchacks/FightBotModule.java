package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Fight Bot — autonomous melee: it locks the same target the
 * aimbot would decide on (players first, closest, never friends/teammates),
 * turns toward it, walks into melee range and swings on every full attack
 * charge, hopping when it runs into a wall.
 *
 * <p>Movement keys are pressed on the bot's behalf and are always released
 * when the target dies, the module is toggled off or the world changes - no
 * stuck-key surprises.</p>
 */
public final class FightBotModule {

	/** Seek-and-destroy radius (Click GUI slider). */
	private static final double DEFAULT_RANGE = 10.0D;
	private static final double MIN_RANGE = 4.0D;
	private static final double MAX_RANGE = 24.0D;
	/** Stop walking once inside vanilla melee reach (3 blocks, squared). */
	private static final double STOP_SQ = 9.0D;
	/** Swing only inside true entity reach (2.85 blocks, squared). */
	private static final double ATTACK_SQ = 8.12D;

	private static boolean enabled = false;
	private static double range = DEFAULT_RANGE;

	private FightBotModule() {
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

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FightBotModule::tick);
	}

	public static void reset() {
		enabled = false;
		releaseKeys(null);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (!enabled) {
			releaseKeys(client);
		}
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Fight Bot: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null
				|| player.isDead()) {
			releaseKeys(client);
			return;
		}
		if (client.currentScreen != null) {
			// Chat/inventory open: hand the keys back to the player.
			releaseKeys(client);
			return;
		}

		Entity target = acquire(client, player);
		if (target == null) {
			releaseKeys(client);
			return;
		}

		// Face the target (body follows the camera on the server).
		Vec3d aim = target.getEyePos();
		Vec3d eyes = player.getEyePos();
		double dx = aim.x - eyes.x;
		double dz = aim.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1.0E-4D) {
			player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		}

		double distSq = target.squaredDistanceTo(player);
		if (distSq > STOP_SQ) {
			// Close the distance; hop over the obstacle we just walked into.
			client.options.forwardKey.setPressed(true);
			client.options.backKey.setPressed(false);
			client.options.jumpKey.setPressed(player.horizontalCollision);
			return;
		}

		// In melee range: stand still and swing on full charge.
		client.options.forwardKey.setPressed(false);
		client.options.jumpKey.setPressed(false);
		if (distSq <= ATTACK_SQ && player.getAttackCooldownProgress(0.0F) >= 1.0F) {
			client.interactionManager.attackEntity(player, target);
			player.swingHand(Hand.MAIN_HAND);
		}
	}

	/** Same target decisions as the aimbot: players first, closest one. */
	private static Entity acquire(MinecraftClient client, ClientPlayerEntity player) {
		List<Entity> candidates = new ArrayList<>(EspModule.getTrackedTargets());
		if (candidates.isEmpty()) {
			Box searchBox = player.getBoundingBox().expand(range);
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
					|| entity.squaredDistanceTo(player) > range * range) {
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

	private static void releaseKeys(MinecraftClient client) {
		if (client == null || client.options == null) {
			return;
		}
		client.options.forwardKey.setPressed(false);
		client.options.backKey.setPressed(false);
		client.options.jumpKey.setPressed(false);
	}
}
