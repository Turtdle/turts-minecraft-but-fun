"""AKM / AK-47 - Soviet 7.62x39 mm assault rifle, curved 30-round steel magazine, ~600 rpm.

Same scale and conventions as the M4 / MP5 / FAMAS (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 880 mm = 26.3 units (modelled z -15.3 .. 11.0). Barrel 415 mm = 12.4 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: the tall stamped dark receiver with the ribbed dust cover on top, the long safety /
selector lever and the reciprocating charging handle on the RIGHT, light brown wood everywhere
else (fixed stock with a steel butt plate, pistol grip, lower handguard, gas-tube cover), the
gas tube above the barrel running into the angled gas block, the front sight post between its
two ears on a tall base near the muzzle, the tangent rear sight on its block in front of the
dust cover, a cleaning rod under the barrel, the slant muzzle brake, and the big curved
magazine that sweeps forward below the receiver.

Slots: OPTIC and MUZZLE. The receiver carries the factory side rail (dovetail on the LEFT, over
the trigger group); an optic comes on a low-profile side mount that clamps to it, rises up the
left side and bridges the dust cover with a short picatinny rail over the receiver. The mount is
built under each optic's `att_*` bone (`_side_mount`), so it is only drawn while an optic is
fitted; the tangent rear sight stays where it is and hides under an optic (bone `rear_sight`).

Arms, mounts, attachments and the animations come from templates.py. Two things are specific to
this gun and live here: the charging handle is part of the bolt carrier (bone `charging_handle`,
the carrier in the ejection port is its child `bolt`), so it cycles when firing; and the
magazine ROCKS out and in round its front lug (`_rock_magazine` re-keys the template's swap).
"""
import math

import attachments as att
import templates as T
from armorygen import Model, _rotate

GUN_ID = "ak47"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
RECV_BOTTOM_Y = 4.0
RECV_TOP_Y = 6.25          # top of the stamped receiver (the dust cover sits on it)
COVER_TOP_Y = 6.75         # top of the dust cover
MOUNT_RAIL_Y = 7.5         # top of the side mount's rail over the dust cover = optic mount height
SIGHT_Y = 7.5              # iron sight line: front post tip and the rear notch
MUZZLE_Z = -15.3           # front of the slant brake
BARREL_END_Z = -14.6       # muzzle mount (the slant brake screws on in front of it)
FRONT_SIGHT_Z = -13.9      # z centre of the front post
GAS_BLOCK_Z = -11.0        # front face of the gas block
HANDGUARD_FRONT_Z = -9.75  # front of the lower handguard (the retainer band sits here)
SIGHT_BLOCK_FRONT_Z = -6.0  # front of the rear sight block = rear of the gas-tube cover
RECV_FRONT_Z = -4.5        # front face of the receiver / trunnion
RECV_REAR_Z = 3.5          # rear face of the receiver (the stock starts here)
REAR_SIGHT_Z = -4.65       # z centre of the rear notch plate
BUTT_Z = 11.0              # rear face of the butt plate

# ---- pivots / mount points -------------------------------------------------------------
MAG_FRONT_Z, MAG_DEPTH = -3.9, 2.0      # front face of the magazine in the well, its front-back size
MAG_PIVOT = (0, RECV_BOTTOM_Y, MAG_FRONT_Z)   # the FRONT LUG: the magazine rocks round it
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
OPTIC_MOUNT = (0, MOUNT_RAIL_Y, -0.75)  # middle of the side mount's rail, over the dust cover
SIDE_RAIL_Z = (-1.75, 2.25)             # factory side rail on the left of the receiver (z from .. to)
CHARGE_Z = -3.4                         # z centre of the charging handle at rest
CHARGE_Y = 6.0
CHARGE_PIVOT = (0, CHARGE_Y, CHARGE_Z)
CHARGE_KNOB = (1.45, CHARGE_Y, CHARGE_Z)   # knob centre, right side
CHARGE_PULL = 2.75                      # travel of the handle / bolt carrier

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                      # centre of the pistol grip (same as the M4)
L_HAND = (0, 4.0, -7.25)                # under the lower handguard

