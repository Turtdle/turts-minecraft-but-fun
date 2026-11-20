"""GIAT FAMAS F1 - French 5.56x45 mm bullpup assault rifle, 25-round straight magazine, ~1000 rpm.

Same scale and conventions as the M4 / MP5 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 757 mm = 22.6 units (modelled z -9.75 .. 12.85). Barrel 488 mm = 14.6 units;
it starts BEHIND the grip (bullpup), only the last 4.5 units stick out of the forend.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette ("le Clairon"): one long plastic body from the forend to the butt, the long CARRY
HANDLE on two pillars over most of it (front post in ears on its front end, rear aperture on its
rear end), the cocking handle on top of the body UNDER the carry handle, the pistol grip with the
full-hand trigger guard, the straight 25-round magazine BEHIND the grip, the deep butt with a
cheek piece and the ejection port beside the shooter's cheek, the bipod legs folded back along
both sides of the body, and a short barrel with the flash hider / rifle-grenade sleeve.

Slots: OPTIC (on top of the carry handle; the rear aperture hides under an optic) and MUZZLE.

Arms, mounts, attachments and all animations come from templates.py, so mod-wide changes are
made there. Everything gun-specific is a constant in the block below; the part builders only
read those constants.
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "famas"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
BODY_BOTTOM_Y = 4.25
BODY_TOP_Y = 6.5           # top of the plastic body (the cocking handle runs on it)
HANDLE_BAR_Y = 7.75        # underside of the carry handle bar (1.25 units of daylight above the body)
HANDLE_TOP_Y = 8.5         # top of the carry handle = optic mount height
SIGHT_Y = 9.0              # iron sight line: front post tip and centre of the rear aperture
MUZZLE_Z = -9.75           # front of the flash hider
BARREL_END_Z = -7.5        # muzzle mount: the flash hider / grenade sleeve sits in front of it
BODY_FRONT_Z = -5.25       # front face of the forend
HANDLE_FRONT_Z = -5.25     # front of the carry handle (front pillar)
HANDLE_REAR_Z = 6.0        # rear of the carry handle (rear pillar)
PILLAR_LEN = 1.25
GUARD_FRONT_Z = -2.0       # front strut of the full-hand trigger guard
MAGWELL_FRONT_Z = 4.0      # the magazine well is BEHIND the pistol grip
MAGWELL_REAR_Z = 6.25
BODY_REAR_Z = 12.35        # rear face of the butt (the pad sits behind it)
BUTT_REAR_Z = 12.85        # rear face of the butt pad
FRONT_SIGHT_Z = -4.75      # z centre of the front post
REAR_SIGHT_Z = 5.25        # z centre of the rear aperture

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, 3.75, 5.125)            # bottom centre of the magazine well
OPTIC_MOUNT = (0, HANDLE_TOP_Y, 0.0)    # top of the carry handle, middle
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
CHARGE_Z = -2.25                        # z centre of the cocking handle at rest
CHARGE_PIVOT = (0, 6.75, CHARGE_Z)
CHARGE_KNOB = (-1.1, 6.95, CHARGE_Z)    # where the LEFT hand hooks it: the left end of its crossbar
CHARGE_PULL = 2.5                       # travel of the handle in its slot

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                      # centre of the pistol grip (same as the M4 / MP5)
L_HAND = (0, 4.0, -4.0)                 # under the forend, in front of the trigger guard

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "holographic", "micro_red_dot", "acog", "suppressor_556", "compensator_556")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.06         # 1000 rpm -> 0.06 s between shots
RECOIL = 0.5
MUZZLE_RISE = 1.5
BOLT_TRAVEL = 1.5
RELOAD_LENGTH = 2.6
RELOAD_EMPTY_LENGTH = 3.1  # magazine swap, then the left hand pulls the cocking handle
HAND_DETOUR = (-3.5, -0.5, 0.0)   # the left hand swings out to the LEFT round the grip / right hand


def _materials(m: Model) -> None:
    m.material("body_poly", (47, 48, 52), 4, 0.80)      # the dark grey plastic body / forend / butt
    m.material("cheek_poly", (58, 59, 63), 4, 0.80)     # cheek piece, a shade lighter so it reads apart
    m.material("handle_black", (30, 30, 34), 4, 0.76)   # carry handle
    m.material("steel_black", (38, 39, 44), 5, 0.74)    # magazine well, trigger guard
    m.material("barrel_steel", (74, 76, 84), 6, 0.72)
    m.material("bolt_steel", (120, 122, 128), 7, 0.72)  # bolt seen in the ejection port
    m.material("phosphate", (26, 26, 30), 4, 0.78)      # sights, controls, flash hider
    m.material("bipod_steel", (84, 86, 92), 6, 0.72)    # the folded bipod legs
    m.material("mag_steel", (60, 62, 68), 6, 0.74)      # straight 25-round steel magazine
    m.material("port", (14, 14, 16), 2, 0.9)            # ejection port, slots


def _body(m: Model) -> None:
    """The one-piece plastic body: forend, receiver and butt, plus the carry handle on top."""
    z0 = BODY_FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, BODY_BOTTOM_Y, z0), (2, BODY_TOP_Y - BODY_BOTTOM_Y, BODY_REAR_Z - z0), "body_poly")
    rc.box((-0.75, 4.0, z0 + 0.5), (1.5, 0.25, GUARD_FRONT_Z - z0 - 0.5), "body_poly", pattern="lines")  # forend belly
    rc.box((-0.75, 5.125, z0 - 0.25), (1.5, 1.0, 0.25), "phosphate")                # barrel collar on the nose
    rc.box((-0.75, 3.5, GUARD_FRONT_Z), (1.5, 0.75, 5.0), "body_poly")              # trigger housing / grip root
    # full-hand trigger guard: from the forend down and back to the bottom of the grip
    rc.box((-0.375, 0.5, GUARD_FRONT_Z), (0.75, 3.0, 0.3), "steel_black")           # front strut
    rc.box((-0.375, 0.5, GUARD_FRONT_Z), (0.75, 0.3, 3.75), "steel_black")          # bottom bar
    # magazine well behind the grip
    rc.box((-1, 3.75, MAGWELL_FRONT_Z), (2, 0.5, MAGWELL_REAR_Z - MAGWELL_FRONT_Z), "steel_black",
           pattern="lines")
    rc.box((-0.25, 3.0, MAGWELL_FRONT_Z - 0.25), (0.5, 0.75, 0.25), "phosphate")    # magazine catch
    # butt: deepens in two steps toward the pad, cheek piece on top, ejection port on the right
    rc.box((-1, 3.25, MAGWELL_REAR_Z), (2, 1.0, BODY_REAR_Z - MAGWELL_REAR_Z), "body_poly")
    rc.box((-1, 2.5, 8.75), (2, 0.75, BODY_REAR_Z - 8.75), "body_poly")
    rc.box((-1, 2.25, BODY_REAR_Z), (2, 4.5, BUTT_REAR_Z - BODY_REAR_Z), "rubber")  # butt pad
    rc.box((-0.875, BODY_TOP_Y, 6.75), (1.75, 0.5, 5.0), "cheek_poly")              # cheek piece
    rc.box((0.5, 5.25, 6.75), (0.55, 0.75, 1.75), "port")                           # ejection port
    rc.box((-1.03, 5.0, 0.5), (2.06, 0.3, 0.3), "phosphate")                        # body pins
    rc.box((-1.03, 5.0, 9.5), (2.06, 0.3, 0.3), "phosphate")
    rc.box((-1.03, 4.75, -1.25), (0.03, 0.5, 0.75), "phosphate")                    # fire selector (left)
    rc.box((-0.25, BODY_TOP_Y, CHARGE_Z - 0.5), (0.5, 0.04, CHARGE_PULL + 1.0), "port")   # cocking-handle slot

    # carry handle: two pillars and the long bar, with a sloped brace in front and behind
    n = HANDLE_REAR_Z - HANDLE_FRONT_Z
    pillar_h = HANDLE_BAR_Y - BODY_TOP_Y
    rc.box((-0.75, BODY_TOP_Y, HANDLE_FRONT_Z), (1.5, pillar_h, PILLAR_LEN), "handle_black")
    rc.box((-0.75, BODY_TOP_Y, HANDLE_REAR_Z - PILLAR_LEN), (1.5, pillar_h, PILLAR_LEN), "handle_black")
    rc.box((-0.75, HANDLE_BAR_Y, HANDLE_FRONT_Z), (1.5, HANDLE_TOP_Y - HANDLE_BAR_Y, n), "handle_black")
    rc.wedge((0, BODY_TOP_Y + 0.2, HANDLE_FRONT_Z + PILLAR_LEN + 1.0),
             (0, HANDLE_BAR_Y + 0.2, HANDLE_FRONT_Z + PILLAR_LEN - 0.25), 1.0, 0.5, "handle_black")
    rc.wedge((0, HANDLE_BAR_Y + 0.2, HANDLE_REAR_Z - PILLAR_LEN + 0.25),
             (0, BODY_TOP_Y + 0.2, HANDLE_REAR_Z - PILLAR_LEN - 1.0), 1.0, 0.5, "handle_black")

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, 7.625))
    bo.box((0.1, 5.25, 6.75), (0.98, 0.75, 1.75), "bolt_steel")

    # cocking handle: a hook with a crossbar on top of the body, under the carry handle; slides +Z
    ch = m.bone("charging_handle", pivot=CHARGE_PIVOT)
    ch.box((-0.25, BODY_TOP_Y, CHARGE_Z - 0.25), (0.5, 0.375, 0.5), "phosphate")                  # stem
    ch.box((-0.875, BODY_TOP_Y + 0.25, CHARGE_Z - 0.25), (1.75, 0.375, 0.5), "phosphate", pattern="knurl")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, BODY_FRONT_Z))
    ba.box((-0.375, 5.125, BARREL_END_Z), (0.75, 0.75, BODY_FRONT_Z - BARREL_END_Z), "barrel_steel")
    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # flash hider / grenade sleeve
    md.box((-0.375, 5.125, MUZZLE_Z), (0.75, 0.75, BARREL_END_Z - MUZZLE_Z), "barrel_steel")
    md.box((-0.375, 5.125, MUZZLE_Z), (0.75, 0.75, 1.0), "phosphate", inflate=0.1, pattern="slots")  # flash hider
    for z in (-8.5, -7.95):                                                         # grenade retaining rings
        md.box((-0.375, 5.125, z), (0.75, 0.75, 0.2), "phosphate", inflate=0.15)


def _bipod(m: Model) -> None:
    """The bipod legs, folded back along both sides of the body under the carry handle."""
    bp = m.bone("bipod_legs", pivot=(0, 6.0, -4.75))
    bp.box((-1.3, 5.7, -5.0), (2.6, 0.7, 0.5), "phosphate")                         # hinge yoke round the body
    for x in (-1.3, 1.0):
        bp.box((x, 5.85, -4.5), (0.3, 0.4, 7.5), "bipod_steel")                     # leg
        bp.box((x - 0.05 if x < 0 else x, 5.75, 3.0), (0.35, 0.6, 0.5), "phosphate")  # foot


def _sights(m: Model) -> None:
    # front post between two protective ears on the front end of the carry handle
    fs = m.bone("front_sight", pivot=(0, HANDLE_TOP_Y, FRONT_SIGHT_Z))
    z = FRONT_SIGHT_Z - 0.25
    fs.box((-0.75, HANDLE_TOP_Y, z), (0.25, SIGHT_Y + 0.25 - HANDLE_TOP_Y, 0.5), "phosphate")
    fs.box((0.5, HANDLE_TOP_Y, z), (0.25, SIGHT_Y + 0.25 - HANDLE_TOP_Y, 0.5), "phosphate")
    fs.box((-0.125, HANDLE_TOP_Y, FRONT_SIGHT_Z - 0.125), (0.25, SIGHT_Y - HANDLE_TOP_Y, 0.25),
           "phosphate")                                                             # post: TOP at SIGHT_Y

    # rear aperture on the rear end of the handle: a frame with a 0.5 hole centred on SIGHT_Y.
    # The whole bone is hidden by the renderer while an optic is fitted.
    rs = m.bone("rear_sight", pivot=(0, HANDLE_TOP_Y, REAR_SIGHT_Z))
    z = REAR_SIGHT_Z - 0.25
    y0, y1 = SIGHT_Y - 0.25, SIGHT_Y + 0.25
    rs.box((-0.75, HANDLE_TOP_Y, z), (1.5, y0 - HANDLE_TOP_Y, 0.5), "phosphate")    # base
    rs.box((-0.75, y0, z), (0.5, 0.5, 0.5), "phosphate")                            # left wall
    rs.box((0.25, y0, z), (0.5, 0.5, 0.5), "phosphate")                             # right wall
    rs.box((-0.75, y1, z), (1.5, 0.25, 0.5), "phosphate")                           # top


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "body_poly", pattern="stipple",
           rot=(-20, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                         # pistol grip
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.875), (0.5, 1, 0.5), "barrel_steel")


def _magazine(m: Model) -> None:
    """Straight 25-round steel magazine behind the grip: 1.75 deep, 3 units below the well."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    z = MAGWELL_FRONT_Z + 0.25
    mg.box((-0.5, 0.75, z), (1, 3.75, 1.75), "mag_steel", pattern="lines")
    mg.box((-0.5, 0.5, z), (1, 0.25, 1.75), "phosphate", inflate=0.08)              # floor plate


