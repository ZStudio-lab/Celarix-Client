package com.mnchacks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Minimal HUD: the client name + version in the top-left corner and a live
 * FPS counter. Nothing else — every toggle lives in the Click GUI.
 */
@Environment(EnvType.CLIENT)
public final class Hud {

	private Hud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register(Hud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}
		TextRenderer textRenderer = client.textRenderer;

		String version = FabricLoader.getInstance().getModContainer("celarix")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");

		context.drawTextWithShadow(textRenderer, "§bCelarix §f" + version, 4, 4, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, "§7FPS: §f" + client.getCurrentFps(), 4, 14, 0xFFFFFFFF);
	}
}