package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class ScaffoldModule {

	private static boolean enabled = false;

	private ScaffoldModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ScaffoldModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Scaffold: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null || !client.options.forwardKey.isPressed()) {
			return;
		}

		BlockPos below = player.getBlockPos().down();
		if (!client.world.getBlockState(below).isAir()) {
			// Walking on solid ground: hop when the next step has no floor.
			Direction facing = player.getHorizontalFacing();
			BlockPos ahead = player.getBlockPos().offset(facing).down();
			if (client.world.getBlockState(ahead).isAir() && player.isOnGround()) {
				player.setVelocity(player.getVelocity().x, 0.42D, player.getVelocity().z);
				player.fallDistance = 0.0F;
			}
			return;
		}

		// Air below (mid-hop): place a block under our feet at the apex.
		if (!player.isOnGround() && player.getVelocity().y <= 0.0D) {
			int slot = findBlockSlot(player);
			if (slot < 0) {
				return;
			}
			player.getInventory().setSelectedSlot(slot);
			tryPlace(client, player, below);
		}
	}

	private static int findBlockSlot(ClientPlayerEntity player) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getStack(slot).getItem() instanceof BlockItem) {
				return slot;
			}
		}
		return -1;
	}

	private static void tryPlace(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
		if (!client.world.getBlockState(pos).isAir()) {
			return;
		}
		for (Direction side : Direction.values()) {
			BlockPos neighbour = pos.offset(side.getOpposite());
			BlockState neighbourState = client.world.getBlockState(neighbour);
			if (neighbourState.isAir() || !neighbourState.getFluidState().isEmpty()
					|| neighbourState.getCollisionShape(client.world, neighbour).isEmpty()) {
				continue;
			}
			Vec3d hitPos = Vec3d.ofCenter(neighbour).add(Vec3d.of(side.getVector()).multiply(0.5D));
			Direction face = side;
			Vec3d look = player.getRotationVec(1.0F);
			player.setYaw((float) (MathHelper.atan2(look.z, look.x) * (180.0D / Math.PI)) - 90.0F);
			BlockHitResult hit = new BlockHitResult(hitPos, face, neighbour, false);
			if (client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit).isAccepted()) {
				player.swingHand(Hand.MAIN_HAND);
				return;
			}
		}
	}
}