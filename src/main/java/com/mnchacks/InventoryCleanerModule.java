package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;

public final class InventoryCleanerModule {

	private static final List<String> JUNK = List.of(
			"rotten flesh", "spider eye", "poisonous potato", "dead bush",
			"bone", "feather", "egg", "glass bottle", "bowl", "string");

	private static boolean enabled = false;
	private static int cooldown = 0;

	private InventoryCleanerModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(InventoryCleanerModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Inventory Cleaner: " + (enabled ? "ON" : "OFF")), true);
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
		var inventory = player.getInventory();
		for (int i = 9; i < inventory.getMainStacks().size(); i++) {
			ItemStack stack = inventory.getMainStacks().get(i);
			if (stack.isEmpty() || stack.hasEnchantments()) {
				continue;
			}
			String name = stack.getName().getString().toLowerCase(java.util.Locale.ROOT);
			boolean junk = false;
			for (String entry : JUNK) {
				if (name.equals(entry)) {
					junk = true;
					break;
				}
			}
			if (!junk) {
				continue;
			}
			int handlerSlot = i;
			client.interactionManager.clickSlot(player.playerScreenHandler.syncId, handlerSlot, 1,
					SlotActionType.THROW, player);
			cooldown = 5;
			return;
		}
	}
}