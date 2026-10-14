package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Auto Leave — automatically disconnects from the server as soon as the
 * player's health drops to the configured threshold (default 3 hearts), so a
 * death never registers. Purely client-side: it just leaves, nothing is
 * spoofed. The threshold is a Click GUI slider.
 */
public final class AutoLeaveModule {

	private static boolean enabled = false;
	private static double threshold = 6.0D;

	private AutoLeaveModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoLeaveModule::tick);
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Leave: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static double getThreshold() {
		return threshold;
	}

	public static void setThreshold(double value) {
		threshold = MathHelper.clamp(value, 1.0D, 18.0D);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.getNetworkHandler() == null) {
			return;
		}
		if (player.isAlive() && player.getHealth() <= threshold) {
			client.player.sendMessage(Text.literal(
					"Auto Leave: disconnecting at " + String.format(java.util.Locale.ROOT,
							"%.1f", player.getHealth()) + " health"), false);
			enabled = false;
			client.world.disconnect();
		}
	}
}
