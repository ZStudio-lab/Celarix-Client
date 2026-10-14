package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Single-player Survival autopilot (toggle: C).
 *
 * <p>A priority state machine that keeps the player alive and progressing:</p>
 * <ol>
 *   <li><b>FIGHT</b> - hostiles nearby: the full combat stack (aimbot, auto
 *   click, best weapon, evasive movement, auto heal).</li>
 *   <li><b>LOOT</b> - walks over item drops (mob drops, mining drops).</li>
 *   <li><b>CHEST</b> - opens a nearby chest/barrel and empties it.</li>
 *   <li><b>SHELTER</b> - at night with no immediate danger: pillar-jumps 4
 *   blocks up and waits out the darkness out of melee reach.</li>
 *   <li><b>MINE</b> - scans loaded chunks for valuable ore (diamond >
 *   emerald > gold > lapis > iron > coal), walks to it, selects the best
 *   pickaxe and mines it. Digs straight down (lava-checked) when the vein is
 *   below.</li>
 *   <li><b>EXPLORE</b> - nothing visible: sweeps in rotating headings so new
 *   chunks (and new ore) keep loading.</li>
 * </ol>
 *
 * <p>Everything runs through the same vanilla-legal input paths as the other
 * modules: simulated keys, real block-breaking, real chest clicks.</p>
 */
public final class SurvivalModule {

	/** Hostiles this close are fought. */
	private static final double FIGHT_RADIUS = 7.0D;
	/** Pick up drops within this distance. */
	private static final double LOOT_RADIUS = 10.0D;
	/** Chest/barrel scan radius. */
	private static final int CHEST_SCAN = 8;
	/** Ore scan radius (horizontal) and vertical band. */
	private static final int ORE_SCAN = 20;
	private static final int ORE_SCAN_Y = 10;
	/** Block interaction reach. */
	private static final double REACH = 4.5D;
	/** Shelter pillar height. */
	private static final int PILLAR_HEIGHT = 4;
	/** Cooldowns (ticks). */
	private static final int CHEST_COOLDOWN = 80;
	private static final int MINE_COOLDOWN = 6;

	private static boolean enabled = false;
	private static String status = "OFF";

	private static int chestCooldown = 0;
	private static int mineCooldown = 0;
	private static int exploreTimer = 0;
	private static int exploreIndex = 0;
	/** Y level where the shelter pillar started (-1 = not sheltering). */
	private static int shelterStartY = -1;

