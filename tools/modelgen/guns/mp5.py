"""Heckler & Koch MP5A5 - 9x19 mm roller-delayed SMG, 30 rounds, ~800 rpm, retractable stock.

Same scale and conventions as the Scorpion EVO 3 / M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 700 mm with the stock out = 21 units (modelled 21.0). Barrel 225 mm = 6.7
units (chamber at about z -3.5 to the muzzle at z -10.25).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: a slim stamped-steel receiver with a rounded top, the COCKING TUBE running on from
it above the barrel to the front-sight tower, the cocking handle on the LEFT at the front of
that tube (above the handguard), a slim polymer handguard under the tube, a hooded ring front
sight, a rotary drum rear sight, the polymer Navy trigger group with its pistol grip, the long
CURVED steel magazine in front of the trigger guard, a short 3-lug barrel stub, and the A5
retractable stock: an end cap, two steel struts along the receiver sides and a butt plate
(modelled extended).

No rails: the optic sits where the HK claw mount clamps (top of the receiver), the suppressor
goes on the 3-lug barrel. Slots: OPTIC and MUZZLE only.

Arms, mounts, attachments and all animations come from templates.py, so mod-wide changes are
made there. Everything gun-specific is a constant in the block below; the part builders only
read those constants.
"""
import math

import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "mp5"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
TOP_Y = 6.75               # top of the receiver = top of the cocking tube (the claw mount sits here)
TUBE_Y = 6.25              # axis of the cocking tube
SIGHT_Y = 7.5              # iron sight line: front post tip, centre of the ring and of the drum aperture
MUZZLE_Z = -10.25          # front of the 3-lug barrel (thread protector)
BARREL_END_Z = -9.25       # where the 3 lugs start = muzzle mount (the suppressor clamps over them)
TOWER_FRONT_Z = -9.0       # front face of the front-sight tower
HANDGUARD_FRONT_Z = -8.25  # front face of the handguard = rear face of the tower
RECEIVER_FRONT_Z = -4.25   # handguard / receiver seam
MAGWELL_FRONT_Z = -3.25
MAGWELL_REAR_Z = -1.75     # = front of the trigger group
RECEIVER_REAR_Z = 3.0      # rear face of the receiver (the stock end cap sits behind it)
FRONT_SIGHT_Z = -8.625     # z centre of the front ring
REAR_SIGHT_Z = 2.0         # z centre of the rear drum
STOCK_CAP_REAR_Z = 4.5     # rear face of the stock end cap
STOCK_REAR_Z = 10.75       # rear face of the butt plate (stock extended)

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, 3.25, -2.5)             # bottom centre of the magazine well
STOCK_PIVOT = (0, BORE_Y, RECEIVER_REAR_Z)
OPTIC_MOUNT = (0, TOP_Y, -1.25)         # claw-mount position: receiver top, in front of the drum
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
CHARGE_Z = -7.25                        # z centre of the cocking-handle knob at rest
CHARGE_KNOB = (-1.375, TUBE_Y, CHARGE_Z)  # knob centre (left side, at the height of the tube axis)
CHARGE_PULL = 2.5                       # travel of the handle in its slot
CHARGE_LOCK_DEG = 40.0                  # how far it turns up into the locking notch

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                      # centre of the pistol grip (same as the M4 / Scorpion)
L_HAND = (0, 4.0, -6.0)                 # under the handguard

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "holographic", "micro_red_dot", "suppressor_9mm")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.07         # 800 rpm -> 0.075 s between shots; stay below it
RECOIL = 0.45
MUZZLE_RISE = 1.4
BOLT_TRAVEL = 1.75
RELOAD_LENGTH = 2.2
RELOAD_EMPTY_LENGTH = 2.8  # the HK slap: lock the handle back, swap the magazine, slap it down


