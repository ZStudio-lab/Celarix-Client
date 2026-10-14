package com.mnchacks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Panic — one click turns every hack off at once. Stateful modules are
 * reset through their reset() methods (which also release keys, cameras and
 * other held state), mixin/GUI-driven ones are flipped off only if they are
 * currently enabled.
 */
public final class PanicModule {

	private PanicModule() {
	}

	public static void trigger() {
		MncHacksClient.panicAll();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("PANIC - all hacks disabled"), true);
		}
	}
}
