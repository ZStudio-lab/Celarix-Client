package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class AirJumpModule {

	private static final int COOLDOWN = 8;

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AirJumpModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AirJumpModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Air Jump: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (client.options.jumpKey.isPressed() && !player.isOnGround()) {
			player.setVelocity(player.getVelocity().x, 0.42D, player.getVelocity().z);
			player.fallDistance = 0.0F;
			cooldown = COOLDOWN;
		}
	}
}