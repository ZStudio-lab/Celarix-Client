package com.mnchacks;

import com.mnchacks.mixin.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Auto Clicker — a plain left-clicker: performs the vanilla attack action
 * every tick, exactly like clicking the mouse. Hitting entities, mining
 * blocks and swinging at air all behave as if the user was clicking.
 */
public final class AutoClickModule {

	/** Ticks between clicks. 1 = as fast as the game ticks. */
	private static final int CLICK_DELAY_TICKS = 1;

	private static boolean enabled = false;
	private static int counter = 0;
	/** True while an orchestrator (Survival) is the reason clicking is on. */
	private static boolean forced = false;
	private static boolean enabledBeforeForce = false;

	private AutoClickModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void uiToggle() {
		forced = false;
		enabled = !enabled;
		counter = 0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.literal("Auto Click: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/** Automatic enable/disable used by the Survival orchestrator. */
	public static void setForced(boolean on) {
		if (on) {
			if (!forced) {
				forced = true;
				enabledBeforeForce = enabled;
				enabled = true;
			}
		} else if (forced) {
			forced = false;
			if (!enabledBeforeForce) {
				enabled = false;
				counter = 0;
			}
		}
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(AutoClickModule::tick);
	}

	public static void reset() {
		enabled = false;
		counter = 0;
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.clickToggle.wasPressed()) {
			uiToggle();
		}

		if (!enabled) {
			return;
		}

		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null) {
			return;
		}

		if (++counter < CLICK_DELAY_TICKS) {
			return;
		}
		counter = 0;

		// Pick the right tool for whatever block the crosshair is on.
		HitResult hit = client.crosshairTarget;
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			AutoToolModule.selectBest(player, client.world.getBlockState(((BlockHitResult) hit).getBlockPos()));
		}

		// The plain left click: vanilla's attack/mining action.
		((MinecraftClientAccessor) client).celarix$doAttack();
	}
}