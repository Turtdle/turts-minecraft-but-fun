"""M14 rifle - US 7.62x51 mm battle rifle, 20-round box magazine, ~750 rpm.

Same scale and conventions as the M4 / MP5 / FAMAS (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1,118 mm = 33.4 units (modelled z -22.9 .. 10.5). Barrel 559 mm = 16.7 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: the long one-piece walnut stock from the steel butt plate (with its folded shoulder
rest) through the semi-pistol-grip wrist to the front band, the brown handguard on top of the
barrel, the low steel receiver with the open-top action, the operating rod running along the
right side with its handle beside the bolt, the rear aperture sight between its two ears and
knobs, the gas cylinder under the barrel in front of the stock, the winged front blade and the
long slotted flash suppressor, and the straight 20-round magazine in front of the trigger guard.

Slots: OPTIC (a low scope-mount rail over the receiver, as on the M1A; the rear sight hides
under an optic), MUZZLE and UNDERBARREL (the bipod, clamped to the gas cylinder in front of the
front band like the issue M2 bipod; its clamp band is built under `att_bipod`, so it is only
drawn with the bipod fitted).

Arms, mounts, attachments and the animations come from templates.py. Three things are specific to
this gun and live here: the operating rod (bone `op_rod`; the bolt seen in the action is its
child `bolt`) cycles when firing; the magazine ROCKS in round its front lug (`_rock_magazine`
re-keys the template's swap); and in `reload_empty` the action stays LOCKED OPEN during the swap,
then the right hand pulls the op-rod handle the last bit back and lets it fly home (`_hold_open`).
"""
import attachments as att
import templates as T
from armorygen import Model, _rotate

GUN_ID = "m14"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
STOCK_TOP_Y = 5.3          # top edge of the stock along the action (the receiver sits on it)
RECV_TOP_Y = 6.4
RAIL_TOP_Y = 6.9           # top of the scope-mount rail = optic mount height
SIGHT_Y = 7.25             # iron sight line: front blade tip and centre of the rear aperture
MUZZLE_Z = -22.9           # front of the flash suppressor
BARREL_END_Z = -20.5       # muzzle mount (the flash suppressor sits in front of it)
FRONT_SIGHT_Z = -20.2      # z centre of the front blade
GAS_FRONT_Z = -18.3        # front of the gas cylinder
BAND_Z = -14.3             # front face of the front band
STOCK_FRONT_Z = -13.8      # front end of the stock (ferrule)
RECV_FRONT_Z = -4.5
MID_REAR_Z = 1.5           # where the deep middle of the stock ends and the wrist starts
RECV_REAR_Z = 3.0
REAR_SIGHT_Z = 2.25        # z centre of the rear aperture
BUTT_Z = 10.5              # rear face of the butt plate

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, 3.5, -4.0)              # the FRONT LUG: the magazine rocks round it
OPTIC_MOUNT = (0, RAIL_TOP_Y, -1.75)    # middle of the scope-mount rail
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
UNDERBARREL_MOUNT = (0, 4.15, BAND_Z - 0.5)   # just under the gas cylinder, its 1.5-long base against
                                              # the front band; the folded legs end at the muzzle
OP_Z = -2.6                             # z centre of the op-rod handle at rest
OP_Y = 5.9
OP_PIVOT = (0, OP_Y, OP_Z)
OP_KNOB = (1.45, OP_Y, OP_Z)            # knob centre, right side
OP_PULL = 3.0                           # travel of the operating rod / bolt
OP_LOCKED = 2.6                         # where the bolt stop holds it on an empty magazine

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2.6, 2.5)                  # round the wrist of the stock, behind the trigger guard
L_HAND = (0, 3.65, -8.5)                # under the fore-end

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "holographic", "acog", "sniper_scope", "suppressor_762", "compensator_762",
               "bipod")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.08         # 750 rpm -> 0.08 s between shots
RECOIL = 1.1
MUZZLE_RISE = 3.0
RELOAD_LENGTH = 2.4
RELOAD_EMPTY_LENGTH = 2.9  # magazine swap with the action locked open, then the op rod is released
MAG_ROCK_DEG = 18.0        # how far the magazine rocks forward round its front lug
MAG_UNHOOK = (0.0, -0.6, -0.25)   # magazine offset when it has just come off the lug


