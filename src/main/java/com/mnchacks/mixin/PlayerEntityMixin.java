package com.mnchacks.mixin;

import com.mnchacks.NoSlowModule;
import com.mnchacks.ReachModule;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No Slow: restores full movement speed while eating, drawing a bow, etc. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

	@Inject(method = "getOffGroundSpeed", at = @At("RETURN"), cancellable = true)
	private void celarix$noSlow(CallbackInfoReturnable<Float> cir) {
		if (NoSlowModule.isEnabled() && ((PlayerEntity) (Object) this).isUsingItem()) {
			cir.setReturnValue(cir.getReturnValueF() * 5.0F);
		}
	}

	@Inject(method = "getBlockInteractionRange", at = @At("RETURN"), cancellable = true)
	private void celarix$extendBlockReach(CallbackInfoReturnable<Double> cir) {
		if (ReachModule.isEnabled()) {
			cir.setReturnValue(cir.getReturnValueD() + ReachModule.EXTRA_REACH);
		}
	}

	@Inject(method = "getEntityInteractionRange", at = @At("RETURN"), cancellable = true)
	private void celarix$extendEntityReach(CallbackInfoReturnable<Double> cir) {
		if (ReachModule.isEnabled()) {
			cir.setReturnValue(cir.getReturnValueD() + ReachModule.EXTRA_REACH);
		}
	}
}