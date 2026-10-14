package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Auto Sprint — sprints automatically whenever the player moves forward (and
 * is not sneaking), exactly like holding the sprint key. Vanilla-legal and
 * works on any server.
 */
public final class AutoSprintModule {

	private static boolean enabled = false;

	private AutoSprintModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Sprint: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled) {
				return;
			}
			ClientPlayerEntity player = client.player;
			if (player == null || player.isSneaking() || player.isUsingItem()) {
				return;
			}
			if (client.options != null && client.options.forwardKey.isPressed()) {
				player.setSprinting(true);
			}
		});
	}
}