# ---- attachments -------------------------------------------------------------------------
OPTICS = ("red_dot", "holographic", "micro_red_dot", "acog")
ATTACHMENTS = (*OPTICS, "suppressor_762", "compensator_762")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.1          # 600 rpm -> 0.1 s between shots
RECOIL = 1.0
MUZZLE_RISE = 2.6
RELOAD_LENGTH = 2.4
RELOAD_EMPTY_LENGTH = 2.9  # magazine swap, then the right hand racks the charging handle
MAG_ROCK_DEG = 24.0        # how far the magazine rocks forward round its front lug
MAG_UNHOOK = (0.0, -0.7, -0.35)   # magazine offset when it has just come off the lug


def _materials(m: Model) -> None:
    m.material("ak_steel", (44, 45, 50), 5, 0.74)       # stamped receiver, trunnions, sight blocks
    m.material("ak_cover", (52, 53, 58), 5, 0.76)       # dust cover, a shade lighter
    m.material("ak_wood", (172, 112, 60), 12, 0.76)     # light brown furniture
    m.material("ak_wood_dark", (150, 94, 50), 10, 0.76)  # gas-tube cover / grip, a shade darker
    m.material("barrel_steel", (66, 68, 75), 6, 0.72)
    m.material("bolt_steel", (132, 134, 140), 7, 0.72)  # bolt carrier, charging handle
    m.material("phosphate", (28, 28, 32), 4, 0.78)      # sights, lever, small parts
    m.material("mag_steel", (60, 62, 68), 6, 0.74)      # the curved ribbed magazine
    m.material("butt_steel", (78, 80, 86), 6, 0.72)     # butt plate, cleaning rod
    m.material("port", (14, 14, 16), 2, 0.9)            # ejection port, slots


