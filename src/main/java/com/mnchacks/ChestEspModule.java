package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chest ESP — storage outlines drawn through walls. Every few
 * ticks the block entities of the loaded chunks around the player are
 * collected and grouped by container type (each with its own colour), and
 * EspModule draws the boxes with the see-through ESP pipeline.
 */
public final class ChestEspModule {

	/** Scan every 10 ticks (twice a second). */
	private static final int SCAN_INTERVAL = 10;
	/** Loaded-chunk radius around the player (7x7 chunks ~ 112 blocks). */
	private static final int CHUNK_RADIUS = 3;
	private static final int MAX_BOXES = 300;

	private static boolean enabled = false;
	private static int scanCooldown = 0;
	private static List<Box> boxes = new ArrayList<>();
	private static List<int[]> colors = new ArrayList<>();

	private ChestEspModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ChestEspModule::tick);
	}

	public static void reset() {
		enabled = false;
		boxes = new ArrayList<>();
		colors = new ArrayList<>();
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			boxes = new ArrayList<>();
			colors = new ArrayList<>();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Chest ESP: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static List<Box> getBoxes() {
		return boxes;
	}

	public static List<int[]> getColors() {
		return colors;
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (scanCooldown > 0) {
			scanCooldown--;
			return;
		}
		scanCooldown = SCAN_INTERVAL;
		scan(client);
	}

	private static void scan(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		boxes = new ArrayList<>();
		colors = new ArrayList<>();
		if (player == null || world == null) {
			return;
		}
		ChunkPos center = player.getChunkPos();
		for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
			for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
				// load=false: never force-generate chunks just for the ESP.
				WorldChunk chunk = world.getChunkManager()
						.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
					int[] color = colorFor(entry.getValue());
					if (color == null) {
						continue;
					}
					boxes.add(new Box(entry.getKey()).expand(0.03D));
					colors.add(color);
					if (boxes.size() >= MAX_BOXES) {
						return;
					}
				}
			}
		}
	}

	/** Storage colour groups. Null = not a tracked container. */
	private static int[] colorFor(BlockEntity blockEntity) {
		if (blockEntity instanceof TrappedChestBlockEntity) {
			return new int[]{255, 150, 60};
		}
		if (blockEntity instanceof EnderChestBlockEntity) {
			return new int[]{190, 90, 255};
		}
		if (blockEntity instanceof ChestBlockEntity) {
			return new int[]{255, 220, 60};
		}
		if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return new int[]{80, 230, 255};
		}
		if (blockEntity instanceof BarrelBlockEntity) {
			return new int[]{215, 140, 60};
		}
		if (blockEntity instanceof HopperBlockEntity) {
			return new int[]{160, 160, 170};
		}
		if (blockEntity instanceof DispenserBlockEntity) {
			return new int[]{255, 90, 90};
		}
		if (blockEntity instanceof FurnaceBlockEntity) {
			return new int[]{200, 200, 215};
		}
		return null;
	}
}
