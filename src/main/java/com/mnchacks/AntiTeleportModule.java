package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * No Teleport / anti-setback.
 *
 * <p>When anticheats flag you (e.g. while flying on a public server) they send
 * a position-look packet that snaps ("setbacks") the player to a fixed place.
 * While this toggle is on, a mixin into the client network handler answers the
 * teleport-confirm protocol (so the server keeps processing movement and you
 * play in realtime) and then drops the packet, so the client never applies the
 * snap. You keep flying without restrictions instead of being frozen.</p>
 *
 * <p>It blocks <b>every</b> server-side teleport while enabled — including
 * legitimate ones (respawn, portals, ender pearls) — so only turn it on when
 * you need it and off again afterwards.</p>
 */
public final class AntiTeleportModule {

	private static boolean enabled = false;
	/** How many teleports were blocked since the last world change (shown on the HUD). */
	public static volatile int blockedCount = 0;

	private AntiTeleportModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		blockedCount = 0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("No Teleport: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Called from the network mixin for every rejected position packet. */
	public static void onBlocked() {
		blockedCount++;
	}

	public static void reset() {
		enabled = false;
		blockedCount = 0;
	}
}