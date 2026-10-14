package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Auto Sword — instantly swaps to a sword whenever the crosshair is on a
 * mob/player (crits scale with the weapon), and back to the previous item
 * when the crosshair moves off. Complements Auto Tool (which handles blocks).
 */
public final class AutoSwordModule {

	private static boolean enabled = false;
	private static int previousSlot = -1;

	private AutoSwordModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Sword: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AutoSwordModule::tick);
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.currentScreen != null
				|| client.crosshairTarget == null) {
			return;
		}

		boolean onTarget = client.crosshairTarget instanceof EntityHitResult hit
				&& hit.getEntity() != null
				&& !(hit.getEntity() instanceof ArmorStandEntity)
				&& hit.getEntity().isAlive();

		if (onTarget) {
			int sword = findSword(player);
			if (sword >= 0) {
				if (previousSlot < 0) {
					previousSlot = player.getInventory().getSelectedSlot();
				}
				player.getInventory().setSelectedSlot(sword);
			}
		} else if (previousSlot >= 0) {
			player.getInventory().setSelectedSlot(previousSlot);
			previousSlot = -1;
		}
	}

	private static int findSword(ClientPlayerEntity player) {
		int best = -1;
		float bestDamage = 4.0F; // only bother swapping for real swords
		var inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isIn(ItemTags.SWORDS)) {
				continue;
			}
			WeaponComponent weapon = stack.get(DataComponentTypes.WEAPON);
			float damage = weapon != null ? (float) weapon.itemDamagePerAttack() : 1.0F;
			if (damage > bestDamage) {
				bestDamage = damage;
				best = slot;
			}
		}
		return best;
	}
}