def _receiver(m: Model) -> None:
    z0, length = RECV_FRONT_Z, RECV_REAR_Z - RECV_FRONT_Z
    h = RECV_TOP_Y - RECV_BOTTOM_Y
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, RECV_BOTTOM_Y, z0), (2, h, length), "ak_steel")                      # stamped receiver
    rc.box((-1.05, RECV_BOTTOM_Y, z0), (2.1, h + 0.25, 1.0), "ak_steel")             # front trunnion
    rc.box((-0.9, RECV_TOP_Y, z0 + 0.75), (1.8, COVER_TOP_Y - RECV_TOP_Y, length - 0.75), "ak_cover",
           pattern="lines")                                                         # ribbed dust cover
    rc.box((-0.6, COVER_TOP_Y, z0 + 1.25), (1.2, 0.15, length - 2.0), "ak_cover")    # its rounded crown
    rc.box((-0.5, RECV_TOP_Y, RECV_REAR_Z - 0.4), (1, 0.75, 0.4), "phosphate")       # cover catch button
    # right side: ejection port / carrier slot, and the long safety lever under it
    rc.box((0.5, 5.6, -3.9), (0.55, 0.8, 2.75), "port")                             # ejection port
    rc.box((0.95, CHARGE_Y - 0.2, -1.15), (0.1, 0.4, CHARGE_PULL), "port")           # handle slot behind the port
    rc.box((1.0, 4.75, -1.75), (0.1, 0.45, 4.0), "phosphate", rot=(-4, 0, 0),
           pivot=(1.0, 5.0, 2.0))                                                   # safety / selector lever
    rc.box((1.0, 4.55, -1.85), (0.3, 0.3, 0.6), "phosphate", rot=(-4, 0, 0),
           pivot=(1.0, 5.0, 2.0))                                                   # its finger tab
    rc.box((1.0, 4.75, 1.75), (0.15, 0.5, 0.5), "bolt_steel")                        # lever pivot
    # left side: rivets and the magazine dimple
    rc.box((-1.04, 4.6, -3.5), (0.04, 0.6, 1.5), "ak_cover")                         # magazine-well dimple
    for z in (-4.0, 1.0, 2.75):
        rc.box((-1.04, 5.5, z), (2.08, 0.25, 0.25), "bolt_steel")                    # rivets / pins
    z0, z1 = SIDE_RAIL_Z
    rc.box((-1.2, 4.85, z0), (0.2, 0.5, z1 - z0), "phosphate", pattern="lines")       # factory side rail (dovetail)
    # trigger guard, magazine catch, grip root
    rc.box((-0.375, 2.6, -1.75), (0.75, 0.2, 2.5), "ak_steel")                       # guard: bottom strip
    rc.box((-0.375, 2.6, -1.75), (0.75, 1.4, 0.25), "ak_steel")                      # guard: front strut
    rc.box((-0.25, 2.2, -1.55), (0.5, 0.9, 0.3), "phosphate", rot=(20, 0, 0),
           pivot=(0, 3.1, -1.4))                                                    # magazine catch paddle
    rc.box((-0.6, 3.4, 0.25), (1.2, 0.6, 2.0), "ak_steel")                           # grip root
    # rear sight block in front of the dust cover (over the chamber)
    rc.box((-0.7, 5.9, SIGHT_BLOCK_FRONT_Z), (1.4, 1.0, RECV_FRONT_Z + 0.75 - SIGHT_BLOCK_FRONT_Z), "ak_steel")

    # bolt carrier: the charging handle is part of it, so ONE bone moves both. `bolt` (the part
    # seen in the ejection port) is its child for the Java side / other tooling.
    ch = m.bone("charging_handle", pivot=CHARGE_PIVOT)
    ch.box((1.0, CHARGE_Y - 0.2, CHARGE_Z - 0.2), (0.3, 0.4, 0.4), "bolt_steel")                 # handle stem
    ch.box((1.2, CHARGE_Y - 0.25, CHARGE_Z - 0.3), (0.5, 0.5, 0.6), "bolt_steel", pattern="lines")  # curved knob
    bo = m.bone("bolt", parent="charging_handle", pivot=(0.5, BORE_Y, -2.5))
    bo.box((0.1, 5.6, -3.9), (0.98, 0.8, 2.75), "bolt_steel")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECV_FRONT_Z))
    ba.box((-0.375, 5.125, BARREL_END_Z), (0.75, 0.75, RECV_FRONT_Z - BARREL_END_Z), "barrel_steel")
    # gas tube above the barrel, from the rear sight block into the gas block
    ba.box((-0.3, 6.15, GAS_BLOCK_Z + 0.5), (0.6, 0.6, SIGHT_BLOCK_FRONT_Z - GAS_BLOCK_Z - 0.5), "barrel_steel")
    # gas block: collar round the barrel and the angled port block up to the gas tube
    ba.box((-0.5, 5.0, GAS_BLOCK_Z), (1.0, 1.0, 0.9), "ak_steel")
    ba.wedge((0, 5.6, GAS_BLOCK_Z - 0.15), (0, 6.5, GAS_BLOCK_Z + 0.8), 0.8, 0.8, "ak_steel")
    ba.box((-0.4, 6.05, GAS_BLOCK_Z + 0.4), (0.8, 0.8, 0.6), "ak_steel")             # tube socket
    # handguard retainer band
    ba.box((-0.6, 4.4, HANDGUARD_FRONT_Z - 0.25), (1.2, 1.6, 0.25), "ak_steel")
    # front sight base on the barrel
    ba.box((-0.5, 5.0, FRONT_SIGHT_Z - 0.45), (1.0, 1.0, 0.9), "ak_steel")           # collar
    ba.box((-0.3, 6.0, FRONT_SIGHT_Z - 0.35), (0.6, 0.7, 0.7), "ak_steel")           # tower
    # cleaning rod under the barrel
    ba.box((-0.1, 4.6, BARREL_END_Z + 0.1), (0.2, 0.2, HANDGUARD_FRONT_Z - BARREL_END_Z - 0.1), "butt_steel")
    ba.box((-0.2, 4.6, BARREL_END_Z + 0.45), (0.4, 0.5, 0.3), "ak_steel")            # bayonet lug / rod guide

    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # AKM slant brake
    n = BARREL_END_Z - MUZZLE_Z
    md.box((-0.45, 5.05, MUZZLE_Z + 0.3), (0.9, 0.9, n - 0.3), "phosphate")          # threaded collar
    md.box((-0.45, 5.05, MUZZLE_Z), (0.9, 0.45, 0.3), "phosphate")                   # the lip: longer at the bottom
    md.box((-0.2, 5.05, MUZZLE_Z + 0.45), (0.4, 0.9, 0.2), "port", inflate=0.03)     # locking notch


