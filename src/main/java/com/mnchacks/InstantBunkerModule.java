package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;

/**
 * Instant Bunker — one-shot: the moment it is toggled on it
 * queues a shell around you (ring at feet level, ring at head level, cap
 * above) and places the blocks from your hotbar one per tick, aiming at
 * every face it clicks. When the last block is placed it switches itself
 * off. Toggle it again any time for a fresh bunker.
 */
public final class InstantBunkerModule {

	private static boolean enabled = false;
	private static final ArrayDeque<BlockPos> queue = new ArrayDeque<>();

	private InstantBunkerModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(InstantBunkerModule::tick);
	}

	public static void reset() {
		enabled = false;
		queue.clear();
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (enabled) {
			queue.clear();
			ClientPlayerEntity player = client != null ? client.player : null;
			if (player != null) {
				BlockPos feet = player.getBlockPos();
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos level = feet.up(dy);
					if (dy == 2) {
						// Cap above the head.
						queue.addLast(level);
						continue;
					}
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (dx == 0 && dz == 0) {
								continue; // where you are standing
							}
							queue.addLast(level.add(dx, 0, dz));
						}
					}
				}
			}
		} else {
			queue.clear();
		}
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Bunker: " + (enabled ? "BUILDING" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null
				|| client.currentScreen != null) {
			return;
		}
		if (queue.isEmpty()) {
			// Done: one-shot behaviour.
			enabled = false;
			if (client.player != null) {
				client.player.sendMessage(Text.literal("Bunker: complete"), true);
			}
			return;
		}

		BlockPos pos = queue.pollFirst();
		BlockState state = client.world.getBlockState(pos);
		if (!state.isAir() && !state.isReplaceable()) {
			return; // already filled in
		}
		if (!selectBlock(client, player)) {
			return; // no placeable blocks in the hotbar
		}
		if (placeAt(client, player, pos)) {
			player.swingHand(Hand.MAIN_HAND);
		}
	}

	/** Places at {@code pos} against the nearest solid neighbour face. */
	private static boolean placeAt(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
		for (Direction side : Direction.values()) {
			BlockPos neighbour = pos.offset(side.getOpposite());
			BlockState neighbourState = client.world.getBlockState(neighbour);
			if (neighbourState.isAir() || !neighbourState.getFluidState().isEmpty()
					|| neighbourState.isReplaceable()
					|| neighbourState.getCollisionShape(client.world, neighbour).isEmpty()) {
				continue;
			}
			Vec3d hitPos = Vec3d.ofCenter(neighbour).add(Vec3d.of(side.getVector()).multiply(0.5D));
			if (player.getEyePos().squaredDistanceTo(hitPos) > 20.25D) {
				continue;
			}
			lookAt(player, hitPos);
			BlockHitResult hit = new BlockHitResult(hitPos, side, neighbour, false);
			ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
			return result.isAccepted();
		}
		return false;
	}

	private static boolean selectBlock(MinecraftClient client, ClientPlayerEntity player) {
		var inventory = player.getInventory();
		ItemStack held = inventory.getStack(inventory.getSelectedSlot());
		if (held.getItem() instanceof BlockItem && !held.isEmpty()) {
			return true;
		}
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.getItem() instanceof BlockItem && !stack.isEmpty()) {
				inventory.setSelectedSlot(slot);
				return true;
			}
		}
		return false;
	}

	private static void lookAt(ClientPlayerEntity player, Vec3d target) {
		Vec3d eyes = player.getEyePos();
		double dx = target.x - eyes.x;
		double dy = target.y - eyes.y;
		double dz = target.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1.0E-4D) {
			player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		}
		player.setPitch((float) MathHelper.clamp(
				-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI)), -90.0F, 90.0F));
	}
}
