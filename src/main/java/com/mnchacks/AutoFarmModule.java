package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AutoFarmModule {

	private static boolean enabled = false;
	private static double radius = 5.0D;
	private static int cooldown = 0;

	private AutoFarmModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static double getRadius() {
		return radius;
	}

	public static void setRadius(double value) {
		radius = MathHelper.clamp(value, 3.0D, 8.0D);
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoFarmModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Farm: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null) {
			return;
		}

		BlockPos origin = player.getBlockPos();
		int r = (int) Math.round(radius);
		BlockPos target = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(origin.add(-r, -2, -r), origin.add(r, 2, r))) {
			BlockState state = client.world.getBlockState(pos);
			boolean mature = false;
			if (state.contains(Properties.AGE_7)) {
				mature = state.get(Properties.AGE_7) == 7;
			} else if (state.contains(Properties.AGE_3)) {
				mature = state.get(Properties.AGE_3) == 3;
			}
			if (!mature) {
				continue;
			}
			double distSq = pos.getSquaredDistance(origin);
			if (distSq < bestSq) {
				bestSq = distSq;
				target = pos.toImmutable();
			}
		}

		if (target == null) {
			return;
		}

		Vec3d center = Vec3d.ofCenter(target);
		Vec3d eyes = player.getEyePos();
		double dx = center.x - eyes.x;
		double dy = center.y - eyes.y;
		double dz = center.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1.0E-4D) {
			player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
			player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI))));
		}
		if (eyes.squaredDistanceTo(center) <= 16.0D) {
			client.interactionManager.updateBlockBreakingProgress(target, Direction.UP);
			player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			cooldown = 2;
		} else {
			AutoWalkModule.setForced(true);
		}
	}
}