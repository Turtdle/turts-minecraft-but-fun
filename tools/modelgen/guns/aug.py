"""Steyr AUG A1 - Austrian 5.56x45 mm bullpup assault rifle, translucent 30-round magazine, ~750 rpm.

Same scale and conventions as the M4 / MP5 / FAMAS (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 790 mm = 23.6 units (modelled z -12.1 .. 11.5). Barrel 508 mm = 15.2 units;
it starts BEHIND the grip (bullpup), 7.35 units of it stick out of the receiver.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: the one-piece green polymer stock (butt, magazine well, pistol grip and the big
full-hand trigger guard are one moulding), the dark aluminium receiver in front of / on top of
it with the INTEGRAL 1.5x optic cast on: a long sighting tube on two webs that doubles as the
carry handle, the cocking handle on the LEFT of the receiver under that tube, the slim barrel
with its gas cylinder on the right and the vertical foregrip folded down from the barrel collar,
the smoky see-through magazine BEHIND the grip, the ejection port beside the shooter's cheek,
and a closed birdcage flash hider.

Slots: MUZZLE only. The optic is part of the gun (bone `scope`, child of `receiver`): a hollow
1.5-wide tube with a 1 x 1 window, optical axis at SIGHT_Y; the `sight_line` locator sits on
that axis at the rear face of the eyepiece (the Java side treats it as a fixed 1.5x scope).

Arms, mounts, attachments and all animations come from templates.py. The bullpup hand detours
(the left hand swings out round the grip to the magazine behind it) are added here, like the
FAMAS does.
"""
import math

import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "aug"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
STOCK_BOTTOM_Y = 3.5       # underside of the stock at the magazine well
RECV_BOTTOM_Y = 4.75       # the receiver sits on the stock's trigger housing
TOP_Y = 6.75               # top of the receiver and of the stock
TUBE_Y0, TUBE_Y1 = 7.5, 9.0  # the sighting tube, outside (0.75 of daylight under it)
SIGHT_Y = 8.25             # optical axis of the integral 1.5x optic
MUZZLE_Z = -12.1           # front of the flash hider
BARREL_END_Z = -11.1       # muzzle mount
RECV_FRONT_Z = -4.75       # front face of the receiver (barrel collar in front of it)
GUARD_FRONT_Z = -2.75      # front strut of the full-hand trigger guard
STOCK_FRONT_Z = 2.5        # where the stock rises to full height (dark receiver in front of it)
SCOPE_FRONT_Z = -3.75      # objective end of the sighting tube
SCOPE_REAR_Z = 4.0         # rear face of the eyepiece
MAGWELL_FRONT_Z = 4.25     # the magazine well is BEHIND the pistol grip
MAGWELL_REAR_Z = 6.75
STOCK_REAR_Z = 11.0        # rear face of the stock (the butt pad sits behind it)
BUTT_Z = 11.5              # rear face of the butt pad
FOREGRIP_Z = -5.5          # z centre of the vertical foregrip

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, STOCK_BOTTOM_Y, 5.5)    # bottom centre of the magazine well
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
CHARGE_Z = -3.5                         # z centre of the cocking handle at rest
CHARGE_Y = 6.3
CHARGE_PIVOT = (0, CHARGE_Y, CHARGE_Z)
CHARGE_KNOB = (-1.35, CHARGE_Y, CHARGE_Z)   # knob centre, LEFT side
CHARGE_PULL = 2.75                      # travel of the handle in its slot
SIGHT_LINE = (0, SIGHT_Y, SCOPE_REAR_Z)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                      # centre of the pistol grip (same as the M4 / FAMAS)
L_HAND = (0, 3.0, FOREGRIP_Z)           # round the vertical foregrip

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("suppressor_556", "compensator_556")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.08         # 750 rpm -> 0.08 s between shots
RECOIL = 0.55
MUZZLE_RISE = 1.5
BOLT_TRAVEL = 1.5
RELOAD_LENGTH = 2.6
RELOAD_EMPTY_LENGTH = 3.1  # magazine swap, then the left hand pulls the cocking handle
HAND_DETOUR = (-3.5, -0.5, 0.0)   # the left hand swings out to the LEFT round the grip / right hand


