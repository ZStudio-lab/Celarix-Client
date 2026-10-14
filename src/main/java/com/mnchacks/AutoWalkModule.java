package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Smart Auto Walk - an all-in-one travel / combat-movement assistant.
 *
 * <p>It drives the vanilla movement keys directly, so it works on any server.</p>
 *
 * <p><b>Travel mode</b>: walks forward, <b>runs</b> (sprints), auto-jumps over
 * small ledges and, when the ground ahead disappears, <b>builds</b> a bridge by
 * silently placing a block from the hotbar (no need to look down). If it has no
 * blocks to bridge with, it <b>crouches</b> and stops at the edge so it cannot
 * fall. It also refuses to walk into lava.</p>
 *
 * <p><b>Evade mode</b>: while an enemy (player or hostile mob) is near, it
 * performs jittery combat movement - sprinting circle-strafes, random
 * direction flips, W/S taps, jumps and the occasional crouch - while keeping
 * the preferred fighting distance, so it is extremely hard to land a hit on.</p>
 */
public final class AutoWalkModule {

	// --- Threat / combat tuning -------------------------------------------

	/** Enemies closer than this switch the module into evasive movement. */
	private static final double THREAT_RADIUS = 6.0D;
	/** Preferred fighting-distance band (stay between these to be hard to hit). */
	private static final double CLOSE_RANGE = 3.0D;
	private static final double FAR_RANGE = 4.5D;

	/** Random ticks between strafe-direction flips (sustained circles). */
	private static final int STRAFE_MIN = 8;
	private static final int STRAFE_MAX = 16;
	/** Ticks of burst movement when juking an incoming swing. */
	private static final int DODGE_TICKS = 5;

	/** Per-tick chance to jump while evading (also lands crits for Auto Click). */
	private static final double JUMP_CHANCE = 0.10D;

	/** W-tap: ticks between sprint resets at melee range. */
	private static final int W_TAP_INTERVAL = 9;
	/** W-tap: how long forward is cut (fresh sprint = more knockback dealt). */
	private static final int W_TAP_TICKS = 2;
	/** Per-tick chance to crouch (shrinks the hitbox) while evading. */
	private static final double CROUCH_CHANCE = 0.03D;

	/** A drop deeper than this (blocks) is treated as a gap and bridged. */
	private static final int SAFE_DROP = 3;

	/** Vanilla block-interaction reach (squared), used to pick valid place faces. */
	private static final double REACH_SQUARED = 4.5D * 4.5D;

	private static final Random RANDOM = new Random();

	// --- State ------------------------------------------------------------

	private static boolean enabled = false;
	private static boolean evading = false;
	private static boolean building = false;

	private static int strafeDir = 1;
	private static int strafeTimer = 0;
	private static int wTapTimer = 0;
	private static int wTapTicks = 0;
	private static int dodgeTicks = 0;
	private static boolean lastAimedAtUs = false;

	/** Hotbar slot to restore once scaffolding stops (-1 = nothing to restore). */
	private static int restoreSlot = -1;

	/** True while the orchestrators is the reason Auto Walk is on. */
	private static boolean forced = false;
	/** Whether Auto Walk was already on before PVP forced it. */
	private static boolean enabledBeforeForce = false;

	private AutoWalkModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** Automatic enable/disable used by the orchestrators. */
	public static void setForced(boolean on) {
		if (on) {
			if (!forced) {
				forced = true;
				enabledBeforeForce = enabled;
				enabled = true;
			}
		} else if (forced) {
			forced = false;
			if (!enabledBeforeForce) {
				enabled = false;
				evading = false;
				building = false;
				resetState();
				release(MinecraftClient.getInstance());
			}
		}
	}

	/** Short label for the HUD: TRAVEL / EVADE / BUILD / OFF. */
	public static String getMode() {
		if (!enabled) {
			return "OFF";
		}
		if (evading) {
			return "EVADE";
		}
		if (building) {
			return "BUILD";
		}
		return "TRAVEL";
	}

