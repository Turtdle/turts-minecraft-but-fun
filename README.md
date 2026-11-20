Modern Armory
=============

Modern firearms for Minecraft, built on NeoForge + GeckoLib: 44 real guns (pistols, SMGs, rifles, marksman and
sniper rifles, shotguns, machine guns) with real ballistics, attachments and a gunsmith table, gun mastery,
grenades (frag, flash, smoke, tear gas, thermite), drones, a laser turret and four tiers of body armour.
Mod id `modernarmory`, package `com.modernarmory`. Works in singleplayer and on servers.

Playing it
----------

**Easiest: the installer.** Download `ModernArmoryInstaller.exe` from the
[latest release](https://github.com/Turtdle/modern-armory/releases/latest) and run it. It needs Minecraft Java
Edition installed and opened once (if it isn't, the installer says so and changes nothing). Pick a game version and
press Install: it installs NeoForge, Modern Armory and GeckoLib into their own folder
(`.minecraft\instances\modern-armory-<version>`) and adds a **Modern Armory** profile to the Minecraft Launcher, so
your normal game is left alone. Close the Minecraft Launcher before installing. Windows may warn about an unknown
publisher (the exe isn't code-signed): click *More info*, then *Run anyway*. Run it again later to update.

**By hand:** install [NeoForge](https://neoforged.net/) for your game version, then put the release's
`modernarmory-<mc>-<version>.jar` and the matching `geckolib-neoforge-<mc>-*.jar` into your `mods` folder.

| Minecraft | NeoForge | GeckoLib |
|-----------|----------|----------|
| 1.21.1 | 21.1.267 | 4.9.3 |
| 26.3 | 26.3.0.113 | 5.5.7 |

**A server:** install the NeoForge *server* for the version (NeoForge installer, *Install server*), put the same two
jars into the server's `mods` folder, and every player needs them too (the installer does that). See
[docs/SERVER_HOSTING.md](docs/SERVER_HOSTING.md) for hosting options.

Developing
----------

### Repository layout

| Path | What |
|------|------|
| `/` (root) | Main build for **Minecraft 1.21.1**: NeoForge 21.1.x, GeckoLib 4, Java 21. |
| `versions/26.3/` | Port for **Minecraft 26.3** (latest): NeoForge 26.3.0.x, GeckoLib 5 (from Modrinth's maven), Java 25. A standalone Gradle build with its own wrapper and `settings.gradle`. |
| `src/main/resources/` | **Shared resources** (lang, GeckoLib geo models, textures, animations, item models, sounds, damage types, tags). The 26.3 build adds this folder as a resources source dir, so assets generated or tuned once work in both versions. |
| `versions/26.3/src/main/resources/` | Files that must differ in 26.x: recipes (26.x ingredient format). An `assets/<ns>/items/<item>.json` here overrides the generated item definition. |
| `versions/26.3/src/main/lang-overlay/` | 26.3-only lang keys, merged into the shared lang files (e.g. the key-binding category). |
| `tools/modelgen/` | Python generator for the gun models (geo JSON, texture, placeholder animation, item display model). |

What the 26.3 build does with the shared root resources (task `prepareSharedAssets` in `versions/26.3/build.gradle`,
output in `versions/26.3/build/generated/sources/sharedAssets`):

* `assets/*/models/item/<item>.json`: GeckoLib items (`"parent": "builtin/entity"`, which no longer exists) are
  rewritten without that parent plus a particle texture, **keeping the display transforms**, so tuning the root
  model also tunes 26.3. Every item model also gets a 26.x item definition `items/<item>.json`
  (`minecraft:special` + `geckolib:geckolib` for GeckoLib items, `minecraft:model` otherwise).
* `assets/*/geo/**` and `assets/*/animations/**` are copied into GeckoLib 5's layout
  (`assets/*/geckolib/models/**`, `assets/*/geckolib/animations/**`). Animation files with no animations (the
  modelgen placeholder) are skipped, because GeckoLib 5 rejects them.
* `assets/*/lang/*.json` are merged with `versions/26.3/src/main/lang-overlay/<ns>/*.json`.
* `data/*/recipe/**` from the root is **not** used (1.21.1 ingredient format); 26.3 has its own copies. Both sets
  are written by `python tools/datagen/recipes.py` from one table.
* Java code is **not** shared: `versions/26.3/src/main/java` is a separate port (Minecraft 26.x and GeckoLib 5
  changed many APIs). Changes to the root code (including tuned constants such as `ClientState.ads*`) have to be
  ported by hand.

### Generating models

```
python tools/modelgen/build.py        # build every gun in tools/modelgen/guns/
python tools/modelgen/build.py m4     # build just one
```

Output goes to the shared `src/main/resources/assets/modernarmory/`, so both versions pick it up.

Other generated data (each script is the single source of its files):

```
python tools/modelgen/display.py        # models/item/<gun>.json display transforms (view classes)
python tools/modelgen/icons.py [id ...] # attachment inventory icons
python tools/texgen/make_textures.py    # scope textures, ammo icons
python tools/soundgen/make_sounds.py    # all sounds (gun_sounds.py: calibre reports, per-gun reload / cycle sounds, sounds.json)
python tools/datagen/recipes.py         # every recipe, in BOTH formats (1.21.1 root + versions/26.3)
```

### Building and running

Gradle itself runs on JDK 21 for both builds. The 26.3 build also needs JDK 25 to compile (as a Gradle
toolchain). To use JDKs you already have, point Gradle at both in `~/.gradle/gradle.properties`:

```
org.gradle.java.home=<path to JDK 21>
org.gradle.java.installations.paths=<path to JDK 21>,<path to JDK 25>
```

(Without that, the foojay toolchain resolver in `settings.gradle` downloads a matching JDK automatically.)

```
# 1.21.1
./gradlew build            # -> build/libs/modernarmory-<version>.jar
./gradlew runClient        # game dir: run/
./gradlew runAutotest      # scripted in-game test -> runs/autotest/screenshots/autotest_*.png
./gradlew runAutotest -Pscenario=tour -Pguns=m4,m2010   # per-gun tour -> autotest_tour_<gun>_*.png

# 26.3
cd versions/26.3
./gradlew build            # -> versions/26.3/build/libs/modernarmory-<version>.jar
./gradlew runClient        # game dir: versions/26.3/run/
./gradlew runAutotest      # same script -> versions/26.3/runs/autotest/screenshots/autotest_*.png
```

### Multiplayer test

`./gradlew runServer` starts a dedicated server in `run/` (in `versions/26.3/run/` for 26.3). For the scripted
multiplayer test it needs, once: `eula.txt` with `eula=true` (accepting the Minecraft EULA), and a `server.properties`
with `online-mode=false` and `server-port=25599`, plus `ops.json` opping the offline test player `Dev`
(UUID `380df991-f603-344c-a090-369bad2a924a`). Then, while it runs, `./gradlew runAutotestMp` joins it and checks hits,
mastery sync, reloads, grenades, flashbangs, armour and the gun limit over the network (`[autotest] mp: 12 of 12`).

Known multiplayer gaps (cosmetic): other players don't see your muzzle flash; bolt / pump cycle sounds reach other
players at the moment of the shot rather than after it; a bullet that enters your view mid-flight restarts its tracer
burnout; ricochet paths differ slightly between clients; a drone pilot's idle body turns its head with the camera; a
frag grenade's 250 fragments are 250 network entities.

Code layout, how to add a gun / cartridge / attachment / sound set / view class, and where the
mod-wide tuning knobs live: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

Resources
---------

Community documentation: https://docs.neoforged.net/ ·
GeckoLib wiki: https://wiki.geckolib.com/ ·
NeoForged Discord: https://discord.neoforged.net/

The Mojang mappings and their license are described at https://github.com/NeoForged/NeoForm/blob/main/Mojang.md
