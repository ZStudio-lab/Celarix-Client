package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class HoleEspModule {

	private static boolean enabled = false;
	private static int scanCooldown = 0;
	private static List<BlockPos> holes = new ArrayList<>();

	private HoleEspModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static List<BlockPos> getHoles() {
		return holes;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HoleEspModule::tick);
	}

	public static void reset() {
		enabled = false;
		holes = new ArrayList<>();
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			holes = new ArrayList<>();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Hole ESP: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		if (scanCooldown > 0) {
			scanCooldown--;
			return;
		}
		scanCooldown = 10;
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		holes = new ArrayList<>();
		BlockPos origin = player.getBlockPos();
		int fy = origin.getY();
		for (BlockPos pos : BlockPos.iterate(origin.add(-8, 0, -8), origin.add(8, 0, 8))) {
			if (holes.size() >= 30) {
				break;
			}
			boolean air1 = client.world.getBlockState(pos).isAir();
			boolean air2 = client.world.getBlockState(pos.down()).isAir();
			boolean air3 = client.world.getBlockState(pos.down(2)).isAir();
			boolean floor = !client.world.getBlockState(pos.down(3)).isAir()
					&& client.world.getBlockState(pos.down(3)).getFluidState().isEmpty();
			if (air1 && air2 && air3 && floor) {
				holes.add(pos.toImmutable());
			}
		}
	}
}