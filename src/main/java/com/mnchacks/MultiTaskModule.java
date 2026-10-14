package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Multi Task — lets you break blocks and swing attacks while
 * eating/drinking/drawing a bow. Vanilla cancels the left-hand actions the
 * moment an item use starts; {@code MinecraftClientMixin} removes exactly
 * those two gates while this module is on. Eating still finishes normally -
 * you just don't have to stop defending yourself while you snack.
 */
public final class MultiTaskModule {

	private static boolean enabled = false;

	private MultiTaskModule() {
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
			client.player.sendMessage(Text.literal("Multi Task: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
