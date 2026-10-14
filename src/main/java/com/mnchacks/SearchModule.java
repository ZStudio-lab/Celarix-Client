package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public final class SearchModule {

	private static boolean enabled = false;
	private static double radius = 16.0D;
	private static int scanCooldown = 0;
	private static List<Box> found = new ArrayList<>();

	private SearchModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static double getRadius() {
		return radius;
	}

	public static void setRadius(double value) {
		radius = value;
	}

	public static List<Box> getFound() {
		return found;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SearchModule::tick);
	}

	public static void reset() {
		enabled = false;
		found = new ArrayList<>();
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			found = new ArrayList<>();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Search: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (scanCooldown > 0) {
			scanCooldown--;
			return;
		}
		scanCooldown = 20;
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		found = new ArrayList<>();
		BlockPos origin = player.getBlockPos();
		int r = (int) Math.round(radius);
		for (BlockPos pos : BlockPos.iterate(origin.add(-r, -6, -r), origin.add(r, 6, r))) {
			if (found.size() >= 40) {
				break;
			}
			Block block = client.world.getBlockState(pos).getBlock();
			boolean ore = oreWeight(block) > 0;
			boolean storage = block instanceof ChestBlock || block instanceof BarrelBlock;
			if (ore || storage) {
				found.add(new Box(pos));
			}
		}
	}

	private static int oreWeight(Block block) {
		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
			return 12;
		}
		if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
			return 10;
		}
		if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
			return 8;
		}
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
			return 4;
		}
		if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			return 2;
		}
		return 0;
	}
}