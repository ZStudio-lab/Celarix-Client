package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Ranged weapon handling for the combat modules — this is what lets the bot
 * actually use the other weapons in the game instead of only melee.
 *
 * <p>When the enemy is at bow range (6–32 blocks) with a clear line of sight,
 * this switches to a bow, leads the target, compensates arrow drop, draws to
 * near full charge and releases. It reports whether it took over the tick so
 * the caller can stand the melee assists (aimbot/clicker) down — they would
 * otherwise fight the bow for the crosshair. Too close, out of arrows or no
 * clear shot → it returns false and the caller falls back to its best melee
 * weapon via melee weapon selection.</p>
 *
 * <p>Drawing works through the vanilla input path: holding the use key makes
 * the game start the draw ({@code doItemUse}) and releasing it fires the
 * arrow, so the server sees completely normal bow play.</p>
 */
public final class RangedModule {

	/** Closer than this the bow is a liability — use the melee weapon. */
	private static final double MIN_RANGE = 8.0D;
	/** Arrow accuracy falls off past this. */
	private static final double MAX_RANGE = 32.0D;
	/** Ticks to hold the draw (20 would be full power). */
	private static final int CHARGE_TICKS = 18;
	/** Pause between shots. */
	private static final int RECOVER_TICKS = 10;
	/** Full-draw arrow speed, blocks per tick. */
	private static final double ARROW_SPEED = 3.0D;
	/** Arrow gravity, blocks per tick². */
	private static final double ARROW_GRAVITY = 0.05D;

	private static boolean engaging = false;
	private static int chargeTicks = 0;
	private static int recoverTicks = 0;
	private static int savedSlot = -1;
	/** Cooldown between bow-into-hotbar transfers. */
	private static int bowMoveCooldown = 0;
	/** True while firing a short point-blank draw (bow-only loadout). */
	private static boolean quickShot = false;

	private RangedModule() {
	}

	public static boolean isEngaging() {
		return engaging;
	}

	public static void reset() {
		engaging = false;
		chargeTicks = 0;
		recoverTicks = 0;
		savedSlot = -1;
		bowMoveCooldown = 0;
		quickShot = false;
	}

	/** Stands down: releases the draw and restores the previous hotbar slot. */
	public static void stop(MinecraftClient client) {
		if (client != null && client.options != null) {
			client.options.useKey.setPressed(false);
		}
		if (client != null && client.player != null && savedSlot >= 0) {
			client.player.getInventory().setSelectedSlot(savedSlot);
		}
		reset();
	}

	/**
	 * Tries to fight {@code target} with a bow. Returns true when it handled
	 * the fight this tick (the caller must not force the aimbot/clicker).
	 */
	public static boolean engage(MinecraftClient client, ClientPlayerEntity player, Entity target) {
		if (client.interactionManager == null) {
			return cancel();
		}
		double distSq = target.squaredDistanceTo(player);
		if (distSq > MAX_RANGE * MAX_RANGE) {
			return cancel();
		}
		if (distSq < MIN_RANGE * MIN_RANGE && hasMeleeWeapon(player)) {
			// Melee is better up close — unless the bow is all we carry.
			return cancel();
		}
		// Never fight the auto-healer for the use key.
		if (player.isUsingItem() && player.getMainHandStack().get(DataComponentTypes.FOOD) != null) {
			return cancel();
		}

		int bowSlot = findBowSlot(player);
		if (bowSlot < 0) {
			// Bow not in the hotbar: drag it out of the backpack, then shoot
			// on a following tick.
			prepareLoadout(client, player);
			return cancel();
		}
		if (!hasAmmo(player) || !hasLineOfSight(client, player, target)) {
			return cancel();
		}

		if (!engaging) {
			savedSlot = player.getInventory().getSelectedSlot();
			engaging = true;
			chargeTicks = 0;
		}
		player.getInventory().setSelectedSlot(bowSlot);

		aimAt(player, target, distSq);

		if (recoverTicks > 0) {
			recoverTicks--;
			return true;
		}

		if (!player.isUsingItem()) {
			// Holding the use key makes vanilla start the draw.
			chargeTicks = 0;
			quickShot = !hasMeleeWeapon(player); // bow-only: fire fast even up close
			client.options.useKey.setPressed(true);
			return true;
		}

		chargeTicks++;
		if (chargeTicks >= (quickShot ? 5 : CHARGE_TICKS)) {
			// Releasing the key fires the arrow.
			client.options.useKey.setPressed(false);
			chargeTicks = 0;
			recoverTicks = RECOVER_TICKS;
		}
		return true;
	}

