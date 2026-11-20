"""B&T APC9K PRO (US Army Sub Compact Weapon) - 9x19 mm, 30 rounds, ~1080 rpm.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 551 mm with the stock out = ~16.5 units (modelled 17).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: one tall boxy upper that runs from the muzzle to the back of the grip with a
full-length top rail, a stubby three-lug muzzle, a short railed fore-end directly in front of the
magazine well, a long smoke-coloured curved magazine and a skeletal side-folding stock.

Arms, mounts, attachments and all animations come from templates.py, so mod-wide changes are
made there. Everything gun-specific is a constant in the block below; the part builders only
read those constants.
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "apc9"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
RAIL_Y = 7.0               # top of the picatinny rail
SIGHT_Y = 7.75             # iron sight line (low pop-up sights): front post tip and centre of the ghost ring
UPPER_FRONT_Z = -8.0       # front face of the upper receiver
UPPER_REAR_Z = 2.5         # rear face of the upper receiver (stock hinge sits behind it)
BARREL_END_Z = -8.5        # end of the bare barrel = muzzle mount
MUZZLE_Z = -9.5            # front of the factory three-lug flash hider
REAR_SIGHT_Z = 1.25        # z centre of the ghost-ring frame
FRONT_SIGHT_Z = -7.25      # z centre of the front post

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, 3.5, -3.25)            # top centre of the magazine well
STOCK_PIVOT = (0, BORE_Y, UPPER_REAR_Z)
OPTIC_MOUNT = (0, RAIL_Y, -2.5)        # middle of the top rail, clear of both iron sights
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
UNDERBARREL_MOUNT = (0, 3.0, -6.0)     # bottom face of the fore-end's bottom rail
SIDE_MOUNT = (1.25, 5.0, -6.5)         # outer face of the right side rail

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                     # centre of the pistol grip (same as the M4)
L_HAND = (0, 3.5, -6.5)                # under the short fore-end, in front of the magazine well

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "holographic", "acog", "micro_red_dot", "suppressor_9mm",
               "vertical_grip", "angled_grip", "laser")
DEFAULT_ATTACHMENTS = {"optic": "red_dot"}   # issued with an Aimpoint Micro-class red dot

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.05         # 1080 rpm -> 0.0556 s between shots; stay below it
RECOIL = 0.5
MUZZLE_RISE = 1.5
BOLT_TRAVEL = 2.0
RELOAD_LENGTH = 2.3
RELOAD_EMPTY_LENGTH = 2.9
BOLT_CATCH = (-1.5, 4.0, -1.5)         # bolt release on the left side of the lower


def _materials(m: Model) -> None:
    m.material("anodised", (40, 41, 46), 5, 0.72)       # black anodised aluminium upper
    m.material("lower_poly", (52, 52, 54), 4, 0.80)     # polymer lower / grip / stock
    m.material("barrel_steel", (84, 86, 94), 6, 0.72)
    m.material("phosphate", (32, 32, 36), 4, 0.78)      # flash hider, sights, small parts
    m.material("smoke", (104, 98, 88), 6, 0.80)         # translucent smoke magazine body
    m.material("rounds", (160, 126, 58), 8, 0.74)       # cartridges showing through the magazine
    m.material("port", (16, 16, 18), 2, 0.9)            # inside of the ejection port


def _receiver(m: Model) -> None:
    length = UPPER_REAR_Z - UPPER_FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4.5, UPPER_FRONT_Z), (2, 2, length), "anodised")                    # boxy upper
    rc.box((-1, 6.5, UPPER_FRONT_Z), (2, 0.5, length), "anodised", pattern="rails")  # full-length top rail
    rc.box((-1, 3.5, -4.5), (2, 1, 7), "lower_poly")                                # polymer lower
    rc.box((-1, 2, -4.5), (2, 1.5, 2.5), "lower_poly", pattern="lines")             # magazine well
    rc.box((-0.5, 2, -2), (1, 0.5, 2.5), "lower_poly")                              # trigger guard
    rc.box((0.5, 5, -3.5), (0.55, 1, 2), "port")                                    # ejection port (behind the bolt)
    rc.box((-1.1, 3.75, 0.25), (2.2, 0.5, 0.5), "phosphate")                        # ambidextrous selector
    rc.box((-1.1, 4, -2), (0.1, 0.5, 1), "phosphate")                               # bolt release (left)

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -2.5))
    bo.box((0.1, 5, -3.5), (1, 1, 2), "bright_steel")

    # non-reciprocating, ambidextrous charging handles on the sides of the upper
    ch = m.bone("charging_handle", pivot=(0, 6.25, -4.5))
    ch.box((-1.5, 6, -5), (0.5, 0.5, 1), "phosphate")
    ch.box((1, 6, -5), (0.5, 0.5, 1), "phosphate")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, UPPER_FRONT_Z))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, UPPER_FRONT_Z - BARREL_END_Z), "barrel_steel")
    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # three-lug flash hider
    md.box((-0.5, 5, MUZZLE_Z), (1, 1, BARREL_END_Z - MUZZLE_Z), "phosphate", pattern="lines")
    md.box((-0.5, 5, BARREL_END_Z - 0.5), (1, 1, 0.25), "phosphate", inflate=0.2)   # the three lugs


def _handguard(m: Model) -> None:
    hg = m.bone("handguard", pivot=(0, BORE_Y, -4.5))
    hg.box((-1, 3.5, -8), (2, 1, 3.5), "anodised")                                  # fore-end under the upper
    hg.box((-1.25, 4.75, -8), (2.5, 0.5, 3), "anodised", pattern="rails")           # side rails
    hg.box((-0.5, 3, -8), (1, 0.5, 3.5), "anodised", pattern="rails")               # bottom rail


def _sights(m: Model) -> None:
    fs = m.bone("front_sight", pivot=(0, RAIL_Y, FRONT_SIGHT_Z))                    # pop-up post, raised
    fs.box((-1, RAIL_Y, FRONT_SIGHT_Z - 0.5), (2, 0.25, 1), "phosphate")            # base
    fs.box((-0.25, RAIL_Y + 0.25, FRONT_SIGHT_Z - 0.25), (0.5, SIGHT_Y - RAIL_Y - 0.25, 0.5),
           "phosphate")                                                             # post: TOP at SIGHT_Y
    for x in (-1, 0.5):                                                             # protective ears
        fs.box((x, RAIL_Y + 0.25, FRONT_SIGHT_Z - 0.25), (0.5, SIGHT_Y - RAIL_Y, 0.5), "phosphate")

    rs = m.bone("rear_sight", pivot=(0, RAIL_Y, REAR_SIGHT_Z))                      # pop-up ghost ring
    z = REAR_SIGHT_Z - 0.25
    rs.box((-1, RAIL_Y, z - 0.25), (2, SIGHT_Y - 0.5 - RAIL_Y, 1), "phosphate")     # base = bottom of the ring
    rs.box((-1, SIGHT_Y - 0.5, z), (0.5, 1, 0.5), "phosphate")                      # 1 x 1 hole centred on SIGHT_Y
    rs.box((0.5, SIGHT_Y - 0.5, z), (0.5, 1, 0.5), "phosphate")
    rs.box((-1, SIGHT_Y + 0.5, z), (2, 0.25, 0.5), "phosphate")                     # top of the ring


def _stock(m: Model) -> None:
    """Skeletal side-folding stock, shown unfolded: top tube, diagonal strut, butt plate."""
    z0 = UPPER_REAR_Z
    st = T.default_part(m, "stock", STOCK_PIVOT)
    st.box((-1, 4.5, z0), (2, 2, 0.5), "phosphate")                                 # folding hinge
    st.box((-0.5, 5.75, z0 + 0.5), (1, 0.75, 4), "lower_poly")                      # top tube
    st.wedge((0, 5.0, z0 + 0.75), (0, 3.0, z0 + 4.5), 0.5, 0.5, "lower_poly")       # lower strut
    st.box((-1, 2.5, z0 + 4.5), (2, 4.25, 0.5), "lower_poly")                       # butt plate
    st.box((-1, 2.5, z0 + 5), (2, 4.25, 0.25), "rubber")                            # butt pad


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "lower_poly", pattern="stipple",
           rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                       # AR-style grip
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.5, 2.5, -1), (1, 1, 0.75), "barrel_steel")


def _magazine(m: Model) -> None:
    """Curved 30-round B&T magazine in translucent smoke polymer; 1.5 deep, about 4.5 below the well."""
    knee = (0, 0.5, -2.5)                                                           # rear edge where the curve starts
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    mg.box((-0.5, 0.5, -4), (1, 1.5, 1.5), "smoke")                                 # straight part below the well
    mg.box((-0.55, 0.75, -3.5), (1.1, 1, 0.5), "rounds")                            # rounds seen through the body
    mg.box((-0.5, -2.5, -4), (1, 3, 1.5), "smoke", pattern="lines",
           rot=(10, 0, 0), pivot=knee, inflate=-0.05)                               # curved lower part
    mg.box((-0.5, -2.75, -4), (1, 0.25, 1.5), "phosphate", rot=(10, 0, 0), pivot=knee, inflate=0.1)  # floor plate


def _animations(m: Model) -> None:
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_LENGTH, rise=MUZZLE_RISE)
    T.reload_mag(m, length=RELOAD_LENGTH)
    T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, pull=BOLT_TRAVEL, catch=BOLT_CATCH)


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_receiver, _barrel, _handguard, _sights, _stock, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT, underbarrel=UNDERBARREL_MOUNT, side=SIDE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, BORE_Y, -2.5),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    _animations(m)
    m.preview_configs = {
        "bare": att.loadout(m),
        "issued": att.loadout(m, *DEFAULT_ATTACHMENTS.values()),
        "loadout_a": att.loadout(m, "holographic", "suppressor_9mm", "vertical_grip", "laser"),
        "loadout_micro": att.loadout(m, "micro_red_dot", "suppressor_9mm"),
    }
    return m
