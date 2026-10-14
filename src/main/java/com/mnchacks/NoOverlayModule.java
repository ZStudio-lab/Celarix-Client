package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * No Overlay — cancels the misc screen overlays rendered by the vanilla HUD
 * (fire when burning, water screen, powder snow screen, pumpkin blur and the
 * vignette). Implemented in {@code InGameHudMixin} by cancelling
 * {@code InGameHud.renderMiscOverlays} while this module is enabled.
 */
public final class NoOverlayModule {

	private static boolean enabled = false;

	private NoOverlayModule() {
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
			client.player.sendMessage(Text.literal("No Overlay: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