def _materials(m: Model) -> None:
    m.material("walnut", (116, 70, 38), 12, 0.76)       # the stock
    m.material("fiberglass", (98, 62, 38), 6, 0.80)     # the brown handguard, a shade duller
    m.material("park_steel", (60, 62, 62), 6, 0.74)     # parkerized receiver, trigger group, sights
    m.material("barrel_steel", (70, 72, 76), 6, 0.72)
    m.material("bolt_steel", (128, 130, 134), 7, 0.72)  # bolt
    m.material("rod_steel", (84, 86, 88), 6, 0.72)      # operating rod
    m.material("phosphate", (34, 35, 37), 4, 0.78)      # flash suppressor, rail, small parts
    m.material("mag_steel", (56, 58, 60), 6, 0.74)
    m.material("butt_steel", (88, 90, 94), 6, 0.72)     # butt plate
    m.material("port", (14, 14, 16), 2, 0.9)


def _stock(m: Model) -> None:
    """Full-length walnut stock: fore-end, deep middle under the action, wrist, butt."""
    st = m.bone("stock", pivot=(0, 4.5, 0))
    st.box((-1.125, 3.9, STOCK_FRONT_Z), (2.25, STOCK_TOP_Y - 3.9, RECV_FRONT_Z - STOCK_FRONT_Z), "walnut")  # fore-end
    st.box((-0.9, 3.7, STOCK_FRONT_Z + 1.0), (1.8, 0.2, RECV_FRONT_Z - STOCK_FRONT_Z - 1.0), "walnut")       # its belly
    st.box((-1.125, 3.5, RECV_FRONT_Z), (2.25, STOCK_TOP_Y - 3.5, MID_REAR_Z - RECV_FRONT_Z), "walnut")      # middle
    st.wedge((0, 4.3, MID_REAR_Z - 0.25), (0, 4.05, 4.25), 1.9, 1.9, "walnut")       # wrist
    st.wedge((0, 4.2, 3.75), (0, 4.4, BUTT_Z - 0.5), 2.0, 2.2, "walnut")             # comb
    st.wedge((0, 3.5, 3.5), (0, 2.5, BUTT_Z - 0.5), 1.9, 1.9, "walnut")              # belly / semi-pistol grip
    st.box((-1.05, 1.4, BUTT_Z - 0.65), (2.1, 4.25, 0.65), "butt_steel")             # butt plate
    st.box((-0.6, 5.65, BUTT_Z - 1.4), (1.2, 0.15, 1.4), "butt_steel")               # folded shoulder rest
    st.box((-1.17, 3.85, STOCK_FRONT_Z - 0.2), (2.34, STOCK_TOP_Y - 3.8, 0.5), "park_steel")   # stock ferrule
    st.box((-0.3, 3.45, -12.5), (0.6, 0.45, 0.5), "park_steel")                      # front sling swivel
    st.box((-0.3, 1.75, 8.0), (0.6, 0.5, 0.5), "park_steel")                         # rear sling swivel


