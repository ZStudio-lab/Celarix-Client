package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Till Aura — holds a hoe and automatically tills dirt, grass and dirt paths
 * around you into farmland (only where the space above is free), so big
 * fields can be prepared without sweeping the crosshair over every block.
 */
public final class TillAuraModule {

	private static final int RADIUS = 4;

	private static boolean enabled = false;
	private static int cooldown = 0;

	private TillAuraModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TillAuraModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Till Aura: " + (enabled ? "ON" : "OFF")), true);
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
		if (player == null || client.world == null || client.interactionManager == null
				|| client.currentScreen != null) {
			return;
		}

		int hoeSlot = -1;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isOf(Items.WOODEN_HOE) || stack.isOf(Items.STONE_HOE)
					|| stack.isOf(Items.IRON_HOE) || stack.isOf(Items.GOLDEN_HOE)
					|| stack.isOf(Items.DIAMOND_HOE) || stack.isOf(Items.NETHERITE_HOE)) {
				hoeSlot = slot;
				break;
			}
		}
		if (hoeSlot < 0) {
			return;
		}

		BlockPos origin = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(origin.add(-RADIUS, -1, -RADIUS),
				origin.add(RADIUS, 1, RADIUS))) {
			Block block = client.world.getBlockState(pos).getBlock();
			if (block != Blocks.DIRT && block != Blocks.GRASS_BLOCK
					&& block != Blocks.COARSE_DIRT && block != Blocks.DIRT_PATH) {
				continue;
			}
			if (!client.world.getBlockState(pos.up()).isAir()) {
				continue;
			}
			if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 25.0D) {
				continue;
			}

			player.getInventory().setSelectedSlot(hoeSlot);
			lookAt(player, pos);
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
			ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
			if (result.isAccepted()) {
				player.swingHand(Hand.MAIN_HAND);
				cooldown = 4;
			}
			return;
		}
	}

	private static void lookAt(ClientPlayerEntity player, BlockPos pos) {
		Vec3d target = Vec3d.ofCenter(pos);
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