def _materials(m: Model) -> None:
    m.material("aug_green", (84, 94, 66), 6, 0.80)      # the olive polymer stock
    m.material("aug_green_dark", (70, 79, 55), 5, 0.80)  # grip panels / port cover, a shade darker
    m.material("recv_grey", (44, 45, 50), 5, 0.74)      # anodised receiver and optic housing
    m.material("scope_black", (32, 32, 36), 4, 0.76)    # the sighting tube
    m.material("black_poly", (36, 36, 39), 4, 0.80)     # foregrip, small polymer parts
    m.material("barrel_steel", (58, 60, 66), 6, 0.72)
    m.material("bolt_steel", (120, 122, 128), 7, 0.72)  # bolt seen in the ejection port
    m.material("phosphate", (26, 26, 30), 4, 0.78)      # flash hider, controls
    m.material("mag_smoke", (82, 84, 82), 5, 0.82)      # the smoky translucent magazine body
    m.material("mag_rounds", (126, 110, 74), 8, 0.86)   # the rounds showing through it
    m.material("port", (14, 14, 16), 2, 0.9)            # ejection port, slots
    m.material("mark_red", (176, 38, 34), 3, 0.9)       # safety button: fire side


def _stock(m: Model) -> None:
    """The one-piece green stock: trigger housing, grip root, magazine well, butt. It carries
    the mount parent bone names the rest of the mod expects: everything fixed is `receiver`."""
    rc = m.bone("receiver", pivot=(0, 5, 0))
    full = TOP_Y - STOCK_BOTTOM_Y
    rc.box((-1, STOCK_BOTTOM_Y, STOCK_FRONT_Z), (2, full, STOCK_REAR_Z - STOCK_FRONT_Z), "aug_green")   # main body
    rc.box((-1, STOCK_BOTTOM_Y, GUARD_FRONT_Z), (2, RECV_BOTTOM_Y - STOCK_BOTTOM_Y,
                                                 STOCK_FRONT_Z - GUARD_FRONT_Z), "aug_green")          # trigger housing
    rc.box((-0.875, TOP_Y, 4.75), (1.75, 0.2, 5.75), "aug_green")                    # rounded comb
    # butt: deepens in two steps toward the pad
    rc.box((-1, 2.75, MAGWELL_REAR_Z + 0.25), (2, 0.75, STOCK_REAR_Z - MAGWELL_REAR_Z - 0.25), "aug_green")
    rc.box((-1, 2.0, 9.0), (2, 0.75, STOCK_REAR_Z - 9.0), "aug_green")
    rc.box((-1, 2.0, STOCK_REAR_Z), (2, TOP_Y - 2.0, BUTT_Z - STOCK_REAR_Z), "rubber")   # butt pad
    rc.box((-1.04, 3.0, 10.0), (2.08, 0.4, 0.4), "phosphate")                        # rear sling swivel pin
    # magazine well lip and the magazine catch behind it
    rc.box((-1.05, STOCK_BOTTOM_Y - 0.25, MAGWELL_FRONT_Z - 0.25), (2.1, 0.5, 0.25), "aug_green_dark")
    rc.box((-0.3, 2.9, MAGWELL_REAR_Z), (0.6, 0.6, 0.3), "phosphate")                # magazine catch
    # ejection port (right) with the bolt behind it; the blanked port on the left
    rc.box((0.5, 5.25, 7.25), (0.55, 0.75, 1.75), "port")
    rc.box((-1.04, 5.2, 7.2), (0.04, 0.85, 1.85), "aug_green_dark")                  # left port cover
    # cross-bolt safety above the grip, take-down button, body pins
    rc.box((-1.06, 4.0, 2.75), (2.12, 0.45, 0.45), "phosphate")
    rc.box((1.02, 4.1, 2.85), (0.06, 0.25, 0.25), "mark_red")
    rc.box((-1.04, 5.0, 3.75), (2.08, 0.35, 0.35), "phosphate")                      # take-down button
    rc.box((-1.03, 4.25, 9.75), (2.06, 0.3, 0.3), "phosphate")                       # butt-plate pin
    # full-hand trigger guard, moulded in one with the stock
    rc.box((-0.45, 0.45, GUARD_FRONT_Z), (0.9, STOCK_BOTTOM_Y - 0.45, 0.4), "aug_green")    # front strut
    rc.box((-0.45, 0.45, GUARD_FRONT_Z), (0.9, 0.4, 4.6), "aug_green")                      # bottom bar

    # ---- the dark receiver: in front of the stock's full-height part, on the trigger housing
    rc.box((-0.9, RECV_BOTTOM_Y, RECV_FRONT_Z), (1.8, TOP_Y - RECV_BOTTOM_Y, STOCK_FRONT_Z - RECV_FRONT_Z),
           "recv_grey")
    rc.box((-0.92, CHARGE_Y - 0.15, CHARGE_Z - 0.4), (0.04, 0.3, CHARGE_PULL + 0.8), "port")   # cocking slot (left)
    rc.box((-0.6, 5.0, RECV_FRONT_Z - 0.75), (1.2, 1.2, 0.75), "recv_grey", pattern="lines")    # barrel locking collar
    rc.box((0.9, 5.4, -4.25), (0.2, 0.5, 0.5), "phosphate")                          # barrel release catch (right)

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, 8.125))
    bo.box((0.1, 5.25, 7.25), (0.98, 0.75, 1.75), "bolt_steel")

    # cocking handle on the LEFT of the receiver, under the sighting tube; non-reciprocating
    ch = m.bone("charging_handle", pivot=CHARGE_PIVOT)
    ch.box((-1.15, CHARGE_Y - 0.15, CHARGE_Z - 0.15), (0.3, 0.3, 0.3), "phosphate")                      # stem
    ch.box((-1.6, CHARGE_Y - 0.25, CHARGE_Z - 0.3), (0.5, 0.5, 0.6), "black_poly", pattern="lines")      # knob


