package com.mnchacks;

import com.mnchacks.mixin.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/**
 * KillAura — automatically attacks every enemy within range without needing
 * the crosshair anywhere near them: 360-degree coverage, attacks come out in
 * any direction, even behind you.
 *
 * <p>Hits are timed to the vanilla attack cooldown (full-charge swings), and
 * with the Reach hack enabled the range extends to the 6-block server limit.
 * No rotation is applied — "silent" attacks that never expose the aimbot.</p>
 */
public final class KillAuraModule {

	private static boolean enabled = false;
	private static double range = 4.5D;
	private static int cooldown = 0;

	private KillAuraModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(KillAuraModule::tick);
	}

	public static double getRange() {
		return range;
	}

	public static void setRange(double value) {
		range = value;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("KillAura: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null || player.isDead()) {
			return;
		}

		// Full-charge swings only: every hit lands at maximum damage.
		if (player.getAttackCooldownProgress(0.0F) < 1.0F) {
			return;
		}

		Box box = player.getBoundingBox().expand(range);
		LivingEntity target = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity entity : client.world.getEntitiesByClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && Teams.isEnemy(e, player))) {
			double distSq = entity.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				target = entity;
			}
		}

		if (target != null) {
			client.interactionManager.attackEntity(player, target);
			player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			cooldown = 2;
		}
	}
}