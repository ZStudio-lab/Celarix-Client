package com.mnchacks.mixin;

import com.mnchacks.CrosshairModule;
import com.mnchacks.NoOverlayModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla crosshair while the custom Crosshair hack is enabled.
 *
 * <p>No Overlay: cancels the misc screen overlays (fire, water, powder snow,
 * pumpkin blur, vignette) while No Overlay is enabled.</p>
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void celarix$customCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
		if (CrosshairModule.isEnabled()) {
			info.cancel();
		}
	}

	@Inject(method = "renderMiscOverlays", at = @At("HEAD"), cancellable = true)
	private void celarix$noOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
		if (NoOverlayModule.isEnabled()) {
			info.cancel();
		}
	}
}
