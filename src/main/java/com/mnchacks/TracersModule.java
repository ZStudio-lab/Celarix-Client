package com.mnchacks;

import net.minecraft.text.Text;

/**
 * Tracers — ESP companion that draws a line from the camera to every tracked
 * entity, so you can see exactly where players and mobs are without scanning
 * the whole screen. Rendered by {@link EspModule} in the same wall-penetrating
 * pass.
 */
public final class TracersModule {

	private static boolean enabled = false;

	private TracersModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Tracers: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}