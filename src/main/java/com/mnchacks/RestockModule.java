package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Restock — keeps the held hotbar stack topped up. Whenever the
 * selected slot drops to a handful (or runs empty), the matching stack is
 * quick-moved in from the backpack. The desired item type is remembered even
 * after the last one is used up, so empty slots refill too.
 */
public final class RestockModule {

	private static final int REFILL_AT = 5;

	private static boolean enabled = false;
	private static int cooldown = 0;
	private static boolean hasDesired = false;
	private static ItemStack desired = ItemStack.EMPTY;

	private RestockModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(RestockModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
		hasDesired = false;
		desired = ItemStack.EMPTY;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Restock: " + (enabled ? "ON" : "OFF")), true);
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

		var inventory = player.getInventory();
		ItemStack held = inventory.getStack(inventory.getSelectedSlot());
		if (!held.isEmpty()) {
			desired = held.copy();
			hasDesired = true;
		}
		if (!hasDesired || (held.getCount() > REFILL_AT && !held.isEmpty())) {
			return;
		}

		int syncId = player.playerScreenHandler.syncId;
		for (int i = 9; i < inventory.getMainStacks().size(); i++) {
			ItemStack stack = inventory.getMainStacks().get(i);
			if (stack.isEmpty() || !ItemStack.areItemsEqual(stack, desired)) {
				continue;
			}
			client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, player);
			cooldown = 5;
			return;
		}
	}
}
