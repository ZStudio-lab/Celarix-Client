package com.mnchacks.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the use-cooldown counter so Fast Place can zero it: vanilla
 * throttles right-clicks to one every 4 ticks, this lets blocks place as
 * fast as the key is clicked. Also exposes the private left-click action
 * for the Auto Clicker.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

	@Accessor("itemUseCooldown")
	void celarix$setItemUseCooldown(int cooldown);

	@Invoker("doAttack")
	boolean celarix$doAttack();
}