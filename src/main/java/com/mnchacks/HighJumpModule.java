package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class HighJumpModule {

	private static boolean enabled = false;
	private static boolean prevJump = false;

	private HighJumpModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HighJumpModule::tick);
	}

	public static void reset() {
		enabled = false;
		prevJump = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("High Jump: " + (enabled ? "ON" : "OFF")), true);
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
		boolean jump = client.options.jumpKey.isPressed();
		if (jump && !prevJump && player.isOnGround()) {
			player.setVelocity(player.getVelocity().x,
					player.getVelocity().y * 1.45D, player.getVelocity().z);
			player.fallDistance = 0.0F;
		}
		prevJump = jump;
	}
}