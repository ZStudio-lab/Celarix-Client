package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class AntiVoidModule {

	private static boolean enabled = false;
	private static double lastSafeY = Double.NEGATIVE_INFINITY;
	private static int cooldown = 0;

	private AntiVoidModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AntiVoidModule::tick);
	}

	public static void reset() {
		enabled = false;
		lastSafeY = Double.NEGATIVE_INFINITY;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		lastSafeY = Double.NEGATIVE_INFINITY;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Anti Void: " + (enabled ? "ON" : "OFF")), true);
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
		if (player == null || client.world == null) {
			return;
		}
		if (player.isOnGround()) {
			lastSafeY = player.getY();
			return;
		}
		if (lastSafeY != Double.NEGATIVE_INFINITY && player.getY() < lastSafeY - 30.0D) {
			player.setPosition(player.getX(), lastSafeY, player.getZ());
			player.setVelocity(0.0D, 0.0D, 0.0D);
			player.fallDistance = 0.0F;
			cooldown = 40;
		}
	}
}