def _materials(m: Model) -> None:
    m.material("steel_black", (38, 39, 44), 5, 0.74)    # painted stamped-steel receiver, tube, tower
    m.material("poly", (52, 52, 55), 4, 0.80)           # handguard, trigger group, grip, butt plate
    m.material("hg_poly", (62, 62, 65), 4, 0.80)        # handguard: a shade lighter so it reads apart
    m.material("barrel_steel", (80, 82, 90), 6, 0.72)
    m.material("bolt_steel", (120, 122, 128), 7, 0.72)  # bolt carrier seen in the ejection port
    m.material("phosphate", (28, 28, 32), 4, 0.78)      # sights, controls, struts
    m.material("mag_steel", (58, 60, 66), 6, 0.74)      # the curved steel magazine
    m.material("port", (14, 14, 16), 2, 0.9)            # ejection port, slots, grooves
    m.material("mark_red", (176, 38, 34), 3, 0.9)       # Navy pictograms: fire
    m.material("mark_white", (214, 214, 208), 3, 0.9)   # Navy pictograms: safe


def _receiver(m: Model) -> None:
    z0, length = RECEIVER_FRONT_Z, RECEIVER_REAR_Z - RECEIVER_FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4.5, z0), (2, 1.75, length), "steel_black")                         # stamped receiver
    rc.box((-0.75, 6.25, z0), (1.5, TOP_Y - 6.25, length), "steel_black")           # rounded top
    rc.box((-1.04, 4.875, z0 + 0.25), (0.04, 0.25, length - 0.25), "port")          # stock-strut groove (left)
    rc.box((1.0, 4.875, -0.75), (0.04, 0.25, RECEIVER_REAR_Z + 0.75), "port")       # ... and right
    rc.box((0.5, 5.5, -2.75), (0.55, 0.75, 1.75), "port")                           # ejection port
    rc.box((-1, 3.25, MAGWELL_FRONT_Z), (2, 1.25, MAGWELL_REAR_Z - MAGWELL_FRONT_Z),
           "steel_black", pattern="lines")                                          # magazine well
    rc.box((-0.375, 2.5, MAGWELL_REAR_Z), (0.75, 0.75, 0.25), "phosphate")          # paddle magazine release

    # Navy trigger group: polymer housing with the ambidextrous selector and pictograms
    rc.box((-1, 3.25, MAGWELL_REAR_Z), (2, 1.25, RECEIVER_REAR_Z - MAGWELL_REAR_Z), "poly")
    rc.box((-0.5, 2.0, -1.5), (1, 0.25, 2.0), "poly")                               # trigger guard: bottom
    rc.box((-0.5, 2.25, -1.5), (1, 1.0, 0.25), "poly")                              # ... and front strut
    rc.box((-1.1, 3.75, 0.5), (2.2, 0.5, 0.5), "phosphate")                         # selector axle
    rc.box((-1.15, 3.8, 0.75), (0.1, 0.4, 1.0), "phosphate")                        # selector lever (left)
    rc.box((-1.03, 4.0, 1.9), (0.03, 0.25, 0.25), "mark_white")                     # pictogram: safe
    rc.box((-1.03, 3.5, 1.9), (0.03, 0.25, 0.25), "mark_red")                       # pictogram: fire
    rc.box((1.0, 4.0, 1.9), (0.03, 0.25, 0.25), "mark_white")
    rc.box((1.0, 3.5, 1.9), (0.03, 0.25, 0.25), "mark_red")

    # cocking tube: runs on from the receiver top, above the barrel, to the front-sight tower
    rc.cyl((0, TUBE_Y, (z0 + HANDGUARD_FRONT_Z) / 2), 1.1, z0 - HANDGUARD_FRONT_Z, "steel_black")
    rc.box((-0.7, TUBE_Y - 0.125, CHARGE_Z - 0.375), (0.25, 0.25, CHARGE_PULL + 0.75), "port")   # handle slot (left)
    rc.box((-0.7, TUBE_Y + 0.125, CHARGE_Z + CHARGE_PULL - 0.375), (0.25, 0.3, 0.75), "port")    # locking notch

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -1.875))
    bo.box((0.1, 5.5, -2.75), (0.98, 0.75, 1.75), "bolt_steel")

    # non-reciprocating cocking handle on the LEFT of the tube; pivot on the tube axis, so the
    # bone can turn up into the notch (rotation about Z) and slide back (+Z)
    ch = m.bone("charging_handle", pivot=(0, TUBE_Y, CHARGE_Z))
    ch.box((-1.1, TUBE_Y - 0.125, CHARGE_Z - 0.125), (0.5, 0.25, 0.25), "phosphate")             # lever
    ch.box((-1.65, TUBE_Y - 0.275, CHARGE_Z - 0.275), (0.55, 0.55, 0.55), "phosphate", pattern="knurl")  # knob


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER_FRONT_Z))
    ba.box((-0.375, 5.125, BARREL_END_Z), (0.75, 0.75, RECEIVER_FRONT_Z - BARREL_END_Z), "barrel_steel")
    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # bare 3-lug muzzle
    md.box((-0.375, 5.125, MUZZLE_Z), (0.75, 0.75, BARREL_END_Z - MUZZLE_Z), "barrel_steel")
    md.box((-0.375, 5.125, BARREL_END_Z - 0.5), (0.75, 0.75, 0.25), "phosphate", inflate=0.15)   # the three lugs
    md.box((-0.375, 5.125, MUZZLE_Z), (0.75, 0.75, 0.3), "phosphate", inflate=0.06, pattern="knurl")  # thread protector


