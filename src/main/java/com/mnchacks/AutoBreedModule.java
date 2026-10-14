package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Auto Breed — feeds nearby adult animals their breeding item automatically
 * (wheat for cows/sheep, carrots for pigs, seeds for chickens) so farms run
 * themselves. Hearts appear; babies grow on their own.
 */
public final class AutoBreedModule {

	private static boolean enabled = false;
	private static int cooldown = 0;

	private AutoBreedModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Breed: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoBreedModule::tick);
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
		if (player == null || client.world == null || client.currentScreen != null
				|| client.interactionManager == null) {
			return;
		}

		// Only act while holding a breeding item for the animal in front.
		ItemStack held = player.getMainHandStack();
		if (held.isEmpty()) {
			return;
		}
		// Find an adult feedable animal in reach.
		for (AnimalEntity animal : client.world.getEntitiesByClass(AnimalEntity.class,
				player.getBoundingBox().expand(3.5D), animal -> animal.isAlive() && !animal.isBaby())) {
			if (!animal.isBreedingItem(held)) {
				continue;
			}
			if (animal.getLoveTicks() > 0) {
				continue;
			}
			// Face the animal and feed it.
			Vec3dHelper.face(player, animal.getPos().add(0.0D, animal.getHeight() * 0.6D, 0.0D));
			client.interactionManager.interactEntity(player, animal, net.minecraft.util.Hand.MAIN_HAND);
			player.swingHand(Hand.MAIN_HAND);
			cooldown = 10;
			return;
		}
	}

	private static final class Vec3dHelper {
		private static void face(ClientPlayerEntity player, net.minecraft.util.math.Vec3d target) {
			net.minecraft.util.math.Vec3d eyes = player.getEyePos();
			double dx = target.x - eyes.x;
			double dy = target.y - eyes.y;
			double dz = target.z - eyes.z;
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			if (horizontal < 1.0E-4D) {
				return;
			}
			player.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
			player.setPitch((float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI))));
		}
	}
}