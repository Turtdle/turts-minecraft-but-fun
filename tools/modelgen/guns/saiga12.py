"""Kalashnikov Saiga-12 - AK-pattern gas-operated semi-auto 12 ga, straight 8-round box magazine.

Same scale and conventions as the AK-47 (see ak47.py, and the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1020 mm = 30.5 units (z -19.5 .. 11.0), 19 in barrel = 14.4 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: the AK's stamped receiver with the ribbed dust cover, the long safety lever and the
reciprocating charging handle on the RIGHT, but all black: polymer fixed stock with a rubber butt
pad, polymer pistol grip, one-piece vented polymer handguard under the barrel with the short gas
tube bare above it; a long, thick shotgun barrel running on to the AK front sight block near the
muzzle and a thread protector; the tangent rear leaf on the trunnion block in front of the dust
cover; and the fat, square, straight 8-round magazine canted slightly forward.

Slot: OPTIC only. The receiver has the same datum lines as ak47.py on purpose, so the factory side
rail (LEFT) and the optic side mount ARE the AK-47's: `_side_mount` and the magazine rock
(`_rock_magazine`) are taken from ak47.py itself (single source: change them there).
"""
import importlib.util
import math
from pathlib import Path

import attachments as att
import templates as T
from armorygen import Model


def _ak():
    """guns/ak47.py as a module (the guns folder is not a package): its side mount, its magazine
    rock and its receiver datum lines are reused here."""
    spec = importlib.util.spec_from_file_location("gun_ak47_shared", Path(__file__).with_name("ak47.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


AK = _ak()
GUN_ID = "saiga12"

# ---- datum lines (receiver = the AK-47's) ------------------------------------------------
BORE_Y = T.BORE_Y
RECV_BOTTOM_Y, RECV_TOP_Y, COVER_TOP_Y = AK.RECV_BOTTOM_Y, AK.RECV_TOP_Y, AK.COVER_TOP_Y
RECV_FRONT_Z, RECV_REAR_Z = AK.RECV_FRONT_Z, AK.RECV_REAR_Z
SIGHT_BLOCK_FRONT_Z = AK.SIGHT_BLOCK_FRONT_Z
SIDE_RAIL_Z = AK.SIDE_RAIL_Z
OPTIC_MOUNT = AK.OPTIC_MOUNT            # middle of the side mount's rail, over the dust cover
SIGHT_Y = 7.5              # iron sight line: front post tip and the rear notch
REAR_SIGHT_Z = AK.REAR_SIGHT_Z          # z centre of the rear notch plate
MUZZLE_Z = -19.5           # front of the thread protector
FRONT_SIGHT_Z = -18.1      # z centre of the front post
GAS_BLOCK_Z = -11.75       # front face of the gas block
HANDGUARD_FRONT_Z = -10.75
BUTT_Z = 11.0              # rear face of the butt pad

# ---- magazine: straight, fat, square --------------------------------------------------------
MAG_FRONT_Z, MAG_DEPTH, MAG_WIDTH = -4.2, 2.5, 1.5
MAG_LEN, MAG_CANT = 6.0, 10.0           # length from its top (inside the well), forward cant in degrees
MAG_PIVOT = (0, RECV_BOTTOM_Y, MAG_FRONT_Z)   # the FRONT LUG: the magazine rocks round it
# ---- charging handle = bolt carrier (right side) ----------------------------------------------
CHARGE_Z, CHARGE_Y = -3.6, 6.0
CHARGE_PIVOT = (0, CHARGE_Y, CHARGE_Z)
CHARGE_KNOB = (1.45, CHARGE_Y, CHARGE_Z)
CHARGE_PULL = 3.0                       # a 12 ga shell is long
PORT_Z = (-4.2, -1.2)                   # ejection port (z from .. to)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                      # centre of the pistol grip (same as the AK)
L_HAND = (0, 4.0, -8.0)                 # under the handguard

OPTICS = ("red_dot", "holographic", "micro_red_dot", "acog")

# ---- animation tuning --------------------------------------------------------------------
FIRE_LENGTH = 0.22         # semi-auto; keep below the shot interval
RECOIL, MUZZLE_RISE = 1.5, 4.0
RELOAD_LENGTH = 2.4
RELOAD_EMPTY_LENGTH = 3.0  # magazine swap, then the right hand racks the charging handle


def _materials(m: Model) -> None:
    m.material("sg_steel", (40, 41, 46), 5, 0.74)        # stamped receiver, trunnions, blocks
    m.material("sg_cover", (48, 49, 54), 5, 0.76)        # dust cover, a shade lighter
    m.material("sg_polymer", (28, 28, 31), 4, 0.80)      # stock, grip, handguard
    m.material("sg_mag", (34, 34, 38), 4, 0.78)          # polymer magazine
    m.material("barrel_steel", (58, 60, 66), 6, 0.72)
    m.material("bolt_steel", (132, 134, 140), 7, 0.72)   # bolt carrier, charging handle
    m.material("phosphate", (24, 24, 27), 4, 0.78)       # sights, lever, small parts
    m.material("port", (14, 14, 16), 2, 0.9)


def _receiver(m: Model) -> None:
    z0, length = RECV_FRONT_Z, RECV_REAR_Z - RECV_FRONT_Z
    h = RECV_TOP_Y - RECV_BOTTOM_Y
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, RECV_BOTTOM_Y, z0), (2, h, length), "sg_steel")                      # stamped receiver
    rc.box((-1.05, RECV_BOTTOM_Y, z0), (2.1, h + 0.25, 1.0), "sg_steel")             # front trunnion
    rc.box((-0.9, RECV_TOP_Y, z0 + 0.75), (1.8, COVER_TOP_Y - RECV_TOP_Y, length - 0.75), "sg_cover",
           pattern="lines")                                                         # ribbed dust cover
    rc.box((-0.6, COVER_TOP_Y, z0 + 1.25), (1.2, 0.15, length - 2.0), "sg_cover")    # its rounded crown
    rc.box((-0.5, RECV_TOP_Y, RECV_REAR_Z - 0.4), (1, 0.75, 0.4), "phosphate")       # cover catch button
    # right side: the long ejection port, the handle slot behind it, the big safety lever under it
    p0, p1 = PORT_Z
    rc.box((0.5, 5.5, p0), (0.55, 0.9, p1 - p0), "port")                            # ejection port
    rc.box((0.95, CHARGE_Y - 0.2, p1), (0.1, 0.4, CHARGE_Z + CHARGE_PULL + 0.3 - p1), "port")   # handle slot
    lever = dict(rot=(-4, 0, 0), pivot=(1.0, 5.0, 2.0))
    rc.box((1.0, 4.7, -1.9), (0.1, 0.5, 4.15), "phosphate", **lever)                 # safety lever
    rc.box((1.0, 4.45, -2.0), (0.35, 0.35, 0.7), "phosphate", **lever)               # its finger tab
    rc.box((1.0, 4.75, 1.75), (0.15, 0.5, 0.5), "bolt_steel")                        # lever pivot
    # left side: rivets and the factory side rail (dovetail), exactly where the AK-47 has it
    for z in (-4.0, 1.0, 2.75):
        rc.box((-1.04, 5.5, z), (2.08, 0.25, 0.25), "bolt_steel")                    # rivets / pins
    z0r, z1r = SIDE_RAIL_Z
    rc.box((-1.2, 4.85, z0r), (0.2, 0.5, z1r - z0r), "phosphate", pattern="lines")    # side rail
    # trigger guard, magazine catch, grip root
    rc.box((-0.375, 2.6, -1.5), (0.75, 0.2, 2.25), "sg_steel")                       # guard: bottom strip
    rc.box((-0.25, 2.3, -1.6), (0.5, 1.7, 0.3), "phosphate", rot=(14, 0, 0),
           pivot=(0, 4.0, -1.45))                                                   # magazine catch paddle
    rc.box((-0.6, 3.4, 0.25), (1.2, 0.6, 2.0), "sg_steel")                           # grip root
    # rear sight block in front of the dust cover (over the chamber)
    rc.box((-0.7, 5.9, SIGHT_BLOCK_FRONT_Z), (1.4, 1.0, RECV_FRONT_Z + 0.75 - SIGHT_BLOCK_FRONT_Z), "sg_steel")

    # bolt carrier: the charging handle is part of it, so ONE bone moves both (as on the AK-47)
    ch = m.bone("charging_handle", pivot=CHARGE_PIVOT)
    ch.box((1.0, CHARGE_Y - 0.2, CHARGE_Z - 0.2), (0.3, 0.4, 0.4), "bolt_steel")                 # handle stem
    ch.box((1.2, CHARGE_Y - 0.25, CHARGE_Z - 0.3), (0.5, 0.5, 0.6), "bolt_steel", pattern="lines")  # knob
    bo = m.bone("bolt", parent="charging_handle", pivot=(0.5, BORE_Y, sum(PORT_Z) / 2))
    bo.box((0.1, 5.5, p0), (0.98, 0.9, p1 - p0), "bolt_steel")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECV_FRONT_Z))
    ba.box((-0.45, 5.05, MUZZLE_Z + 0.4), (0.9, 0.9, RECV_FRONT_Z - MUZZLE_Z - 0.4), "barrel_steel")   # 19 in barrel
    ba.box((-0.45, 5.05, MUZZLE_Z), (0.9, 0.9, 0.6), "phosphate", pattern="knurl", inflate=0.1)       # thread protector
    # short gas tube above the barrel, from the rear sight block into the gas block
    ba.box((-0.3, 6.15, GAS_BLOCK_Z + 0.5), (0.6, 0.6, SIGHT_BLOCK_FRONT_Z - GAS_BLOCK_Z - 0.5), "barrel_steel")
    ba.box((-0.55, 4.95, GAS_BLOCK_Z), (1.1, 1.1, 1.0), "sg_steel")                  # gas block: collar
    ba.box((-0.4, 6.05, GAS_BLOCK_Z + 0.1), (0.8, 0.8, 0.9), "sg_steel")             # tube socket
    ba.box((-0.25, 6.2, GAS_BLOCK_Z - 0.2), (0.5, 0.5, 0.3), "phosphate", pattern="knurl")   # gas regulator knob
    # front sight block near the muzzle (the post and its ears are the `front_sight` bone)
    ba.box((-0.55, 4.95, FRONT_SIGHT_Z - 0.45), (1.1, 1.1, 0.9), "sg_steel")         # collar
    ba.box((-0.3, 6.05, FRONT_SIGHT_Z - 0.35), (0.6, 0.65, 0.7), "sg_steel")         # tower