def _detour(anim, t0: float, t1: float) -> None:
    """Bullpup: on its way between the forend / cocking handle and the magazine BEHIND the grip
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
    _detour(a, 0.0, 0.3 * k)                 # forend -> magazine
    _detour(a, 1.68 * k, 2.0 * k)            # magazine -> forend
    # empty: same swap, then the LEFT hand hooks the cocking handle under the carry handle
    ke = RELOAD_EMPTY_LENGTH / 3.0
    e = T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, empty_action="cycle",
                     bolt_bone="charging_handle", lift_deg=0.0, pull=CHARGE_PULL, hand="left",
                     knob=CHARGE_KNOB, also=("bolt",))
    st = T.HandCycle.STRAIGHT
    cyc = lambda name: (1.68 + st[name]) * ke                  # the hand cycle: 1.0 base s from 1.68
    _detour(e, 0.0, 0.3 * ke)                # forend -> magazine
    _detour(e, 1.68 * ke, cyc("grab"))       # magazine -> cocking handle
    return {
        "reload": {"mag_out": round(0.5 * k, 3), "mag_in": round(1.45 * k, 3), "mag_slap": round(1.6 * k, 3)},
        "reload_empty": {"mag_out": round(0.5 * ke, 3), "mag_in": round(1.45 * ke, 3),
                         "mag_slap": round(1.6 * ke, 3), "hand_on_handle": round(cyc("grab"), 3),
                         "handle_back": round(cyc("back"), 3), "handle_release": round(cyc("hold"), 3),
                         "handle_home": round(cyc("fwd"), 3)},
    }


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_body, _barrel, _bipod, _sights, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, 5.625, 7.625),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    m.reload_times = _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "loadout_a": att.loadout(m, "red_dot", "suppressor_556") | arms,
        "loadout_b": att.loadout(m, "acog", "compensator_556"),
        "loadout_micro": att.loadout(m, "micro_red_dot") | arms,
    }
    return m
