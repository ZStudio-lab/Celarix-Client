# Celarix Client

A client-side utility mod for Minecraft on **Fabric** — 92 modules across combat, movement,
world, render, player and automation, all driven from one built-in Click GUI.

The whole client lives behind a single key. Press **`Left Alt`** and every window is there:
draggable, resizable, with live sliders and toggles that re-pack instead of stacking.

> Showcase page: [`Versions/index.html`](Versions/index.html)

---

## Downloads

Separate, purpose-built jars per game version — nothing is bolted on or left behind.

| Minecraft | Client version | Jar |
|---|---|---|
| 1.21.5 | v1.0.7 | [`celarix-client-1.0.7-mc1.21.5.jar`](Versions/1.21.5/celarix-client-1.0.7-mc1.21.5.jar) |
| 1.20.4 | v1.0.0 | [`celarix-client-1.0.0-mc1.20.4.jar`](Versions/1.20.4/celarix-client-1.0.0-mc1.20.4.jar) |

Both jars are built from the sources in this repository.

## Requirements

| | |
|---|---|
| Minecraft | 1.21.5 or 1.20.4 |
| Mod loader | [Fabric Loader](https://fabricmc.net/use/installer/) |
| Dependency | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Java | 21 or newer |
| Side | Client-side (single-player, LAN and multiplayer) |

## Installing

1. Install **Fabric Loader** for your game version.
2. Put **Fabric API** in your `mods` folder.
3. Put the matching `celarix-client-*.jar` in the same `mods` folder.
4. Launch the game and press **`Left Alt`**.

Only one Celarix jar should be installed at a time.

## Modules

92 modules in 7 Click GUI windows.

| Window | Modules | Examples |
|---|---|---|
| Combat | 11 | Aimbot, Kill Aura, Bow Aimbot, Criticals, Reach, Auto Totem |
| Movement | 19 | Fly, Speed, Sprint, Step, Jesus, Elytra Fly, Scaffold, Parkour |
| Player | 16 | Auto Eat, Auto Tool, Auto Armor, Auto Fish, Fast Place, Inventory Cleaner |
| World | 10 | Nuker, Tunneller, Instant Bunker, Auto Farm, Vein Miner, Auto Sign |
| Render | 19 | ESP, Tracers, Fullbright, X-Ray, Chest ESP, Radar, Zoom, No Fog |
| Client | 6 | Friends, HUD, Panic, Click GUI, Anti-Spam, Fast Place |
| Misc | 11 | Anti-AFK, Auto Reconnect, Auto Respawn, Auto Leave, No Fall, Auto Walk |

Combat targeting locks onto the nearest **enemy** and never fires on friends — add a player to
the Friends list and Aimbot, Bow Aimbot and FightBot will leave them alone.

## Building from source

Requires JDK 21+ (JDK 22 is used for the toolchain).

```
gradlew.bat build
```

The mod jar is written to `build/libs/celarix-client-<version>.jar`.

To run a development client instead:

```
gradlew.bat runClient
```

Build settings live in [`gradle.properties`](gradle.properties) — the target game version,
loader version, and `mod_version` are all defined there.

## Repository layout

```
src/main/java/com/mnchacks/     mod source (modules, GUI, mixins, HUD)
src/main/resources/             fabric.mod.json, mixins config, lang, logo
Versions/                       showcase page and prebuilt release jars
```

## Notes

- This is a **client-side** mod. Whether utility clients are allowed is entirely up to the
  server you play on — read the rules first.
- Not affiliated with Mojang Studios or Microsoft.

## License

[MIT](LICENSE)
