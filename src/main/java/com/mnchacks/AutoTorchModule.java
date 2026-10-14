package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;

public final class AutoTorchModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoTorchModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoTorchModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Torch: " + (enabled ? "ON" : "OFF")), true);
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
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null) {
			return;
		}
		BlockPos feet = player.getBlockPos();
		int light = client.world.getLightLevel(LightType.BLOCK, feet);
		if (light > 2) {
			return;
		}
		if (!client.world.getBlockState(feet.down()).isOpaqueFullCube()) {
			return;
		}
		int torchSlot = -1;
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getStack(slot).getItem() == Items.TORCH) {
				torchSlot = slot;
				break;
			}
		}
		if (torchSlot < 0) {
			cooldown = 100;
			return;
		}
		player.getInventory().setSelectedSlot(torchSlot);
		Vec3dHelper helper = new Vec3dHelper();
		net.minecraft.util.math.Vec3d hitPos = net.minecraft.util.math.Vec3d.ofCenter(feet.down())
				.add(0.0D, 0.5D, 0.0D);
		BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, feet.down(), false);
		client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
		player.swingHand(Hand.MAIN_HAND);
		cooldown = 40;
	}

	private static final class Vec3dHelper {
	}
}