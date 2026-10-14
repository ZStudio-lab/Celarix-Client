package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * No Slowdown for walls — while pressed against a wall and moving forward,
 * keeps sprint speed so wall-hugging (slabs, fences, openings) never stalls
 * momentum.
 */
public final class NoWallSlowModule {

	private static boolean enabled = false;

	private NoWallSlowModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("No Wall Slow: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NoWallSlowModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		if (player.horizontalCollision && client.options.forwardKey.isPressed()) {
			// Re-apply forward impulse along the view direction so movement
			// continues sliding along the wall instead of stalling.
			var look = player.getRotationVec(1.0F);
			var slide = new net.minecraft.util.math.Vec3d(look.x, 0.0D, look.z);
			if (slide.lengthSquared() > 1.0E-6D) {
				slide = slide.normalize().multiply(0.12D);
				player.setVelocity(slide.x, player.getVelocity().y, slide.z);
				player.setSprinting(player.getHungerManager().getFoodLevel() > 6);
			}
		}
	}
}