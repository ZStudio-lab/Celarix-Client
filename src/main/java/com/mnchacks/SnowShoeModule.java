package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Snow Shoe — walk on top of powder snow as if wearing leather boots (see
 * {@code PowderSnowBlockMixin}: the vanilla walk-on check returns true while
 * this module is enabled). You stop sinking in, and you can still dig
 * yourself out with sneak.
 */
public final class SnowShoeModule {

	private static boolean enabled = false;

	private SnowShoeModule() {
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
			client.player.sendMessage(Text.literal("Snow Shoe: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
