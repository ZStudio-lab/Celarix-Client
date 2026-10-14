package com.mnchacks.mixin;

import com.mnchacks.NoHurtCamModule;
import com.mnchacks.ZoomModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Hurt Cam: cancels the red tilt/shake applied when damaged.
 *
 * <p>Zoom: divides the world projection's field of view while the zoom
 * module is on. The {@code changingFov} argument is only true for the world
 * call — the held-item projection (false) is left untouched, so the hand
 * keeps its normal size exactly like with a spyglass.</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void celarix$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo info) {
		if (NoHurtCamModule.isEnabled()) {
			info.cancel();
		}
	}

	@Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)F", at = @At("RETURN"))
	private void celarix$zoom(Camera camera, float tickDelta, boolean changingFov,
			CallbackInfoReturnable<Float> info) {
		if (changingFov && ZoomModule.isEnabled()) {
			info.setReturnValue(info.getReturnValueF() * (float) ZoomModule.getFovMultiplier());
		}
	}
}
