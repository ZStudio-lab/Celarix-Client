package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * No Fog — pushes the fog start/end out to beyond the far plane in
 * {@code BackgroundRendererMixin}, so terrain, sky and fluid fog all
 * disappear. You simply see to your render distance with no haze.
 */
public final class NoFogModule {

	private static boolean enabled = false;

	private NoFogModule() {
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
			client.player.sendMessage(Text.literal("No Fog: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
