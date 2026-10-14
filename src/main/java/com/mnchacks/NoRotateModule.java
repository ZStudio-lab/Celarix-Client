package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * No Rotate — when the server sends a position packet it also forces your
 * view angles. This module stores your view before the packet applies and
 * restores it after, so the server can move you but never turn your camera.
 */
public final class NoRotateModule {

	private static boolean enabled = false;
	private static float storedYaw;
	private static float storedPitch;

	private NoRotateModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("No Rotate: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Called from the network mixin before the position packet applies. */
	public static void capture(ClientPlayerEntity player) {
		storedYaw = player.getYaw();
		storedPitch = player.getPitch();
	}

	/** Called from the network mixin after the position packet applies. */
	public static void restore(ClientPlayerEntity player) {
		player.setYaw(storedYaw);
		player.setPitch(storedPitch);
	}
}