def _handguard(m: Model) -> None:
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECV_FRONT_Z))
    z0 = HANDGUARD_FRONT_Z
    hg.box((-1.125, 4.25, z0), (2.25, 1.6, RECV_FRONT_Z - z0), "ak_wood")            # lower handguard
    hg.box((-0.9, 4.0, z0 + 0.5), (1.8, 0.25, RECV_FRONT_Z - z0 - 1.0), "ak_wood")   # its rounded belly
    hg.box((-1.2, 4.9, z0 + 1.0), (2.4, 0.6, 3.0), "ak_wood")                        # palm swells
    # gas-tube cover (upper handguard)
    hg.box((-0.8, 5.85, z0 + 0.25), (1.6, 1.15, SIGHT_BLOCK_FRONT_Z - z0 - 0.25), "ak_wood_dark")
    hg.box((-0.55, 7.0, z0 + 0.5), (1.1, 0.12, SIGHT_BLOCK_FRONT_Z - z0 - 0.75), "ak_wood_dark")
    hg.box((-0.85, 5.85, SIGHT_BLOCK_FRONT_Z - 0.25), (1.7, 1.2, 0.25), "ak_steel")  # rear ferrule
    hg.box((-0.85, 5.85, z0 + 0.25), (1.7, 1.2, 0.25), "ak_steel")                   # front ferrule


def _sights(m: Model) -> None:
    # front sight: the post between two protective ears on the tower
    fs = m.bone("front_sight", pivot=(0, 6.7, FRONT_SIGHT_Z))
    z = FRONT_SIGHT_Z - 0.3
    fs.box((-0.65, 6.6, z), (1.3, 0.3, 0.6), "phosphate")                           # cross bar
    fs.box((-0.65, 6.9, z), (0.25, SIGHT_Y + 0.35 - 6.9, 0.6), "phosphate")          # left ear
    fs.box((0.4, 6.9, z), (0.25, SIGHT_Y + 0.35 - 6.9, 0.6), "phosphate")            # right ear
    fs.box((-0.1, 6.9, FRONT_SIGHT_Z - 0.1), (0.2, SIGHT_Y - 6.9, 0.2), "phosphate")  # post: TOP at SIGHT_Y

    # tangent rear sight: the leaf lies on the block, hinged at its front, the notch at its rear
    rs = m.bone("rear_sight", pivot=(0, 6.9, REAR_SIGHT_Z))
    rs.box((-0.3, 6.9, SIGHT_BLOCK_FRONT_Z + 0.15), (0.6, 0.2, REAR_SIGHT_Z - SIGHT_BLOCK_FRONT_Z - 0.15),
           "phosphate", pattern="lines")                                            # leaf with range marks
    rs.box((-0.55, 6.85, -5.2), (1.1, 0.3, 0.3), "phosphate")                        # slider
    z = REAR_SIGHT_Z - 0.1
    rs.box((-0.45, 6.9, z), (0.9, SIGHT_Y - 0.2 - 6.9, 0.2), "phosphate")            # notch plate: below the notch
    rs.box((-0.45, SIGHT_Y - 0.2, z), (0.3, 0.35, 0.2), "phosphate")                 # left of the notch
    rs.box((0.15, SIGHT_Y - 0.2, z), (0.3, 0.35, 0.2), "phosphate")                  # right of the notch


def _stock(m: Model) -> None:
    """Fixed wooden stock: tapers from the receiver down to the steel butt plate."""
    st = m.bone("stock", pivot=(0, BORE_Y, RECV_REAR_Z))
    z0, z1 = RECV_REAR_Z, BUTT_Z - 0.5
    st.box((-0.9, 4.1, z0), (1.8, 2.0, 0.5), "ak_steel")                             # rear trunnion tang
    st.wedge((0, 5.25, z0 + 0.25), (0, 4.55, z1), 1.5, 1.6, "ak_wood")               # comb
    st.wedge((0, 4.85, z0 + 0.4), (0, 3.25, z1), 1.4, 1.6, "ak_wood")                # belly
    st.box((-0.8, 2.35, z1 - 0.1), (1.6, 3.1, BUTT_Z - z1 + 0.1), "butt_steel")      # butt plate
    st.box((-0.25, 4.0, BUTT_Z), (0.5, 0.6, 0.06), "phosphate")                      # cleaning-kit trap
    st.box((-0.95, 3.2, 8.75), (0.2, 0.8, 0.4), "butt_steel")                        # sling swivel (left)


