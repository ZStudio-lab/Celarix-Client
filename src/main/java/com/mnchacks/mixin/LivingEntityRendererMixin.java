package com.mnchacks.mixin;

import com.mnchacks.DerpModule;
import com.mnchacks.EspModule;
import com.mnchacks.InvisRevealModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Invis Reveal: vanilla skips the body of living entities the local player can
 * not see (state.invisibleToPlayer) by returning null from getRenderLayer.
 * While ESP is enabled this overrides that for invisible players, rendering
 * their real skin instead — invisible players become fully visible.
 *
 * <p>Derp: overwrites the head rotation in your own player's render state
 * every frame, so your model spins like a maniac in third person. Render-only
 * — your real aim is untouched.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	@Inject(method = "getRenderLayer", at = @At("RETURN"), cancellable = true)
	private void celarix$revealInvisible(LivingEntityRenderState state, boolean showBody, boolean translucent,
			boolean showOutlines, CallbackInfoReturnable<RenderLayer> cir) {
		if (!InvisRevealModule.isEnabled() || !EspModule.isEnabled()) {
			return;
		}
		if (state.invisibleToPlayer && state instanceof PlayerEntityRenderState playerState) {
			cir.setReturnValue(RenderLayer.getEntityTranslucent(playerState.skinTextures.texture()));
		}
	}

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void celarix$derp(LivingEntity entity, LivingEntityRenderState state, float delta,
			CallbackInfo info) {
		if (!DerpModule.isEnabled()) {
			return;
		}
		if (entity != MinecraftClient.getInstance().player) {
			return;
		}
		state.relativeHeadYaw = (entity.age * 37) % 360 - 180.0F;
		state.pitch = (float) Math.sin(entity.age * 0.45D) * 55.0F;
	}
}
