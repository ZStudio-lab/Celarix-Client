package com.mnchacks.mixin;

import com.mnchacks.FastBreakModule;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fast Break: accelerates block breaking while the hack is enabled. Reach is
 * handled in {@code PlayerEntityMixin} (1.21.5 computes interaction range from
 * {@link net.minecraft.entity.player.PlayerEntity#getBlockInteractionRange()} /
 * {@code getEntityInteractionRange()}, no longer a field on the interaction
 * manager).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

	@Shadow
	private float currentBreakingProgress;

	@Inject(method = "updateBlockBreakingProgress", at = @At("RETURN"), cancellable = true)
	private void celarix$fastBreak(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (FastBreakModule.isEnabled() && !cir.getReturnValueZ()) {
			this.currentBreakingProgress += 0.35F;
		}
	}
}