def _receiver(m: Model) -> None:
    z0, n = RECV_FRONT_Z, RECV_REAR_Z - RECV_FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5.5, 0))
    rc.box((-0.9, STOCK_TOP_Y, z0), (1.8, RECV_TOP_Y - STOCK_TOP_Y, n), "park_steel")
    rc.box((-0.9, RECV_TOP_Y, 1.5), (1.8, 0.3, RECV_REAR_Z - 1.5), "park_steel")     # heel: rear sight base
    rc.box((-0.75, STOCK_TOP_Y, z0 - 0.6), (1.5, 1.0, 0.6), "park_steel")            # barrel ring
    rc.box((0.35, 5.75, -3.5), (0.6, 0.7, 3.0), "port")                              # open action (top right)
    rc.box((-1.0, 5.6, -2.25), (0.12, 0.5, 0.9), "phosphate")                        # bolt stop (left)
    rc.box((0.9, 5.45, 1.0), (0.15, 0.4, 0.9), "phosphate")                          # selector lock (right)
    # scope-mount rail over the action: side bracket on the left, rail on two feet
    rc.box((-0.6, RAIL_TOP_Y - 0.4, -4.0), (1.2, 0.4, 4.75), "phosphate", pattern="rails")
    rc.box((-0.6, RECV_TOP_Y, -4.0), (1.2, 0.1, 0.75), "phosphate")                  # front foot
    rc.box((-0.6, RECV_TOP_Y, 0.0), (1.2, 0.1, 0.75), "phosphate")                   # rear foot (clip guide)
    rc.box((-1.05, 5.5, -2.9), (0.15, 1.35, 2.6), "phosphate")                       # side bracket
    rc.box((-1.25, 5.8, -1.8), (0.2, 0.4, 0.4), "bolt_steel", pattern="knurl")       # its clamp screw
    # trigger group
    rc.box((-0.35, 2.3, -1.5), (0.7, 0.2, 3.0), "park_steel")                        # guard: bottom
    rc.box((-0.35, 2.3, -1.5), (0.7, 1.2, 0.25), "park_steel")                       # guard: front
    rc.box((-0.35, 2.3, 1.25), (0.7, 1.0, 0.25), "park_steel")                       # guard: rear
    rc.box((-0.15, 2.6, -1.2), (0.3, 0.7, 0.15), "phosphate")                        # safety tab inside the guard
    rc.box((-0.2, 2.5, -1.75), (0.4, 1.0, 0.25), "phosphate")                        # magazine catch

    # operating rod: handle beside the bolt, the rod itself forward along the right side to the
    # gas cylinder. ONE bone moves rod, handle and (child) bolt.
    op = m.bone("op_rod", pivot=OP_PIVOT)
    op.box((0.75, STOCK_TOP_Y, -13.0), (0.3, 0.3, 10.5), "rod_steel")                # rod
    op.box((0.9, STOCK_TOP_Y, OP_Z - 0.45), (0.25, 0.65, 0.9), "rod_steel")          # hump up to the handle
    op.box((1.15, OP_Y - 0.2, OP_Z - 0.2), (0.15, 0.4, 0.4), "rod_steel")            # handle stem
    op.box((1.2, OP_Y - 0.25, OP_Z - 0.3), (0.5, 0.5, 0.6), "rod_steel", pattern="lines")   # handle knob
    bo = m.bone("bolt", parent="op_rod", pivot=(0.5, BORE_Y, -2.0))
    bo.box((0.1, 5.75, -3.5), (0.88, 0.73, 3.0), "bolt_steel")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECV_FRONT_Z))
    ba.box((-0.4, 5.1, BARREL_END_Z), (0.8, 0.8, RECV_FRONT_Z - BARREL_END_Z), "barrel_steel")
    ba.box((-0.4, 4.2, GAS_FRONT_Z), (0.8, 0.9, BAND_Z - GAS_FRONT_Z), "park_steel")     # gas cylinder
    ba.box((-0.25, 4.4, GAS_FRONT_Z - 0.3), (0.5, 0.5, 0.3), "phosphate", pattern="knurl")   # gas plug
    ba.box((-0.55, 4.15, GAS_FRONT_Z + 0.4), (1.1, 1.85, 0.35), "park_steel")        # front cylinder ring
    ba.box((0.4, 4.4, -15.2), (0.2, 0.45, 0.45), "phosphate")                        # spindle valve
    ba.box((-0.6, 4.1, BAND_Z), (1.2, 2.0, 0.3), "park_steel")                       # front band
    ba.box((-0.5, 5.0, BARREL_END_Z), (1.0, 1.0, 0.6), "park_steel")                 # front sight base / castle nut

    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # long slotted flash suppressor
    n = BARREL_END_Z - MUZZLE_Z
    md.box((-0.4, 5.1, MUZZLE_Z), (0.8, 0.8, n), "phosphate", inflate=0.05, pattern="slots")
    md.box((-0.4, 5.1, MUZZLE_Z), (0.8, 0.8, 0.3), "phosphate", inflate=0.12)        # front ring
    md.box((-0.15, 4.45, -21.6), (0.3, 0.6, 0.7), "phosphate")                       # bayonet lug


def _handguard(m: Model) -> None:
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECV_FRONT_Z))
    z0 = BAND_Z + 0.3
    hg.box((-0.75, STOCK_TOP_Y, z0), (1.5, 1.05, RECV_FRONT_Z - 0.6 - z0), "fiberglass")
    hg.box((-0.5, STOCK_TOP_Y + 1.05, z0 + 0.3), (1.0, 0.12, RECV_FRONT_Z - 1.2 - z0), "fiberglass")   # rounded crown
    hg.box((-0.8, STOCK_TOP_Y, RECV_FRONT_Z - 1.0), (1.6, 1.1, 0.25), "park_steel")  # handguard clip


