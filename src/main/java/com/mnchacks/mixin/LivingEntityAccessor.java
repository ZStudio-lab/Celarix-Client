package com.mnchacks.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the between-jumps cooldown for No Jump Delay. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

	@Accessor("jumpingCooldown")
	void celarix$setJumpingCooldown(int cooldown);
}