def _side_mount(m: Model, optic: str) -> None:
    """Low-profile AK side mount, built as a child of `att_<optic>` so the game only draws it while
    that optic is fitted (one copy per optic; the copies are never shown together). Clamp on the
    factory side rail with its throw lever, an upright arm up the left side, a deck bridging the
    dust cover (0.05 clear of its crown) and the short picatinny rail the optic sits on."""
    sm = m.bone(f"side_mount_{optic}", parent=f"att_{optic}", pivot=OPTIC_MOUNT)
    y, z0, z1 = MOUNT_RAIL_Y, SIDE_RAIL_Z[0] + 0.5, SIDE_RAIL_Z[1] - 0.5
    sm.box((-1.6, 4.75, z0), (0.4, 0.85, z1 - z0), "att_metal")                      # clamp on the side rail
    sm.box((-1.8, 4.95, z0 + 0.5), (0.2, 0.4, 2.0), "att_black", pattern="lines")    # throw lever
    sm.box((-1.6, 5.6, z0 + 0.5), (0.35, y - 0.55 - 5.6, 2.0), "att_metal")          # upright arm up the left side
    sm.box((-1.6, y - 0.55, -2.5), (2.35, 0.25, 4.0), "att_metal")                   # deck over the dust cover
    sm.box((-0.6, y - 0.3, -3.25), (1.2, 0.3, 5.0), "att_metal", pattern="rails")    # rail: top = MOUNT_RAIL_Y


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-0.7, 0.3, 0.2), (1.4, 3.7, 1.8), "ak_wood_dark", rot=(-20, 0, 0), pivot=(0, 4, 1))
    gr.box((-0.75, 0.2, 0.15), (1.5, 0.3, 1.9), "ak_wood_dark", rot=(-20, 0, 0), pivot=(0, 4, 1))  # flared base
    tr = m.bone("trigger", pivot=(0, 4.0, -0.5))
    tr.box((-0.2, 2.9, -0.9), (0.4, 1.1, 0.45), "barrel_steel")


def _magazine(m: Model) -> None:
    """The curved 30-round steel magazine: an arc of five segments, 2 deep, leaning further
    forward the lower it gets, about 4.5 units below the receiver. Bone pivot = the front lug."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    y, z = 4.6, MAG_FRONT_Z + MAG_DEPTH / 2                                         # top centre, inside the well
    seg, tilts = 1.15, (6, 14, 24, 34, 42)
    for i, tilt in enumerate(tilts):
        r = math.radians(tilt)
        y1, z1 = y - seg * math.cos(r), z - seg * math.sin(r)
        over = 0.2 if i < len(tilts) - 1 else 0.0                                   # overlap closes the outer gaps
        end = (0, y1 - over * math.cos(r), z1 - over * math.sin(r))
        mg.wedge((0, y, z), end, 1.2 - 0.03 * i, MAG_DEPTH, "mag_steel",
                 pattern="lines" if i in (1, 2, 3) else "none")
        y, z = y1, z1
    r = math.radians(tilts[-1])
    mg.wedge((0, y, z), (0, y - 0.25 * math.cos(r), z - 0.25 * math.sin(r)), 1.3, MAG_DEPTH + 0.2,
             "phosphate")                                                           # floor plate
    m.mag_grab = (0.0, y + 0.6, z + 0.5)                                            # where the left fist takes it


def _rock_magazine(a, m: Model, base: float, t0: float = 0.0) -> None:
    """Turn the template's straight-down magazine swap into the AK rock: at the two moments the
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


def _animations(m: Model) -> dict:
    """Returns the reload key times in REAL seconds for the docs / sound timing."""
    T.idle(m)
    # the charging handle is the bolt carrier: it cycles with every shot
    T.fire_auto(m, recoil=RECOIL, bolt_bone="charging_handle", bolt_travel=CHARGE_PULL * 0.8,
                length=FIRE_LENGTH, rise=MUZZLE_RISE)
    k = RELOAD_LENGTH / 2.2
    a = T.reload_mag(m, length=RELOAD_LENGTH, grab=m.mag_grab)
    _rock_magazine(a, m, 2.2)
    # empty (no bolt hold-open on an AK): same swap, then the RIGHT hand racks the charging handle
    ke = RELOAD_EMPTY_LENGTH / 3.0
    e = T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, empty_action="cycle",
                     bolt_bone="charging_handle", lift_deg=0.0, pull=CHARGE_PULL, hand="right",
                     knob=CHARGE_KNOB, grab=m.mag_grab)
    _rock_magazine(e, m, 3.0)
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
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    for optic in OPTICS:
        _side_mount(m, optic)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, 6.0, -2.5),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    m.reload_times = _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "suppressed": att.loadout(m, "suppressor_762") | arms,
        "red_dot": att.loadout(m, "red_dot"),
        "holographic": att.loadout(m, "holographic") | arms,
        "micro_red_dot": att.loadout(m, "micro_red_dot") | arms,
        "acog": att.loadout(m, "acog", "compensator_762") | arms,
    }
    return m
