package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Invis — makes the player invisible.
 *
 * <p><b>Singleplayer / LAN (real):</b> the integrated server runs in this JVM,
 * so the flag is set on the actual server-side player entity. Other LAN
 * players genuinely stop seeing you — no model, no nametag — and mobs lose
 * their target, exactly like an invisibility potion.</p>
 *
 * <p><b>Remote servers (client-side only):</b> entity visibility is decided by
 * the server and broadcast to other players. A client cannot change that —
 * your own model disappears from third-person view, but other players still
 * see you. There is no packet that makes a server stop tracking you.</p>
 */
public final class InvisModule {

	private static boolean enabled = false;

	private InvisModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null) {
				apply(client);
			}
		});
	}

	private static void apply(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}

		// Local model: hidden in third-person / F5 (re-assert every tick —
		// the server's data-tracker sync keeps clearing the flag).
		player.setInvisible(enabled);

		// Real invisibility on the integrated server (singleplayer / LAN):
		// set the flag on the actual server-side player entity, which is what
		// other LAN players and mob targeting are based on.
		MinecraftServer server = client.getServer();
		if (server != null) {
			server.execute(() -> {
				ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(player.getUuid());
				if (serverPlayer != null) {
					serverPlayer.setInvisible(enabled);
				}
			});
		}
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) {
			apply(client);
			if (client.player != null) {
				client.player.sendMessage(Text.literal("Invis: " + (enabled ? "ON" : "OFF")), true);
			}
		}
	}

	public static void reset() {
		enabled = false;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) {
			apply(client);
		}
	}
}