def _handguard(m: Model) -> None:
    """One-piece black polymer handguard under the barrel; the gas tube stays bare above it."""
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECV_FRONT_Z))
    z0, length = HANDGUARD_FRONT_Z, RECV_FRONT_Z - HANDGUARD_FRONT_Z
    hg.box((-1.15, 4.25, z0), (2.3, 1.75, length), "sg_polymer")                     # body
    hg.box((-0.9, 3.95, z0 + 0.5), (1.8, 0.3, length - 1.0), "sg_polymer", pattern="lines")   # ribbed belly
    hg.box((-1.2, 4.2, z0 - 0.2), (2.4, 1.85, 0.3), "sg_steel")                      # front retainer
    for x in (-1.2, 1.15):                                                          # cooling vents, both sides
        for i in range(3):
            hg.box((x, 5.25, z0 + 0.9 + 1.7 * i), (0.05, 0.4, 1.1), "port")


def _sights(m: Model) -> None:
    # front sight: the post between two protective ears on the tower
    fs = m.bone("front_sight", pivot=(0, 6.7, FRONT_SIGHT_Z))
    z = FRONT_SIGHT_Z - 0.3
    fs.box((-0.65, 6.6, z), (1.3, 0.3, 0.6), "phosphate")                           # cross bar
    fs.box((-0.65, 6.9, z), (0.25, SIGHT_Y + 0.35 - 6.9, 0.6), "phosphate")          # left ear
    fs.box((0.4, 6.9, z), (0.25, SIGHT_Y + 0.35 - 6.9, 0.6), "phosphate")            # right ear
    fs.box((-0.1, 6.9, FRONT_SIGHT_Z - 0.1), (0.2, SIGHT_Y - 6.9, 0.2), "phosphate")  # post: TOP at SIGHT_Y

    # tangent rear leaf on the block, hinged at its front, the notch at its rear
    rs = m.bone("rear_sight", pivot=(0, 6.9, REAR_SIGHT_Z))
    rs.box((-0.3, 6.9, SIGHT_BLOCK_FRONT_Z + 0.15), (0.6, 0.2, REAR_SIGHT_Z - SIGHT_BLOCK_FRONT_Z - 0.15),
           "phosphate", pattern="lines")                                            # leaf with range marks
    rs.box((-0.55, 6.85, -5.2), (1.1, 0.3, 0.3), "phosphate")                        # slider
    z = REAR_SIGHT_Z - 0.1
    rs.box((-0.45, 6.9, z), (0.9, SIGHT_Y - 0.2 - 6.9, 0.2), "phosphate")            # notch plate: below the notch
    rs.box((-0.45, SIGHT_Y - 0.2, z), (0.3, 0.35, 0.2), "phosphate")                 # left of the notch
    rs.box((0.15, SIGHT_Y - 0.2, z), (0.3, 0.35, 0.2), "phosphate")                  # right of the notch


