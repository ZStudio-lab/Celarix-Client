package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ParkourModule {

	private static boolean enabled = false;

	private ParkourModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ParkourModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Parkour: " + (enabled ? "ON" : "OFF")), true);
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
		if (!player.isOnGround() || player.isSneaking()
				|| !client.options.forwardKey.isPressed()) {
			return;
		}
		Vec3d velocity = player.getVelocity();
		if (velocity.x * velocity.x + velocity.z * velocity.z < 0.01D) {
			return;
		}
		BlockPos ahead = BlockPos.ofFloored(
				player.getX() + velocity.x * 0.7D,
				player.getY() - 0.51D,
				player.getZ() + velocity.z * 0.7D);
		if (client.world.getBlockState(ahead).isAir()
				&& client.world.getBlockState(ahead.down()).isAir()) {
			player.setVelocity(velocity.x, 0.42D, velocity.z);
			player.fallDistance = 0.0F;
		}
	}
}