package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Anti Knockback — drops the velocity-update packets that shove the player
 * around, so hits (and explosions routed through the same packet) no longer
 * knock you back.
 *
 * <p>The mixin in {@code ClientPlayNetworkHandlerMixin} cancels the packet
 * only when it targets our own player; other entities still move normally.</p>
 */
public final class KnockbackModule {

	private static boolean enabled = false;
	/** Blocked packets since the last toggle (shown nowhere critical). */
	public static volatile int blockedCount = 0;

	private KnockbackModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** No tick loop needed — the network mixin handles everything. */
	public static void register() {
	}

	public static void toggle() {
		enabled = !enabled;
		blockedCount = 0;
		MinecraftClientHolder.message("Anti Knockback: " + (enabled ? "ON" : "OFF"));
	}

	public static void reset() {
		enabled = false;
		blockedCount = 0;
	}

	/** Called from the network mixin for every dropped velocity packet. */
	public static void onBlocked() {
		blockedCount++;
	}

	/** Tiny helper so the mixin thread can show a chat note safely. */
	private static final class MinecraftClientHolder {
		private static void message(String text) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null && client.player != null) {
				client.player.sendMessage(Text.literal(text), true);
			}
		}
	}
}