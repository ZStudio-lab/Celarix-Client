package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

public final class ElytraFlyModule {

	private static boolean enabled = false;

	private ElytraFlyModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ElytraFlyModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Elytra Fly: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| !player.isGliding()) {
			return;
		}
		if (client.options.forwardKey.isPressed()) {
			var look = player.getRotationVec(1.0F);
			player.setVelocity(look.x * 1.5D, look.y * 1.5D, look.z * 1.5D);
		} else if (player.getVelocity().y < -0.1D) {
			player.setVelocity(player.getVelocity().x, -0.05D, player.getVelocity().z);
		}
	}
}