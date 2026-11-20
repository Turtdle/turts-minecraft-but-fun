"""SIG Sauer MPX, 8 in barrel (Army COTS order) - 9x19 mm, 30 rounds, ~850 rpm.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 610 mm with the stock out = ~18 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: a short AR-15 - flat-top upper with one continuous top rail over the receiver and a
slim M-LOK handguard, AR grip and T charging handle - but with a pistol-sized magazine well, a
thin, gently curved 9 mm magazine, no iron sights and a two-rod telescoping stock.

Arms, mounts, attachments and all animations come from templates.py, so mod-wide changes are
made there. Everything gun-specific is a constant in the block below; the part builders only
read those constants.
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "mpx"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
RAIL_Y = 7.0               # top of the picatinny rail
RECEIVER_FRONT_Z = -4.0
RECEIVER_REAR_Z = 2.0
HANDGUARD_FRONT_Z = -9.5
MLOK_SLOTS = 3             # slots per face of the handguard
BARREL_END_Z = -10.0       # end of the bare barrel = muzzle mount
MUZZLE_Z = -11.0           # front of the factory three-lug flash hider

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, 4, -3)                 # top centre of the magazine well
STOCK_PIVOT = (0, 5, RECEIVER_REAR_Z)
OPTIC_MOUNT = (0, RAIL_Y, -1.5)        # over the receiver
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
UNDERBARREL_MOUNT = (0, 4.5, -6.25)    # bottom M-LOK face of the handguard, behind the left hand
SIDE_MOUNT = (1.0, BORE_Y, -5.75)      # right M-LOK face, toward the rear (the hand is in front)

# No iron sights: the sight line is the axis of the issued red dot, at its rear face.
DEFAULT_ATTACHMENTS = {"optic": "red_dot"}
SIGHT_Y = RAIL_Y + att.OPTIC_AXIS_Y[DEFAULT_ATTACHMENTS["optic"]]     # 8.25
SIGHT_Z = OPTIC_MOUNT[2] + 1.5         # rear face of the red dot (its `sight_red_dot` locator)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                     # centre of the pistol grip (same as the M4)
L_HAND = (0, 4.5, -8)                  # under the front half of the handguard

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "holographic", "acog", "micro_red_dot", "suppressor_9mm",
               "vertical_grip", "angled_grip", "laser")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.065        # 850 rpm -> 0.0706 s between shots; stay below it
RECOIL = 0.5
MUZZLE_RISE = 1.5
BOLT_TRAVEL = 2.0
RELOAD_LENGTH = 2.3
RELOAD_EMPTY_LENGTH = 2.9


def _materials(m: Model) -> None:
    m.material("anodised", (46, 47, 53), 5, 0.72)       # receiver and handguard
    m.material("furniture", (58, 58, 60), 5, 0.78)      # grip and stock polymer
    m.material("barrel_steel", (84, 86, 94), 6, 0.72)
    m.material("phosphate", (32, 32, 36), 4, 0.78)      # flash hider, small parts
    m.material("mag_poly", (30, 30, 33), 4, 0.80)       # black polymer magazine
    m.material("port", (16, 16, 18), 2, 0.9)            # inside of the ejection port / M-LOK slots


def _receiver(m: Model) -> None:
    z0, length = RECEIVER_FRONT_Z, RECEIVER_REAR_Z - RECEIVER_FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 5, z0), (2, 1.5, length), "anodised")                               # upper receiver
    rc.box((-1, 6.5, z0), (2, 0.5, length), "anodised", pattern="rails")            # flat-top rail
    rc.box((-1, 4, z0), (2, 1, length), "anodised")                                 # lower receiver
    rc.box((-1, 2.5, -4), (2, 1.5, 2), "anodised")                                  # pistol-sized magazine well
    rc.box((-0.5, 2, -2), (1, 0.5, 2.5), "anodised")                                # trigger guard
    rc.box((0.5, 5, -2), (0.55, 1, 2), "port")                                      # ejection port (behind the bolt)
    rc.box((0.5, 5, 0), (1, 1, 0.75), "anodised")                                   # brass deflector
    rc.box((-1.1, 4.25, 0.5), (2.2, 0.5, 0.5), "phosphate")                         # ambidextrous selector
    rc.box((-1.1, 4.5, -2), (2.2, 0.5, 0.75), "phosphate")                          # ambidextrous bolt release

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -1))
    bo.box((0.1, 5, -2), (1, 1, 2), "bright_steel")

    ch = m.bone("charging_handle", pivot=(0, 6.25, RECEIVER_REAR_Z))                # AR-type T handle
    ch.box((-1.25, 5.75, RECEIVER_REAR_Z), (2.5, 0.75, 0.5), "phosphate")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER_FRONT_Z))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, HANDGUARD_FRONT_Z - BARREL_END_Z), "barrel_steel")
    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # three-lug flash hider
    md.box((-0.5, 5, MUZZLE_Z), (1, 1, BARREL_END_Z - MUZZLE_Z), "phosphate", pattern="lines")
    md.box((-0.5, 5, BARREL_END_Z - 0.5), (1, 1, 0.25), "phosphate", inflate=0.2)   # the three lugs


def _handguard(m: Model) -> None:
    """Slim free-float M-LOK handguard; its top rail continues the receiver rail."""
    z0, length = HANDGUARD_FRONT_Z, RECEIVER_FRONT_Z - HANDGUARD_FRONT_Z
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECEIVER_FRONT_Z))
    hg.box((-1, 4.5, z0), (2, 2, length), "anodised", pattern="slots")              # M-LOK tube
    hg.box((-1, 6.5, z0), (2, 0.5, length), "anodised", pattern="rails")            # top rail
    hg.box((-1, 4.5, RECEIVER_FRONT_Z - 0.5), (2, 2, 0.5), "barrel_steel", inflate=0.1)  # barrel nut collar
    for i in range(MLOK_SLOTS):                                                     # M-LOK slots, both sides and bottom
        z = z0 + 0.5 + i * 1.5
        hg.box((-1.05, 5.25, z), (2.1, 0.5, 1), "port")
        hg.box((-0.25, 4.45, z), (0.5, 0.1, 1), "port")


def _stock(m: Model) -> None:
    """Thin telescoping stock, shown extended: housing, two rods along the sides, butt plate."""
    z0 = RECEIVER_REAR_Z
    st = T.default_part(m, "stock", STOCK_PIVOT)
    st.box((-1, 4, z0), (2, 1.75, 1), "furniture")                                  # stock housing
    for x in (-1.25, 0.75):                                                         # telescoping rods
        st.box((x, 4.75, z0 + 0.5), (0.5, 0.5, 4), "barrel_steel")
    st.box((-1.25, 2.5, z0 + 4.5), (2.5, 4, 0.5), "furniture", pattern="lines")     # butt plate
    st.box((-1.25, 2.5, z0 + 5), (2.5, 4, 0.25), "rubber")                          # butt pad


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "furniture", pattern="stipple",
           rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                       # AR grip
    tr = m.bone("trigger", pivot=(0, 4, -0.5))
    tr.box((-0.5, 2.75, -1), (1, 1.25, 0.75), "barrel_steel")


def _magazine(m: Model) -> None:
    """Slim, slightly curved 30-round polymer magazine; 1.5 deep, about 4.5 below the well."""
    knee = (0, 1, -2.25)                                                            # rear edge where the curve starts
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    mg.box((-0.5, 1, -3.75), (1, 1.5, 1.5), "mag_poly", pattern="lines")            # straight part below the well
    mg.box((-0.5, -2, -3.75), (1, 3, 1.5), "mag_poly", pattern="lines",
           rot=(10, 0, 0), pivot=knee, inflate=-0.05)                               # curved lower part
    mg.box((-0.5, -2.25, -3.75), (1, 0.25, 1.5), "mag_poly", rot=(10, 0, 0), pivot=knee, inflate=0.1)  # floor plate


def _animations(m: Model) -> None:
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_LENGTH, rise=MUZZLE_RISE)
    T.reload_mag(m, length=RELOAD_LENGTH)
    T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, pull=BOLT_TRAVEL)


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_receiver, _barrel, _handguard, _stock, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT, underbarrel=UNDERBARREL_MOUNT, side=SIDE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, BORE_Y, -1),
               sight_line=(0, SIGHT_Y, SIGHT_Z))
    _animations(m)
    m.preview_configs = {
        "bare": att.loadout(m),
        "issued": att.loadout(m, *DEFAULT_ATTACHMENTS.values()),
        "loadout_a": att.loadout(m, "holographic", "suppressor_9mm", "angled_grip", "laser"),
        "loadout_micro": att.loadout(m, "micro_red_dot", "suppressor_9mm"),
    }
    return m
