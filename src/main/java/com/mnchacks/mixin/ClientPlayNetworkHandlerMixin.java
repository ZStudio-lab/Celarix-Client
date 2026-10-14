package com.mnchacks.mixin;

import com.mnchacks.AntiTeleportModule;
import com.mnchacks.HealthFreezeModule;
import com.mnchacks.KnockbackModule;
import com.mnchacks.NoRotateModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No Teleport — rejects server teleports without freezing the player.
 *
 * <p>When the server sends a position-look packet (a "setback" to a fixed
 * place), vanilla both applies it and answers with a teleport-confirm. If we
 * simply dropped the packet, the server would never receive a confirm and
 * would ignore our movement packets — locking the player in place.</p>
 *
 * <p>Instead this mixin <b>confirms the teleport id</b> (so the server keeps
 * processing our movement in realtime) and then cancels the vanilla handler,
 * so the client never actually applies the snap. Net effect: you keep flying
 * and playing where you are while the server is told what it wants to hear.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onPlayerPositionLook", at = @At("HEAD"), cancellable = true)
	private void celarix$rejectTeleport(PlayerPositionLookS2CPacket packet, CallbackInfo info) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			NoRotateModule.capture(player);
		}
		if (AntiTeleportModule.isEnabled()) {
			// Acknowledge the teleport so the server unlocks movement handling.
			// sendPacket lives on the superclass, so reach it through a cast —
			// @Shadow can only reference methods declared in the target itself.
			((ClientPlayNetworkHandler) (Object) this)
					.sendPacket(new TeleportConfirmC2SPacket(packet.teleportId()));
			AntiTeleportModule.onBlocked();
			// ...but never apply the position change itself.
			info.cancel();
		}
	}

	/**
	 * Anti Knockback: drops velocity packets aimed at our own player, so hits
	 * (and explosions routed through the same packet) no longer shove us.
	 */
	@Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
	private void celarix$cancelKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo info) {
		if (!KnockbackModule.isEnabled()) {
			return;
		}
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null && packet.getEntityId() == player.getId()) {
			KnockbackModule.onBlocked();
			info.cancel();
		}
	}

	/**
	 * Health Freeze: rewrites every incoming health sync to full, applying
	 * only the hunger part, so the client never registers damage.
	 */
	@Inject(method = "onHealthUpdate", at = @At("HEAD"), cancellable = true)
	private void celarix$freezeHealth(HealthUpdateS2CPacket packet, CallbackInfo info) {
		if (!HealthFreezeModule.isEnabled()) {
			return;
		}
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player != null) {
			player.setHealth(20.0F);
			player.getHungerManager().setFoodLevel(packet.getFood());
			player.getHungerManager().setSaturationLevel(packet.getSaturation());
		}
		info.cancel();
	}

	@Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
	private void celarix$noRotate(PlayerPositionLookS2CPacket packet, CallbackInfo info) {
		if (NoRotateModule.isEnabled()) {
			ClientPlayerEntity player = MinecraftClient.getInstance().player;
			if (player != null) {
				NoRotateModule.restore(player);
			}
		}
	}
}