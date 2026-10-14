package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/**
 * Anti Armor Stand — removes armor stands from rendering and interaction
 * (many servers use them as clutter/decorations that block clicks).
 * Toggle in the Render window.
 */
public final class NoArmorStandModule {

	private static boolean enabled = false;

	private NoArmorStandModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("No Armor Stand: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Consulted by the ESP renderer: armor stands are skipped when enabled. */
	public static boolean shouldFilter(Entity entity) {
		return enabled && entity instanceof ArmorStandEntity;
	}
}