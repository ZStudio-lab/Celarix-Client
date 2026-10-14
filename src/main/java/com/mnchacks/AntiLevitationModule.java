package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Anti Levitation — cancels the upward drift of the levitation effect so you
 * fall normally instead of floating away (the effect stays active; only the
 * client-side vertical boost is cancelled each tick).
 */
public final class AntiLevitationModule {

	private static boolean enabled = false;

	private AntiLevitationModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Anti Levitation: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled) {
				return;
			}
			ClientPlayerEntity player = client.player;
			if (player == null || client.currentScreen != null) {
				return;
			}
			if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION)
					&& player.getVelocity().y > 0.0D) {
				player.setVelocity(player.getVelocity().x, 0.0D, player.getVelocity().z);
				player.fallDistance = 0.0F;
			}
		});
	}
}