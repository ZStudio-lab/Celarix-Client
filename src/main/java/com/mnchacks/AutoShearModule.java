package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Auto Shear — shears in the hotbar get used on every woolly sheep within
 * reach, turning shearing a pen of sheep into standing still for a moment.
 */
public final class AutoShearModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoShearModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoShearModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Shear: " + (enabled ? "ON" : "OFF")), true);
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

		int shearSlot = -1;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isOf(Items.SHEARS)) {
				shearSlot = slot;
				break;
			}
		}
		if (shearSlot < 0) {
			return;
		}

		SheepEntity sheep = null;
		double bestSq = Double.MAX_VALUE;
		Box box = player.getBoundingBox().expand(4.5D);
		for (SheepEntity candidate : client.world.getEntitiesByClass(SheepEntity.class, box,
				s -> s.isAlive() && !s.isSheared() && !s.isBaby())) {
			double distSq = candidate.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				sheep = candidate;
			}
		}
		if (sheep == null) {
			return;
		}

		player.getInventory().setSelectedSlot(shearSlot);
		aimAt(player, sheep.getEyePos());
		ActionResult result = client.interactionManager.interactEntity(player, sheep, Hand.MAIN_HAND);
		if (result.isAccepted()) {
			player.swingHand(Hand.MAIN_HAND);
			cooldown = 10;
		}
	}

	private static void aimAt(ClientPlayerEntity player, Vec3d target) {
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
