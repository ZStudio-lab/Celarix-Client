package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Speed module.
 *
 * <p>Movement is server-authoritative, so this cannot simply push the player
 * faster from the client (that just rubber-bands). Instead the client toggles
 * the module and notifies the server, which grants a hidden Speed effect. The
 * client therefore never desyncs and vanilla never pulls you back.</p>
 *
 * <p>Works on the integrated (single player / LAN host) server and on any
 * dedicated server that also has this jar. On a vanilla server with no mod,
 * nothing happens - the server is the one that owns movement.</p>
 */
public final class SpeedModule {

	private static boolean enabled = false;

	private SpeedModule() {
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
			client.player.sendMessage(Text.literal("Speed: " + (enabled ? "ON" : "OFF")), true);
		}
		sendState(enabled);
	}
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpeedModule::tick);
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.speedToggle.wasPressed()) {
			enabled = !enabled;
			if (client.player != null) {
				client.player.sendMessage(Text.literal("Speed: " + (enabled ? "ON" : "OFF")), true);
			}
			sendState(enabled);
		}
	}

	private static void sendState(boolean on) {
		ClientPlayNetworking.send(new MncHacksServer.SpeedStatePayload(on));
	}
}