def _scope(m: Model) -> None:
    """The integral 1.5x optic: a hollow square tube (1 x 1 window, 0.25 walls) on two webs."""
    sc = m.bone("scope", parent="receiver", pivot=(0, SIGHT_Y, 0))
    z0, n = SCOPE_FRONT_Z, SCOPE_REAR_Z - SCOPE_FRONT_Z
    y0, y1 = TUBE_Y0, TUBE_Y1
    sc.box((-0.75, y0, z0), (1.5, 0.25, n), "scope_black")                           # bottom wall
    sc.box((-0.75, y1 - 0.25, z0), (1.5, 0.25, n), "scope_black")                    # top wall
    sc.box((-0.75, y0 + 0.25, z0), (0.25, 1.0, n), "scope_black")                    # left wall
    sc.box((0.5, y0 + 0.25, z0), (0.25, 1.0, n), "scope_black")                      # right wall
    for x in (-0.875, 0.625):                                                        # objective / eyepiece rims
        sc.box((x, y0 - 0.125, z0), (0.25, 1.75, 0.6), "scope_black")
        sc.box((x, y0 - 0.125, SCOPE_REAR_Z - 0.6), (0.25, 1.75, 0.6), "rubber")
    for y in (y0 - 0.125, y1 - 0.125):
        sc.box((-0.875, y, z0), (1.75, 0.25, 0.6), "scope_black")
        sc.box((-0.875, y, SCOPE_REAR_Z - 0.6), (1.75, 0.25, 0.6), "rubber")
    # the two webs that carry the tube (the gap between them is the carry-handle opening)
    sc.box((-0.6, TOP_Y, -3.25), (1.2, y0 - TOP_Y, 1.25), "recv_grey")
    sc.box((-0.6, TOP_Y, 1.75), (1.2, y0 - TOP_Y, 1.25), "recv_grey")
    sc.wedge((0, TOP_Y + 0.15, -1.25), (0, y0 + 0.1, -2.25), 1.0, 0.45, "recv_grey")  # front web brace
    sc.wedge((0, y0 + 0.1, 2.0), (0, TOP_Y + 0.15, 1.0), 1.0, 0.45, "recv_grey")      # rear web brace
    # adjusters and the emergency sights on top of the tube
    sc.box((-0.3, y1, -0.6), (0.6, 0.25, 0.6), "phosphate", pattern="knurl")         # elevation screw
    sc.box((0.75, SIGHT_Y - 0.3, -0.6), (0.2, 0.6, 0.6), "phosphate", pattern="knurl")   # windage screw
    sc.box((-0.1, y1, z0 + 0.7), (0.2, 0.2, 0.3), "phosphate")                       # emergency front blade
    sc.box((-0.35, y1, SCOPE_REAR_Z - 1.0), (0.25, 0.2, 0.3), "phosphate")           # emergency rear notch
    sc.box((0.1, y1, SCOPE_REAR_Z - 1.0), (0.25, 0.2, 0.3), "phosphate")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECV_FRONT_Z))
    ba.box((-0.375, 5.125, BARREL_END_Z), (0.75, 0.75, RECV_FRONT_Z - BARREL_END_Z), "barrel_steel")
    # gas cylinder / regulator, offset to the right of the barrel
    ba.box((0.2, 5.3, -7.5), (0.75, 0.75, 2.0), "barrel_steel")
    ba.box((0.25, 5.35, -7.9), (0.65, 0.65, 0.4), "phosphate", pattern="knurl")      # gas regulator knob
    ba.box((-0.5, 5.0, -7.0), (1.0, 1.0, 0.6), "phosphate")                          # gas block collar
    # foregrip hinge under the barrel and the vertical foregrip folded DOWN
    ba.box((-0.45, 4.3, FOREGRIP_Z - 0.6), (0.9, 0.8, 1.2), "phosphate")             # hinge block
    fg = m.bone("foregrip", parent="barrel", pivot=(0, 4.5, FOREGRIP_Z))
    fg.box((-0.5, 1.5, FOREGRIP_Z - 0.55), (1.0, 2.9, 1.1), "black_poly", pattern="lines")
    fg.box((-0.55, 1.3, FOREGRIP_Z - 0.6), (1.1, 0.3, 1.2), "black_poly")            # flared base

    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # closed birdcage flash hider
    md.box((-0.375, 5.125, MUZZLE_Z), (0.75, 0.75, BARREL_END_Z - MUZZLE_Z), "phosphate", inflate=0.1,
           pattern="slots")
    md.box((-0.375, 5.125, BARREL_END_Z - 0.25), (0.75, 0.75, 0.25), "phosphate", inflate=0.18)   # lock ring


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-0.9, 0, 0), (1.8, 4, 2), "aug_green", rot=(-20, 0, 0), pivot=(0, 4, 1), inflate=-0.05)
    gr.box((-0.95, 1.0, 0.25), (1.9, 2.25, 1.5), "aug_green_dark", pattern="stipple",
           rot=(-20, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                         # grip panels
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.3, 2.4, -0.9), (0.6, 1.1, 0.5), "black_poly")                         # wide polymer trigger