	public static void uiToggle() {
		MinecraftClient client = MinecraftClient.getInstance();
		enabled = !enabled;
		resetState();
		evading = false;
		building = false;
		release(client);
		message(client, "Auto Walk: " + (enabled ? "ON" : "OFF"));
	}
	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(AutoWalkModule::tick);
	}

	public static void reset() {
		enabled = false;
		evading = false;
		building = false;
		resetState();
		release(MinecraftClient.getInstance());
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.walkToggle.wasPressed()) {
			enabled = !enabled;
			resetState();
			evading = false;
			building = false;
			release(client);
			message(client, "Auto Walk: " + (enabled ? "ON" : "OFF"));
		}

		if (!enabled) {
			return;
		}

		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null || player.isSpectator()) {
			release(client);
			return;
		}

		building = false;
		Entity threat = nearestThreat(client, player);
		evading = threat != null && threat.squaredDistanceTo(player) <= THREAT_RADIUS * THREAT_RADIUS;

		if (evading) {
			// Keep the weapon in hand while fighting.
			restoreSlot(client, player);
			evade(client, player, threat);
		} else {
			travel(client, player);
			if (!building) {
				restoreSlot(client, player);
			}
		}
	}

	// --- Travel -----------------------------------------------------------

	private static void travel(MinecraftClient client, ClientPlayerEntity player) {
		boolean forward = true;
		boolean sneak = false;
		boolean jump = false;

		Direction facing = player.getHorizontalFacing();
		BlockPos ahead = player.getBlockPos().offset(facing);

		// Never walk into lava.
		if (isLava(client, ahead) || isLava(client, player.getBlockPos())) {
			forward = false;
			sneak = true;
		}

		// Bridge over (or stop at) a dangerous drop directly ahead. Real
		// bridging: stop and sneak at the edge, look down at the gap and place
		// against the ground under us, then step forward. Walking while placing
		// gets the server's placement rejected (wrong rotation) and drops you
		// into the void.
		if (forward) {
			BlockPos aheadGround = ahead.down();
			boolean gap = !isGround(client, aheadGround) && !hasGroundBelow(client, aheadGround, SAFE_DROP);
			if (gap) {
				if (!player.isOnGround()) {
					// Airborne over a gap we cannot fix this tick.
					forward = false;
				} else {
					// Hold the edge with sneak so vanilla's clamp keeps us on.
					forward = false;
					sneak = true;
					building = bridge(client, player, aheadGround);
					if (building) {
						// Block placed: step onto it.
						forward = true;
						sneak = false;
					}
				}
			}
		}

		// Auto-jump over a one-block ledge or obstacle.
		if (forward && player.isOnGround() && player.horizontalCollision) {
			jump = true;
		}

		apply(client, forward, false, false, false, jump, sneak, forward && !sneak);
	}

	// --- Evade ------------------------------------------------------------

	private static void evade(MinecraftClient client, ClientPlayerEntity player, Entity threat) {
		double dist = Math.sqrt(threat.squaredDistanceTo(player));

		// Sustained circles: humans hold a strafe for a beat, not twitch.
		if (--strafeTimer <= 0) {
			strafeDir = RANDOM.nextBoolean() ? 1 : -1;
			strafeTimer = STRAFE_MIN + RANDOM.nextInt(STRAFE_MAX - STRAFE_MIN + 1);
		}

		// Instinct: the instant their crosshair lands on us, juke - flip the
		// circle and burst-move with a jump. This reads the attacker's look
		// vector, so the dodge happens on their swing wind-up, not randomly.
		boolean aimedAtUs = isAimingAtUs(threat, player) && dist < 4.5D;
		if (aimedAtUs && !lastAimedAtUs) {
			strafeDir = -strafeDir;
			strafeTimer = STRAFE_MIN + RANDOM.nextInt(STRAFE_MAX - STRAFE_MIN + 1);
			dodgeTicks = DODGE_TICKS;
		}
		lastAimedAtUs = aimedAtUs;

		// Fight-distance bands: approach from far, orbit in the sweet spot,
		// back off only when they are glued to us.
		boolean forward = dist > FAR_RANGE;
		boolean back = dist < CLOSE_RANGE;
		boolean left = strafeDir < 0;
		boolean right = strafeDir > 0;

		// Don't dodge off a cliff: cancel input that would step into a drop or
		// lava, flipping the strafe direction if that is the problem.
		if (!isStepSafe(client, player, moveVector(player, forward, back, left, right))) {
			if (left || right) {
				strafeDir = -strafeDir;
				strafeTimer = STRAFE_MIN;
				left = strafeDir < 0;
				right = strafeDir > 0;
			} else if (back) {
				back = false;
				forward = true;
			} else {
				forward = false;
				back = true;
			}

			if (!isStepSafe(client, player, moveVector(player, forward, back, left, right))) {
				// Nowhere safe to go: hold position this tick.
				forward = false;
				back = false;
				left = false;
				right = false;
			}
		}

		boolean jump = player.isOnGround()
				&& (dodgeTicks > 0 || RANDOM.nextDouble() < JUMP_CHANCE);
		boolean sneak = !jump && player.isOnGround() && RANDOM.nextDouble() < CROUCH_CHANCE;
		if (dodgeTicks > 0) {
			dodgeTicks--;
		}

		// W-tap: cut forward briefly at melee range so the next hit carries a
		// fresh sprint - more knockback dealt, the classic combo mechanic.
		if (dist <= 3.2D) {
			if (++wTapTimer >= W_TAP_INTERVAL) {
				wTapTimer = 0;
				wTapTicks = W_TAP_TICKS;
			}
		} else {
			wTapTimer = 0;
		}
		if (wTapTicks > 0) {
			wTapTicks--;
			forward = false;
		}

		apply(client, forward, back, left, right, jump, sneak, !sneak);
	}

	/**
	 * True when the attacker's crosshair is on us - the tell that a swing or
	 * shot is coming. This is what the juke reaction reads.
	 */
	private static boolean isAimingAtUs(Entity threat, ClientPlayerEntity player) {
		Vec3d toUs = player.getEyePos().subtract(threat.getEyePos());
		if (toUs.lengthSquared() < 1.0E-6D) {
			return false;
		}
		return threat.getRotationVec(1.0F).dotProduct(toUs.normalize()) > 0.93D;
	}

	/** World-space horizontal direction implied by a set of movement inputs. */
	private static Vec3d moveVector(ClientPlayerEntity player, boolean forward, boolean back,
									boolean left, boolean right) {
		Vec3d look = player.getRotationVec(1.0F);
		Vec3d fwd = new Vec3d(look.x, 0.0D, look.z);
		if (fwd.lengthSquared() < 1.0E-6D) {
			return Vec3d.ZERO;
		}
		fwd = fwd.normalize();
		Vec3d rightVec = new Vec3d(-fwd.z, 0.0D, fwd.x);

		Vec3d out = Vec3d.ZERO;
		if (forward) {
			out = out.add(fwd);
		}
		if (back) {
			out = out.subtract(fwd);
		}
		if (right) {
			out = out.add(rightVec);
		}
		if (left) {
			out = out.subtract(rightVec);
		}
		return out;
	}

	/** Whether stepping one block along {@code move} keeps us over solid ground. */
	private static boolean isStepSafe(MinecraftClient client, ClientPlayerEntity player, Vec3d move) {
		if (move.lengthSquared() < 1.0E-4D) {
			return true;
		}
		Vec3d probe = player.getPos().add(move.x * 1.25D, 0.0D, move.z * 1.25D);
		BlockPos feet = BlockPos.ofFloored(probe.x, player.getY(), probe.z);
		if (isLava(client, feet) || isLava(client, feet.down())) {
			return false;
		}
		return hasGroundBelow(client, feet, SAFE_DROP);
	}

	// --- Building / scaffolding -------------------------------------------

	/** Bridges across the gap ahead: place over the gap, else under ourselves. */
	private static boolean bridge(MinecraftClient client, ClientPlayerEntity player, BlockPos gapPos) {
		if (tryBuild(client, player, gapPos)) {
			return true;
		}
		// Fall back to pillaring off the block under our own feet.
		return tryBuild(client, player, player.getBlockPos().down());
	}

	/**
	 * Places a block at {@code pos} against the nearest solid neighbour, first
	 * aiming at the exact face being clicked - servers reject placements whose
	 * rotation is nowhere near the hit vector.
	 */
	private static boolean tryBuild(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
		if (client.interactionManager == null || !canReplace(client, pos) || !ensureBlockSelected(client, player)) {
			return false;
		}
		for (Direction side : Direction.values()) {
			// `side` is the face of the neighbour that touches `pos`.
			BlockPos neighbour = pos.offset(side.getOpposite());
			BlockState state = client.world.getBlockState(neighbour);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.isReplaceable()
					|| state.getCollisionShape(client.world, neighbour).isEmpty()) {
				continue;
			}

			Vec3d hitPos = Vec3d.ofCenter(neighbour).add(Vec3d.of(side.getVector()).multiply(0.5D));
			if (player.getEyePos().squaredDistanceTo(hitPos) > REACH_SQUARED) {
				continue;
			}
			lookAt(player, hitPos);
			BlockHitResult hit = new BlockHitResult(hitPos, side, neighbour, false);
			ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
			if (result.isAccepted()) {
				player.swingHand(Hand.MAIN_HAND);
				return true;
			}
		}
		return false;
	}

	/** Points yaw and pitch at a world position (so block places look legit). */
	private static void lookAt(ClientPlayerEntity player, Vec3d target) {
		Vec3d eyes = player.getEyePos();
		double dx = target.x - eyes.x;
		double dy = target.y - eyes.y;
		double dz = target.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		if (horizontal > 1.0E-4D) {
			player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI))));
		}
	}

	private static boolean canReplace(MinecraftClient client, BlockPos pos) {
		BlockState state = client.world.getBlockState(pos);
		return state.isAir() || state.isReplaceable();
	}

	/** Ensures a full block is held, silently switching hotbar slots if needed. */
	private static boolean ensureBlockSelected(MinecraftClient client, ClientPlayerEntity player) {
		if (isPlaceableBlock(player.getMainHandStack())) {
			return true;
		}
		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (isPlaceableBlock(inventory.getStack(slot))) {
				if (restoreSlot < 0) {
					restoreSlot = inventory.getSelectedSlot();
				}
				inventory.setSelectedSlot(slot);
				return true;
			}
		}
		return false;
	}

	private static boolean isPlaceableBlock(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
			return false;
		}
		BlockState state = ((BlockItem) stack.getItem()).getBlock().getDefaultState();
		// Only full, opaque blocks make a reliable bridge (skips torches, etc.).
		return !state.isAir() && state.isOpaque();
	}

	private static void restoreSlot(MinecraftClient client, ClientPlayerEntity player) {
		if (restoreSlot >= 0) {
			player.getInventory().setSelectedSlot(restoreSlot);
			restoreSlot = -1;
		}
	}

	// --- World helpers ----------------------------------------------------

	private static boolean isGround(MinecraftClient client, BlockPos pos) {
		BlockState state = client.world.getBlockState(pos);
		return state.getFluidState().isEmpty() && !state.getCollisionShape(client.world, pos).isEmpty();
	}

	private static boolean hasGroundBelow(MinecraftClient client, BlockPos pos, int depth) {
		for (int i = 0; i <= depth; i++) {
			if (isGround(client, pos.down(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLava(MinecraftClient client, BlockPos pos) {
		return client.world.getFluidState(pos).isIn(FluidTags.LAVA);
	}

	// --- Threats ----------------------------------------------------------

	private static Entity nearestThreat(MinecraftClient client, ClientPlayerEntity player) {
		Box box = player.getBoundingBox().expand(THREAT_RADIUS);
		Entity best = null;
		double bestSq = Double.MAX_VALUE;
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e != player && e != client.getCameraEntity() && e.isAlive() && Teams.isEnemy(e, player))) {
			double distSq = entity.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = entity;
			}
		}
		return best;
	}

	// --- Input plumbing ---------------------------------------------------

	private static void apply(MinecraftClient client, boolean forward, boolean back, boolean left, boolean right,
							  boolean jump, boolean sneak, boolean sprint) {
		client.options.forwardKey.setPressed(forward);
		client.options.backKey.setPressed(back);
		client.options.leftKey.setPressed(left);
		client.options.rightKey.setPressed(right);
		client.options.jumpKey.setPressed(jump);
		client.options.sneakKey.setPressed(sneak);
		client.options.sprintKey.setPressed(sprint && !sneak);
	}

	private static void release(MinecraftClient client) {
		if (client == null) {
			return;
		}
		if (client.options != null) {
			apply(client, false, false, false, false, false, false, false);
		}
		if (client.player != null) {
			restoreSlot(client, client.player);
		} else {
			restoreSlot = -1;
		}
	}

	private static void resetState() {
		strafeDir = 1;
		strafeTimer = 0;
		wTapTimer = 0;
		wTapTicks = 0;
		dodgeTicks = 0;
		lastAimedAtUs = false;
	}

	private static void message(MinecraftClient client, String text) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(text), true);
		}
	}
}
