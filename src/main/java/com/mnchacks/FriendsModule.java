package com.mnchacks;

import com.mnchacks.mixin.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Friends — middle-click a player to add/remove them as a friend. Friends are
 * never attacked (aimbot, killaura, triggerbot all respect the list) and get
 * a green box in the ESP.
 */
public final class FriendsModule {

	private static boolean enabled = true;
	private static boolean prevPick = false;
	private static final Set<String> friends = ConcurrentHashMap.newKeySet();

	private FriendsModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean isFriend(String name) {
		return friends.contains(name);
	}

	public static void reset() {
		enabled = false;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(FriendsModule::tick);
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Friends: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void tick(MinecraftClient client) {
		if (!enabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.crosshairTarget == null) {
			return;
		}
		boolean pick = client.options.pickItemKey.isPressed();
		if (pick && !prevPick
				&& client.crosshairTarget instanceof EntityHitResult hit
				&& hit.getEntity() instanceof ClientPlayerEntity other
				&& other != player) {
			String name = other.getName().getString();
			if (friends.remove(name)) {
				message(client, "Friend removed: " + name);
			} else {
				friends.add(name);
				message(client, "Friend added: " + name);
			}
		}
		prevPick = pick;
	}

	private static void message(MinecraftClient client, String text) {
		if (client.player != null) {
			client.player.sendMessage(Text.literal(text), true);
		}
	}
}