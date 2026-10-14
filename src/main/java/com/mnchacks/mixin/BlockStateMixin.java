package com.mnchacks.mixin;

import com.mnchacks.XRayModule;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X-Ray (opacity mode): while enabled, every block that is not on the
 * whitelist reports itself as non-opaque, so the chunk mesher stops culling
 * faces behind normal terrain and the world becomes see-through around the
 * highlighted ores.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateMixin {

	@Inject(method = "isOpaqueFullCube()Z", at = @At("HEAD"), cancellable = true)
	private void celarix$xray(CallbackInfoReturnable<Boolean> info) {
		if (XRayModule.isEnabled()) {
			Block block = ((AbstractBlock.AbstractBlockState) (Object) this).getBlock();
			if (!XRayModule.isVisible(block)) {
				info.setReturnValue(false);
			}
		}
	}
}
