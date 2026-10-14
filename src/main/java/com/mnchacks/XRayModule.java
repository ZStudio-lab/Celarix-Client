package com.mnchacks;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Set;

/**
 * X-Ray — opacity-based X-Ray. While enabled, every block that is not
 * on the ore/storage whitelist reports itself as non-opaque (see
 * {@code BlockStateMixin}), so the chunk renderer stops culling faces behind
 * them and the world turns transparent around the highlighted ores. The
 * chunk meshes are rebuilt on every toggle.
 *
 * <p>Pairs perfectly with Fullbright: X-Ray shows the ores, Fullbright makes
 * them visible in the dark.
 */
public final class XRayModule {

	/** Blocks that stay solid (visible) while the rest of the world fades. */
	private static final Set<Block> ORES = Set.of(
			Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
			Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
			Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
			Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
			Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
			Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
			Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
			Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
			Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS,
			Blocks.SPAWNER,
			Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST);

	private static boolean enabled = false;

	private XRayModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** Whether {@code block} should stay visible while X-Ray is on. */
	public static boolean isVisible(Block block) {
		return ORES.contains(block);
	}

	public static void reset() {
		if (enabled) {
			enabled = false;
			reloadChunks();
		}
	}

	public static void toggle() {
		enabled = !enabled;
		reloadChunks();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("X-Ray: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Rebuilds every chunk mesh so the new occlusion takes effect at once. */
	private static void reloadChunks() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.world != null && client.worldRenderer != null) {
			client.worldRenderer.reload();
		}
	}
}
