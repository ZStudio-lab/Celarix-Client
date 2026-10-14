package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Step — lets the player walk up full blocks. The actual step height is
 * applied by {@code LivingEntityStepMixin} (1.21+ removed the stepHeight
 * field; the getter is hooked instead), so this module only tracks the
 * toggle and exposes {@link #isEnabled()}.
 */
public final class StepModule {

	private static boolean enabled = false;

	private StepModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(StepModule::tick);
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Step: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		// Step height is handled entirely by LivingEntityStepMixin.
	}
}