package com.mnchacks.mixin;

import com.mnchacks.SnowShoeModule;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow Shoe: the vanilla walk-on-powder-snow check (normally true only with
 * leather boots) returns true while the module is enabled, so you can cross
 * powder snow fields without sinking in.
 */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {

	@Inject(method = "canWalkOnPowderSnow(Lnet/minecraft/entity/Entity;)Z",
			at = @At("HEAD"), cancellable = true)
	private static void celarix$snowShoe(Entity entity, CallbackInfoReturnable<Boolean> info) {
		if (SnowShoeModule.isEnabled()) {
			info.setReturnValue(true);
		}
	}
}
