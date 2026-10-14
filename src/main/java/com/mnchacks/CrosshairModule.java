package com.mnchacks;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Custom Crosshair — replaces the vanilla crosshair with a clean animated
 * design: a dot with four accent-tinted lines that spread apart while the
 * attack cooldown recharges, and flash white on a full charge. Hides in
 * third-person, when a screen is open, or when the vanilla HUD is hidden.
 */
public final class CrosshairModule {

	private static final int ACCENT = 0xFF3FBFEE;
	private static final int HOT = 0xFFFF5A5A;

	private static boolean enabled = true;

	private CrosshairModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Crosshair: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	public static void register() {
		HudRenderCallback.EVENT.register(CrosshairModule::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!enabled || client.player == null || client.options.hudHidden) {
			return;
		}
		if (client.options.getPerspective() != Perspective.FIRST_PERSON) {
			return;
		}
		if (client.interactionManager == null) {
			return;
		}
		// No crosshair while a GUI is open (vanilla behaviour).
		if (client.currentScreen != null) {
			return;
		}

		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		int cx = width / 2;
		int cy = height / 2;

		// Gap widens as the attack meter recharges, closes when ready.
		float charge = client.player.getAttackCooldownProgress(0.0F);
		int gap = 3 + Math.round((1.0F - charge) * 5.0F);
		int length = 7;

		// Charge colour: red while recharging, accent when a full-damage hit
		// is ready.
		int color = charge >= 0.99F ? ACCENT : HOT;
		int outline = 0xCC000000;

		// All geometry is drawn symmetric around the exact screen center:
		// 2px-thick arms, a 2x2 center dot, each with a 1px dark outline.
		// Horizontal arms (left + right), 2px tall, centered on cy.
		line(context, cx + gap, cy - 1, cx + gap + length, cy + 1, color, outline);
		line(context, cx - gap - length, cy - 1, cx - gap, cy + 1, color, outline);
		// Vertical arms (top + bottom), 2px wide, centered on cx.
		line(context, cx - 1, cy + gap, cx + 1, cy + gap + length, color, outline);
		line(context, cx - 1, cy - gap - length, cx + 1, cy - gap, color, outline);
		// Center dot, 2x2, exactly on (cx, cy).
		context.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
	}

	private static void line(DrawContext context, int x1, int y1, int x2, int y2, int color, int outline) {
		int left = Math.min(x1, x2);
		int right = Math.max(x1, x2);
		int top = Math.min(y1, y2);
		int bottom = Math.max(y1, y2);
		// Dark outline frame first (1px larger on every side)...
		context.fill(left - 1, top - 1, right + 1, bottom + 1, outline);
		// ...then the colored core on top.
		context.fill(left, top, right, bottom, color);
	}
}