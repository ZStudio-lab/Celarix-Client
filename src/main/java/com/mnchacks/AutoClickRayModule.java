package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Auto Click Ray — an alternative to the plain Auto Clicker: attacks only
 * while an enemy is under the crosshair (prevents suspicious swings at air
 * that plain autoclickers produce).
 */
public final class AutoClickRayModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoClickRayModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Click Ray: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoClickRayModule::tick);
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
				|| client.interactionManager == null || client.crosshairTarget == null) {
			return;
		}
		if (client.crosshairTarget instanceof EntityHitResult hit
				&& hit.getEntity() instanceof net.minecraft.entity.LivingEntity living
				&& living.isAlive() && Teams.isEnemy(living, player)) {
			client.interactionManager.attackEntity(player, living);
			player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			cooldown = 2;
		}
	}
}