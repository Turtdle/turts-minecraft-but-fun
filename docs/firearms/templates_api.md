## Result

Created `tools\modelgen\templates.py` and extended `tools\modelgen\attachments.py` (5 new muzzle devices; existing functions untouched; `armorygen.py` not changed). No Java/Gradle/JSON assets touched.

Verification (2 build iterations, all into the scratchpad):
- Demo `<scratch>\tmpl_demo.py` builds 6 guns (auto rifle, turn-bolt, straight-pull, pump shotgun, belt-fed MG, pistol) that together call every template; previews are in `...\scratchpad\previews\`.
- I looked at the pistol and MG arm previews, the .50 brake preview, and 7 animation strips (rifle/pistol/M26-style/MG/pump `reload_empty`, pump `reload`, bolt and pump `fire`). Arms and motion look right at strip resolution. The other 17 strips, the four other new muzzle-device previews and the rifle / shotgun_pump still previews were built but not viewed.
- Regression against the originals: `T.arms(..., "rifle")` reproduces the M4 and M2010 arm bones exactly; `T.idle` and `T.fire_auto` give byte-identical JSON to the M4; `T.fire_bolt` matches the M2010 `fire` except for one redundant trailing zero key. `m4.py` and `m2010.py` still build with the edited `attachments.py`.
- Not launched in game.

## Architecture (for mod-wide changes)
- **Module constants**: `BORE_Y`, `ARM_W`/`ARM_LEN` (3/26), `PITCH_UP`, `ROLL_RIGHT`, easing names, bone-name constants, and the `MOUNTS`, `SLOT_BONES`, `DEFAULT_PARTS` tables.
- **`ArmStyle`** (frozen dataclass) and **`ARM_STYLES`** registry: arm rotations, finger, and per-hold reload choreography (cant, mag drop, grab height, bolt-catch slap). A new hold is one new entry.
- **`Rig`**: what `arms()` built (`right_hand`, `left_hand`, `style_name`, `style`), stored as `m.rig`. Animation templates read hand positions from it, so `arms()` must be called first.
- **`Clip`**: wraps an armorygen `Animation`. Key times are authored on a base timeline and scaled by `length/base`; bones that are `None` or absent are skipped (this is what makes a bone optional); `need()` raises for required bones; `done()` raises if any track's first or last key is off the rest pose.
- **`HandCycle`** (dataclass `side, handle, knob, lift, pull, also`): a hand leaves its grip to work a turn-bolt, or with `lift=0` a straight pull / charging handle. Shared by `fire_bolt`, `fire_straight_pull`, `reload_mag` and `reload_belt`.

## API reference (`import templates as T`)
Units: model units (1 ≈ 3.35 cm), degrees, seconds. Muzzle −Z, +X right, bore y ≈ 5.5. `length=None` means the base length.

**Setup**
- `arms(m, right_hand, left_hand=None, style="rifle") -> Rig` builds `right_arm` / `left_arm` (children of root, pivot at the hand).
  - `rifle`: exactly the M4/M2010 setup, R (135,40,0), L (128,−36,0), trigger finger.
  - `pistol`: R (118,30,0), L (116,−34,0); `left_hand=None` cups at right hand + (−1, −0.75, −0.25).
  - `shotgun_pump`: rifle arms, left hand placed on the pump.
  - `mg`: L (116,−48,0); put `left_hand` about 1 unit above the feed cover / handguard top.
- `mounts(m, optic=None, muzzle=None, underbarrel=None, side=None) -> dict` creates `optic_mount` (child of `receiver`), `muzzle_mount` (child of `barrel`), `underbarrel_mount` and `side_mount` (children of `handguard`). Parent bones must exist.
- `default_part(m, slot, pivot) -> Bone` returns the bone to add boxes to: `"muzzle"` gives `muzzle_default` (needs `barrel`); `"stock"` gives `stock_default` under `stock`; `"magazine"` gives `magazine_default` under `magazine`. The `stock` / `magazine` parent is created if missing. Constants: `T.MUZZLE_DEFAULT`, `T.STOCK_DEFAULT`, `T.MAGAZINE_DEFAULT`.
- `attach(m, *ids)` adds shared attachments by id to the right mount / `stock` / `magazine` bone at that bone's pivot.
- `locators(m, muzzle_flash, shell_eject, sight_line, **extra)` creates the three game locators plus extras such as `load_port=(...)`.
- `part_bounds(m, bone) -> (lo, hi)` gives rest bounds of a bone and its children, ignoring `att_*`.

**Animations** (names `idle`, `fire`, `reload`, `reload_empty`; each returns the `Animation`)
- `idle(m, length=3.0)`: looping breathing sway. Bones: root.
- `fire_auto(m, recoil=0.8, bolt_bone="bolt", bolt_travel=2.0, length=0.07, rise=2.0)`: kick plus one bolt cycle. Optional: `bolt_bone`, `trigger`.
- `fire_bolt(m, bolt_bone="bolt_handle", length=1.30, lift_deg=70, pull=3.0, recoil=1.5, rise=4.0, knob=None)`: the M2010 cycle with the right hand working the bolt. Needs `right_arm` and `bolt_bone` (pivot on the bolt axis, handle toward +X; knob defaults to 2.5 right of the pivot). Optional: `trigger`.
- `fire_pump(m, pump_bone="pump", travel=3.0, length=0.9, recoil=1.5, rise=5.0, bolt_bone="bolt")`: kick, then the left hand and pump rack back and forward. Needs `left_arm` and `pump_bone`. Optional: `bolt_bone`, `trigger`.
- `fire_straight_pull(m, bolt_bone="bolt_handle", pull=3.0, length=0.9, recoil=1.5, rise=4.0, hand="right", knob=None)`: for the M26 MASS; `hand="left"` for a left-side handle. Needs the chosen arm and `bolt_bone`.
- `fire_pistol(m, slide_bone="slide", travel=2.0, length=0.1, recoil=0.5, flip=5.0)`: slide cycle plus muzzle flip about the model origin. Optional: `slide_bone`, `trigger`.
- `fire_revolver(m, length=0.25, recoil=1.0, flip=14.0, *, cylinder_bone="cylinder", hammer_bone="hammer", step_deg=60, cock_deg=40)`: double-action revolver: hammer flick, cylinder turns one chamber (snaps back to 0 in the last 2 ms, invisible on a cylinder that looks the same every `step_deg`), muzzle flip. Needs `cylinder_bone` (pivot on its axis). Optional: `hammer_bone`, `trigger`.
- `reload_mag(m, mag_bone="magazine", length=None, empty=False, bolt_action=False, *, style=None, bolt_bone=None, empty_action=None, lift_deg=None, pull=None, hand="right", knob=None, also=(), grab=None, catch=None)`: the M4 left-hand mag swap. Needs `left_arm` and `mag_bone`.
  - Base lengths: 2.2; empty 2.6; empty with a hand cycle 3.0.
  - `style` defaults to the `arms()` style; `pistol` drops the mag out of the grip with the palm under the base plate.
  - `empty_action="bolt_release"` (default): `bolt_bone` (default `bolt`, pistol `slide`) is held back `pull` (default 2) and snaps home; rifle holds also slap the bolt catch at `catch`.
  - `empty_action="cycle"`: a `HandCycle` on `bolt_bone` (default `bolt_handle`) with `lift_deg` (0 = straight pull / charging handle), `pull` (default 3), `hand`, `knob`, `also`.
  - `bolt_action=True` is shorthand for cycle with a 70° lift.
- `reload_belt(m, cover_bone="feed_cover", box_bone="ammo_box", length=None, empty=False, *, open_deg=65, charge_bone="charging_handle", charge_pull=3.0, charge_hand="right", charge_also=("bolt",), knob=None)`: cover up, box swap, belt laid on the tray, cover slammed; empty adds a charging-handle pull. Base 4.2 / 5.0.
  - Needs `left_arm`, `cover_bone` (pivot = hinge at the rear of the cover) and `box_bone`; make the visible belt a child of the box.
  - Empty also needs `charge_bone` and the arm of `charge_hand`.
- `reload_shell(m, length=None, empty=False, *, pump_bone="pump", bolt_bone="bolt", travel=3.0, port=None, eject=None, shell_bone="shell", chamber_shell_bone="shell_chamber")`: needs `left_arm`.
  - `empty=False` writes `reload`: one shell into the loading port, base 0.8 s, meant to be looped per shell. `port` defaults to locator `load_port`, else (0, 3.5, −3).
  - `empty=True` writes `reload_empty`: action opened, shell dropped into the ejection port, action closed; base 1.7 s. Uses the pump if `pump_bone` exists, otherwise holds `bolt_bone` back and releases it. `eject` defaults to locator `shell_eject`.
  - Optional `shell` / `shell_chamber` bones are modelled at rest hidden inside the gun and ride in the hand during the animation.
- `reload_revolver(m, length=None, empty=False, *, rod_tip, seat, clear, open_deg=95, eject_travel=1.0, cylinder_bone="cylinder", crane_bone="crane", ejector_bone="ejector", rounds_bone="rounds", speedloader_bone="speedloader", latch_bone="latch", tip=(32, -14))`: swing-out revolver with a speedloader, base 2.6 s, the same choreography for `reload` and `reload_empty` (`empty` only picks the name). Latch, cylinder out to the left on the crane, muzzle up, ejector rod pushed and cases fall, muzzle down, speedloader in, cylinder shut.
  - Needs `left_arm`, `crane_bone` (pivot = hinge, +Z rotation opens to −X), `cylinder_bone` (child of the crane) and `ejector_bone`; `rod_tip` = front end of the ejector rod.
  - Optional `rounds` (six cartridges at rest in the chambers) and `speedloader` (at rest hidden inside the cylinder, pivot at its knob; `seat` = +Z travel to seated, `clear` = cartridge length), both children of the cylinder; optional `latch`.

## New attachment ids (all slot `muzzle`, registered in `att.SLOTS`)
| id | function | size (long × across) | locator |
|---|---|---|---|
| `suppressor_9mm` | `add_suppressor_9mm` | 5.2 × 1.75 | none |
| `suppressor_762` (.30 / 7.62 / 6.8 / .300 BLK) | `add_suppressor_762` | 6.75 × 2.25 | none |
| `suppressor_338` | `add_suppressor_338` | 8.75 × 2.5 | none |
| `compensator_762` | `add_compensator_762` | 2.75 long | `flash_compensator_762` |
| `muzzle_brake_50` | `add_muzzle_brake_50` | 4 long × 4 wide | `flash_muzzle_brake_50` |

## Caveats
- **Java registration**: the five new attachment ids and their `flash_*` locators exist only in the Python models; the Java side still has to register them.
- **Bolt locked open**: the M4's `reload_empty` starts with the bolt already back, which breaks the rest-pose rule. The templates open the bolt/slide in the first 0.05 s instead and hold it until release (same in the non-pump `reload_shell(empty=True)`).
- **Reload timings are not byte-identical to the originals**: `reload_mag` uses the M4 timeline for every gun (the M2010's slightly slower swap is reached via `length=2.6` / `3.2`), adds a "mag clears the well" key, and computes the grab point from the magazine's bounds. Pass `grab=` to pin it.
- **`m4.py` / `m2010.py` not migrated**: they still carry their own arm and animation code, so a mod-wide change in `templates.py` will not reach them until they are ported.
- **`"step"` easing**: the optional shell bones use it for the hidden teleport into the hand. It is in armorygen's `EASINGS` but I have not confirmed it in game.
- **Per-gun tuning**: `fire` length must stay below the shot interval (`fire_bolt` / `fire_pump` / `fire_straight_pull` should equal it). The pistol and MG arm rotations are judged from previews only.
- **Call order**: `arms()` and all bones before the animation templates; `mounts()` before `attach()`. `mounts()` requires `receiver` / `barrel` / `handguard` for the slots requested, so a pistol can pass only `muzzle=`.
- **Leftovers**: the scratchpad also holds `regress.py`, `assets\` and `assets_regress\` from verification; nothing was written into the mod's assets or `tools\modelgen\previews`.
