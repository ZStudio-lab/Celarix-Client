package com.mnchacks;

import com.mnchacks.mixin.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Fast Place — removes vanilla's 4-tick right-click throttle, so blocks
 * (and other placeables) come out as fast as the use key is clicked.
 */
public final class FastPlaceModule {

	private static boolean enabled = false;

	private FastPlaceModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Fast Place: " + (enabled ? "ON" : "OFF")), true);
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
			ClientPlayerEntity player = client.player;
			if (player == null || client.options == null || !client.options.useKey.isPressed()) {
				return;
			}
			((MinecraftClientAccessor) client).celarix$setItemUseCooldown(0);
		});
	}
}