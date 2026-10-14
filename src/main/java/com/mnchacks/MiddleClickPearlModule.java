package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * Middle Click Pearl — throws an ender pearl the moment you middle-click
 * while holding one (no need to aim-throw with right click timing).
 */
public final class MiddleClickPearlModule {

	private static boolean enabled = false;
	private static boolean prevPick = false;
	private static int useHold = 0;

	private MiddleClickPearlModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("MC Pearl: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		useHold = 0;
		prevPick = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(MiddleClickPearlModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			useHold = 0;
			return;
		}

		if (useHold > 0) {
			if (--useHold == 0) {
				client.options.useKey.setPressed(false);
			}
			return;
		}

		boolean pick = client.options.pickItemKey.isPressed();
		if (pick && !prevPick && isHoldingPearl(player)) {
			// Tap the use key: vanilla's doItemUse throws the pearl.
			client.options.useKey.setPressed(true);
			useHold = 2;
		}
		prevPick = pick;
	}

	private static boolean isHoldingPearl(ClientPlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		ItemStack off = player.getOffHandStack();
		return main.getItem() == Items.ENDER_PEARL || off.getItem() == Items.ENDER_PEARL;
	}
}