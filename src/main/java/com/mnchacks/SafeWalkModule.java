package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class SafeWalkModule {

	private static boolean enabled = false;

	private SafeWalkModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SafeWalkModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Safe Walk: " + (enabled ? "ON" : "OFF")), true);
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
		if (!player.isOnGround() || player.isSneaking()) {
			return;
		}
		Vec3d velocity = player.getVelocity();
		if (velocity.x * velocity.x + velocity.z * velocity.z < 0.01D) {
			return;
		}
		BlockPos belowAhead = BlockPos.ofFloored(
				player.getX() + velocity.x,
				player.getY() - 0.6D,
				player.getZ() + velocity.z);
		if (client.world.getBlockState(belowAhead).isAir()
				&& client.world.getBlockState(belowAhead.down()).isAir()) {
			player.setSneaking(true);
		}
	}
}