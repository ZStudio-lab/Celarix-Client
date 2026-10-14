package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Inventory Walk — keeps working movement keys while any screen is open
 * (inventory, chest, chat, the Celarix menu, ...).
 *
 * <p>With a screen open the game stops polling the movement keybindings, so
 * walking halts. {@code KeyboardInputMixin} rebuilds the movement input from
 * the raw physical key states instead, so sneaking, jumping and sprinting
 * through a screen behave exactly like in the normal game.
 *
 * <p>On unpaused worlds (every server, and singleplayer with a
 * non-pausing screen) movement continues; on a paused singleplayer world
 * the game itself does not tick the player, which no mod can bypass.
 */
public final class InventoryWalkModule {

	private static boolean enabled = false;

	private InventoryWalkModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Inventory Walk: " + (enabled ? "ON" : "OFF")), true);
		}
	}
}
