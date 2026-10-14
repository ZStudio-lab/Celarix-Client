package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Survival flight. While enabled, the client forces creative-style flight
 * abilities every tick:
 *
 * <ul>
 *   <li>WASD to move, Space = up, Shift = down</li>
 *   <li>sprint for a 2x speed boost</li>
 *   <li>releasing every key stops you instantly (no vanilla drift)</li>
 *   <li>the Click GUI "Speed" slider scales the whole flight (0.05 - 0.5)</li>
 * </ul>
 *
 * <p>In single player / LAN it also tells the integrated server to allow
 * flight for this player (via a custom payload), so vanilla's flight
 * protection never interferes.</p>
 *
 * <p>On a remote server that does not run the mod, the flight is client-side
 * only and the server's movement checks will reject it (you get kicked). That
 * is a server-side rule and cannot be worked around from the client.</p>
 */
public final class FlyModule {

	private static final double DEFAULT_SPEED = 0.1D;
	private static final double MIN_SPEED = 0.05D;
	private static final double MAX_SPEED = 0.5D;
	/** Extra velocity multiplier while the sprint key is held. */
	private static final float SPRINT_BOOST = 2.0F;

	private static boolean enabled = false;
	private static int tickCounter = 0;
	private static double speed = DEFAULT_SPEED;

	private FlyModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static double getSpeed() {
		return speed;
	}

	public static void setSpeed(double value) {
		speed = MathHelper.clamp(value, MIN_SPEED, MAX_SPEED);
	}

	public static void uiToggle() {
		toggle(MinecraftClient.getInstance());
	}
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FlyModule::tick);
	}

	/** Called on join/disconnect to drop any state from a previous server. */
	public static void reset() {
		enabled = false;
		tickCounter = 0;
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.flyToggle.wasPressed()) {
			toggle(client);
		}

		if (!enabled) {
			return;
		}

		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || player.isSpectator()) {
			return;
		}

		tickCounter++;

		// Re-assert flight immediately if something cleared it (e.g. landing,
		// respawn, world switch, speed slider change) and keep the server
		// informed every second.
		PlayerAbilities abilities = player.getAbilities();
		boolean missing = !abilities.allowFlying || !abilities.flying
				|| Math.abs(abilities.getFlySpeed() - (float) speed) > 1.0E-6F;

		if (missing || tickCounter % 20 == 0) {
			abilities.allowFlying = true;
			abilities.flying = true;
			abilities.setFlySpeed((float) speed);
			player.sendAbilitiesUpdate();
		}

		if (tickCounter % 20 == 0) {
			sendFlightState(true);
		}

		if (abilities.flying) {
			var input = player.input.playerInput;
			boolean hasInput = input.forward() || input.backward() || input.left()
					|| input.right() || input.jump() || input.sneak();
			if (!hasInput) {
				// Crisp stop: creative flight normally coasts for a while.
				player.setVelocity(Vec3d.ZERO);
			} else if (player.isSprinting()) {
				player.setVelocity(player.getVelocity().multiply(SPRINT_BOOST));
			}
		}
	}

	private static void toggle(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}

		enabled = !enabled;
		tickCounter = 0;

		PlayerAbilities abilities = player.getAbilities();
		if (enabled) {
			player.fallDistance = 0.0F;
			abilities.allowFlying = true;
			abilities.flying = true;
			abilities.setFlySpeed((float) speed);
		} else {
			abilities.flying = false;
			abilities.allowFlying = player.isCreative() || player.isSpectator();
			abilities.setFlySpeed(0.05F);
			// Drop leftover flight velocity so disabling mid-air doesn't
			// launch you sideways.
			player.setVelocity(Vec3d.ZERO);
		}

		player.sendAbilitiesUpdate();
		sendFlightState(enabled);
		player.sendMessage(Text.literal("Survival Fly: " + (enabled ? "ON" : "OFF")), true);
	}

	/** Tells the (integrated) server whether flight should be allowed for us. */
	private static void sendFlightState(boolean on) {
		ClientPlayNetworking.send(new MncHacksServer.FlyStatePayload(on));
	}
}
