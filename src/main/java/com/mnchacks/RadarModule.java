package com.mnchacks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Radar — a HUD minimap: every tracked entity (the ESP's awareness list)
 * is drawn as a dot around the centre of the screen, rotated so "up" is
 * always the direction you are facing. Players red, teammates green, mobs
 * orange; dots clamp to the edge when far away.
 */
@Environment(EnvType.CLIENT)
public final class RadarModule {

	private static final double RANGE = 64.0D;
	private static final double SCALE = 2.5D;
	private static final int EDGE_PX = 70;

	private static boolean enabled = false;

	private RadarModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void register() {
		HudRenderCallback.EVENT.register(RadarModule::render);
	}

	public static void reset() {
		enabled = false;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			client.player.sendMessage(Text.literal("Radar: " + (enabled ? "ON" : "OFF")), true);
		}
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		if (!enabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.options.hudHidden) {
			return;
		}

		int centerX = client.getWindow().getScaledWidth() / 2;
		int centerY = client.getWindow().getScaledHeight() / 2;

		// Centre marker: you.
		context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xFFFFFFFF);

		double radians = Math.toRadians(player.getYaw());
		// Right-hand and forward vectors at the current yaw.
		double rightX = -Math.cos(radians);
		double rightZ = -Math.sin(radians);
		double fwdX = -Math.sin(radians);
		double fwdZ = Math.cos(radians);

		for (Entity entity : EspModule.getTrackedTargets()) {
			if (entity == player || !entity.isAlive()) {
				continue;
			}
			double relX = entity.getX() - player.getX();
			double relZ = entity.getZ() - player.getZ();
			if (relX * relX + relZ * relZ > RANGE * RANGE) {
				continue;
			}
			// Screen space: +sx = right of you, -sy = in front of you.
			double sx = relX * rightX + relZ * rightZ;
			double sy = -(relX * fwdX + relZ * fwdZ);
			double px = sx * SCALE;
			double py = sy * SCALE;
			double dist = Math.sqrt(px * px + py * py);
			if (dist > EDGE_PX) {
				px = px / dist * EDGE_PX;
				py = py / dist * EDGE_PX;
			}

			int color;
			if (entity instanceof PlayerEntity) {
				color = Teams.isTeammate(entity, player) ? 0xFF40FF70 : 0xFFFF4040;
			} else {
				color = 0xFFFFA030;
			}
			int x = centerX + (int) Math.round(px);
			int y = centerY + (int) Math.round(py);
			context.fill(x - 1, y - 1, x + 2, y + 2, color);
		}
	}
}
