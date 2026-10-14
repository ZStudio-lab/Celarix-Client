package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoriesModule {

	private static boolean enabled = false;

	private TrajectoriesModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Trajectories: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Simulated flight path of the held throwable, or an empty list. */
	public static List<Vec3d> compute(MinecraftClient client, ClientPlayerEntity player) {
		if (!enabled || player == null || client.world == null) {
			return List.of();
		}
		ItemStack held = player.getMainHandStack();
		Item item = held.getItem();
		double power = -1.0D;
		if (item == Items.ENDER_PEARL || item == Items.SNOWBALL || item == Items.EGG) {
			power = 1.5D;
		} else if (item == Items.BOW && player.isUsingItem()) {
			power = 1.0D + Math.min(player.getItemUseTime(), 20) * 0.1D;
		}
		if (power < 0.0D) {
			return List.of();
		}

		List<Vec3d> points = new ArrayList<>();
		Vec3d pos = player.getEyePos();
		Vec3d velocity = player.getRotationVec(1.0F).multiply(power);
		for (int i = 0; i < 60; i++) {
			points.add(pos);
			pos = pos.add(velocity);
			velocity = velocity.multiply(0.99D).subtract(0.0D, 0.03D, 0.0D);
			if (!client.world.getBlockState(BlockPos.ofFloored(pos)).isAir()) {
				break;
			}
		}
		return points;
	}
}