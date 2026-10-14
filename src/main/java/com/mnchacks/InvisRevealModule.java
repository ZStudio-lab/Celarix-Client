package com.mnchacks;

import net.minecraft.text.Text;

/**
 * Invis Reveal — while ESP is on, invisible players are rendered with their
 * real skins (a render mixin overrides the vanilla hide for player models),
 * so you can see and track them normally.
 */
public final class InvisRevealModule {

	private static boolean enabled = true;

	private InvisRevealModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** No tick loop needed — the render mixin handles everything. */
	public static void register() {
	}

	public static void toggle() {
		enabled = !enabled;
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Invis Reveal: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}