def _stock(m: Model) -> None:
    """Fixed black polymer stock, AK pattern, with a rubber butt pad."""
    st = m.bone("stock", pivot=(0, BORE_Y, RECV_REAR_Z))
    z0, z1 = RECV_REAR_Z, BUTT_Z - 0.6
    st.box((-0.9, 4.1, z0), (1.8, 2.0, 0.5), "sg_steel")                             # rear trunnion tang
    st.wedge((0, 5.25, z0 + 0.25), (0, 4.6, z1), 1.5, 1.6, "sg_polymer")             # comb
    st.wedge((0, 4.85, z0 + 0.4), (0, 3.3, z1), 1.4, 1.6, "sg_polymer")              # belly
    st.box((-0.8, 2.4, z1 - 0.1), (1.6, 3.1, 0.3), "sg_polymer")                     # butt plate
    st.box((-0.8, 2.4, z1 + 0.2), (1.6, 3.1, BUTT_Z - z1 - 0.2), "rubber", pattern="lines")   # butt pad
    st.box((-0.95, 3.3, 8.75), (0.2, 0.8, 0.4), "sg_steel")                          # sling swivel (left)


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-0.7, 0.3, 0.2), (1.4, 3.7, 1.8), "sg_polymer", pattern="stipple", rot=(-20, 0, 0), pivot=(0, 4, 1))
    gr.box((-0.75, 0.2, 0.15), (1.5, 0.3, 1.9), "sg_polymer", rot=(-20, 0, 0), pivot=(0, 4, 1))   # flared base
    tr = m.bone("trigger", pivot=(0, 4.0, -0.5))
    tr.box((-0.2, 2.9, -0.9), (0.4, 1.1, 0.45), "barrel_steel")


