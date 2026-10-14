package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.text.Text;

public final class AutoReconnectModule {

	private static boolean enabled = false;
	private static int timer = 0;

	private AutoReconnectModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoReconnectModule::tick);
	}

	public static void reset() {
		enabled = false;
		timer = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Reconnect: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (!(client.currentScreen instanceof DisconnectedScreen)) {
			timer = 0;
			return;
		}
		if (timer == 0) {
			timer = 100;
			return;
		}
		if (--timer <= 0) {
			var info = client.getCurrentServerEntry();
			if (info != null && client.currentScreen != null) {
				ConnectScreen.connect(client.currentScreen, client,
						ServerAddress.parse(info.address), info, false,
						new CookieStorage(java.util.Map.of()));
			}
		}
	}
}