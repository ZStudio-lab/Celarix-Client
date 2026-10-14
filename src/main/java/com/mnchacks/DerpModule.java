package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Derp — purely cosmetic: spins your own head model wildly while the
 * module is on. Purely a client-side render effect (the render state of your
 * player model is overwritten every frame) — your actual aim and what other
 * players see are untouched.
 */
public final class DerpModule {

	private static boolean enabled = false;

	private DerpModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Derp: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
