package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Extended reach — adds {@value #EXTRA_REACH} blocks to the crosshair
 * interaction raycast, so Auto Click can hit entities (and mine blocks) at
 * up to ~9.5 blocks instead of the vanilla 4.5.
 *
 * <p>Works by extending {@code PlayerEntity.getBlockInteractionRange()} and
 * {@code getEntityInteractionRange()}, which are exactly what the game uses
 * to build the crosshair target. Note that <b>vanilla</b> servers still
 * reject interactions past 6 blocks — on servers with relaxed hit validation
 * (like ArchMC) the full extension lands.</p>
 *
 * <p>Enabled by default; toggle it in the Celarix menu.</p>
 */
public final class ReachModule {

	/** How many blocks are added to the vanilla crosshair reach. */
	public static final float EXTRA_REACH = 5.0F;

	private static boolean enabled = false;

	private ReachModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Reach: " + (enabled ? "ON (+5)" : "OFF")), true);
		}
	}

	/** Re-asserts the default (extended) state on world change. */
	public static void reset() {
		enabled = false;
	}
}