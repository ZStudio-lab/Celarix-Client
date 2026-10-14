package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class BreadcrumbsModule {

	private static final int MAX_POINTS = 400;

	private static boolean enabled = false;
	private static int counter = 0;
	private static final ArrayDeque<Vec3d> points = new ArrayDeque<>();

	private BreadcrumbsModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static List<Vec3d> getPoints() {
		return new ArrayList<>(points);
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BreadcrumbsModule::tick);
	}

	public static void reset() {
		enabled = false;
		points.clear();
	}

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			points.clear();
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Breadcrumbs: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			points.clear();
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		if (++counter % 5 == 0) {
			points.addLast(player.getPos());
			if (points.size() > MAX_POINTS) {
				points.removeFirst();
			}
		}
	}
}