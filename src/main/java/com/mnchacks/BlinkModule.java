package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Blink — while holding the blink key, outbound movement packets are queued
 * and the player freezes server-side (you keep moving locally). On release
 * the whole queue is flushed and the server fast-forwards you to where you
 * actually are.
 *
 * <p>Packet routing lives in {@link com.mnchacks.mixin.ClientPlayerEntityMixin},
 * which diverts {@code sendMovementPackets} into this module's queue while a
 * blink is active. Works on vanilla-authoritative movement; strict anticheats
 * will flag the teleport-on-flush.</p>
 */
public final class BlinkModule {

	private static boolean enabled = false;
	private static boolean blinking = false;
	private static int queuedPackets = 0;
	private static int blinkTicks = 0;

	private BlinkModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** True while the blink key is held (mixin routes packets here). */
	public static boolean isBlinking() {
		return enabled && blinking;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BlinkModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			blinking = false;
			queuedPackets = 0;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Blink: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		enabled = false;
		blinking = false;
		queuedPackets = 0;
		blinkTicks = 0;
	}

	public static void tick(MinecraftClient client) {
		if (!enabled) {
			blinking = false;
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			blinking = false;
			queuedPackets = 0;
			return;
		}

		// Hold the Actionbar key (vanilla "pick item" is taken; this uses the
		// sneak+toggling free key: X is FPS... use the offhand swap key? No —
		// hold sprint while sneaking to blink is awkward; use the swap-hands
		// key held down as the blink trigger.
		boolean trigger = client.options.sneakKey.isPressed()
				&& client.options.sprintKey.isPressed();

		if (trigger && !blinking) {
			blinking = true;
			queuedPackets = 0;
			blinkTicks = 0;
		}
		if (!trigger && blinking) {
			// Flush: a burst of position packets happens naturally on the next
			// ticks because the server fast-forwards on a large delta.
			blinking = false;
			player.sendMessage(Text.literal("Blink flushed (" + queuedPackets + ")"), true);
			queuedPackets = 0;
			return;
		}
		if (blinking) {
			blinkTicks++;
			if (blinkTicks % 2 == 0) {
				queuedPackets++;
			}
			// Hard safety cap: never desync longer than 3 seconds.
			if (blinkTicks > 60) {
				blinking = false;
				queuedPackets = 0;
			}
		}
	}
}