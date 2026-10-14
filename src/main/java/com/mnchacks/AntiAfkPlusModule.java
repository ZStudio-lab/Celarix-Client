package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Anti AFK Plus — rotates the view, hops, swings, and micro-strafes in random
 * patterns so idle-detection plugins never trigger.
 */
public final class AntiAfkPlusModule {

	private static boolean enabled = false;
	private static int timer = 0;
	private static int phase = 0;

	private static final Random RANDOM = new Random();

	private AntiAfkPlusModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		timer = 0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Anti AFK+: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AntiAfkPlusModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		if (--timer > 0) {
			return;
		}
		timer = 100 + RANDOM.nextInt(120);
		phase = (phase + 1) % 3;

		switch (phase) {
			case 0 -> {
				if (player.isOnGround()) {
					player.setVelocity(player.getVelocity().x, 0.42D, player.getVelocity().z);
				}
				player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			}
			case 1 -> player.setYaw(player.getYaw() + (RANDOM.nextBoolean() ? 15.0F : -15.0F));
			default -> {
				Vec3d look = player.getRotationVec(1.0F);
				Vec3d side = new Vec3d(-look.z, 0.0D, look.x).normalize().multiply(0.15D);
				player.setVelocity(side.x, player.getVelocity().y, side.z);
			}
		}
	}
}