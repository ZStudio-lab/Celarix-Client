package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.text.Text;

/**
 * Auto Fish — casts the rod, watches the bobber for the bite dip, reels in
 * and recasts, forever. Detects the bite as a sharp downward bobber velocity
 * while the bobber sits in water (the splash moment).
 */
public final class AutoFishModule {

	/** Ticks the use key is held to reel in / cast. */
	private static final int USE_HOLD_TICKS = 3;
	/** Ticks to wait between reeling in and recasting. */
	private static final int RECAST_DELAY = 15;

	private static boolean enabled = false;
	private static int reelTimer = 0;
	private static int recastTimer = 0;
	private static double lastBobberVy = 0.0D;

	private AutoFishModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Fish: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoFishModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null) {
			return;
		}

		// Reeling: release the use key once the hold finishes.
		if (reelTimer > 0) {
			if (--reelTimer == 0) {
				client.options.useKey.setPressed(false);
				recastTimer = RECAST_DELAY;
			}
			return;
		}
		// Recast delay: press the use key again when it expires.
		if (recastTimer > 0) {
			if (--recastTimer == 0) {
				client.options.useKey.setPressed(true);
				reelTimer = USE_HOLD_TICKS;
			}
			return;
		}

		FishingBobberEntity bobber = findBobber(client, player);
		if (bobber == null) {
			// No bobber in the water: tap the use key to cast.
			if (client.options.useKey.isPressed()) {
				client.options.useKey.setPressed(false);
			} else {
				client.options.useKey.setPressed(true);
			}
			return;
		}

		// Bite = the bobber dips sharply while sitting in the water.
		double vy = bobber.getVelocity().y;
		if (bobber.isTouchingWater() && lastBobberVy > -0.02D && vy < -0.06D) {
			client.options.useKey.setPressed(true);
			reelTimer = USE_HOLD_TICKS;
		}
		lastBobberVy = vy;
	}

	private static FishingBobberEntity findBobber(MinecraftClient client, ClientPlayerEntity player) {
		for (FishingBobberEntity bobber : client.world.getEntitiesByClass(FishingBobberEntity.class,
				player.getBoundingBox().expand(30.0D),
				b -> b.isAlive() && player.equals(b.getPlayerOwner()))) {
			return bobber;
		}
		return null;
	}
}