def _handguard(m: Model) -> None:
    """Slim polymer handguard under the cocking tube, and the front-sight tower that holds the
    barrel and the front end of the tube."""
    z0, length = HANDGUARD_FRONT_Z, RECEIVER_FRONT_Z - HANDGUARD_FRONT_Z
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECEIVER_FRONT_Z))
    hg.box((-1, 4.5, z0), (2, 1.25, length), "hg_poly", pattern="stipple")          # body round the barrel
    hg.box((-0.75, 4.25, z0 + 0.5), (1.5, 0.25, length - 0.5), "hg_poly")           # rounded belly
    hg.box((-1.02, 5.0, z0 + 0.25), (2.04, 0.25, 0.25), "phosphate")                # handguard pin
    tower_len = HANDGUARD_FRONT_Z - TOWER_FRONT_Z
    hg.box((-0.625, 4.875, TOWER_FRONT_Z), (1.25, TOP_Y - 4.875, tower_len), "steel_black")      # tower
    hg.box((-0.45, TUBE_Y - 0.45, TOWER_FRONT_Z - 0.1), (0.9, 0.9, 0.1), "phosphate")            # tube end cap


def _sights(m: Model) -> None:
    # hooded front sight: a ring on the tower with the post inside; ring centre and post tip at SIGHT_Y
    z = FRONT_SIGHT_Z - 0.25
    fs = m.bone("front_sight", pivot=(0, TOP_Y, FRONT_SIGHT_Z))
    fs.box((-0.75, TOP_Y, z), (1.5, SIGHT_Y - 0.5 - TOP_Y, 0.5), "phosphate")       # bottom of the ring
    fs.box((-0.75, SIGHT_Y - 0.5, z), (0.25, 1, 0.5), "phosphate")                  # 1 x 1 opening centred on SIGHT_Y
    fs.box((0.5, SIGHT_Y - 0.5, z), (0.25, 1, 0.5), "phosphate")
    fs.box((-0.75, SIGHT_Y + 0.5, z), (1.5, 0.25, 0.5), "phosphate")                # top of the hood
    fs.box((-0.125, SIGHT_Y - 0.5, FRONT_SIGHT_Z - 0.125), (0.25, 0.5, 0.25), "phosphate")       # post: TOP at SIGHT_Y

    # rotary drum rear sight: a drum on a base plate, the aperture cut through it on SIGHT_Y.
    # The whole bone is hidden by the renderer while an optic is fitted.
    rs = m.bone("rear_sight", pivot=(0, TOP_Y, REAR_SIGHT_Z))
    hole = 0.75
    y0, y1 = SIGHT_Y - hole / 2, SIGHT_Y + hole / 2
    rs.box((-0.75, TOP_Y, REAR_SIGHT_Z - 0.75), (1.5, 0.125, 1.5), "phosphate")     # base plate
    rs.cyl((0, (TOP_Y + 0.125 + y0) / 2, REAR_SIGHT_Z), 1.4, y0 - TOP_Y - 0.125, "phosphate", axis="y")  # drum
    rs.box((-0.625, y0, REAR_SIGHT_Z - 0.375), (0.25, hole, 0.75), "phosphate")     # aperture: left wall
    rs.box((0.375, y0, REAR_SIGHT_Z - 0.375), (0.25, hole, 0.75), "phosphate")      # right wall
    rs.box((-0.625, y1, REAR_SIGHT_Z - 0.375), (1.25, 0.25, 0.75), "phosphate", pattern="knurl")  # top of the drum
    rs.box((0.75, TOP_Y + 0.125, REAR_SIGHT_Z - 0.25), (0.25, 0.375, 0.5), "phosphate")          # windage screw (right)


