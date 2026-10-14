package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Air Place — lets you place blocks in mid-air without an existing block to
 * click against. While enabled, right-clicking (or Fast Place holding) places
 * a block at the air position the crosshair points at, up to reach.
 *
 * <p>How it works: the crosshair raycast normally ends on a block face; with
 * this hack, when the raycast misses (or hits air), the block position is
 * taken from the ray point itself and a synthetic placement is sent for the
 * nearest face. Vanilla servers accept placements inside the world border
 * that pass their reach check — where the anti-air-place check is strict the
 * placement is simply rejected, like any other failed click.</p>
 */
public final class AirPlaceModule {

	/** How far past the crosshair point to snap the target position. */
	private static final double REACH = 4.5D;

	private static boolean enabled = false;

	private AirPlaceModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Air Place: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AirPlaceModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null || !client.options.useKey.isPressed()) {
			return;
		}

		// Only when holding a placeable block.
		ItemStack held = player.getMainHandStack();
		if (!(held.getItem() instanceof BlockItem)) {
			return;
		}

		// Raycast from the eyes along the look vector; take the point at reach
		// distance when the ray hits nothing solid.
		Vec3d eyes = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d end = eyes.add(look.multiply(REACH));
		net.minecraft.util.hit.HitResult hit = client.world.raycast(
				new net.minecraft.world.RaycastContext(eyes, end,
						net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
						net.minecraft.world.RaycastContext.FluidHandling.NONE, player));

		BlockPos target;
		if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
			// Crosshair on a block face: vanilla handles it — nothing to do.
			return;
		}
		if (hit.getType() == HitResult.Type.MISS) {
			target = BlockPos.ofFloored(end);
		} else {
			// Crosshair inside/against something non-solid (grass etc.):
			// place into the block space in front of the ray point.
			target = BlockPos.ofFloored(end.add(look.multiply(0.5D)));
		}

		if (!client.world.getBlockState(target).isAir()) {
			return;
		}

		// Send the click against the neighbour block along the look axis (the
		// server places at clickedPos.offset(side), which lands exactly on the
		// crosshair point).
		Direction face = Direction.getFacing(look.x, look.y, look.z);
		BlockPos neighbor = target.offset(face.getOpposite());
		Vec3d hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(face.getVector()).multiply(0.5D));
		BlockHitResult place = new BlockHitResult(hitPos, face, neighbor, false);
		client.interactionManager.interactBlock(player, net.minecraft.util.Hand.MAIN_HAND, place);
		player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
	}
}