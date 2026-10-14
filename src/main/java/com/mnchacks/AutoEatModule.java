package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class AutoEatModule {

	private static boolean enabled = false;
	private static double threshold = 14.0D;
	private static int cooldown = 0;
	private static int swapPhase = 0;
	private static int swapSourceSlot = -1;

	private AutoEatModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoEatModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
		swapPhase = 0;
		swapSourceSlot = -1;
		release();
	}

	public static double getThreshold() {
		return threshold;
	}

	public static void setThreshold(double value) {
		threshold = value;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Eat: " + (enabled ? "ON" : "OFF")), true);
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
			release();
			return;
		}

		if (player.getHungerManager().getFoodLevel() > (int) threshold) {
			release();
			return;
		}

		PlayerInventory inventory = player.getInventory();

		// Food in the hotbar: hold the use key until eaten.
		for (int slot = 0; slot < 9; slot++) {
			if (isFood(inventory.getStack(slot))) {
				inventory.setSelectedSlot(slot);
				client.options.useKey.setPressed(true);
				return;
			}
		}

		// Food in the backpack: drag it into the hotbar.
		for (int i = 9; i < inventory.getMainStacks().size(); i++) {
			if (!isFood(inventory.getMainStacks().get(i))) {
				continue;
			}
			int syncId = player.playerScreenHandler.syncId;
			if (swapPhase == 0) {
				for (int hotbar = 0; hotbar < 9; hotbar++) {
					if (inventory.getStack(hotbar).isEmpty()) {
						client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, player);
						cooldown = 5;
						return;
					}
				}
				swapSourceSlot = i;
				client.interactionManager.clickSlot(syncId, swapSourceSlot, 0, SlotActionType.PICKUP, player);
				swapPhase = 1;
				cooldown = 3;
				return;
			}
			if (swapPhase == 1) {
				client.interactionManager.clickSlot(syncId, 36 + inventory.getSelectedSlot(), 0,
						SlotActionType.PICKUP, player);
				swapPhase = 2;
				cooldown = 3;
				return;
			}
			client.interactionManager.clickSlot(syncId, swapSourceSlot, 0, SlotActionType.PICKUP, player);
			swapPhase = 0;
			swapSourceSlot = -1;
			cooldown = 3;
			return;
		}
		release();
	}

	private static boolean isFood(ItemStack stack) {
		return !stack.isEmpty() && stack.get(DataComponentTypes.FOOD) != null;
	}

	private static void release() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.options != null) {
			client.options.useKey.setPressed(false);
		}
	}
}