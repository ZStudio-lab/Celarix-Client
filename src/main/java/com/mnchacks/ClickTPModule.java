package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Click TP — left-clicking a block teleports you on top of it (instead of
 * mining it), up to 60 blocks away. Uses client-authoritative movement, so it
 * works on vanilla servers; anticheats with teleport checks will rubber-band.
 */
public final class ClickTPModule {

	private static final double MAX_DISTANCE = 60.0D;

	private static boolean enabled = false;
	private static boolean prevAttack = false;
	private static int cooldown = 0;

	private ClickTPModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		prevAttack = false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Click TP: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ClickTPModule::tick);
	}

	public static void reset() {
		enabled = false;
		prevAttack = false;
		cooldown = 0;
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
				|| client.crosshairTarget == null) {
			return;
		}
		boolean attack = client.options.attackKey.isPressed();
		if (!attack || prevAttack) {
			prevAttack = attack;
			return;
		}
		prevAttack = attack;

		if (client.crosshairTarget.getType() != HitResult.Type.BLOCK
				|| !(client.crosshairTarget instanceof BlockHitResult blockHit)) {
			return;
		}
		BlockPos target = blockHit.getBlockPos().up();
		double distSq = Vec3d.ofCenter(target).squaredDistanceTo(player.getEyePos());
		if (distSq > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}
		player.setPosition(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
		player.setVelocity(0.0D, 0.0D, 0.0D);
		player.fallDistance = 0.0F;
		cooldown = 10;
	}
}