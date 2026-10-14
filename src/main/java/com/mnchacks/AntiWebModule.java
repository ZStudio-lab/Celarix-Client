package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Anti Web — removes the slowdown inside cobwebs: while inside one, movement
 * is restored to normal speed so you walk through webs like they aren't there.
 */
public final class AntiWebModule {

	private static boolean enabled = false;

	private AntiWebModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Anti Web: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AntiWebModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null) {
			return;
		}
		BlockState state = client.world.getBlockState(player.getBlockPos());
		if (state.getBlock() == Blocks.COBWEB && client.options.forwardKey.isPressed()) {
			var look = player.getRotationVec(1.0F);
			player.setVelocity(look.x * 0.5D, -0.05D, look.z * 0.5D);
		}
	}
}