	/** Any real melee weapon in the hotbar? (bows do not count) */
	private static boolean hasMeleeWeapon(ClientPlayerEntity player) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			Item item = stack.getItem();
			if (stack.isIn(ItemTags.SWORDS) || item instanceof AxeItem || item instanceof TridentItem) {
				return true;
			}
		}
		return false;
	}

	/** Abandons the bow for this tick (releases a half draw, keeps the slot). */
	private static boolean cancel() {
		if (engaging) {
			engaging = false;
			chargeTicks = 0;
			quickShot = false;
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null && client.options != null) {
				client.options.useKey.setPressed(false);
			}
		}
		return false;
	}

	private static int findBowSlot(ClientPlayerEntity player) {
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getStack(slot).getItem() instanceof BowItem) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * Drags a bow out of the backpack into a free hotbar slot (shift-click).
	 * Without this the bow was "never used" whenever it sat in the inventory.
	 */
	private static void prepareLoadout(MinecraftClient client, ClientPlayerEntity player) {
		if (bowMoveCooldown > 0) {
			bowMoveCooldown--;
			return;
		}
		if (client.interactionManager == null) {
			return;
		}
		PlayerInventory inventory = player.getInventory();
		int backpackBow = -1;
		for (int i = 9; i < inventory.getMainStacks().size(); i++) {
			if (inventory.getMainStacks().get(i).getItem() instanceof BowItem) {
				backpackBow = i;
				break;
			}
		}
		if (backpackBow < 0) {
			return;
		}
		for (int i = 0; i < 9; i++) {
			if (inventory.getStack(i).isEmpty()) {
				// PlayerScreenHandler: backpack index maps 1:1 to handler slot.
				client.interactionManager.clickSlot(player.playerScreenHandler.syncId, backpackBow, 0,
						SlotActionType.QUICK_MOVE, player);
				bowMoveCooldown = 10;
				return;
			}
		}
	}

	private static boolean hasAmmo(ClientPlayerEntity player) {
		if (player.getAbilities().creativeMode) {
			return true;
		}
		for (ItemStack stack : player.getInventory().getMainStacks()) {
			if (stack.getItem() instanceof ArrowItem) {
				return true;
			}
		}
		if (player.getEquippedStack(EquipmentSlot.OFFHAND).getItem() instanceof ArrowItem) {
			return true;
		}
		return false;
	}

	private static boolean hasLineOfSight(MinecraftClient client, ClientPlayerEntity player, Entity target) {
		Vec3d start = player.getEyePos();
		Vec3d end = target.getPos().add(0.0D, target.getHeight() * 0.6D, 0.0D);
		BlockHitResult hit = client.world.raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Leads the target and compensates arrow drop. */
	private static void aimAt(ClientPlayerEntity player, Entity target, double distSq) {
		double dist = Math.sqrt(distSq);
		double flight = dist / ARROW_SPEED;
		Vec3d aim = target.getPos()
				.add(0.0D, target.getHeight() * 0.6D, 0.0D)
				.add(target.getVelocity().multiply(flight));
		aim = aim.add(0.0D, 0.5D * ARROW_GRAVITY * flight * (flight + 1.0D), 0.0D);

		Vec3d eyes = player.getEyePos();
		double dx = aim.x - eyes.x;
		double dy = aim.y - eyes.y;
		double dz = aim.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 1.0E-4D) {
			return;
		}
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI))));
	}
}