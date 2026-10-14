package com.mnchacks;

import net.minecraft.text.Text;

public final class NoHurtCamModule {

	private static boolean enabled = false;

	private NoHurtCamModule() {
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
			client.player.sendMessage(Text.literal("No Hurt Cam : " + (enabled ? "ON" : "OFF")), true);
		}
	}
}