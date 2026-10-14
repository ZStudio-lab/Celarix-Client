package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * New Chunks — chunk-freshness outlines. Every chunk that loads
 * while the module is enabled gets a bright outline (ring + corner pillars at
 * your eye level, drawn through walls by the ESP pipeline), which is a good
 * way to see where the world around you was only just generated. The newest
 * chunks win; the history is capped so the render never grows unbounded.
 */
public final class NewChunksModule {

	/** Newest chunks kept for rendering. */
	private static final int MAX_CHUNKS = 400;

	private static boolean enabled = false;
	private static final ArrayDeque<ChunkPos> chunks = new ArrayDeque<>();

	private NewChunksModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			if (!enabled) {
				return;
			}
			ChunkPos pos = chunk.getPos();
			for (ChunkPos existing : chunks) {
				if (existing.equals(pos)) {
					return;
				}
			}
			chunks.addLast(pos);
			while (chunks.size() > MAX_CHUNKS) {
				chunks.removeFirst();
			}
		});
	}

	public static void reset() {
		enabled = false;
		chunks.clear();
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			chunks.clear();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("New Chunks: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Newest-first snapshot of the tracked chunk positions. */
	public static List<ChunkPos> getChunks() {
		return new ArrayList<>(chunks);
	}
}