def _magazine(m: Model) -> None:
    """The 30-round translucent magazine behind the grip: three gently curving segments, 2 deep,
    with the column of rounds showing through the smoky body on both sides."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    y, z = 4.25, MAG_PIVOT[2]                                                       # top, inside the well
    seg, depth, tilts = 1.5, 2.0, (2, 7, 12)
    for i, tilt in enumerate(tilts):
        r = math.radians(tilt)
        y1, z1 = y - seg * math.cos(r), z - seg * math.sin(r)
        over = 0.15 if i < len(tilts) - 1 else 0.0                                  # overlap closes the outer gaps
        end = (0, y1 - over * math.cos(r), z1 - over * math.sin(r))
        mg.wedge((0, y, z), end, 1.0, depth, "mag_smoke")
        if i < 2:                                                                   # rounds: the top two thirds are full
            mg.wedge((0, y, z + 0.1), (0, y1, z1 + 0.1), 1.06, depth - 0.9, "mag_rounds", pattern="lines")
        y, z = y1, z1
    r = math.radians(tilts[-1])
    mg.wedge((0, y, z), (0, y - 0.3 * math.cos(r), z - 0.3 * math.sin(r)), 1.1, depth + 0.2,
             "black_poly")                                                          # floor plate


def _detour(anim, t0: float, t1: float) -> None:
    """Bullpup: on its way between the foregrip / cocking handle and the magazine BEHIND the grip
    the left hand would pass straight through the grip and the right hand. Add a waypoint half
    way between the LEFT_ARM position keys nearest to `t0` and `t1`, swung out to the left."""
    frames = anim.tracks[T.LEFT_ARM]["position"]
    a, b = (min(frames, key=lambda t: abs(t - x)) for x in (t0, t1))
    (va, _), (vb, _) = frames[a], frames[b]
    mid = tuple((p + q) / 2 + d for p, q, d in zip(va, vb, HAND_DETOUR))
    anim.key(T.LEFT_ARM, "position", round((a + b) / 2, 4), mid, T.IN)
    anim.key(T.LEFT_ARM, "position", b, vb, T.OUT)


def _animations(m: Model) -> dict:
    """Returns the reload key times in REAL seconds for the docs / sound timing."""
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_LENGTH, rise=MUZZLE_RISE)
    # the templates find the magazine from its bone, so the swap happens behind the grip
    k = RELOAD_LENGTH / 2.2
    a = T.reload_mag(m, length=RELOAD_LENGTH)
    _detour(a, 0.0, 0.3 * k)                 # foregrip -> magazine
    _detour(a, 1.68 * k, 2.0 * k)            # magazine -> foregrip
    # empty: same swap, then the LEFT hand pulls the cocking handle under the optic and lets go
    ke = RELOAD_EMPTY_LENGTH / 3.0
    e = T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, empty_action="cycle",
                     bolt_bone="charging_handle", lift_deg=0.0, pull=CHARGE_PULL, hand="left",
                     knob=CHARGE_KNOB, also=("bolt",))
    st = T.HandCycle.STRAIGHT
    cyc = lambda name: (1.68 + st[name]) * ke                  # the hand cycle: 1.0 base s from 1.68
    _detour(e, 0.0, 0.3 * ke)                # foregrip -> magazine
    _detour(e, 1.68 * ke, cyc("grab"))       # magazine -> cocking handle
    return {
        "reload": {"mag_out": round(0.5 * k, 3), "mag_in": round(1.45 * k, 3), "mag_slap": round(1.6 * k, 3)},
        "reload_empty": {"mag_out": round(0.5 * ke, 3), "mag_in": round(1.45 * ke, 3),
                         "mag_slap": round(1.6 * ke, 3), "hand_on_handle": round(cyc("grab"), 3),
                         "handle_back": round(cyc("back"), 3), "handle_release": round(cyc("hold"), 3),
                         "handle_home": round(cyc("fwd"), 3), "hand_on_grip": round(cyc("end"), 3)},
    }


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_stock, _scope, _barrel, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, muzzle=MUZZLE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, 5.625, 8.125),
               sight_line=SIGHT_LINE)
    m.reload_times = _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "suppressed": att.loadout(m, "suppressor_556") | arms,
    }
    return m
