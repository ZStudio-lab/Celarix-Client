package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * AutoSign — when you place a sign, the edit screen is closed automatically
 * and the sign is written with a preset text (set in code, defaults to a
 * blank-ish marker so signs no longer stop the flow of building).
 */
public final class AutoSignModule {

	private static final String SIGN_TEXT = "Celarix";

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoSignModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Sign: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled) {
				return;
			}
			if (cooldown > 0) {
				cooldown--;
				return;
			}
			ClientPlayerEntity player = client.player;
			if (player == null || client.currentScreen == null) {
				return;
			}
			// Any sign edit screen: close it — vanilla writes the (empty or
			// prefilled) text when the GUI closes.
			String screen = client.currentScreen.getClass().getSimpleName();
			if (screen.contains("SignEdit") || screen.contains("AbstractSignEdit")) {
				player.closeHandledScreen();
				cooldown = 20;
			}
		});
	}
}