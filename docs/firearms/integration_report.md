# Refactor
TASK 1 (architecture refactor) is done and verified. `./gradlew build` passes. The core autotest gives the same screenshots and the same [autotest] log values as the baseline taken before any change. The tour scenario works for m4 and m2010. I only touched the root project: versions/26.3 and tools/modelgen/guns/*.py are unchanged.

## New architecture
New package `com.modernarmory.gun`:
- **Catalog and specs:**
  - `Guns` is the one gun catalog.
  - `GunDefinition(id, displayName, spec, modelId)`, with `.model("m4")` for variants that share a model.
  - `GunSpec` is a record built with a fluent `Builder` and has `toBuilder()` for variants. `build()` names every missing value and rejects default attachments in slots the gun doesn't have.
  - `GunFamilies` has the family templates: `ar15()`, `boltSniper()`, `dmr()`, `pistol()`, `smg()`, `pumpShotgun()`, `tubeSemiShotgun()`, `beltFedMg()`, `heavyMg()`, `antiMateriel()`. They pre-fill view class, fire modes, feed, slots and typical spread/accuracy. Only `ar15` and `boltSniper` pre-fill sounds, because no other recordings exist yet, so guns in the other families must call `.sounds(...)`.
  - `GunView` moved here from `item`.
- **Mechanics as strategies:**
  - `FireMode`: SEMI, BURST, AUTO, BOLT, PUMP, STRAIGHT_PULL. Each has a label, a `Trigger` strategy (SINGLE / BURST / AUTO) and a `manualCycle` flag; mastery now uses that flag instead of `== BOLT`.
  - `TriggerState` holds the trigger state the strategies work on.
  - `Feed`: `MAGAZINE` (+1 in the chamber), `BELT` (no +1), `TUBE` (shell by shell, chambers on empty, a trigger pull stops it when a round is chambered). `MagazineFeed` and `TubeFeed` work against `ReloadContext` / `ReloadTask`.
  - `ProjectileLoad`: SINGLE, or PELLETS (N `BulletEntity`s spread over the pattern, damage per pellet). It is chosen from the cartridge.
  - `ShooterSpread` holds the movement-spread rules. They were copied in both `GunHandler` and `GunCrosshair`; now both use this class.
- **Sounds:** `SoundSet` (fire, suppressed, reload, reloadEmpty, shellInsert, cycle + delay) and `SoundSets` (AR15, M2010). `ModSounds` is still the one list of sound events.
- **Cartridges:** `Cartridge` has a builder, an id and an `ammoItemId`, plus shot-load data (pellets, pellet mass, patternMoa). `Cartridges` is a catalog with `byId` / `all()`. New `ProjectileSpec` record (one bullet or pellet in flight).
- **Registration:** `ModItems` registers ammo, guns and attachments from the catalogs, with `gun(def)` and `ammo(cartridge)` lookups. The ammo ids `ammo_556` / `ammo_300wm` are unchanged. The creative tab iterates the catalogs.
- **Items:** one `GunItem(GunDefinition)` class for every gun, and a new `AmmoItem`. Both fall back to the catalog / cartridge name when there is no lang key. `GunAnimations.restart` replays the per-shell reload animation.
- **`GunHandler`** has no gun-specific code left.
- **Framing (`ViewClass`):** RIFLE / PISTOL / HEAVY. The single source is `src/main/resources/assets/modernarmory/view_classes.json`.
  - Java reads it for the ADS origin/scale and the hip cant.
  - `tools/modelgen/display.py` (plus `display_items.json`) writes every `models/item/<gun>.json` from the same numbers. It has a `--check` mode and `build.py` calls it automatically.
  - The autotest compares each gun's baked item transforms with its view class and logs `[autotest] framing MISMATCH` on drift.
  - The M4 and M2010 values are unchanged, and the generated m4/m4a1/m2010 JSON files are byte-identical to before.
  - PISTOL and HEAVY hold untuned starting values.

## Adding a gun: one catalog entry, plus its model files and one `display_items.json` line
```java
public static final GunDefinition M17 = add(GunDefinition.of("m17", "M17 Pistol", GunFamilies.pistol()
        .cartridge(Cartridges.NATO_9X19).muzzleVelocity(373).rpm(180)
        .magazine(17).reloadSeconds(1.8, 2.3).damage(5.0f).mass(0.83)
        .sights(GunView.ironSights(6.75f, 13f)).sounds(SoundSets.PISTOL_9MM)
        .slots(OPTIC, MUZZLE, SIDE)));
```
The M4A1 shows the variant form: `M4.spec().toBuilder().fireModes(SEMI, AUTO).mass(3.6)` plus `.model("m4")`.

## AutoTest
- `AutoTest` is now the runner plus the script API (`at`, `cmd`, `shot`, `look`, `buildRange`, `cameraFromStand`, `finish`) and the catalog check.
- `AutoTestCore` is the old script with the same timings and screenshot names.
- `AutoTestTour` takes all its timings from each gun's spec. Per gun it saves `autotest_tour_<gun>_{1_hip,2_aim,3_fire,4_reload,5_third_person_side}` and logs `[autotest] tour <gun>: feed, loaded / after firing / after reload`. Without `-Pguns` it tours every gun.
- In `build.gradle`, `-Pscenario` / `-Pguns` go through `providers.gradleProperty` into `systemProperties`, which is safe with the configuration cache. Gradle re-configures when they change, then reuses the cache entry.

## Verification
- **Core run:** I compared all 43 screenshots with `runs/autotest_baseline` by pixel diff and looked at 01, 02, 04, 05a, 17, 18, 27, 28, 30, 32, 34 and 35. The gun renders, ADS, scopes, Gunsmith Table and HUD are effectively identical (01/02/27/28 mean diff ≤ 0.04). The remaining differences are random smoke, impacts, recoil and sway; a diff of two runs of the old code shows the same size of variation.
- **Log lines:** they match the baseline: scope at 6.5x is 76.39 px per 20 mil; at 20x it is 235.64 vs 235.64; mastery is 34 xp / level 0 after the zombie, then 4000 xp / level 5.
  - One intermediate run logged 4 xp. In that run the bullets only wounded the zombie and sunlight fire finished it, so it was hip-fire RNG, not a regression. The final run gave 34. That step of the core test depends on random spread and the zombie can die from fire, so it can occasionally log 4 xp.
- **Tour m4,m2010:** passes. m4 went 30 → 27 → 31 (the +1 chambered round); m2010 went 5 → 2 → 6.
- **TUBE feed and pellets:** checked with a temporary M590 catalog entry and 00-buck cartridge, which I then removed.
  - A temporary log showed 9 pellets per shot with the right mass, energy and pattern spread.
  - The tube went 8 → 6 → 9, which is 8 in the tube plus the chamber, shell by shell.
  - Not tested in game: the trigger interrupt during a tube reload, and BELT. Both are short code paths, but no belt or tube gun is registered yet.

## Files
- **New:**
  - `gun/` (all classes listed above)
  - `ballistics/ProjectileSpec.java`
  - `item/AmmoItem.java`
  - `client/AutoTestCore.java`, `client/AutoTestTour.java`
  - `src/main/resources/assets/modernarmory/view_classes.json`
  - `tools/modelgen/display.py`, `tools/modelgen/display_items.json`
  - `docs/ARCHITECTURE.md`
- **Removed:** `item/GunSpec.java`, `item/GunSpecs.java`, `item/FireMode.java`, `item/GunView.java`.
- **Modified:**
  - `ballistics/Cartridge.java`, `ballistics/Cartridges.java`
  - `entity/BulletEntity.java` (`fire()` now takes a `ProjectileSpec`)
  - `item/GunItem.java`, `item/GunAnimations.java`
  - `registry/ModItems.java`, `registry/ModCreativeTabs.java`
  - `server/GunHandler.java`
  - `mastery/MasteryHandler.java`
  - `client/ClientState.java`, `client/GunViews.java`, `client/GunHud.java`, `client/GunCrosshair.java`, `client/AutoTest.java`
  - `menu/GunsmithMenu.java`
  - import changes in `attachment/GunStats.java`, `attachment/AttachmentItem.java`, `client/ScopeOverlay.java`
  - `build.gradle`, `tools/modelgen/build.py`, `README.md`
- **Regenerated:** `display.py` rewrote the item model JSONs of the 25 new (unregistered) guns. They were armorygen placeholders; their hand-held framing now comes from their view class, and their inventory, item-frame and ground icons are fitted from the model's bounds.

## Not preserved / to know
- Item registration order changed (ammo is registered before guns). This has no visible effect. The creative-tab order is the same as before.
- The M2010 spec's unused `burstCount` is now the default 3 instead of 1.
- **For the next task:**
  - The M590 model in the temporary test: aiming (sight_y 7.5, eye_z 9.5 from models.json) leaves the view largely blocked by the gun's own geometry. Check its sights in the next task.
  - Calibre-specific attachments match one exact `Cartridge` (identity). The new shared suppressors (for example `suppressor_762` across several .30-cal cartridges) will need a calibre-group rule.
  - The shared red dot and holographic look oversized on pistols. That is model data, not something I changed.
- **Scratch folders to delete:** the tool refused my `rm`, so I moved screenshot sets aside instead. These folders can be deleted by hand: `runs/autotest_old_screens`, `runs/autotest_screens_before_core1`, `runs/autotest_core_after1`, `runs/autotest_core_final`, `runs/autotest_tour1` to `runs/autotest_tour4`.
- **Kept:** `runs/autotest_baseline` (the pre-refactor baseline) and `runs/autotest/screenshots` (the last tour run).

# Integrate
## TASK 2: the 25 new guns are integrated

`./gradlew build` passes. I only changed the root project. Under `versions/26.3` I wrote recipe JSONs and nothing else. I did not touch `tools/modelgen/guns/*.py`, and I did not use Fable.

The interrupted attempt had only left `ballistics/CaliberClass.java`. I kept it and wired it in.

**Tour (`-Pscenario=tour -Pguns=m17,m590,m249`):** no crashes. Models and animations show. The catalog check reports `28 guns, framing + lang + sounds + default attachments ok`, and the log has no missing-sound warnings.
- **m17:** magazine feed, 17 → 14 → 18 (+1 chambered). The sight picture is clean.
- **m590:** tube feed, 8 → 5 → 8, shell by shell. The ghost ring is big but you can see through it.
- **m249:** belt feed, 200 → 197 → 200, with no +1. ACOG 4x ADS works, and the feed cover opens with the optic on it.

**Core regression run:** the log values match the baseline (76.39 px and 235.64 px per 20 mil; mastery 34 xp / level 0, then 4000 xp / level 5). Screenshots 01/02/27/28/29/32/33 differ by ≤0.02 mean. The other differences come from sway, smoke and randomness.

### Architecture changes (all data-driven)
- **`ballistics/DamageModel`:** one damage scale for the mod: 8 × (E / 1557 J)^0.75, anchored on M855 at 880 m/s.
  - A gun's damage is derived from its cartridge and velocity unless `.damage()` is set. Only the M2010 keeps its explicit 20.
  - `BulletEntity` uses the same law at impact.
  - I removed `.damage(8)` from the ar15 family; the derived value for the M4 is exactly 8.0.
- **`Cartridge.caliberClass`:** attachments are matched by `CaliberClass`, no longer by exact cartridge. The .30 can fits 7.62 M80A1 / M118LR, 6.8 and .300 WM.
- **`gun/GunModels`:** reads the `att_<id>` bones from the geo file on the classpath, so it works on servers too. `AttachmentItem.fits(GunDefinition)` now requires slot + calibre class + the bone in the model, so nothing invisible can be fitted.
- **`gun/Support`:**
  - `SHOULDER`: a fitted bipod attachment deploys when crouched, as before.
  - `BIPOD`: integral bipod for the M249, M240 and M107.
  - `TRIPOD` (M2A1): crouched gives ×0.15 recoil. **Standing gives ×3 recoil and ×2.5 spread** (documented).
  - `GunHandler` and `GunCrosshair` use the same rules. M4/M2010 maths are unchanged.
- **`GunSpec.adsSeconds`:** per family (pistol 0.15, SMG 0.18, rifle 0.2, battle rifle 0.22, DMR 0.25, MG 0.35, anti-materiel 0.4, M2 0.45). `ClientState` steps on the game tick, and the M4/M2010 stay at exactly 4 ticks.
- **Sounds:** `gun/Report` enum (calibre bare/suppressed shot events) plus `SoundSets.forGun(id, ...)`.
  - The convention is `<id>_reload`, `<id>_reload_empty`, and `<id>_cycle` for manual actions. Tube guns use `<id>_reload` as the shell insert.
  - `ModSounds.mechanical(name)` registers these on demand, so a new gun needs no `ModSounds` edit.
  - The builder has `.report(...)`, which replaces `.sounds(...)` and vice versa, and `build(id)`.
- **Families:** added `battleRifle()` and `straightPullShotgun()`.
  - `pistol` and `smg` pre-fill 9x19 and their report; the shotgun families pre-fill 00 buck and the 12 ga report; `heavyMg` and `antiMateriel` pre-fill .50.
  - The MG family slots now include MUZZLE; `antiMateriel` is OPTIC+MUZZLE.
- **AutoTest catalog check:** now also checks every sound of each gun's set (`sound MISSING`) and every default attachment (`default attachment MISMATCH`).
- **Docs:** `docs/ARCHITECTURE.md` and `README.md` are updated.

### Tools and assets
- **`tools/soundgen/gun_sounds.py`** (new; `make_sounds.py` runs it):
  - Parameterised `Report` / `SuppressedReport` give 13 report events, 2–3 variants each.
  - The OOP kits are `MagKit` (release / charge / bolt), `BeltKit`, `ShellKit` (pump / bolt), and the cycles `BoltCycle`, `PumpCycle`, `StraightPullCycle`. They are authored on the same base timelines as `templates.py` and stretched to each gun's animation lengths from `models.json`.
  - It writes 55 mechanical sounds plus the `sounds.json` entries. Shot volumes are relative to m4_fire 0.45: 9 mm 0.4, suppressed 9 mm 0.3, 6.8/7.62/.300 0.5, .338 0.55, .50 0.6, 12 ga 0.5.
- **`tools/datagen/recipes.py`** (new): one table of all 57 recipes, written in both formats, with a conflict check. The existing recipes are reproduced with the same content.
- **`tools/texgen/make_textures.py`:** new `ammo_icons()` makes 8 icons. Shells are red hulls with brass heads; the .50 is one fat round.
- **`tools/modelgen/icons.py`:** now takes ids on the command line. The 5 new attachment icons and item models are done.
- **Ammo item models:** 8.
- **Lang:** 25 guns, 8 ammo, 5 attachments, plus `subtitles.modernarmory.cycle`.
- **Display:** `display.py --check` says all item models are up to date. The previous agent had already generated the fitted gui/ground/fixed transforms.

### Guns
Order in the catalog and creative tab: pistols, SMGs, 5.56, battle rifles, DMRs, snipers, shotguns, MGs, anti-materiel. Ammo is in calibre order. Masses are as carried (loaded, with issued optic / suppressor). The "dmg" figure is derived per projectile; the "kick" figure is standing, unaimed, in degrees.

- **m17:** 9x19, semi, 17-round mag, 373 m/s, 180 rpm, 1.8/2.3 s, 1.03 kg, dmg 3.7, kick 0.71
- **glock17:** 9x19, semi, 17-round mag, 371 m/s, 1.7/2.2 s, 0.91 kg, slots MUZZLE/SIDE
- **glock19:** 9x19, semi, 15-round mag, 365 m/s, 1.7/2.2 s, 0.85 kg
- **apc9:** 9x19, semi/auto, 30-round mag, 368 m/s, 1080 rpm, 2.3/2.9 s, 3.1 kg, red dot fitted
- **mpx:** 9x19, semi/auto, 30-round mag, 400 m/s, 850 rpm, 3.2 kg, red dot fitted (no irons)
- **m16a4:** 5.56, semi / 3-round burst, 30-round mag, 945 m/s, 800 rpm, 2.4/2.9 s, 4.0 kg, dmg 8.9
- **hk416:** 5.56, semi/auto, 884 m/s, 850 rpm, 2.3/2.8 s, 4.0 kg, all six slots
- **mcx:** 5.56, semi/auto, 810 m/s, 850 rpm, 3.5 kg, red dot fitted
- **m7:** 6.8x51, semi/auto, 20-round mag, 915 m/s, 750 rpm, 2.6/3.2 s, 5.8 kg, ACOG + suppressor_762 fitted, dmg 14
- **xm8:** 6.8x51, semi/auto, 25-round mag, 884 m/s, 750 rpm, 2.5/3.1 s, 5.4 kg, ACOG + suppressor_762
- **mk17:** 7.62 M80A1, semi/auto, 20-round mag, 870 m/s, 600 rpm, 2.5/3.0 s, 4.2 kg, dmg 13.7, kick 2.3
- **mk20:** 7.62 M118LR, semi, 20-round mag, 783 m/s, 1.0 MOA, 2.5/3.3 s, 6.2 kg, scope fitted
- **m110:** M118LR, semi, 20-round mag, 783 m/s, 1.0 MOA, 7.9 kg, scope + suppressor_762
- **m110a1:** M118LR, semi, 20-round mag, 750 m/s, 1.5 MOA, 7.4 kg, scope + suppressor_762, STOCK slot
- **mk13:** .300 WM, bolt, 5-round mag, 869 m/s, 1.5 s cycle, 0.6 MOA, 3.5/4.5 s, 7.3 kg, scope, dmg 20.3
- **mk21:** .338 LM, bolt, 10-round mag, 905 m/s, 1.5 s cycle, 1 MOA, 8.0 kg, scope, dmg 23.7, kick 5.2
- **mk22:** .338 NM, bolt, 10-round mag, 818 m/s, 1.5 s cycle, 0.8 MOA, 8.5 kg, scope, dmg 23.4
- **m590:** 12 ga 00 buck (9 pellets, 95 MOA pattern), pump, tube 8+1, 400 m/s, 1.0 s cycle, 0.65 s per shell / 1.7 s empty, 3.8 kg, no slots, 2.2 dmg per pellet
- **benelli_m4:** 00 buck, semi, tube 7+1, 395 m/s, 180 rpm, 0.6/1.5 s, 4.1 kg, OPTIC
- **m26mass:** 00 buck, straight-pull, 5-round box, 335 m/s, 1.5 s cycle, 2.8/3.8 s, 2.5 kg, OPTIC/STOCK
- **m249:** 5.56, auto, 200-round belt (no +1), 915 m/s, 850 rpm, 9/7 s, 10 kg, integral bipod, ACOG
- **m250:** 6.8, auto, 100-round belt, 945 m/s, 800 rpm, 7/6 s, 9.5 kg, ACOG + suppressor_762 + bipod attachment
- **m240:** 7.62 M80A1, auto, 100-round belt, 935 m/s, 700 rpm, 9/7 s, 14.5 kg, integral bipod, ACOG
- **m2a1:** .50 BMG, auto/semi, 100-round belt, 887 m/s, 550 rpm, 8/10 s, 38.2 kg, TRIPOD, no slots, dmg 47.7, kick 7.0 (crouched ×0.15, standing ×3)
- **m107:** .50 BMG, semi, 10-round mag, 853 m/s, 4.0/5.5 s, 15 kg, integral bipod, scope + muzzle_brake_50 (×0.5 recoil), dmg 45

Eye z: for guns whose models.json eye_z belongs to a magnified optic, I used a cheek-weld value for irons / reflex sights. These are m7, xm8, m250, mk20, m110, m110a1, m249, m240 and m107, with values between 9 and 11 taken from the model notes and stock positions. The scope's own eye position still comes from its locator plus eye relief.

### Cartridges (8 new)
| id | load | caliber class |
|---|---|---|
| 9x19 | M882 8.04 g, G7 0.075 | PISTOL_9MM |
| 68x51 | XM1186 7.84 g, G7 0.215, fragments >600 m/s | RIFLE_30 |
| 762x51 | M80A1 8.42 g, G7 0.195, fragments >580 m/s | RIFLE_30 |
| 762_m118lr | 11.34 g, G7 0.243 | RIFLE_30 |
| 12ga_00 | 9 × 3.49 g pellets, G7 0.022, 95 MOA | GAUGE_12 |
| 338lm | 16.2 g, G7 0.322 | MAGNUM_338 |
| 338nm | 19.44 g, G7 0.384 | MAGNUM_338 |
| 50bmg | M33 42.8 g, G7 0.32 | BMG_50 |

Ammo recipes give 16 rounds of 9 mm, 8 of 6.8, 12 of 7.62, 10 of M118LR, 6 shells of 12 ga, 4 of each .338 and 2 of .50.

### New attachments
All are MUZZLE slot, matched by calibre class.

| id | fits | effect |
|---|---|---|
| suppressor_9mm | 9 mm | suppressed, recoil 0.9 |
| suppressor_762 | .30 class | suppressed, recoil 0.85, ADS 0.94 |
| compensator_762 | .30 class | recoil 0.78 |
| suppressor_338 | .338 | suppressed, recoil 0.8, ADS 0.9 |
| muzzle_brake_50 | .50 | recoil 0.5; its flash locator `flash_muzzle_brake_50` is used |

The existing suppressor_556, compensator_556 and pmag_40 are now RIFLE_556 class; suppressor_300 is RIFLE_30.

### Model issues for the modeller (from the tour and models.json; not fixed)
- **m2a1:** the hands are on the spade grips, not at the standard grip point, and HEAVY framing is untuned, so first person probably needs a per-gun offset. The tripod is part of the model, so it shows everywhere. The gun is about 50 units long.
- **HEAVY framing in general (m249 seen):** the ACOG looks very large and close while lowering from ADS. View-class tuning is needed.
- **Pistols:** the shared red_dot and holographic are rifle-sized and look oversized on the M17. The PEQ-15 is as big as the grip. There is no pistol RDS or light.
- **m107:** the front sight stands in front of the scope objective. The integral bipod's fold direction is uncertain.
- **m250:** the feed cover opens front-up (template limitation; the real one opens sideways). The non-empty reload (7 s) is longer than the empty one (6 s), as the spec gives; the M249 and M240 are the same at 9 / 7 s.
- **xm8:** the bipod legs reach past the bare muzzle. The left fist touches the magazine well.
- **Stand-ins:** M157 / M145 → ACOG, SLX → suppressor_762, the various scopes → the Leupold sniper_scope.

### Known gaps
- I toured only m17, m590 and m249. The other 22 guns (framing, eye_z, sight pictures, PISTOL/HEAVY view classes) are for the next QA stage. I have not listened to any of the generated sounds; I only checked that they exist and are timed to the templates.
- Attachment `adsSpeed` multipliers are still unused. This was already the case; ADS time is now per gun only.
- The M2A1 has no movement slowdown; only the standing penalty applies.
- **26.3:** the new recipes reference items the 26.3 Java does not register yet. They will be skipped with a log error until the port catches up. 26.3 also needs the Java port of everything above.
- The M4 / M2010 still use their own recorded sound sets, and m4.py / m2010.py are not on templates.py.

### Files
**New:**
- `src/main/java/com/modernarmory/ballistics/DamageModel.java`
- `src/main/java/com/modernarmory/gun/{Support,Report,GunModels}.java`
- `tools/soundgen/gun_sounds.py`
- `tools/datagen/recipes.py`

**Modified Java:**
- `gun/{Guns,GunSpec,GunDefinition,GunFamilies,SoundSets}`
- `ballistics/{Cartridge,Cartridges}`
- `attachment/{Attachment,Attachments,AttachmentItem}`
- `registry/ModSounds`, `server/GunHandler`, `entity/BulletEntity`
- `client/{ClientState,GunCrosshair,AutoTest}`, `menu/GunsmithMenu`

**Assets and data:**
- `sounds.json` plus 89 new .ogg files
- `lang/en_us.json`
- 8 ammo textures and item models; 5 attachment icons and item models
- recipes in both directories

**Tools:** `tools/soundgen/make_sounds.py`, `tools/texgen/make_textures.py`, `tools/modelgen/icons.py`

**Docs:** `docs/ARCHITECTURE.md`, `README.md`

**Screenshots:** `runs\autotest\screenshots`. The core set is current. The tour shots for m17, m590 and m249 are from this run; the other tour files there are older.


# Fix
Modern Armory 1.21.1 QA fix pass: report

All major issues are fixed except the pistol arm geometry, which is a model-stage problem. I fixed all the cheap minor issues too. Verification passed: the full 28-gun tour, a re-run of the changed guns and the core scenario. The build passed and the jar is installed, byte-identical (checked with cmp). I did not touch versions/26.3 or tools/modelgen/guns/*.py, and I did not use Fable.

## Issues fixed (one line each)
1. **PISTOL hip framing (major):** view_classes.json PISTOL first-person translation changed from [-4.48,0,2] to [-6.6,1.5,2], and hip_yaw went from 5 to 3. The pistols now sit about 100-150 px right of centre, clear of the hotbar, with the forearm visible from the bottom-right. ADS sight pictures are unchanged. display.py regenerated m17/glock17/glock19.json, and the `_note` is updated.
2. **PISTOL third person (major):** thirdperson translation changed from [0,-1.5,1] to [0,-5,-3]. The grip is now in the crossbow-hold fists and the muzzle sits past them.
3. **Pistol reload under the HUD (minor):** fixed as a result of 1. The 4_reload frames are now clear of the ammo counter.
4. **M2A1 standing recoil (major):** Support.TRIPOD standing recoil changed from x3 to x1.25, about 7 degrees per aimed shot, the same as the M107. The 3_fire horizon is now level.
5. **M2A1 tripod always rendered (major):** added a data-driven `PartVisibility` enum (FOLDS_UNDER_OPTIC / FIRST_PERSON_ONLY), set with `GunSpec.Builder.part(bone, rule)` and applied in GunRenderer.applyVisibility. `heavyMg()` sets tripod = FIRST_PERSON_ONLY. Third person, icons, dropped items and item frames now show no tripod, and the first-person hip view is unchanged (pixel diff 0.003).
6. **Folding front BUIS raised under the scope (minor):** `battleRifle()`, `dmr()` and `antiMateriel()` set front_sight = FOLDS_UNDER_OPTIC, which covers mk17, mk20, m110 and m107. Fixed-sight guns (M4, M16A4, M249 and so on) are untouched. Verified: there is no post in front of the scope in the m110/mk20 hip shots.
7. **M2A1 aimed view crowded by the grips (minor):** eye_z changed from 6 to 8 in Guns.M2A1, and models.json m2a1 eye_z and its notes are updated. The grips and fists now cover far less of the screen.
8. **Attachment aim speed never applied (minor):** added `GunStats.adsSeconds()` (adsSeconds / adsSpeed) and a `GunStats.of(spec, attachments)` overload. ClientState.aimStep uses it.
9. **Scope power the same on every gun (minor, realism):** added `ScopePower` and `GunSpec.Builder.issuedScopePower(min, max)`. GunViews applies it only while the factory-fitted optic is on the gun. Values: M110 3.5-10, M110A1 3-20, Mk 20 3.5-15, Mk 13/21/22 5-25, and M107 4.5-14 (its real Leupold 4.5-14x, beyond what QA listed). The M2010 stays at 6.5-20. Verified: mk20 opens at 3.5x and the M2010 at 6.5x.
10. **Tour 3_fire taken mid-blend (m26mass, m250, m7, xm8, m107, m249, m240 and others, minor):** AutoTestTour now times the 3_fire shot from the gun's effective ADS time (factory attachments included), and the reload starts after it. Verified: every gun's 3_fire is fully at the hip with the impacts visible.
11. **Third-person tilt (minor):** AutoTestTour resets head, body and look to 180/0 before the side shot. Verified: all 28 guns are level, matching the M4.
12. **M590 smoke hiding the pattern (minor):** GunHandler sends 1 smoke particle instead of 2. At the hip it spawns at the gun-hand side and lower (new helper `muzzleSmoke`). Aimed shots keep the old position under the line of sight.
13. **Extra harness fix, not in the QA list:** in the tour, single-shot guns now hold the trigger for 2 ticks. A 1-tick tap sometimes reached the server as press and release in the same tick and was lost, so round counts varied between runs. All tour round counts are now correct.
14. **Doc updates:** docs/ARCHITECTURE.md now covers PartVisibility, ScopePower, the aim-speed rule, the new tripod factor and the tuned PISTOL class. The Guns class javadoc mentions issuedScopePower.

## Not fixed, and why
- **No dedicated optic or suppressor models yet.** Four minor asset issues need new models: an LPVO for the M7/XM8/M250, a micro red dot for the MCX/MPX, a short 6.8 can for the XM8 and a short 7.62 can for the M110A1. The ACOG, red_dot and suppressor_762 stand in until the model stage builds them.
- **Scope off the bottom-right corner at the hip (mk20, m110, m110a1, cosmetic):** the RIFLE framing is tuned on the M4 and should stay as it is. Moving the optic mount is a model-stage change.
- **Optional HEAVY third-person tweak:** not applied, because it would change m249, m240, m107 and m250 too. With the tripod hidden, the M2A1 spade grips sit at chin height rather than inside the head, which looks acceptable.
- **Pistol smoke when aiming:** the puff still forms near the target in pistol 3_fire frames (1 particle per shot now). This is realistic for aimed fire, so I left it.

## Model issues for the modeller
1. **Pistol arm template (striker_pistols.py / templates.py, shared by m17, glock17 and glock19):** the right hand does not wrap the grip. Suggested fix: right_arm pivot about [0,4.25,1.0] instead of [0,3,1.5], with the rotation kept. The support hand should cup the fist. Re-check 1_hip, 2_aim and 3_fire with the new PISTOL framing.
2. **Pistol reload template:** if the magazine swap still reads poorly, reduce the root drop and roll. With the new framing the reload is already clear of the HUD.
3. **New attachment models:** `lpvo_m157` (1-8x, with a sight_lpvo_m157 locator) for M7/XM8/M250; `micro_red_dot` (about 1.5 units wide, with a sight locator) for MCX/MPX; `suppressor_68_short` (heat-shield sleeve) for XM8; `suppressor_762_short` (about 60% of suppressor_762, like the SRM6) for M110A1.
4. **platforms_762.py:** consider moving the optic mount forward about 1.5 units (for example to (0,7,-3.5)) so the mk20/m110/m110a1 scope eyepiece stays on screen at the hip. Re-check the aim eye position afterwards.

## Verification
- **Build:** `./gradlew build` OK. Catalog check: "28 guns, framing + lang + sounds + default attachments ok".
- **Tour:** full tour of all 28 guns, then a re-run of m17, glock17, glock19, mpx, m7, mk20, m2010 and m250 after the final pistol and tap changes. Round counts are all correct (for example m7 20→17→21, m250 100→97→100, m2010 5→2→6). Checked from contact sheets:
  - pistol hip, aim, reload and third person
  - every 3_fire at the hip
  - every third-person shot level
  - M2A1 without the tripod in third person
  - BUIS folded on the M110/Mk 20
  - Mk 20 at 3.5x and M2010 at 6.5x
- **Core scenario against runs/autotest_baseline:** all 43 screenshots are present. Scope log lines match exactly ("6.5x … 20 mil = 76.387 px", "~20x … 235.64 px"), and mastery matches (34 xp at level 0, then 4000 xp at level 5).
  - Most frames differ by a mean of 0.0-1.5.
  - 04 (M4 hip burst) differs because the smoke is smaller and moved, as intended.
  - 16, 19, 20 and 24a-c (recoil, eye box) vary randomly, as in earlier runs.
  - **18a/18b changed in timing:** the M2010's scope and suppressor now slow ADS from 0.2 s to 0.28 s, so the scope-in starts one tick later. I moved these two frames in AutoTestCore +1 tick (428/429 → 429/430) so they capture the same point in the blend. They match the baseline again (mean 2.0 / 1.3).
- **Scratch artefacts:** screenshot sets and contact sheets are in <scratch>. That folder has `old_shots` (the pre-fix screenshots), `tour1` and `tour2` (the new tour runs) and `sheets` (contact sheets).

## Install status
INSTALLED. The game was checked first: no java.exe or javaw.exe process had a command line containing modern-armory-1.21.1. Copied build/libs/modernarmory-1.0.0.jar to %APPDATA%\.minecraft\instances\modern-armory-1.21.1\mods\modernarmory-1.0.0.jar, and cmp reports the two files identical. Nothing else in that folder was touched.

## Files changed (for the 26.3 port)
**Changed in this run (paths relative to the repository root):**
- src/main/java/com/modernarmory/gun/PartVisibility.java (NEW)
- src/main/java/com/modernarmory/gun/ScopePower.java (NEW)
- src/main/java/com/modernarmory/gun/GunSpec.java: new record components `parts` and `issuedScopePower`, builder methods `part(...)` and `issuedScopePower(...)`, and a validation
- src/main/java/com/modernarmory/gun/GunFamilies.java
- src/main/java/com/modernarmory/gun/Support.java
- src/main/java/com/modernarmory/gun/Guns.java
- src/main/java/com/modernarmory/attachment/GunStats.java
- src/main/java/com/modernarmory/client/ClientState.java
- src/main/java/com/modernarmory/client/GunViews.java
- src/main/java/com/modernarmory/client/renderer/GunRenderer.java
- src/main/java/com/modernarmory/client/AutoTestTour.java
- src/main/java/com/modernarmory/client/AutoTestCore.java
- src/main/java/com/modernarmory/server/GunHandler.java
- src/main/resources/assets/modernarmory/view_classes.json
- src/main/resources/assets/modernarmory/models/item/m17.json, glock17.json, glock19.json (regenerated by display.py)
- docs/firearms/models.json (m2a1 eye_z and notes)
- docs/ARCHITECTURE.md

**Also newer than the last versions/26.3 sync (11:26), from the earlier sound stage, not this run:**
- src/main/java/com/modernarmory/client/AutoTest.java
- src/main/resources/assets/modernarmory/sounds.json
- src/main/resources/assets/modernarmory/sounds/*.ogg (all the new per-gun reload, cycle and calibre fire sounds)
- tools/soundgen/gun_sounds.py
- tools/soundgen/make_sounds.py

There is no git history, so I cannot show changes from before that sync. Port any earlier refactor files from the previous stage's own list.