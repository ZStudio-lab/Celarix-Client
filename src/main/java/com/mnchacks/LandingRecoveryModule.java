package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * No Fall Damage Delay — combines with No Fall for instant recovery: after
 * landing, releases the sneak flag and re-enables sprinting immediately.
 */
public final class LandingRecoveryModule {

	private static boolean enabled = false;

	private LandingRecoveryModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Landing Recovery: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(LandingRecoveryModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		if (player.isOnGround() && player.fallDistance > 0.0F) {
			// Just landed: clear residual fall tracking and restore sprint.
			player.fallDistance = 0.0F;
			if (client.options.forwardKey.isPressed() && !player.isSneaking()) {
				player.setSprinting(player.getHungerManager().getFoodLevel() > 6);
			}
		}
	}
}