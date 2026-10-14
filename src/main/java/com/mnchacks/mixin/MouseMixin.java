package com.mnchacks.mixin;

import com.mnchacks.FreeCamModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Free Cam: while the camera is detached, mouse look turns the invisible
 * camera body instead of the frozen player, so the view follows the mouse
 * even though the body never moves.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	@Redirect(method = "updateMouse",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
	private void celarix$freeCamLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
		if (FreeCamModule.isEnabled()) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null && client.getCameraEntity() != null && client.getCameraEntity() != player) {
				client.getCameraEntity().changeLookDirection(cursorDeltaX, cursorDeltaY);
				return;
			}
		}
		player.changeLookDirection(cursorDeltaX, cursorDeltaY);
	}
}
