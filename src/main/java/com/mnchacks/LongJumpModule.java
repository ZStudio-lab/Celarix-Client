package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class LongJumpModule {

	private static boolean enabled = false;
	private static boolean prevJump = false;

	private LongJumpModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(LongJumpModule::tick);
	}

	public static void reset() {
		enabled = false;
		prevJump = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Long Jump: " + (enabled ? "ON" : "OFF")), true);
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
			player.setVelocity(player.getVelocity().x * 1.6D,
					player.getVelocity().y * 1.3D, player.getVelocity().z * 1.6D);
			player.fallDistance = 0.0F;
		}
		prevJump = jump;
	}
}