def _sights(m: Model) -> None:
    # front: blade between two protective wings on the castle nut
    fs = m.bone("front_sight", pivot=(0, 6.0, FRONT_SIGHT_Z))
    z = FRONT_SIGHT_Z - 0.25
    fs.box((-0.6, 6.0, z), (1.2, 0.3, 0.5), "park_steel")                            # base
    fs.box((-0.6, 6.3, z), (0.2, SIGHT_Y + 0.15 - 6.3, 0.5), "park_steel")           # left wing
    fs.box((0.4, 6.3, z), (0.2, SIGHT_Y + 0.15 - 6.3, 0.5), "park_steel")            # right wing
    fs.box((-0.1, 6.3, FRONT_SIGHT_Z - 0.1), (0.2, SIGHT_Y - 6.3, 0.2), "park_steel")   # blade: TOP at SIGHT_Y

    # rear: aperture between two ears, elevation knob left, windage knob right.
    # The whole bone is hidden by the renderer while an optic is fitted.
    rs = m.bone("rear_sight", pivot=(0, 6.7, REAR_SIGHT_Z))
    y0, y1 = SIGHT_Y - 0.25, SIGHT_Y + 0.25
    rs.box((-0.9, 6.7, REAR_SIGHT_Z - 0.35), (0.25, 0.9, 0.7), "park_steel")         # left ear
    rs.box((0.65, 6.7, REAR_SIGHT_Z - 0.35), (0.25, 0.9, 0.7), "park_steel")         # right ear
    z = REAR_SIGHT_Z - 0.1
    rs.box((-0.4, 6.7, z), (0.8, y0 - 6.7, 0.2), "phosphate")                        # aperture arm
    rs.box((-0.4, y0, z), (0.15, 0.5, 0.2), "phosphate")                             # left of the hole
    rs.box((0.25, y0, z), (0.15, 0.5, 0.2), "phosphate")                             # right of the hole
    rs.box((-0.4, y1, z), (0.8, 0.15, 0.2), "phosphate")                             # top
    rs.box((-1.25, 6.6, REAR_SIGHT_Z - 0.3), (0.35, 0.6, 0.6), "phosphate", pattern="knurl")   # elevation knob
    rs.box((0.9, 6.6, REAR_SIGHT_Z - 0.3), (0.35, 0.6, 0.6), "phosphate", pattern="knurl")     # windage knob


def _bipod_clamp(m: Model) -> None:
    """The bipod's yoke round the gas cylinder (child of `att_bipod`: only drawn with the bipod),
    behind the spindle valve, with its clamp screw on the left."""
    bc = m.bone("bipod_clamp", parent="att_bipod", pivot=UNDERBARREL_MOUNT)
    y, z = UNDERBARREL_MOUNT[1], UNDERBARREL_MOUNT[2] - 1.0
    bc.box((-0.55, y, z), (1.1, 5.0 - y, 0.5), "att_metal")                          # yoke round the cylinder
    bc.box((-0.75, y + 0.2, z + 0.05), (0.2, 0.4, 0.4), "att_black", pattern="knurl")  # clamp screw (left)


def _trigger(m: Model) -> None:
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.2, 2.6, -0.85), (0.4, 0.95, 0.4), "barrel_steel")


