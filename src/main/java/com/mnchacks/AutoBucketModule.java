package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.registry.tag.FluidTags;

/**
 * Auto Bucket — the MLG save: while falling hard with a water bucket in the
 * hotbar, it places the water on the ground right before you land, then
 * scoops it back up with an empty bucket afterwards. Only triggers on real
 * falls (more than 5 blocks fallen) - never while walking.
 */
public final class AutoBucketModule {

	private static final double TRIGGER_FALL = 5.0D;
	private static final int SCAN_DEPTH = 8;

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoBucketModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoBucketModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Bucket: " + (enabled ? "ON" : "OFF")), true);
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

		// Scoop the water back up after landing.
		if (player.isOnGround() && client.world.getFluidState(player.getBlockPos()).isIn(FluidTags.WATER)) {
			int bucketSlot = findSlot(player, Items.BUCKET);
			if (bucketSlot >= 0) {
				player.getInventory().setSelectedSlot(bucketSlot);
				BlockPos water = player.getBlockPos();
				lookAt(player, Vec3d.ofCenter(water));
				BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(water), Direction.UP, water, false);
				ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
				if (result.isAccepted()) {
					player.swingHand(Hand.MAIN_HAND);
					cooldown = 10;
				}
			}
			return;
		}

		if (player.isOnGround() || player.isTouchingWater() || player.isClimbing()
				|| player.isGliding() || player.getAbilities().flying
				|| player.fallDistance <= TRIGGER_FALL) {
			return;
		}

		// Find the ground below and the time-to-impact.
		BlockPos ground = player.getBlockPos();
		int depth = 0;
		while (depth < SCAN_DEPTH && client.world.getBlockState(ground).isAir()) {
			ground = ground.down();
			depth++;
		}
		if (depth >= SCAN_DEPTH) {
			return; // nothing to place on nearby
		}
		if (!client.world.getBlockState(ground.up()).isAir()) {
			return; // landing cell not free
		}
		double fallSpeed = -player.getVelocity().y;
		if (fallSpeed <= 0.1D) {
			return;
		}
		double ticksToImpact = depth / fallSpeed;
		if (ticksToImpact > 2.0D) {
			return; // not yet - wait for the last moment
		}

		int bucketSlot = findSlot(player, Items.WATER_BUCKET);
		if (bucketSlot < 0) {
			return;
		}
		player.getInventory().setSelectedSlot(bucketSlot);
		lookAt(player, Vec3d.ofCenter(ground));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5D, 0),
				Direction.UP, ground, false);
		ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
		if (result.isAccepted()) {
			player.swingHand(Hand.MAIN_HAND);
			cooldown = 4;
		}
	}

	private static int findSlot(ClientPlayerEntity player, net.minecraft.item.Item item) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.isOf(item)) {
				return slot;
			}
		}
		return -1;
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
