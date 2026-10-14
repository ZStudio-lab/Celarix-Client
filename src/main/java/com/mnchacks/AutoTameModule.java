package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Auto Tame — hold the taming food (bones for wolves, fish for cats, seeds
 * for parrots) and this uses it on every untamed tameable animal within
 * reach until one of them accepts you. Chance-based taming is handled by the
 * built-in cooldown: it keeps offering until it takes.
 */
public final class AutoTameModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoTameModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoTameModule::tick);
	}

	public static void reset() {
		enabled = false;
		cooldown = 0;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Tame: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null
				|| client.currentScreen != null) {
			return;
		}
		if (player.getMainHandStack().isEmpty()) {
			return; // hold the taming food yourself
		}

		TameableEntity animal = null;
		double bestSq = Double.MAX_VALUE;
		Box box = player.getBoundingBox().expand(4.5D);
		for (TameableEntity candidate : client.world.getEntitiesByClass(TameableEntity.class, box,
				t -> t.isAlive() && !t.isTamed() && !t.isBaby())) {
			double distSq = candidate.squaredDistanceTo(player);
			if (distSq < bestSq) {
				bestSq = distSq;
				animal = candidate;
			}
		}
		if (animal == null) {
			return;
		}

		aimAt(player, animal.getEyePos());
		ActionResult result = client.interactionManager.interactEntity(player, animal, Hand.MAIN_HAND);
		if (result.isAccepted()) {
			player.swingHand(Hand.MAIN_HAND);
			cooldown = 20;
		}
	}

	private static void aimAt(ClientPlayerEntity player, Vec3d target) {
		Vec3d eyes = player.getEyePos();
		double dx = target.x - eyes.x;
		double dy = target.y - eyes.y;
		double dz = target.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal > 1.0E-4D) {
			player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
		}
		player.setPitch((float) MathHelper.clamp(
				-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI)), -90.0F, 90.0F));
	}
}