def _magazine(m: Model) -> None:
    """Straight 20-round box magazine, leaning a little forward; bone pivot = its front lug."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    lean, piv = (6, 0, 0), (0, 4.4, -2.85)
    mg.box((-0.55, 0.45, -3.95), (1.1, 3.95, 2.2), "mag_steel", pattern="lines", rot=lean, pivot=piv)
    mg.box((-0.6, 0.25, -4.0), (1.2, 0.25, 2.3), "phosphate", rot=lean, pivot=piv)   # floor plate
    m.mag_grab = _rotate((0.0, 0.9, -2.85), piv, lean)                              # where the left fist takes it


def _rock_magazine(a, m: Model, base: float, t0: float = 0.0) -> None:
    """Turn the template's straight-down magazine swap into the M14 rock: at the two moments the
    magazine is 'just clear of the well' (base t0 + 0.5 going out, t0 + 1.3 coming back) it is
    instead tipped MAG_ROCK_DEG forward round its front lug and only slightly unhooked; the left
    hand follows the swing of the magazine's bottom. Re-keys `magazine` and `left_arm` on the
    built animation `a` (base length `base`), nothing else."""
    k = a.length / base
    t = lambda b: round(min(b * k, a.length), 4)
    piv, grab, lh = m.bones["magazine"].pivot, m.mag_grab, m.rig.left_hand
    rock = (MAG_ROCK_DEG, 0.0, 0.0)
    swung = _rotate(grab, piv, rock)
    hand = tuple(swung[i] - lh[i] + MAG_UNHOOK[i] for i in range(3))                # fist on the rocked magazine
    mag_pos, arm_pos = a.tracks["magazine"]["position"], a.tracks[T.LEFT_ARM]["position"]
    for bt, ease in ((t0 + 0.5, T.IN), (t0 + 1.3, T.OUT)):
        mag_pos[t(bt)] = (MAG_UNHOOK, ease)
        arm_pos[t(bt)] = (hand, ease)
    half = (MAG_ROCK_DEG * 0.5, 0.0, 0.0)
    a.tracks["magazine"]["rotation"] = {
        t(t0 + 0.38): (T.ZERO, "linear"), t(t0 + 0.5): (rock, T.OUT), t(t0 + 0.72): (half, T.IN),
        t(t0 + 1.0): (half, "linear"), t(t0 + 1.3): (rock, T.OUT), t(t0 + 1.45): (T.ZERO, T.IN)}


def _hold_open(a, base: float, cyc: dict) -> None:
    """reload_empty: the bolt stop holds the action open on the empty magazine. Re-key `op_rod`
    so it is back at OP_LOCKED from the first 0.05 base s (the rest-pose rule) until the right hand
    takes the handle, pulls it the last bit to OP_PULL and lets go; the hand meets the handle
    where it is locked. `cyc` = the hand cycle's base key times."""
    k = a.length / base
    t = lambda b: round(min(b * k, a.length), 4)
    locked, back = (0.0, 0.0, OP_LOCKED), (0.0, 0.0, OP_PULL)
    a.tracks["op_rod"]["position"] = {
        0.0: (T.ZERO, "linear"), t(0.05): (locked, T.OUT), t(cyc["grab"]): (locked, "linear"),
        t(cyc["back"]): (back, T.OUT), t(cyc["hold"]): (back, "linear"), t(cyc["fwd"]): (T.ZERO, T.IN)}
    arm = a.tracks[T.RIGHT_ARM]["position"]
    v, ease = arm[t(cyc["grab"])]
    arm[t(cyc["grab"])] = ((v[0], v[1], v[2] + OP_LOCKED), ease)
    # the hand lets the handle go: it stays back while the rod flies home, then returns to the wrist
    v, _ = arm[t(cyc["hold"])]
    arm[t(cyc["fwd"])] = ((v[0] + 0.4, v[1] - 0.2, v[2] - 0.5), T.IO)


def _animations(m: Model) -> dict:
    """Returns the reload key times in REAL seconds for the docs / sound timing."""
    T.idle(m)
    # the operating rod (and the bolt with it) cycles with every shot
    T.fire_auto(m, recoil=RECOIL, bolt_bone="op_rod", bolt_travel=OP_PULL * 0.8, length=FIRE_LENGTH,
                rise=MUZZLE_RISE)
    k = RELOAD_LENGTH / 2.2
    a = T.reload_mag(m, length=RELOAD_LENGTH, grab=m.mag_grab)
    _rock_magazine(a, m, 2.2)
    ke = RELOAD_EMPTY_LENGTH / 3.0
    e = T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, empty_action="cycle", bolt_bone="op_rod",
                     lift_deg=0.0, pull=OP_PULL, hand="right", knob=OP_KNOB, grab=m.mag_grab)
    _rock_magazine(e, m, 3.0)
    st = T.HandCycle.STRAIGHT
    base_cyc = {name: 1.72 + st[name] for name in st}          # the hand cycle: 1.0 base s from 1.72
    _hold_open(e, 3.0, base_cyc)
    cyc = lambda name: round(base_cyc[name] * ke, 3)
    return {
        "reload": {"mag_out": round(0.5 * k, 3), "mag_in": round(1.45 * k, 3), "mag_slap": round(1.6 * k, 3)},
        "reload_empty": {"action_locked_open": round(0.05 * ke, 3), "mag_out": round(0.5 * ke, 3),
                         "mag_in": round(1.45 * ke, 3), "mag_slap": round(1.6 * ke, 3),
                         "hand_on_handle": cyc("grab"), "handle_back": cyc("back"),
                         "handle_release": cyc("hold"), "handle_home": cyc("fwd"),
                         "hand_on_grip": cyc("end")},
    }


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_stock, _receiver, _barrel, _handguard, _sights, _trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT, underbarrel=UNDERBARREL_MOUNT)
    T.attach(m, *ATTACHMENTS)
    _bipod_clamp(m)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.0, 6.1, -2.0),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    m.reload_times = _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "scoped": att.loadout(m, "sniper_scope", "suppressor_762") | arms,
        "red_dot": att.loadout(m, "red_dot"),
        "bipod": att.loadout(m, "bipod") | arms,
        "bipod_scoped": att.loadout(m, "bipod", "sniper_scope"),
    }
    return m