def _magazine(m: Model) -> None:
    """Straight 8-round box: much wider and squarer than a rifle magazine, canted MAG_CANT forward.
    Its top 0.6 sits inside the well. Bone pivot = the front lug (it rocks in like an AK's)."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    r = math.radians(MAG_CANT)
    along = lambda d: (0, 4.6 - d * math.cos(r), MAG_FRONT_Z + MAG_DEPTH / 2 - d * math.sin(r))
    mg.wedge(along(0), along(MAG_LEN), MAG_WIDTH, MAG_DEPTH, "sg_mag")                          # body
    mg.wedge(along(1.4), along(MAG_LEN - 0.6), MAG_WIDTH + 0.12, MAG_DEPTH - 0.8, "sg_mag",
             pattern="lines")                                                                # side ribs
    mg.wedge(along(MAG_LEN), along(MAG_LEN + 0.3), MAG_WIDTH + 0.15, MAG_DEPTH + 0.2, "phosphate")   # floor plate
    grab = along(MAG_LEN - 0.6)
    m.mag_grab = (0.0, grab[1], grab[2] + 0.5)                                       # where the left fist takes it


def _animations(m: Model) -> dict:
    """Returns the reload key times in REAL seconds for the docs / sound timing."""
    T.idle(m)
    # the charging handle is the bolt carrier: it cycles with every shot
    T.fire_auto(m, recoil=RECOIL, bolt_bone="charging_handle", bolt_travel=CHARGE_PULL * 0.8,
                length=FIRE_LENGTH, rise=MUZZLE_RISE)
    k = RELOAD_LENGTH / 2.2
    a = T.reload_mag(m, length=RELOAD_LENGTH, grab=m.mag_grab)
    AK._rock_magazine(a, m, 2.2)
    # empty (no bolt hold-open): same swap, then the RIGHT hand racks the charging handle
    ke = RELOAD_EMPTY_LENGTH / 3.0
    e = T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, empty_action="cycle",
                     bolt_bone="charging_handle", lift_deg=0.0, pull=CHARGE_PULL, hand="right",
                     knob=CHARGE_KNOB, grab=m.mag_grab)
    AK._rock_magazine(e, m, 3.0)
    st = T.HandCycle.STRAIGHT
    cyc = lambda name: round((1.72 + st[name]) * ke, 3)        # the hand cycle: 1.0 base s from 1.72
    return {
        "reload": {"mag_out": round(0.5 * k, 3), "mag_in": round(1.45 * k, 3), "mag_slap": round(1.6 * k, 3)},
        "reload_empty": {"mag_out": round(0.5 * ke, 3), "mag_in": round(1.45 * ke, 3),
                         "mag_slap": round(1.6 * ke, 3), "hand_on_handle": cyc("grab"),
                         "handle_back": cyc("back"), "handle_release": cyc("hold"),
                         "handle_home": cyc("fwd"), "hand_on_grip": cyc("end")},
    }


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_receiver, _barrel, _handguard, _sights, _stock, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT)
    T.attach(m, *OPTICS)
    for optic in OPTICS:
        AK._side_mount(m, optic)                      # the AK side mount, under each att_<optic>
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, 6.0, sum(PORT_Z) / 2),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    m.reload_times = _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "red_dot": att.loadout(m, "red_dot") | arms,
        "holographic": att.loadout(m, "holographic"),
        "micro_red_dot": att.loadout(m, "micro_red_dot") | arms,
        "acog": att.loadout(m, "acog") | arms,
    }
    return m
