package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Auto Jump — variant that keeps hopping while the jump key is held (helps
 * with parkour routes and climbing hills quickly).
 */
public final class AutoHopModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoHopModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Hop: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoHopModule::tick);
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
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		if (player.isOnGround() && player.horizontalCollision) {
			player.setVelocity(player.getVelocity().x, 0.42D, player.getVelocity().z);
			player.fallDistance = 0.0F;
			cooldown = 4;
		}
	}
}