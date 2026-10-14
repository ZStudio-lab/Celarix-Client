package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;

/**
 * Auto Tool — automatically selects the hotbar tool that mines the current
 * block fastest (and that can actually harvest it), so Survival mining and
 * Auto Click always use the right item.
 *
 * <p>Consulted by Auto Click before breaking a block and by the Survival bot
 * before mining ore.</p>
 */
public final class AutoToolModule {

	private static boolean enabled = true;

	private AutoToolModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Auto Tool: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	/**
	 * Swaps to the fastest hotbar tool for {@code state} (only tools that can
	 * actually harvest the block are considered; hands keep whatever was
	 * selected when nothing suitable exists).
	 */
	public static void selectBest(ClientPlayerEntity player, BlockState state) {
		if (!enabled || player == null) {
			return;
		}
		PlayerInventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		if (isBestTool(inventory.getStack(current), state)) {
			return;
		}
		int best = -1;
		float bestSpeed = 1.0F;
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isSuitableFor(state)) {
				continue;
			}
			float speed = stack.getMiningSpeedMultiplier(state);
			if (speed > bestSpeed) {
				bestSpeed = speed;
				best = slot;
			}
		}
		if (best >= 0 && best != current) {
			inventory.setSelectedSlot(best);
		}
	}

	/** Whether the held item already harvests the block at a good speed. */
	private static boolean isBestTool(ItemStack stack, BlockState state) {
		return stack.isSuitableFor(state) && stack.getMiningSpeedMultiplier(state) > 1.0F;
	}
}