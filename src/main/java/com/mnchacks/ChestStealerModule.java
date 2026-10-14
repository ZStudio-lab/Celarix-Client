package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class ChestStealerModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private ChestStealerModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ChestStealerModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Chest Stealer: " + (enabled ? "ON" : "OFF")), true);
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
		if (player == null || client.world == null || client.interactionManager == null) {
			return;
		}

		if (client.currentScreen instanceof HandledScreen<?> handled
				&& handled.getScreenHandler() instanceof GenericContainerScreenHandler container) {
			for (int i = 0; i < container.getRows() * 9; i++) {
				if (!container.slots.get(i).getStack().isEmpty()) {
					client.interactionManager.clickSlot(container.syncId, i, 0,
							SlotActionType.QUICK_MOVE, player);
				}
			}
			player.closeHandledScreen();
			cooldown = 40;
			return;
		}

		BlockPos origin = player.getBlockPos();
		BlockPos chest = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(origin.add(-4, -2, -4), origin.add(4, 2, 4))) {
			BlockState state = client.world.getBlockState(pos);
			if (state.getBlock() instanceof ChestBlock) {
				double distSq = pos.getSquaredDistance(origin);
				if (distSq < bestSq) {
					bestSq = distSq;
					chest = pos.toImmutable();
				}
			}
		}
		if (chest == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(chest);
		if (player.getEyePos().squaredDistanceTo(center) <= 12.0D) {
			client.interactionManager.interactBlock(player, Hand.MAIN_HAND,
					new BlockHitResult(center, Direction.UP, chest, false));
			cooldown = 40;
		}
	}
}