	private SurvivalModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** Used by the in-game menu (no keybind press needed). */
	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (enabled) {
			CombatBrain.reset();
		} else {
			standDown();
		}
		status = enabled ? "STARTING" : "OFF";
		message(client, "Survival Bot: " + (enabled ? "ON" : "OFF"));
	}

	public static String getStatus() {
		return status;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SurvivalModule::tick);
	}

	public static void reset() {
		enabled = false;
		status = "OFF";
		standDown();
		chestCooldown = 0;
		mineCooldown = 0;
		exploreTimer = 0;
		exploreIndex = 0;
		shelterStartY = -1;
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.survivalToggle.wasPressed()) {
			enabled = !enabled;
			if (enabled) {
				CombatBrain.reset();
			} else {
				standDown();
			}
			status = enabled ? "STARTING" : "OFF";
			message(client, "Survival Bot: " + (enabled ? "ON" : "OFF"));
		}

		if (!enabled) {
			return;
		}

		if (chestCooldown > 0) {
			chestCooldown--;
		}
		if (mineCooldown > 0) {
			mineCooldown--;
		}

		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || player.isSpectator()) {
			standDown();
			status = "OFF";
			return;
		}

		// Eyes always on; healing is handled by Health Freeze (network layer).
		EspModule.setForced(true);

		// A chest we opened gets emptied before anything else happens.
		if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> handled) {
			handleChestScreen(client, player, handled);
			return;
		}

		// --- 1: fight hostiles -------------------------------------------
		CombatBrain.onTick(client, player);
		Entity mob = CombatBrain.acquireTarget(client, player, FIGHT_RADIUS, 16.0D);
		if (mob != null && mob.squaredDistanceTo(player) <= FIGHT_RADIUS * FIGHT_RADIUS) {
			status = "FIGHT";
			selectBestWeapon(player);
			if (mob.squaredDistanceTo(player) > 3.0D * 3.0D) {
				steerAt(player, mob.getPos().add(0.0D, mob.getHeight() * 0.5D, 0.0D));
			}
			AimModule.setForced(true);
			AutoClickModule.setForced(true);
			AutoWalkModule.setForced(true);
			return;
		}
		AimModule.setForced(false);
		AutoClickModule.setForced(false);

		// --- 2: low health: kite to heal, or keep pushing if Run Away is off
		if (player.getHealth() <= 8.0D && BotConfig.runAway) {
			status = "RETREAT";
			Entity danger = nearestHostile(client, player, 12.0D);
			if (danger != null) {
				steerAway(player, danger.getPos());
			}
			AutoWalkModule.setForced(true);
			return;
		}

		// --- 2b: shoot hostiles at bow range ------------------------------
		Entity rangedTarget = CombatBrain.acquireTarget(client, player, 24.0D, 24.0D);
		if (rangedTarget != null) {
			double distSq = rangedTarget.squaredDistanceTo(player);
			if (distSq >= 8.0D * 8.0D && distSq <= 24.0D * 24.0D
					&& RangedModule.engage(client, player, rangedTarget)) {
				status = "SHOOT";
				AimModule.setForced(false);
				AutoClickModule.setForced(false);
				AutoWalkModule.setForced(false);
				return;
			}
		}

		// --- 3: loot drops -------------------------------------------------
		ItemEntity drop = nearestDrop(client, player);
		if (drop != null) {
			status = "LOOT";
			steer(player, drop.getPos());
			AutoWalkModule.setForced(true);
			return;
		}

		// --- 4: chests ------------------------------------------------------
		if (chestCooldown == 0) {
			BlockPos chest = nearestContainer(client, player);
			if (chest != null) {
				status = "CHEST";
				Vec3d center = Vec3d.ofCenter(chest);
				if (player.getEyePos().squaredDistanceTo(center) <= REACH * REACH) {
					openBlock(client, player, chest);
					chestCooldown = CHEST_COOLDOWN;
				} else {
					steer(player, center);
					AutoWalkModule.setForced(true);
				}
				return;
			}
		}

		// --- 5: shelter at night --------------------------------------------
		if (!client.world.isDay()) {
			status = "SHELTER";
			AutoWalkModule.setForced(false);
			shelter(client, player);
			return;
		}
		shelterStartY = -1;

		// --- 6: mine valuables ----------------------------------------------
		BlockPos ore = nearestOre(client, player);
		if (ore != null && mineCooldown == 0) {
			status = "MINE";
			mine(client, player, ore);
			return;
		}

		// --- 7: explore ------------------------------------------------------
		if (--exploreTimer <= 0) {
			exploreTimer = 240;
			exploreIndex = (exploreIndex + 1) & 7;
		}
		status = "EXPLORE";
		player.setYaw(exploreIndex * 45.0F);
		AutoWalkModule.setForced(true);
	}

	/** Keeps the highest-damage melee weapon in the hotbar selected. */
	private static void selectBestWeapon(ClientPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		int best = -1;
		double bestDamage = 0.0D;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getStack(slot);
			double[] damage = {0.0D};
			stack.applyAttributeModifiers(EquipmentSlot.MAINHAND,
					(RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) -> {
						if (attribute == EntityAttributes.ATTACK_DAMAGE
								&& modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
							damage[0] += modifier.value();
						}
					});
			if (damage[0] > bestDamage) {
				bestDamage = damage[0];
				best = slot;
			}
		}
		if (best >= 0 && best != inventory.getSelectedSlot()) {
			inventory.setSelectedSlot(best);
		}
	}

	/** Turns everything off and restores keys. */
	private static void standDown() {
		AimModule.setForced(false);
		AutoClickModule.setForced(false);
		EspModule.setForced(false);
		AutoWalkModule.setForced(false);
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.options != null) {
			client.options.jumpKey.setPressed(false);
		}
	}

	// --- Mining -------------------------------------------------------------

	private static void mine(MinecraftClient client, ClientPlayerEntity player, BlockPos ore) {
		Vec3d center = Vec3d.ofCenter(ore);
		double distSq = player.getEyePos().squaredDistanceTo(center);

		// In reach: face it and break it.
		if (distSq <= (REACH + 1.0D) * (REACH + 1.0D)) {
			selectBestPickaxe(client, player);
			AutoToolModule.selectBest(player, client.world.getBlockState(ore));
			steerAt(player, center);
			if (client.interactionManager != null) {
				client.interactionManager.updateBlockBreakingProgress(ore, Direction.UP);
				player.swingHand(Hand.MAIN_HAND);
			}
			AutoWalkModule.setForced(false);
			return;
		}

		// Vein is below us: dig a shaft straight down (lava-checked).
		BlockPos feet = player.getBlockPos();
		if (ore.getY() < feet.getY() - 1
				&& Math.abs(ore.getX() - feet.getX()) <= 1
				&& Math.abs(ore.getZ() - feet.getZ()) <= 1) {
			if (isLava(client, feet.down()) || isLava(client, feet.down(2))) {
				mineCooldown = 100; // never dig into lava - wait and rescan
				return;
			}
			selectBestPickaxe(client, player);
			steerAt(player, Vec3d.ofCenter(feet.down()));
			if (client.interactionManager != null) {
				client.interactionManager.updateBlockBreakingProgress(feet.down(), Direction.UP);
				player.swingHand(Hand.MAIN_HAND);
			}
			return;
		}

		// Otherwise walk toward it; Auto Walk handles jumps and bridging.
		steer(player, center);
		AutoWalkModule.setForced(true);
	}

	private static BlockPos nearestOre(MinecraftClient client, ClientPlayerEntity player) {
		BlockPos origin = player.getBlockPos();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(origin.add(-ORE_SCAN, -ORE_SCAN_Y, -ORE_SCAN),
				origin.add(ORE_SCAN, ORE_SCAN_Y, ORE_SCAN))) {
			int weight = oreWeight(client.world.getBlockState(pos).getBlock());
			if (weight == 0) {
				continue;
			}
			double score = Math.sqrt(pos.getSquaredDistance(origin)) - weight;
			if (score < bestScore) {
				bestScore = score;
				best = pos.toImmutable();
			}
		}
		return best;
	}

	private static int oreWeight(Block block) {
		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
			return 12;
		}
		if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
			return 10;
		}
		if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
			return 8;
		}
		if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
			return 5;
		}
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
			return 4;
		}
		if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			return 2;
		}
		return 0;
	}

	private static void selectBestPickaxe(MinecraftClient client, ClientPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		int best = -1;
		int bestScore = 0;
		for (int slot = 0; slot < 9; slot++) {
			int score = pickaxeScore(inventory.getStack(slot));
			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}
		if (best < 0) {
			// Pull one out of the backpack if we own one.
			for (int i = 9; i < inventory.getMainStacks().size(); i++) {
				if (pickaxeScore(inventory.getMainStacks().get(i)) > 0) {
					for (int hotbar = 0; hotbar < 9; hotbar++) {
						if (inventory.getStack(hotbar).isEmpty()) {
							client.interactionManager.clickSlot(player.playerScreenHandler.syncId, i, 0,
									SlotActionType.QUICK_MOVE, player);
							return;
						}
					}
					return;
				}
			}
			return;
		}
		inventory.setSelectedSlot(best);
	}

	private static int pickaxeScore(ItemStack stack) {
		net.minecraft.item.Item item = stack.getItem();
		if (item == Items.NETHERITE_PICKAXE) {
			return 5;
		}
		if (item == Items.DIAMOND_PICKAXE) {
			return 4;
		}
		if (item == Items.IRON_PICKAXE) {
			return 3;
		}
		if (item == Items.STONE_PICKAXE) {
			return 2;
		}
		if (item == Items.WOODEN_PICKAXE) {
			return 1;
		}
		return 0;
	}

	// --- Shelter ------------------------------------------------------------

	/** Pillar-jumps into the air out of melee reach and waits for dawn. */
	private static void shelter(MinecraftClient client, ClientPlayerEntity player) {
		int blockSlot = findBuildBlockSlot(client, player);
		if (blockSlot < 0) {
			// No blocks: dig into the ground instead (safe from melee too).
			BlockPos feet = player.getBlockPos();
			if (client.world.getBlockState(feet.down()).getCollisionShape(client.world, feet.down()).isEmpty()) {
				return;
			}
			if (!isLava(client, feet.down(2)) && !isLava(client, feet.down(3))) {
				mine(client, player, feet.down(3));
			}
			return;
		}

		if (shelterStartY < 0) {
			shelterStartY = player.getBlockPos().getY();
		}
		int height = player.getBlockPos().getY() - shelterStartY;
		if (height >= PILLAR_HEIGHT) {
			// High enough: stand still and wait for the sun.
			client.options.jumpKey.setPressed(false);
			return;
		}

		player.getInventory().setSelectedSlot(blockSlot);
		client.options.jumpKey.setPressed(true);
		// Place a block under our feet at the jump apex.
		if (!player.isOnGround() && player.getVelocity().y <= 0.0D) {
			tryPlace(client, player, player.getBlockPos().down());
		}
	}

	private static int findBuildBlockSlot(MinecraftClient client, ClientPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		int best = -1;
		int bestScore = 0;
		for (int slot = 0; slot < 9; slot++) {
			int score = buildBlockScore(inventory.getStack(slot));
			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}
		if (best >= 0) {
			return best;
		}
		// Drag some out of the backpack.
		if (client.interactionManager != null) {
			for (int i = 9; i < inventory.getMainStacks().size(); i++) {
				if (buildBlockScore(inventory.getMainStacks().get(i)) > 0) {
					for (int hotbar = 0; hotbar < 9; hotbar++) {
						if (inventory.getStack(hotbar).isEmpty()) {
							client.interactionManager.clickSlot(player.playerScreenHandler.syncId, i, 0,
									SlotActionType.QUICK_MOVE, player);
							return -1;
						}
					}
					return -1;
				}
			}
		}
		return -1;
	}

	private static int buildBlockScore(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		String name = stack.getName().getString().toLowerCase(Locale.ROOT);
		if (name.contains("cobblestone")) {
			return 5;
		}
		if (name.contains("dirt")) {
			return 4;
		}
		if (name.contains("planks") || name.contains("wood")) {
			return 3;
		}
		if (name.contains("netherrack")) {
			return 1;
		}
		return 0;
	}

	// --- Loot / chests ------------------------------------------------------

	private static ItemEntity nearestDrop(MinecraftClient client, ClientPlayerEntity player) {
		Box box = player.getBoundingBox().expand(LOOT_RADIUS);
		ItemEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (ItemEntity item : client.world.getEntitiesByClass(ItemEntity.class, box, e -> e.isAlive())) {
			double distSq = item.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = item;
			}
		}
		return best;
	}

	private static Entity nearestHostile(MinecraftClient client, ClientPlayerEntity player, double radius) {
		Box box = player.getBoundingBox().expand(radius);
		Entity best = null;
		double bestSq = radius * radius;
		for (Entity entity : client.world.getEntitiesByClass(Entity.class, box,
				e -> e != player && e.isAlive() && Teams.isEnemy(e, player))) {
			double distSq = entity.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = entity;
			}
		}
		return best;
	}

	private static BlockPos nearestContainer(MinecraftClient client, ClientPlayerEntity player) {
		BlockPos origin = player.getBlockPos();
		BlockPos best = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(origin.add(-CHEST_SCAN, -3, -CHEST_SCAN),
				origin.add(CHEST_SCAN, 3, CHEST_SCAN))) {
			BlockState state = client.world.getBlockState(pos);
			if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) {
				double distSq = pos.getSquaredDistance(origin);
				if (distSq < bestSq) {
					bestSq = distSq;
					best = pos.toImmutable();
				}
			}
		}
		return best;
	}

	/** Shift-clicks everything out of an open chest, then closes it. */
	private static void handleChestScreen(MinecraftClient client, ClientPlayerEntity player,
										  net.minecraft.client.gui.screen.ingame.HandledScreen<?> handled) {
		status = "CHEST";
		if (client.interactionManager != null
				&& handled.getScreenHandler() instanceof GenericContainerScreenHandler container) {
			int size = container.getRows() * 9;
			for (int i = 0; i < size; i++) {
				if (!container.slots.get(i).getStack().isEmpty()) {
					client.interactionManager.clickSlot(container.syncId, i, 0, SlotActionType.QUICK_MOVE, player);
				}
			}
		}
		player.closeHandledScreen();
		chestCooldown = CHEST_COOLDOWN;
	}

	// --- Interaction helpers ------------------------------------------------

	private static void openBlock(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
		if (client.interactionManager == null) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(pos);
		BlockHitResult hit = new BlockHitResult(center, Direction.UP, pos, false);
		client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
		player.swingHand(Hand.MAIN_HAND);
	}

	private static void tryPlace(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
		if (client.interactionManager == null) {
			return;
		}
		BlockState state = client.world.getBlockState(pos);
		if (!state.isAir() && !state.isReplaceable()) {
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
			if (player.getEyePos().squaredDistanceTo(hitPos) > 20.0D) {
				continue;
			}
			steerAt(player, hitPos);
			BlockHitResult hit = new BlockHitResult(hitPos, side, neighbour, false);
			if (client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit).isAccepted()) {
				player.swingHand(Hand.MAIN_HAND);
				return;
			}
		}
	}

	private static boolean isLava(MinecraftClient client, BlockPos pos) {
		return client.world.getFluidState(pos).isIn(FluidTags.LAVA);
	}

	private static void steer(ClientPlayerEntity player, Vec3d target) {
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		if (dx * dx + dz * dz < 1.0E-4D) {
			return;
		}
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
	}

	/** Faces yaw + pitch at a point (for mining and placing). */
	private static void steerAt(ClientPlayerEntity player, Vec3d target) {
		Vec3d eyes = player.getEyePos();
		double dx = target.x - eyes.x;
		double dy = target.y - eyes.y;
		double dz = target.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 1.0E-4D) {
			return;
		}
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI))));
	}

	private static void steerAway(ClientPlayerEntity player, Vec3d from) {
		double dx = player.getX() - from.x;
		double dz = player.getZ() - from.z;
		if (dx * dx + dz * dz < 1.0E-4D) {
			return;
		}
		player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
	}

	private static void message(MinecraftClient client, String text) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(text), true);
		}
	}
}
