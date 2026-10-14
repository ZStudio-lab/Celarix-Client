package com.mnchacks;

import net.minecraft.text.Text;

public final class NoSlowModule {

	private static boolean enabled = false;

	private NoSlowModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("No Slow : " + (enabled ? "ON" : "OFF")), true);
		}
	}
}