package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Vein Miner — break one ore and the whole connected vein of
 * that same ore comes down with it. Seeding happens when you start mining an
 * ore block under the crosshair (a flood fill over same-type neighbours, up
 * to 32 blocks). The vein is then mined block by block, nearest first, only
 * within normal reach, with the camera turning to each block. Walk away and
 * the rest of the vein waits; come back and it continues.
 */
public final class VeinMinerModule {

	private static final int MAX_VEIN = 32;
	/** Vanilla survival block reach (4.5), squared. */
	private static final double REACH_SQ = 20.25D;

	private static boolean enabled = false;
	private static final ArrayDeque<BlockPos> vein = new ArrayDeque<>();
	/** The block currently being broken. */
	private static BlockPos current = null;

	private VeinMinerModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(VeinMinerModule::tick);
	}

	public static void reset() {
		enabled = false;
		vein.clear();
		current = null;
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			vein.clear();
			current = null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Vein Miner: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null) {
			return;
		}

		// Seed a vein when the player starts mining an ore under the crosshair.
		if (vein.isEmpty() && current == null
				&& client.crosshairTarget instanceof BlockHitResult hit
				&& client.options.attackKey.isPressed()) {
			BlockPos seed = hit.getBlockPos();
			BlockState state = client.world.getBlockState(seed);
			if (state.getBlock() instanceof ExperienceDroppingBlock) {
				seedVein(client, seed, state.getBlock());
			}
		}

		if (current == null) {
			pickNext(client, player);
			if (current == null) {
				return;
			}
		}

		BlockState state = client.world.getBlockState(current);
		if (!(state.getBlock() instanceof ExperienceDroppingBlock)) {
			// Already gone (broken by us or someone else).
			current = null;
			return;
		}
		if (current.getSquaredDistance(player.getEyePos()) > REACH_SQ) {
			// Out of reach right now - park it at the end of the queue and
			// try another block; walking closer resumes it.
			vein.addLast(current);
			current = null;
			return;
		}

		aimAt(player, current);
		boolean broken = client.interactionManager.updateBlockBreakingProgress(current, Direction.UP);
		player.swingHand(Hand.MAIN_HAND);
		if (broken) {
			current = null;
		}
	}

	/** Picks the closest queued ore that is still there and within reach. */
	private static void pickNext(MinecraftClient client, ClientPlayerEntity player) {
		int scanned = 0;
		int size = vein.size();
		while (scanned++ < size && current == null && !vein.isEmpty()) {
			BlockPos pos = vein.pollFirst();
			BlockState state = client.world.getBlockState(pos);
			if (!(state.getBlock() instanceof ExperienceDroppingBlock)) {
				continue;
			}
			if (pos.getSquaredDistance(player.getEyePos()) > REACH_SQ) {
				vein.addLast(pos);
				continue;
			}
			current = pos;
		}
	}

	/** Flood fill over same-type neighbours, newest-last so mining is ordered. */
	private static void seedVein(MinecraftClient client, BlockPos seed, net.minecraft.block.Block block) {
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
		frontier.add(seed.toImmutable());
		visited.add(seed.toImmutable());
		int found = 0;
		while (!frontier.isEmpty() && found < MAX_VEIN) {
			BlockPos pos = frontier.pollFirst();
			found++;
			vein.addLast(pos.toImmutable());
			for (Direction direction : Direction.values()) {
				BlockPos next = pos.offset(direction);
				if (!visited.add(next)) {
					continue;
				}
				BlockState state = client.world.getBlockState(next);
				if (state.getBlock() == block) {
					frontier.add(next.toImmutable());
				}
			}
		}
	}

	private static void aimAt(ClientPlayerEntity player, BlockPos pos) {
		Vec3d center = Vec3d.ofCenter(pos);
		Vec3d eyes = player.getEyePos();
		double dx = center.x - eyes.x;
		double dy = center.y - eyes.y;
		double dz = center.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1.0E-4D) {
			player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		}
		player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI))));
	}
}
