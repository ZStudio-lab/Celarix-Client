package com.mnchacks;

import net.minecraft.text.Text;

/**
 * Health Freeze — a network mixin rewrites every incoming health-update
 * packet to full health, so the client never registers damage: hearts stay
 * maxed, hurt-cam never plays, and client-side health triggers never fire.
 *
 * <p><b>Honest limitation:</b> on multiplayer servers health is
 * server-authoritative — the server still knows your real health and can
 * still kill you. This is a client-side freeze (visual + client logic), not
 * invincibility.</p>
 *
 * <p>Replaces the old item-eating Auto Heal: no consumables are used.</p>
 */
public final class HealthFreezeModule {

	private static boolean enabled = false;

	private HealthFreezeModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Health Freeze: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}