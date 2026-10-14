package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class FastClimbModule {

	private static boolean enabled = false;

	private FastClimbModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FastClimbModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Fast Climb: " + (enabled ? "ON" : "OFF")), true);
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
		if (player.isClimbing()) {
			double speed = client.options.jumpKey.isPressed() ? 0.5D : 0.28D;
			player.setVelocity(player.getVelocity().x, speed, player.getVelocity().z);
		}
	}
}