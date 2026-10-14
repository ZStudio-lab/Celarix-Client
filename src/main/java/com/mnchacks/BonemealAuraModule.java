package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
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
import net.minecraft.util.math.random.Random;

/**
 * Bonemeal Aura — bone meal in the hotbar gets used on every growable crop
 * around you (anything the vanilla fertilizable check accepts), so farms
 * mature while you stand there.
 */
public final class BonemealAuraModule {

	private static final int RADIUS = 4;

	private static boolean enabled = false;
	private static int cooldown = 0;
	private static final Random RANDOM = Random.create();

	private BonemealAuraModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BonemealAuraModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Bonemeal: " + (enabled ? "ON" : "OFF")), true);
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

		int mealSlot = -1;
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getStack(slot).isOf(Items.BONE_MEAL)) {
				mealSlot = slot;
				break;
			}
		}
		if (mealSlot < 0) {
			return;
		}

		BlockPos origin = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(origin.add(-RADIUS, -2, -RADIUS),
				origin.add(RADIUS, 2, RADIUS))) {
			BlockState state = client.world.getBlockState(pos);
			if (!(state.getBlock() instanceof Fertilizable fertilizable)
					|| !fertilizable.isFertilizable(client.world, pos, state)
					|| !fertilizable.canGrow(client.world, RANDOM, pos, state)) {
				continue;
			}
			if (player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 25.0D) {
				continue;
			}

			player.getInventory().setSelectedSlot(mealSlot);
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
