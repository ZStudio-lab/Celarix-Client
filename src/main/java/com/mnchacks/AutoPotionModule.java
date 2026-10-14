package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Auto Potion — hands-off healing: as soon as your health drops to the
 * threshold (Click GUI slider), it switches to a splash potion from the
 * hotbar, flicks your view to the ground so the splash lands at your feet,
 * drinks... rather throws it, and hands your view back. Cooldowns keep it
 * from chain-throwing the whole stack at once.
 */
public final class AutoPotionModule {

	private static final int THROW_TIMEOUT = 40;
	private static final int REUSE_COOLDOWN = 20;

	private static boolean enabled = false;
	private static double threshold = 8.0D;
	private static int cooldown = 0;

	/** Throw sequence state: -1 idle, otherwise the hotbar slot being thrown. */
	private static int throwingSlot = -1;
	private static float savedPitch;
	private static int throwTicks = 0;

	private AutoPotionModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoPotionModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
		cancelThrow(null);
	}

	public static double getThreshold() {
		return threshold;
	}

	public static void setThreshold(double value) {
		threshold = MathHelper.clamp(value, 4.0D, 16.0D);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Potion: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (cooldown > 0) {
			cooldown--;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null
				|| client.currentScreen != null) {
			cancelThrow(client);
			return;
		}

		if (throwingSlot >= 0) {
			throwStep(client, player);
			return;
		}

		if (!enabled || cooldown > 0) {
			return;
		}
		if (!player.isAlive() || player.isCreative()
				|| player.getHealth() > threshold) {
			return;
		}

		int slot = findSplashPotion(player);
		if (slot >= 0) {
			throwingSlot = slot;
			savedPitch = player.getPitch();
			throwTicks = 0;
		}
	}

	/** Flicks the view down, holds use until the potion actually leaves the hand. */
	private static void throwStep(MinecraftClient client, ClientPlayerEntity player) {
		throwTicks++;
		ItemStack stack = player.getInventory().getStack(throwingSlot);

		boolean thrown = !(stack.getItem() instanceof SplashPotionItem);
		if (thrown || throwTicks > THROW_TIMEOUT) {
			cancelThrow(client);
			cooldown = REUSE_COOLDOWN;
			return;
		}

		player.getInventory().setSelectedSlot(throwingSlot);
		// Aim at the feet: a splash thrown straight down always catches the
		// thrower in its radius.
		player.setPitch(MathHelper.clamp(player.getPitch() + 12.0F, 0.0F, 88.0F));
		client.options.useKey.setPressed(true);
	}

	private static void cancelThrow(MinecraftClient client) {
		if (throwingSlot < 0) {
			return;
		}
		throwingSlot = -1;
		if (client != null && client.options != null) {
			client.options.useKey.setPressed(false);
		}
		ClientPlayerEntity player = client != null ? client.player : null;
		if (player != null) {
			player.setPitch(savedPitch);
		}
	}

	private static int findSplashPotion(ClientPlayerEntity player) {
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = player.getInventory().getStack(slot);
			if (stack.getItem() instanceof SplashPotionItem) {
				return slot;
			}
		}
		return -1;
	}
}
