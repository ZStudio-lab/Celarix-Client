package com.mnchacks.mixin;

import com.mnchacks.StepModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Step height for the Step hack. 1.21+ removed the stepHeight field/setter,
 * so this injects the getter instead and returns a full-block height for the
 * local player while the hack is enabled.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStepMixin {

	@Inject(method = "getStepHeight", at = @At("HEAD"), cancellable = true)
	private void celarix$stepHeight(CallbackInfoReturnable<Float> cir) {
		if (StepModule.isEnabled()
				&& (Object) this == MinecraftClient.getInstance().player) {
			cir.setReturnValue(1.0F);
		}
	}
}