package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Zoom — telescopic zoom: the world projection's field of view
 * is divided by the zoom level (default 5x). The held item is deliberately
 * left at its normal size, exactly like a spyglass round.
 *
 * <p>The FOV itself is multiplied in {@code GameRendererMixin} — this class
 * only holds the state, the keybind and the GUI slider.
 */
public final class ZoomModule {

	private static boolean enabled = false;
	/** How many times the view is magnified (2x - 10x). */
	private static double level = 5.0D;

	private ZoomModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (Keybinds.zoomToggle.wasPressed()) {
				toggle();
			}
		});
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Zoom: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static double getLevel() {
		return level;
	}

	public static void setLevel(double value) {
		level = MathHelper.clamp(value, 2.0D, 10.0D);
	}

	/** FOV multiplier applied by the mixin (1 / magnification). */
	public static double getFovMultiplier() {
		return 1.0D / level;
	}
}
