package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.Random;

public final class AntiAfkModule {

	private static boolean enabled = false;
	private static int timer = 0;

	private static final Random RANDOM = new Random();

	private AntiAfkModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AntiAfkModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		timer = 0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Anti AFK: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		if (--timer > 0) {
			return;
		}
		timer = 600 + RANDOM.nextInt(240);
		player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		if (player.isOnGround()) {
			player.setVelocity(player.getVelocity().x, 0.42D, player.getVelocity().z);
		}
	}
}