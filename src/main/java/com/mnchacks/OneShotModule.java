package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * One-shot ("one hit kill") module.
 *
 * <p>Damage is calculated by the server, so the client cannot make anything
 * die in one hit on its own. The client toggles the module and tells the
 * server, which then makes that player's melee attacks lethal.</p>
 *
 * <p>Works on the integrated (single player / LAN host) server and on any
 * dedicated server that also has this jar. On a vanilla server with no mod it
 * does nothing - the server is the one that applies damage.</p>
 */
public final class OneShotModule {

	private static boolean enabled = false;

	private OneShotModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** Called on join/disconnect to drop any state from a previous server. */
	public static void reset() {
		enabled = false;
	}

	public static void uiToggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.literal("One-Shot: " + (enabled ? "ON" : "OFF")), true);
		}
		sendState(enabled);
	}
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(OneShotModule::tick);
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.oneShotToggle.wasPressed()) {
			enabled = !enabled;
			if (client.player != null) {
				client.player.sendMessage(Text.literal("One-Shot: " + (enabled ? "ON" : "OFF")), true);
			}
			sendState(enabled);
		}
	}

	private static void sendState(boolean on) {
		ClientPlayNetworking.send(new MncHacksServer.OneShotStatePayload(on));
	}
}
