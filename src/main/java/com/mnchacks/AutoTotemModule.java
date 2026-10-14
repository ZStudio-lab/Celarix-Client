package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class AutoTotemModule {

	private static final int OFFHAND_SLOT = 45;

	private static boolean enabled = false;
	private static int cooldown = 0;
	private static int phase = 0;
	private static int sourceSlot = -1;

	private AutoTotemModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoTotemModule::tick);
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
			client.player.sendMessage(Text.literal("Auto Totem: " + (enabled ? "ON" : "OFF")), true);
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

		if (player.getEquippedStack(EquipmentSlot.OFFHAND).getItem() == Items.TOTEM_OF_UNDYING) {
			return;
		}

		if (phase == 0) {
			for (int i = 0; i < inventory.getMainStacks().size(); i++) {
				if (inventory.getMainStacks().get(i).getItem() == Items.TOTEM_OF_UNDYING) {
					sourceSlot = i < 9 ? i + 36 : i;
					client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
					phase = 1;
					cooldown = 3;
					return;
				}
			}
			cooldown = 40;
			return;
		}
		if (phase == 1) {
			client.interactionManager.clickSlot(syncId, OFFHAND_SLOT, 0, SlotActionType.PICKUP, player);
			phase = 2;
			cooldown = 3;
			return;
		}
		client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
		phase = 0;
		sourceSlot = -1;
		cooldown = 10;
	}
}