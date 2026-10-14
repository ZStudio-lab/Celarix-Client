package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class AutoArmorModule {

	/** Armor body slots to scan, feet → head. */
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
	/** Matching container-slot ids in the player inventory screen (5=head → 8=feet). */
	private static final int[] HANDLER_SLOTS = {8, 7, 6, 5};

	private static boolean enabled = false;
	private static int cooldown = 0;
	private static int phase = 0;
	private static int armorSlot = -1;
	private static int sourceSlot = -1;

	private AutoArmorModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoArmorModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
		phase = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Armor: " + (enabled ? "ON" : "OFF")), true);
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
		PlayerInventory inventory = player.getInventory();
		int syncId = player.playerScreenHandler.syncId;

		if (phase == 0) {
			for (int a = 0; a < ARMOR_SLOTS.length; a++) {
				ItemStack worn = player.getEquippedStack(ARMOR_SLOTS[a]);
				float wornScore = armorScore(worn);
				int handlerSlot = HANDLER_SLOTS[a];
				for (int i = 0; i < inventory.getMainStacks().size(); i++) {
					ItemStack stack = inventory.getMainStacks().get(i);
					if (!isArmorFor(stack, ARMOR_SLOTS[a])) {
						continue;
					}
					if (armorScore(stack) > wornScore) {
						armorSlot = handlerSlot;
						sourceSlot = i;
						client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
						phase = 1;
						cooldown = 3;
						return;
					}
				}
			}
			cooldown = 40;
			return;
		}
		if (phase == 1) {
			client.interactionManager.clickSlot(syncId, armorSlot, 0, SlotActionType.PICKUP, player);
			phase = 2;
			cooldown = 3;
			return;
		}
		client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
		phase = 0;
		armorSlot = -1;
		sourceSlot = -1;
		cooldown = 10;
	}

	/** Is this stack an equippable item for the given armor body slot? */
	private static boolean isArmorFor(ItemStack stack, EquipmentSlot slot) {
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		return equippable != null && equippable.slot() == slot;
	}

	/**
	 * Defense score: sum of the addition armor attribute modifiers
	 * (replaces the removed ArmorItem.getProtection()).
	 */
	private static float armorScore(ItemStack stack) {
		if (stack.isEmpty()) {
			return -1.0F;
		}
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		if (equippable == null) {
			return -1.0F;
		}
		EquipmentSlot slot = equippable.slot();
		float[] score = {0.0F};
		stack.applyAttributeModifiers(slot, (RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) -> {
			if (attribute == EntityAttributes.ARMOR
					&& modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
				score[0] += (float) modifier.value();
			}
		});
		return score[0];
	}
}