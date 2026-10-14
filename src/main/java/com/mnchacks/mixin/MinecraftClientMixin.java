package com.mnchacks.mixin;

import com.mnchacks.MultiTaskModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Multi Task: vanilla refuses left-hand actions while an item is being used
 * (eating, drinking, drawing a bow) in two places - the continuous
 * block-breaking handler and the input queue that would otherwise swallow
 * every attack click. Both isUsingItem() gates read as "not using" while the
 * module is enabled, so you can snack and defend yourself at the same time.
 * The doItemUse gate is left untouched so eating still works normally.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

	@Redirect(method = "handleBlockBreaking",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
	private boolean celarix$multiTaskBreaking(ClientPlayerEntity player) {
		return !MultiTaskModule.isEnabled() && player.isUsingItem();
	}

	@Redirect(method = "handleInputEvents",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z",
					ordinal = 0))
	private boolean celarix$multiTaskInput(ClientPlayerEntity player) {
		return !MultiTaskModule.isEnabled() && player.isUsingItem();
	}
}
