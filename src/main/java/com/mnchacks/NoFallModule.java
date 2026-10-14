package com.mnchacks;

import net.minecraft.text.Text;

/**
 * No Fall — while falling, the movement packet claims the player is on the
 * ground, so the server never accumulates fall damage. Classic, and behaves
 * like holding a feather.
 */
public final class NoFallModule {

	private static boolean enabled = false;

	private NoFallModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("No Fall: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}