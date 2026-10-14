package com.mnchacks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side half of the mod.
 *
 * <p>On the integrated server (single player / LAN host) this accepts small
 * state payloads from our client and applies the corresponding behavior
 * server-side, so vanilla's checks never fight the client:</p>
 * <ul>
 *   <li>{@code fly_state} — grants flight abilities (no "floating" kick);</li>
 *   <li>{@code speed_state} — applies a hidden Speed effect;</li>
 *   <li>{@code oneshot_state} — makes that player's melee hits lethal.</li>
 * </ul>
 *
 * <p>If the jar is also installed on a dedicated server, the same mechanism
 * works there for players running this mod.</p>
 */
public class MncHacksServer implements ModInitializer {

	public static final Identifier FLY_CHANNEL = Identifier.of("celarix", "fly_state");
	public static final Identifier SPEED_CHANNEL = Identifier.of("celarix", "speed_state");
	public static final Identifier ONESHOT_CHANNEL = Identifier.of("celarix", "oneshot_state");
	public static final Identifier HEAL_CHANNEL = Identifier.of("celarix", "heal_state");

	/** Speed effect amplifier (0-based): 3 = Speed IV. */
	private static final int SPEED_AMPLIFIER = 3;

	private static final Set<UUID> FLIGHT_ALLOWED = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> SPEED_ENABLED = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> ONE_SHOT = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> HEAL_ENABLED = ConcurrentHashMap.newKeySet();

	/** Guards against our own lethal hit re-entering the attack callback. */
	private static boolean applyingOneShot = false;

	/** {@code fly_state} payload (client → server). */
	public record FlyStatePayload(boolean enabled) implements CustomPayload {
		public static final CustomPayload.Id<FlyStatePayload> ID = new CustomPayload.Id<>(FLY_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, FlyStatePayload> CODEC =
				PacketCodec.tuple(PacketCodecs.BOOLEAN, FlyStatePayload::enabled, FlyStatePayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** {@code speed_state} payload (client → server). */
	public record SpeedStatePayload(boolean enabled) implements CustomPayload {
		public static final CustomPayload.Id<SpeedStatePayload> ID = new CustomPayload.Id<>(SPEED_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, SpeedStatePayload> CODEC =
				PacketCodec.tuple(PacketCodecs.BOOLEAN, SpeedStatePayload::enabled, SpeedStatePayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** {@code oneshot_state} payload (client → server). */
	public record OneShotStatePayload(boolean enabled) implements CustomPayload {
		public static final CustomPayload.Id<OneShotStatePayload> ID = new CustomPayload.Id<>(ONESHOT_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, OneShotStatePayload> CODEC =
				PacketCodec.tuple(PacketCodecs.BOOLEAN, OneShotStatePayload::enabled, OneShotStatePayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** {@code heal_state} payload (client → server). */
	public record HealStatePayload(boolean enabled) implements CustomPayload {
		public static final CustomPayload.Id<HealStatePayload> ID = new CustomPayload.Id<>(HEAL_CHANNEL);
		public static final PacketCodec<RegistryByteBuf, HealStatePayload> CODEC =
				PacketCodec.tuple(PacketCodecs.BOOLEAN, HealStatePayload::enabled, HealStatePayload::new);

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	@Override
	public void onInitialize() {
		// Payload codecs: play-phase, client → server.
		PayloadTypeRegistry.playC2S().register(FlyStatePayload.ID, FlyStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SpeedStatePayload.ID, SpeedStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(OneShotStatePayload.ID, OneShotStatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HealStatePayload.ID, HealStatePayload.CODEC);

		// --- Flight ---
		ServerPlayNetworking.registerGlobalReceiver(FlyStatePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (payload.enabled()) {
					FLIGHT_ALLOWED.add(player.getUuid());
				} else {
					// Zero out fall distance so landing right after turning
					// fly off doesn't instantly hurt.
					if (FLIGHT_ALLOWED.remove(player.getUuid())) {
						player.fallDistance = 0.0F;
					}
					restoreFly(player);
				}
			});
		});

		// --- Speed ---
		ServerPlayNetworking.registerGlobalReceiver(SpeedStatePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (payload.enabled()) {
					SPEED_ENABLED.add(player.getUuid());
				} else {
					SPEED_ENABLED.remove(player.getUuid());
					player.removeStatusEffect(StatusEffects.SPEED);
				}
			});
		});

		// --- One-shot ---
		ServerPlayNetworking.registerGlobalReceiver(OneShotStatePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (payload.enabled()) {
					ONE_SHOT.add(player.getUuid());
				} else {
					ONE_SHOT.remove(player.getUuid());
				}
			});
		});

		// --- Auto Heal ---
		ServerPlayNetworking.registerGlobalReceiver(HealStatePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> {
				if (payload.enabled()) {
					HEAL_ENABLED.add(player.getUuid());
				} else {
					HEAL_ENABLED.remove(player.getUuid());
				}
			});
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				UUID id = player.getUuid();

				if (FLIGHT_ALLOWED.contains(id) && !player.isSpectator()) {
					PlayerAbilities abilities = player.getAbilities();
					abilities.allowFlying = true;
					abilities.setFlySpeed(0.1F);
				}

				if (SPEED_ENABLED.contains(id) && !player.isSpectator()) {
					keepSpeed(player);
				}

				if (HEAL_ENABLED.contains(id) && !player.isSpectator() && player.isAlive()) {
					if (player.getHealth() < player.getMaxHealth()) {
						player.setHealth(player.getMaxHealth());
					}
				}
			}
		});

		// One shot: replace an enabled player's melee hit with a lethal one.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient() || applyingOneShot) {
				return ActionResult.PASS;
			}
			if (!(player instanceof ServerPlayerEntity) || !ONE_SHOT.contains(player.getUuid())) {
				return ActionResult.PASS;
			}
			if (!(entity instanceof LivingEntity)) {
				return ActionResult.PASS;
			}

			LivingEntity target = (LivingEntity) entity;
			if (!target.isAlive()) {
				return ActionResult.PASS;
			}

			applyingOneShot = true;
			try {
				// Clear vanilla invulnerability frames so the hit always lands,
				// then deal a lethal amount.
				target.timeUntilRegen = 0;
				target.damage((ServerWorld) world,
						target.getDamageSources().playerAttack(player), Float.MAX_VALUE);
			} finally {
				applyingOneShot = false;
			}
			// We handled the hit ourselves; don't let vanilla apply a second one.
			return ActionResult.FAIL;
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUuid();
			FLIGHT_ALLOWED.remove(id);
			SPEED_ENABLED.remove(id);
			ONE_SHOT.remove(id);
			HEAL_ENABLED.remove(id);
		});
	}

	/** Keeps a Speed effect on the player, but hides its particles and icon. */
	private static void keepSpeed(ServerPlayerEntity player) {
		StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
		if (current == null || current.getAmplifier() < SPEED_AMPLIFIER || current.getDuration() < 20) {
			player.addStatusEffect(new StatusEffectInstance(
					StatusEffects.SPEED, 60, SPEED_AMPLIFIER, false, false, false));
		}
	}

	private static void restoreFly(ServerPlayerEntity player) {
		PlayerAbilities abilities = player.getAbilities();
		abilities.flying = false;
		abilities.allowFlying = player.isCreative() || player.isSpectator();
		abilities.setFlySpeed(0.05F);
	}
}