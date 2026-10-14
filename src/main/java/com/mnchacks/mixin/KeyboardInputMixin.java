package com.mnchacks.mixin;

import com.mnchacks.InventoryWalkModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory Walk: with any screen open the game never polls the movement
 * keybindings, so {@code KeyboardInput.tick()} would always report a neutral
 * input and walking stops. This replaces that tick with one built from the
 * raw physical key states (which GLFW still tracks while a screen is open),
 * so movement continues through inventories, chats and the Click GUI.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

	@Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
	private void celarix$inventoryWalk(CallbackInfo ci) {
		if (!InventoryWalkModule.isEnabled()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.currentScreen == null || client.options == null
				|| client.getWindow() == null) {
			return;
		}
		GameOptions options = client.options;
		boolean forward = celarix$isPressed(client, options.forwardKey);
		boolean backward = celarix$isPressed(client, options.backKey);
		boolean left = celarix$isPressed(client, options.leftKey);
		boolean right = celarix$isPressed(client, options.rightKey);
		boolean jump = celarix$isPressed(client, options.jumpKey);
		boolean sneak = celarix$isPressed(client, options.sneakKey);
		boolean sprint = celarix$isPressed(client, options.sprintKey);

		this.playerInput = new PlayerInput(forward, backward, left, right, jump, sneak, sprint);

		// Same math as the cancelled vanilla body: Vec2f(sideways, forward),
		// normalized.
		float forwardMovement = celarix$multiplier(forward, backward);
		float sidewaysMovement = celarix$multiplier(left, right);
		this.movementVector = new Vec2f(sidewaysMovement, forwardMovement).normalize();
		ci.cancel();
	}

	private static float celarix$multiplier(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0F;
		}
		return positive ? 1.0F : -1.0F;
	}

	private static boolean celarix$isPressed(MinecraftClient client, KeyBinding binding) {
		long handle = client.getWindow().getHandle();
		InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
		return switch (key.getCategory()) {
			case KEYSYM -> InputUtil.isKeyPressed(handle, key.getCode());
			case SCANCODE -> InputUtil.isKeyPressed(handle, key.getCode());
			case MOUSE -> GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
		};
	}
}
