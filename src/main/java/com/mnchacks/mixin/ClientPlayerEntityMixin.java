package com.mnchacks.mixin;

import com.mnchacks.BlinkModule;
import com.mnchacks.FreeCamModule;
import com.mnchacks.NoFallModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Fall: right before movement packets go out, a falling player is flagged
 * as on-ground, so the server never registers fall distance.
 *
 * <p>Free Cam: the whole movement tick of the body is skipped while the
 * camera is detached, which freezes it in place — no walking, no gravity,
 * and above all no sneaking (Shift only moves the camera down).</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

	@Inject(method = "sendMovementPackets", at = @At("HEAD"))
	private void celarix$noFall(CallbackInfo info) {
		if (!NoFallModule.isEnabled()) {
			return;
		}
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		if (!self.isOnGround() && self.fallDistance > 0.0D) {
			self.setOnGround(true);
		}
	}

	@Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
	private void celarix$blink(CallbackInfo info) {
		// Blink: hold movement packets back so the server freezes you, then
		// release them all at once when the key lets go.
		if (BlinkModule.isBlinking()) {
			info.cancel();
		}
	}

	@Inject(method = "tickMovement()V", at = @At("HEAD"), cancellable = true)
	private void celarix$freeCamFreeze(CallbackInfo info) {
		if (!FreeCamModule.isEnabled()) {
			return;
		}
		// Skipping the whole movement tick stops input polling, input-driven
		// velocity, gravity and the resulting move() of the body — it stays
		// exactly where Free Cam started. The neutral input also keeps the
		// server-side input state clean (no phantom held keys or sneaks).
		ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
		self.input.playerInput = PlayerInput.DEFAULT;
		info.cancel();
	}

	@Inject(method = "isSneaking()Z", at = @At("HEAD"), cancellable = true)
	private void celarix$freeCamNoSneak(CallbackInfoReturnable<Boolean> info) {
		if (FreeCamModule.isEnabled()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "isInSneakingPose()Z", at = @At("HEAD"), cancellable = true)
	private void celarix$freeCamNoSneakPose(CallbackInfoReturnable<Boolean> info) {
		if (FreeCamModule.isEnabled()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
			at = @At("HEAD"), cancellable = true)
	private void celarix$freeCamFreezeMove(MovementType type, Vec3d movement, CallbackInfo info) {
		// Position-change chokepoint: even if any code path managed to feed
		// the frozen body a velocity, move() is where it would take effect.
		// With this cancelled the body physically cannot leave the spot
		// where Free Cam started (server teleports still get through - they
		// use setPosition, not move).
		if (FreeCamModule.isEnabled()) {
			info.cancel();
		}
	}
}