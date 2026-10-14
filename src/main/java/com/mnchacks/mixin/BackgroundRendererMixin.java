package com.mnchacks.mixin;

import com.mnchacks.NoFogModule;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Fog: the fog distances returned by the vanilla fog renderer are pushed
 * far past the far plane, which makes every fog (terrain, sky, fluids)
 * invisible while keeping the fog colour untouched.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

	@Inject(method = "applyFog(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/BackgroundRenderer$FogType;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/render/Fog;",
			at = @At("RETURN"), cancellable = true)
	private static void celarix$noFog(Camera camera, BackgroundRenderer.FogType fogType,
									  Vector4f color, float viewDistance, boolean thickFog,
									  float tickDelta, CallbackInfoReturnable<Fog> info) {
		if (!NoFogModule.isEnabled()) {
			return;
		}
		Fog fog = info.getReturnValue();
		// Start/end beyond the far plane: the shader's fog factor clamps to 0
		// everywhere, so nothing hazes out. Colour and shape stay as-is.
		info.setReturnValue(new Fog(1.0E6F, 1.0E7F, fog.shape(),
				fog.red(), fog.green(), fog.blue(), fog.alpha()));
	}
}
