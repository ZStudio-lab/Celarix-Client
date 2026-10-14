package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.text.Text;

/**
 * Auto Respawn — the instant you die, the respawn packet is sent and the
 * death screen is skipped, so the bot (or you) is back in the world within
 * a tick of dying.
 */
public final class AutoRespawnModule {

	private static boolean enabled = false;

	private AutoRespawnModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Respawn: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled) {
				return;
			}
			if (client.player != null && client.player.isDead()
					&& client.currentScreen instanceof DeathScreen
					&& client.getNetworkHandler() != null) {
				client.getNetworkHandler().sendPacket(
						new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
				client.setScreen(null);
			}
		});
	}
}