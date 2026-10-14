package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Anti Spam — collapses chat floods: an identical message arriving within a
 * second of the previous one is suppressed instead of repeated (see
 * {@code ChatHudMixin}). Nothing is edited in the history - floods simply
 * stop filling the chat.
 */
public final class AntiSpamModule {

	private static boolean enabled = false;

	private AntiSpamModule() {
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
			client.player.sendMessage(Text.literal("Anti Spam: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
