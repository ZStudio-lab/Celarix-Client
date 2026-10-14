package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;

/**
 * Free Cam — detaches the camera from the body and flies it around freely
 * (WASD to move, Space up, Shift down, sprint to speed up), while the player
 * body stays exactly where it was.
 *
 * <p>The camera is a vanilla {@link MarkerEntity}: invisible, no hitbox, no
 * AI and an empty tick. It is never added to the world's entity list, so the
 * ESP, tracers and every other renderer never see it. Mouse look is
 * redirected to the marker by {@code MouseMixin}, and
 * {@code ClientPlayerEntityMixin} freezes the body: movement input, gravity
 * and sneak are all neutralised, so <b>Shift never makes the body crouch
 * while Free Cam is on</b> — it only moves the camera down.
 */
public final class FreeCamModule {

	/** Camera speed in blocks per tick; sprint multiplies it. */
	private static final double SPEED = 0.5D;
	private static final double SPRINT_SPEED = 2.5D;

	private static boolean enabled = false;
	private static Entity cameraEntity;

	private FreeCamModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FreeCamModule::tick);
	}

	/** Camera off and released; called on join/disconnect and shutdown. */
	public static void reset() {
		disable();
	}

	public static void toggle() {
		if (enabled) {
			disable();
		} else {
			enable();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Free Cam: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void enable() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null) {
			return;
		}
		cameraEntity = new MarkerEntity(EntityType.MARKER, client.world);
		Vec3d pos = client.player.getEyePos();
		cameraEntity.setPosition(pos.x, pos.y, pos.z);
		cameraEntity.lastX = pos.x;
		cameraEntity.lastY = pos.y;
		cameraEntity.lastZ = pos.z;
		cameraEntity.setYaw(client.player.getYaw());
		cameraEntity.setPitch(client.player.getPitch());
		cameraEntity.lastYaw = client.player.getYaw();
		cameraEntity.lastPitch = client.player.getPitch();
		client.setCameraEntity(cameraEntity);
		// The first-person hand belongs to the body, not to the detached camera.
		client.gameRenderer.setRenderHand(false);
		client.player.setVelocity(Vec3d.ZERO);
		client.player.input.playerInput = PlayerInput.DEFAULT;
		enabled = true;
	}

	private static void disable() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) {
			client.setCameraEntity(client.player);
			client.gameRenderer.setRenderHand(true);
			if (client.player != null) {
				client.player.setVelocity(Vec3d.ZERO);
				// Release any keys that were held while frozen so the server
				// sees a clean neutral input instead of a stuck one.
				client.player.input.playerInput = PlayerInput.DEFAULT;
			}
		}
		cameraEntity = null;
		enabled = false;
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.freeCamToggle.wasPressed()) {
			toggle();
		}
		if (!enabled) {
			return;
		}
		if (client.player == null || client.world == null || cameraEntity == null
				|| client.getCameraEntity() != cameraEntity) {
			// Left the world, died or something else took the camera: bail out.
			disable();
			return;
		}
		moveCamera(client);
		// Kill any velocity the world accumulated on the frozen body
		// (server knockback, gravity leftovers) so it never drifts.
		client.player.setVelocity(Vec3d.ZERO);
	}

	private static void moveCamera(MinecraftClient client) {
		GameOptions options = client.options;
		if (options == null) {
			return;
		}
		// Read the physical key states: this works with the Click GUI or a
		// chat screen open too, not just in the vanilla game state.
		boolean forward = isPressed(client, options.forwardKey);
		boolean backward = isPressed(client, options.backKey);
		boolean left = isPressed(client, options.leftKey);
		boolean right = isPressed(client, options.rightKey);
		boolean up = isPressed(client, options.jumpKey);
		boolean down = isPressed(client, options.sneakKey);
		boolean sprint = isPressed(client, options.sprintKey);

		double forwardInput = (forward ? 1.0D : 0.0D) - (backward ? 1.0D : 0.0D);
		double strafeInput = (right ? 1.0D : 0.0D) - (left ? 1.0D : 0.0D);
		double verticalInput = (up ? 1.0D : 0.0D) - (down ? 1.0D : 0.0D);

		Vec3d delta = new Vec3d(forwardInput, verticalInput, strafeInput);
		if (delta.lengthSquared() < 1.0E-4D) {
			return;
		}
		double speed = sprint ? SPRINT_SPEED : SPEED;
		Vec3d scaled = delta.normalize().multiply(speed);

		double yawRad = Math.toRadians(cameraEntity.getYaw());
		double dx = -Math.sin(yawRad) * scaled.x - Math.cos(yawRad) * scaled.z;
		double dz = Math.cos(yawRad) * scaled.x - Math.sin(yawRad) * scaled.z;

		cameraEntity.lastX = cameraEntity.getX();
		cameraEntity.lastY = cameraEntity.getY();
		cameraEntity.lastZ = cameraEntity.getZ();
		cameraEntity.setPosition(cameraEntity.getX() + dx, cameraEntity.getY() + scaled.y,
				cameraEntity.getZ() + dz);
	}

	/** Whether the key bound to {@code binding} is physically held right now. */
	private static boolean isPressed(MinecraftClient client, KeyBinding binding) {
		if (client.getWindow() == null) {
			return false;
		}
		long handle = client.getWindow().getHandle();
		InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
		return switch (key.getCategory()) {
			case KEYSYM -> InputUtil.isKeyPressed(handle, key.getCode());
			case SCANCODE -> InputUtil.isKeyPressed(handle, key.getCode());
			case MOUSE -> org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, key.getCode())
					== org.lwjgl.glfw.GLFW.GLFW_PRESS;
		};
	}
}
