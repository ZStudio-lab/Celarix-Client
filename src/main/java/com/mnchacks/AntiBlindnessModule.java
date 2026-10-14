package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;

/**
 * Anti Blindness — cancels the client-side darkness pulse and blindness fog
 * by constantly re-applying night vision while the corresponding effects are
 * active (night vision overrides the fog rendering client-side).
 */
public final class AntiBlindnessModule {

	private static boolean enabled = false;

	private AntiBlindnessModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Anti Blindness: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AntiBlindnessModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		StatusEffectInstance blindness = player.getStatusEffect(StatusEffects.BLINDNESS);
		StatusEffectInstance darkness = player.getStatusEffect(StatusEffects.DARKNESS);
		if (blindness == null && darkness == null) {
			return;
		}
		// Night vision counteracts the fog client-side. 100 ticks with
		// ambient=false keeps the icon hidden-ish and never flashes.
		player.setStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 120, 0, true, false), null);
	}
}