def _stock(m: Model) -> None:
    """A5 retractable stock, modelled extended: end cap on the receiver, two steel struts that
    slide in the grooves on the receiver sides, fork and butt plate."""
    z0, z1 = RECEIVER_REAR_Z, STOCK_REAR_Z
    st = T.default_part(m, "stock", STOCK_PIVOT)
    st.box((-1, 4.5, z0), (2, TOP_Y - 4.5, STOCK_CAP_REAR_Z - z0 - 0.5), "phosphate", inflate=0.1)   # end cap
    st.box((-0.75, 4.75, STOCK_CAP_REAR_Z - 0.5), (1.5, 1.75, 0.5), "phosphate")                     # its domed rear
    st.box((-0.5, 3.75, z0), (1, 0.75, 0.75), "phosphate")                                           # release catch / sling loop
    for x in (-1.125, 0.625):                                                                        # the two struts
        st.box((x, 4.75, z0 + 0.5), (0.5, 0.5, z1 - 1.0 - z0 - 0.5), "barrel_steel")
    st.box((-1.125, 4.5, z1 - 1.25), (2.25, 1.0, 0.5), "poly")                                       # fork
    st.box((-1.125, 2.75, z1 - 0.75), (2.25, 3.5, 0.5), "poly", pattern="lines")                     # butt plate
    st.box((-1.125, 2.75, z1 - 0.25), (2.25, 3.5, 0.25), "rubber")                                   # butt pad
    st.box((-1.0, 2.5, z1 - 1.0), (2.0, 0.75, 0.9), "poly", rot=(-18, 0, 0),
           pivot=(0, 3.25, z1 - 0.5))                                                                # toe, hooked forward


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "poly", pattern="stipple",
           rot=(-20, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                         # pistol grip
    gr.box((-1, -0.1, 0), (2, 0.35, 2), "poly", rot=(-20, 0, 0), pivot=(0, 4, 1), inflate=0.05)  # flared base
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.875), (0.5, 1, 0.5), "barrel_steel")


def _magazine(m: Model) -> None:
    """The curved 30-round steel magazine: an arc of four 1.35-long segments, 1.25 deep, leaning
    further forward the lower it gets (5 -> 26 degrees), about 4 units below the well."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    y, z = 4.0, MAG_PIVOT[2]                                                        # top, inside the well
    seg, depth, tilts = 1.35, 1.25, (5, 12, 19, 26)
    for i, tilt in enumerate(tilts):
        r = math.radians(tilt)
        y1, z1 = y - seg * math.cos(r), z - seg * math.sin(r)
        over = 0.12 if i < len(tilts) - 1 else 0.0                                  # overlap closes the outer gaps
        end = (0, y1 - over * math.cos(r), z1 - over * math.sin(r))
        mg.wedge((0, y, z), end, 1.0 - 0.04 * i, depth, "mag_steel",
                 pattern="lines" if i in (1, 2) else "none")
        y, z = y1, z1
    r = math.radians(tilts[-1])
    mg.wedge((0, y, z), (0, y - 0.25 * math.cos(r), z - 0.25 * math.sin(r)), 1.1, depth + 0.2,
             "phosphate")                                                           # floor plate


def _animations(m: Model) -> dict:
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_LENGTH, rise=MUZZLE_RISE)
    T.reload_mag(m, length=RELOAD_LENGTH)
    # empty: the HK slap - handle locked back first, magazine swapped, handle slapped down
    return T.reload_hk_slap(m, length=RELOAD_EMPTY_LENGTH, handle="charging_handle", knob=CHARGE_KNOB,
                            pull=CHARGE_PULL, lock_deg=CHARGE_LOCK_DEG, also=("bolt",))


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_receiver, _barrel, _handguard, _sights, _stock, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, 5.875, -1.875),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    m.hk_slap_times = _animations(m)       # real seconds: locked / mag_out / mag_in / mag_slap / slap / home
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "loadout_a": att.loadout(m, "red_dot", "suppressor_9mm") | arms,
        "loadout_b": att.loadout(m, "holographic", "suppressor_9mm"),
        "loadout_micro": att.loadout(m, "micro_red_dot") | arms,
    }
    return m
