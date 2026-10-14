package com.mnchacks;

import com.mnchacks.mixin.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

public final class TriggerBotModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private TriggerBotModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TriggerBotModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Trigger Bot: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null || client.crosshairTarget == null) {
			return;
		}
		if (client.crosshairTarget instanceof EntityHitResult hit
				&& hit.getEntity() instanceof LivingEntity living
				&& living.isAlive() && Teams.isEnemy(living, player)
				&& player.getAttackCooldownProgress(0.0F) >= 1.0F) {
			((MinecraftClientAccessor) client).celarix$doAttack();
			cooldown = 2;
		}
	}
}