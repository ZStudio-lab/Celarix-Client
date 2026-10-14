package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.text.Text;

/**
 * Fullbright — maxes the brightness option so caves and the night are fully
 * lit. Restores the player's original gamma when toggled off (and on world
 * change / shutdown).
 */
public final class FullbrightModule {

	private static final double FULLBRIGHT_GAMMA = 16.0D;

	private static boolean enabled = false;
	private static Double previousGamma = null;

	private FullbrightModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** No tick loop needed — mixin + option change only. */
	public static void register() {
	}

	public static void toggle() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.options == null) {
			return;
		}
		GameOptions options = client.options;
		enabled = !enabled;
		if (enabled) {
			previousGamma = options.getGamma().getValue();
			options.getGamma().setValue(FULLBRIGHT_GAMMA);
		} else {
			options.getGamma().setValue(previousGamma != null ? previousGamma : 1.0D);
			previousGamma = null;
		}
		if (client.player != null) {
			client.player.sendMessage(Text.literal("Fullbright: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		if (enabled) {
			toggle();
		}
	}
}