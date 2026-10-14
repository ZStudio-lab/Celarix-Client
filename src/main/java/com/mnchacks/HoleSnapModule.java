package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class HoleSnapModule {

	private static boolean enabled = false;

	private HoleSnapModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HoleSnapModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Hole Snap: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || !player.isOnGround()) {
			return;
		}
		BlockPos best = null;
		double bestSq = 3.5D * 3.5D;
		for (BlockPos hole : HoleEspModule.getHoles()) {
			double distSq = hole.getSquaredDistance(player.getBlockPos());
			if (distSq < bestSq) {
				bestSq = distSq;
				best = hole;
			}
		}
		if (best == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(best);
		double dx = center.x - player.getX();
		double dz = center.z - player.getZ();
		if (dx * dx + dz * dz < 1.0E-4D) {
			return;
		}
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
	}
}