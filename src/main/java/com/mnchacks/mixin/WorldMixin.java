package com.mnchacks.mixin;

import com.mnchacks.NoWeatherModule;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Weather: cancels the client-side rain/thunder gradient, so it never renders. */
@Mixin(World.class)
public abstract class WorldMixin {

	@Inject(method = "setRainGradient", at = @At("HEAD"), cancellable = true)
	private void celarix$noWeather(float gradient, CallbackInfo info) {
		if (NoWeatherModule.isEnabled()) {
			info.cancel();
		}
	}
}