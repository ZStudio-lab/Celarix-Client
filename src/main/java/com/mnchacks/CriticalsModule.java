package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

public final class CriticalsModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private CriticalsModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(CriticalsModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Criticals: " + (enabled ? "ON" : "OFF")), true);
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
		if (player == null || client.world == null || !player.isOnGround() || player.isTouchingWater()) {
			return;
		}
		// Only hop when an enemy is in swing range and a hit is winding up.
		Box box = player.getBoundingBox().expand(5.0D);
		boolean enemyNear = !client.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
				box, e -> e != player && e.isAlive() && Teams.isEnemy(e, player)).isEmpty();
		if (!enemyNear || player.getAttackCooldownProgress(0.0F) < 0.6F) {
			return;
		}
		// Small hop: while falling the next hit lands as a critical.
		player.setVelocity(player.getVelocity().x, 0.35D, player.getVelocity().z);
		player.fallDistance = 0.0F;
		cooldown = 3;
	}
}