package com.mnchacks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Client entrypoint. Registers the modules, the world renderer (ESP), the
 * minimal HUD and the Click GUI (minus key).
 */
public class MncHacksClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Keybinds.register();

		// World memory feeds the Survival bot; register it first so its
		// snapshot is fresh by the time the modules tick.
		Awareness.register();

		FlyModule.register();
		EspModule.register();
		AimModule.register();
		SpeedModule.register();
		OneShotModule.register();
		AutoClickModule.register();
		KillAuraModule.register();
		AutoWalkModule.register();
		FpsModule.register();
		SurvivalModule.register();
		FastPlaceModule.register();
		AirPlaceModule.register();
		AutoRespawnModule.register();
		AutoFishModule.register();
		AutoTranslateModule.register();
		BlinkModule.register();
		AntiAfkPlusModule.register();
		AntiLevitationModule.register();
		ClickTPModule.register();
		MiddleClickPearlModule.register();
		AntiWebModule.register();
		NoArmorStandModule.register();
		AntiBlindnessModule.register();
		AutoClickRayModule.register();
		NoWallSlowModule.register();
		LandingRecoveryModule.register();
		AutoHopModule.register();
		AutoSwordModule.register();
		AutoBreedModule.register();
		AutoSignModule.register();
		FullbrightModule.register();
		AutoSprintModule.register();
		KnockbackModule.register();
		InvisModule.register();
		InvisRevealModule.register();
		AirJumpModule.register();
		SpiderModule.register();
		JesusModule.register();
		StepModule.register();
		FastBreakModule.register();
		AutoArmorModule.register();
		AutoTotemModule.register();
		NoSlowModule.register();
		NoHurtCamModule.register();
		NoWeatherModule.register();
		AntiAfkModule.register();
		AutoJumpModule.register();
		NoJumpDelayModule.register();
		LongJumpModule.register();
		HighJumpModule.register();
		ParkourModule.register();
		SafeWalkModule.register();
		TriggerBotModule.register();
		DolphinModule.register();
		NukerModule.register();

		// v1.0.1 - new hacks: detached free camera, telescopic zoom and
		// movement through open screens.
		FreeCamModule.register();
		ZoomModule.register();

		// v1.0.2 - storage ESP, X-Ray, auto leave.
		// (Panic and No Overlay are state holders driven by the GUI/mixins.)
		ChestEspModule.register();
		AutoLeaveModule.register();

		// v1.0.4 - healing potions, parachute, snow shoes, new chunks.
		// descent, powder-snow walking, fresh-chunk outlines. Snow Shoe is
		// mixin-driven (GUI toggle only).
		AutoPotionModule.register();
		GlideModule.register();
		NewChunksModule.register();

		// v1.0.5 - bow aiming, vein mining, fog removal (No Fog is
		// mixin-driven, GUI toggle only).
		BowAimbotModule.register();
		VeinMinerModule.register();

		// v1.0.6 - autonomous navigation: tunnel digging, seek & destroy.
		TunnellerModule.register();
		FightBotModule.register();

		// v1.0.7 - utility batch: restock, farming auras, bunker, radar,
		// animal helpers, MLG bucket, chat flood filter, multitasking, derp.
		// (Derp / Anti Spam / Multi Task are mixin-driven, GUI toggle only.)
		RestockModule.register();
		BonemealAuraModule.register();
		TillAuraModule.register();
		InstantBunkerModule.register();
		RadarModule.register();
		AutoShearModule.register();
		AutoTameModule.register();
		AutoBucketModule.register();

		// Ported but previously dormant modules - registering them brings
		// their Click GUI entries to life.
		ChestStealerModule.register();
		InventoryCleanerModule.register();
		AutoEatModule.register();
		FastClimbModule.register();
		FastFallModule.register();
		ElytraFlyModule.register();
		SneakModule.register();
		CriticalsModule.register();
		AntiVoidModule.register();
		ScaffoldModule.register();
		AutoFarmModule.register();
		AutoTorchModule.register();
		HoleSnapModule.register();
		HoleEspModule.register();
		BreadcrumbsModule.register();
		RotationLockModule.register();
		FriendsModule.register();
		AutoReconnectModule.register();

		// Start each connection with the stateful modules off and their keys
		// released, so nothing carries over from a previous world.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetModules());
		// FPS Boost changes global video settings, so also restore those when
		// leaving the world (and before shutdown writes options.txt).
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			resetModules();
			FpsModule.reset();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			FpsModule.reset();
			FullbrightModule.reset();
			FreeCamModule.reset();
		});

		WorldRenderEvents.END.register(EspModule::renderWorld);
		CrosshairModule.register();
		Hud.register();
		CelarixScreen.register();
	}

	private static void resetModules() {
		Awareness.reset();
		RangedModule.reset();
		ReachModule.reset();
		AntiTeleportModule.reset();
		FlyModule.reset();
		SpeedModule.reset();
		OneShotModule.reset();
		AutoClickModule.reset();
		AutoWalkModule.reset();
		SurvivalModule.reset();
		KnockbackModule.reset();
		BlinkModule.reset();
		ClickTPModule.reset();
		MiddleClickPearlModule.reset();
		InvisModule.reset();
		StepModule.reset();
		LongJumpModule.reset();
		HighJumpModule.reset();
		// v1.0.1 additions (Friends and Auto Reconnect deliberately persist:
		// friends should survive a reconnect, and auto-reconnect must stay on
		// across the disconnect to be able to do its job).
		FreeCamModule.reset();
		ZoomModule.reset();
		InventoryWalkModule.reset();
		ChestStealerModule.reset();
		InventoryCleanerModule.reset();
		AutoEatModule.reset();
		FastClimbModule.reset();
		FastFallModule.reset();
		ElytraFlyModule.reset();
		SneakModule.reset();
		CriticalsModule.reset();
		AntiVoidModule.reset();
		ScaffoldModule.reset();
		AutoFarmModule.reset();
		AutoTorchModule.reset();
		HoleSnapModule.reset();
		HoleEspModule.reset();
		BreadcrumbsModule.reset();
		RotationLockModule.reset();
		// v1.0.2 additions.
		ChestEspModule.reset();
		XRayModule.reset();
		NoOverlayModule.reset();
		AutoLeaveModule.reset();
		// v1.0.4 additions.
		AutoPotionModule.reset();
		GlideModule.reset();
		SnowShoeModule.reset();
		NewChunksModule.reset();
		// v1.0.5 additions.
		BowAimbotModule.reset();
		VeinMinerModule.reset();
		NoFogModule.reset();
		// v1.0.6 additions.
		TunnellerModule.reset();
		FightBotModule.reset();
		// v1.0.7 additions.
		RestockModule.reset();
		BonemealAuraModule.reset();
		TillAuraModule.reset();
		InstantBunkerModule.reset();
		RadarModule.reset();
		AutoShearModule.reset();
		AutoTameModule.reset();
		AutoBucketModule.reset();
		DerpModule.reset();
		AntiSpamModule.reset();
		MultiTaskModule.reset();
	}

	/**
	 * Panic: every hack off at once. Resets the stateful modules (releasing
	 * held keys, cameras and other state) and flips the GUI/mixin-driven
	 * ones off only when they are currently on. Friends and the Click GUI
	 * itself stay untouched.
	 */
	public static void panicAll() {
		resetModules();
		// Normally persistent modules (reset-on-join exceptions) - panic
		// turns even these off.
		if (EspModule.isEnabled()) {
			EspModule.uiToggle();
		}
		if (TracersModule.isEnabled()) {
			TracersModule.toggle();
		}
		if (FullbrightModule.isEnabled()) {
			FullbrightModule.reset();
		}
		if (CrosshairModule.isEnabled()) {
			CrosshairModule.toggle();
		}
		if (HealthFreezeModule.isEnabled()) {
			HealthFreezeModule.toggle();
		}
		if (NoFallModule.isEnabled()) {
			NoFallModule.toggle();
		}
		if (InvisRevealModule.isEnabled()) {
			InvisRevealModule.toggle();
		}
		if (SearchModule.isEnabled()) {
			SearchModule.toggle();
		}
		if (TrajectoriesModule.isEnabled()) {
			TrajectoriesModule.toggle();
		}
		FpsModule.reset();
	}
}