package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Glide — automatic parachute. While falling, the downward speed is clamped
 * to a gentle constant (and fall distance reset), so long drops become slow,
 * safe descents. Elytra gliding, creative flight, water, climbing and being
 * on the ground are all left alone.
 */
public final class GlideModule {

	/** Clamped fall speed in blocks per tick (terminal velocity is ~3.9). */
	private static final double GLIDE_FALL_SPEED = 0.3D;

	private static boolean enabled = false;

	private GlideModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(GlideModule::tick);
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Glide: " + (enabled ? "ON" : "OFF")), true);
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
		if (player.isOnGround() || player.isTouchingWater() || player.isClimbing()
				|| player.isGliding() || player.getAbilities().flying) {
			return;
		}
		Vec3d velocity = player.getVelocity();
		if (velocity.y < -GLIDE_FALL_SPEED) {
			player.setVelocity(velocity.x, -GLIDE_FALL_SPEED, velocity.z);
			// A parachute descent should not end in a crater.
			player.fallDistance = 0.0F;
		}
	}
}
