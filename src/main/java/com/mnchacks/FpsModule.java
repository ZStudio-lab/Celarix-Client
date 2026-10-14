package com.mnchacks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.text.Text;

/**
 * FPS Boost.
 *
 * <p>Applies an aggressive low-cost preset to the vanilla video settings while
 * enabled, and restores your original settings when toggled off (or when you
 * leave the world / quit) so nothing is permanently changed.</p>
 *
 * <p>What it touches, biggest wins first:</p>
 * <ul>
 *   <li>max FPS - removes the frame cap (vanilla defaults to 120)</li>
 *   <li>chunk builder - only meshes chunks near you, freeing background
 *       threads and cutting stutter</li>
 *   <li>particles - minimal</li>
 *   <li>clouds - off</li>
 *   <li>graphics mode - fast</li>
 *   <li>entity render distance scaling - halved (entities still render
 *       close by; the world render distance is deliberately untouched)</li>
 *   <li>biome blend 0, entity shadows off, smooth lighting off, vsync off</li>
 *   <li>FOV / distortion effect scales - 0 (nausea and spyglass wobble
 *       become free)</li>
 * </ul>
 */
public final class FpsModule {

	// --- Preset values ----------------------------------------------------

	/** Entity render distance scaling while boosting (only lowered). */
	private static final double TARGET_ENTITY_DISTANCE = 0.5D;
	/** 260 is vanilla's "Unlimited". */
	private static final int TARGET_MAX_FPS = 260;

	private static final ParticlesMode TARGET_PARTICLES = ParticlesMode.MINIMAL;
	private static final GraphicsMode TARGET_GRAPHICS = GraphicsMode.FAST;
	private static final CloudRenderMode TARGET_CLOUDS = CloudRenderMode.OFF;
	private static final ChunkBuilderMode TARGET_CHUNK_BUILDER = ChunkBuilderMode.NEARBY;
	private static final double TARGET_EFFECT_SCALE = 0.0D;

	private static boolean enabled = false;

	/** Whether we currently hold a snapshot of the user's original settings. */
	private static boolean hasSnapshot = false;
	private static double savedEntityDistance;
	private static int savedBiomeBlend;
	private static boolean savedEntityShadows;
	private static boolean savedSmoothLighting;
	private static boolean savedVsync;
	private static ParticlesMode savedParticles;
	private static GraphicsMode savedGraphics;
	private static CloudRenderMode savedClouds;
	private static int savedMaxFps;
	private static ChunkBuilderMode savedChunkBuilder;
	private static double savedFovEffectScale;
	private static double savedDistortionEffectScale;

	private FpsModule() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void uiToggle() {
		MinecraftClient client = MinecraftClient.getInstance();
		enabled = !enabled;
		if (enabled) {
			apply(client);
		} else {
			restore();
		}
		if (client.player != null) {
			client.player.sendMessage(Text.literal("FPS Boost: " + (enabled ? "ON" : "OFF")), true);
		}
	}
	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(FpsModule::tick);
	}

	/** Turns the boost off and restores the original video settings. */
	public static void reset() {
		enabled = false;
		restore();
	}

	private static void tick(MinecraftClient client) {
		while (Keybinds.fpsToggle.wasPressed()) {
			enabled = !enabled;
			if (enabled) {
				apply(client);
			} else {
				restore();
			}
			if (client.player != null) {
				client.player.sendMessage(Text.literal("FPS Boost: " + (enabled ? "ON" : "OFF")), true);
			}
		}
	}

	private static void apply(MinecraftClient client) {
		GameOptions options = client.options;

		// Snapshot once, so repeated toggles or world changes don't overwrite
		// the user's real settings with the boosted ones.
		if (!hasSnapshot) {
			savedEntityDistance = options.getEntityDistanceScaling().getValue();
			savedBiomeBlend = options.getBiomeBlendRadius().getValue();
			savedEntityShadows = options.getEntityShadows().getValue();
			savedSmoothLighting = options.getAo().getValue();
			savedVsync = options.getEnableVsync().getValue();
			savedParticles = options.getParticles().getValue();
			savedGraphics = options.getGraphicsMode().getValue();
			savedClouds = options.getCloudRenderMode().getValue();
			savedMaxFps = options.getMaxFps().getValue();
			savedChunkBuilder = options.getChunkBuilderMode().getValue();
			savedFovEffectScale = options.getFovEffectScale().getValue();
			savedDistortionEffectScale = options.getDistortionEffectScale().getValue();
			hasSnapshot = true;
		}

		// Only ever lower this, so we never hurt a user who already runs light.
		options.getEntityDistanceScaling().setValue(Math.min(savedEntityDistance, TARGET_ENTITY_DISTANCE));

		options.getParticles().setValue(TARGET_PARTICLES);
		options.getGraphicsMode().setValue(TARGET_GRAPHICS);
		options.getCloudRenderMode().setValue(TARGET_CLOUDS);
		options.getChunkBuilderMode().setValue(TARGET_CHUNK_BUILDER);
		options.getBiomeBlendRadius().setValue(0);
		options.getEntityShadows().setValue(false);
		options.getAo().setValue(false);
		options.getEnableVsync().setValue(false);
		// Uncap the frame rate (260 = vanilla "Unlimited") - only raises it.
		options.getMaxFps().setValue(Math.max(savedMaxFps, TARGET_MAX_FPS));
		options.getFovEffectScale().setValue(TARGET_EFFECT_SCALE);
		options.getDistortionEffectScale().setValue(TARGET_EFFECT_SCALE);
	}

	private static void restore() {
		if (!hasSnapshot) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return;
		}
		GameOptions options = client.options;

		options.getEntityDistanceScaling().setValue(savedEntityDistance);
		options.getBiomeBlendRadius().setValue(savedBiomeBlend);
		options.getEntityShadows().setValue(savedEntityShadows);
		options.getAo().setValue(savedSmoothLighting);
		options.getEnableVsync().setValue(savedVsync);
		options.getParticles().setValue(savedParticles);
		options.getGraphicsMode().setValue(savedGraphics);
		options.getCloudRenderMode().setValue(savedClouds);
		options.getMaxFps().setValue(savedMaxFps);
		options.getChunkBuilderMode().setValue(savedChunkBuilder);
		options.getFovEffectScale().setValue(savedFovEffectScale);
		options.getDistortionEffectScale().setValue(savedDistortionEffectScale);

		hasSnapshot = false;
	}
}
