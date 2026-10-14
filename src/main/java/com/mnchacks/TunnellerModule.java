package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Tunneller — continuously digs a 1x2 tunnel in the horizontal
 * direction you are facing (snapped to the nearest cardinal), head block
 * before feet block so falling gravel never buries you. Never digs into
 * liquids, never touches unbreakable blocks, and simply waits when the way
 * ahead is clear - walk forward (pairs well with Auto Walk) and the tunnel
 * extends in front of you.
 */
public final class TunnellerModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private TunnellerModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TunnellerModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Tunneller: " + (enabled ? "ON" : "OFF")), true);
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

		// Snap the facing direction to the nearest cardinal (live - turning
		// turns the tunnel).
		double radians = Math.toRadians(player.getYaw());
		double fx = -Math.sin(radians);
		double fz = Math.cos(radians);
		int dx;
		int dz;
		if (Math.abs(fx) > Math.abs(fz)) {
			dx = fx > 0 ? 1 : -1;
			dz = 0;
		} else {
			dx = 0;
			dz = fz > 0 ? 1 : -1;
		}

		BlockPos feet = player.getBlockPos();
		BlockPos frontFeet = feet.add(dx, 0, dz);
		BlockPos frontHead = feet.up().add(dx, 0, dz);

		// Never tunnel into liquids - pause until the player routes around.
		if (!client.world.getFluidState(frontFeet).isEmpty()
				|| !client.world.getFluidState(frontHead).isEmpty()) {
			return;
		}

		// Head first (so gravel/sand above cannot drop into the dug feet).
		if (dig(client, player, frontHead)) {
			return;
		}
		dig(client, player, frontFeet);
	}

	/** Returns true when this block was dug (or needs to wait); false = clear. */
	private static boolean dig(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
		BlockState state = client.world.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.getBlock() == Blocks.BEDROCK || state.getHardness(client.world, pos) < 0.0F) {
			// Unbreakable wall: stop dead rather than hammering it forever.
			return true;
		}
		aimAt(player, pos);
		client.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
		player.swingHand(Hand.MAIN_HAND);
		cooldown = 1;
		return true;
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
		player.setPitch((float) MathHelper.clamp(
				-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI)), -90.0F, 90.0F));
	}
}
