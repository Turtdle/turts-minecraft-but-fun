"""CZ Scorpion EVO 3 A1 - 9x19 mm select-fire SMG, 30 rounds, ~1150 rpm.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 670 mm with the stock out = 20 units (modelled 20.25), 410 mm folded = the
12.75 units from the flash hider to the stock hinge. Barrel 196 mm = 5.85 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: one boxy polymer body from the handguard to the stock hinge with a full-length top
rail, the magazine well directly in front of a big trigger guard, a long smoke-coloured
translucent magazine, short rails at 3 / 6 / 9 o'clock on the fore-end, a charging handle on the
LEFT of the fore-end above the barrel (non-reciprocating), low flip-up sights and a long skeletal
side-folding stock (triangular opening, tall butt).

Arms, mounts, attachments and all animations come from templates.py, so mod-wide changes are
made there. Everything gun-specific is a constant in the block below; the part builders only
read those constants.
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "scorpion_evo3"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
RAIL_Y = 7.0               # top of the picatinny rail
SIGHT_Y = 7.75             # iron sight line: front post tip and centre of the rear aperture
HANDGUARD_FRONT_Z = -8.5   # front face of the fore-end
RECEIVER_FRONT_Z = -4.75   # fore-end / receiver seam = front of the magazine well
RECEIVER_REAR_Z = 3.0      # rear face of the receiver (the stock hinge sits behind it)
BARREL_END_Z = -8.75       # end of the bare threaded barrel = muzzle mount
MUZZLE_Z = -9.75           # front of the factory flash hider
FRONT_SIGHT_Z = -8.0       # z centre of the front post
REAR_SIGHT_Z = 2.25        # z centre of the rear aperture drum
STOCK_REAR_Z = 10.5        # rear face of the butt pad (stock extended)

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, 3.5, -3.75)            # top centre of the magazine well
STOCK_PIVOT = (1.0, BORE_Y, RECEIVER_REAR_Z)   # the folding hinge, on the RIGHT rear corner
OPTIC_MOUNT = (0, RAIL_Y, -1.5)        # over the receiver, clear of both iron sights
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
UNDERBARREL_MOUNT = (0, 3.75, -6.5)    # bottom face of the 6 o'clock rail
SIDE_MOUNT = (1.25, 5.15, -7.0)        # outer face of the 3 o'clock rail
CHARGE_Z = -7.25                       # z centre of the charging-handle knob at rest
CHARGE_KNOB = (-1.5, 6.0, CHARGE_Z)    # knob centre (left side)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                     # centre of the pistol grip (same as the M4)
L_HAND = (0, 4.0, -6.75)               # under the fore-end, in front of the magazine well

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "holographic", "acog", "micro_red_dot", "suppressor_9mm",
               "vertical_grip", "angled_grip", "laser")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.048        # 1150 rpm -> 0.0522 s between shots; stay below it
RECOIL = 0.45
MUZZLE_RISE = 1.5
BOLT_TRAVEL = 2.0
CHARGE_PULL = 2.25         # charging-handle travel in its slot
RELOAD_LENGTH = 2.2
RELOAD_EMPTY_LENGTH = 2.6


def _materials(m: Model) -> None:
    m.material("upper", (40, 41, 45), 5, 0.74)          # upper receiver / fore-end
    m.material("poly", (50, 50, 53), 4, 0.80)           # polymer lower, grip, stock
    m.material("barrel_steel", (84, 86, 94), 6, 0.72)
    m.material("phosphate", (30, 30, 34), 4, 0.78)      # flash hider, sights, controls
    m.material("smoke", (112, 104, 92), 6, 0.80)        # translucent smoke magazine body
    m.material("rounds", (164, 128, 58), 8, 0.74)       # cartridges showing through the magazine
    m.material("port", (16, 16, 18), 2, 0.9)            # ejection port, slots


def _receiver(m: Model) -> None:
    z0, length = RECEIVER_FRONT_Z, RECEIVER_REAR_Z - RECEIVER_FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4.5, z0), (2, 2, length), "upper")                                  # upper receiver
    rc.box((-1, 6.5, z0), (2, 0.5, length), "upper", pattern="rails")               # top rail
    rc.box((-1, 3.5, z0), (2, 1, length), "poly")                                   # trigger housing / lower
    rc.box((-1, 2.25, z0), (2, 1.25, 2), "poly", pattern="lines")                   # magazine well
    rc.box((-0.5, 2, -2.75), (1, 0.5, 3.25), "poly")                                # big trigger guard
    rc.box((0.5, 5, -4.25), (0.55, 1, 2), "port")                                   # ejection port (behind the bolt)
    rc.box((-1.1, 3.75, 0.5), (2.2, 0.5, 0.5), "phosphate")                         # ambidextrous selector
    rc.box((-1.1, 4, -2.5), (0.1, 0.5, 1), "phosphate")                             # bolt catch (left)
    rc.box((-1.1, 2.6, -2.7), (2.2, 0.4, 0.4), "phosphate")                         # ambidextrous magazine release

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -3.25))
    bo.box((0.1, 5, -4.25), (1, 1, 2), "bright_steel")

    # non-reciprocating charging handle in its slot on the LEFT of the fore-end
    ch = m.bone("charging_handle", pivot=(0, CHARGE_KNOB[1], CHARGE_Z))
    ch.box((-1.75, 5.75, CHARGE_Z - 0.25), (0.75, 0.5, 0.5), "phosphate")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER_FRONT_Z))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, HANDGUARD_FRONT_Z - BARREL_END_Z), "barrel_steel")
    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # factory flash hider
    md.box((-0.5, 5, MUZZLE_Z), (1, 1, BARREL_END_Z - MUZZLE_Z), "phosphate", pattern="lines")
    md.box((-0.5, 5, BARREL_END_Z - 0.3), (1, 1, 0.25), "phosphate", inflate=0.15)  # collar


def _handguard(m: Model) -> None:
    """Fore-end: continues the receiver, top rail runs on, short rails at 3 / 6 / 9 o'clock."""
    z0, length = HANDGUARD_FRONT_Z, RECEIVER_FRONT_Z - HANDGUARD_FRONT_Z
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECEIVER_FRONT_Z))
    hg.box((-1, 4.25, z0), (2, 2.25, length), "upper")                              # body
    hg.box((-1, 6.5, z0), (2, 0.5, length), "upper", pattern="rails")               # top rail
    hg.box((-1.25, 4.9, z0 + 0.25), (2.5, 0.5, 2.25), "upper", pattern="rails")     # 3 and 9 o'clock rails
    hg.box((-0.5, 3.75, z0 + 0.25), (1, 0.5, 3), "upper", pattern="rails")          # 6 o'clock rail
    hg.box((-1.03, 5.85, CHARGE_Z - 0.5), (2.06, 0.3, CHARGE_PULL + 0.75), "port")  # charging-handle slots
    hg.box((-0.75, 4.75, z0 - 0.05), (1.5, 1.5, 0.05), "port")                      # front opening round the barrel


def _sights(m: Model) -> None:
    fs = m.bone("front_sight", pivot=(0, RAIL_Y, FRONT_SIGHT_Z))                    # flip-up post, raised
    fs.box((-1, RAIL_Y, FRONT_SIGHT_Z - 0.5), (2, 0.25, 1), "phosphate")            # base
    fs.box((-0.25, RAIL_Y + 0.25, FRONT_SIGHT_Z - 0.25), (0.5, SIGHT_Y - RAIL_Y - 0.25, 0.5),
           "phosphate")                                                             # post: TOP at SIGHT_Y
    for x in (-1, 0.5):                                                             # protective ears
        fs.box((x, RAIL_Y + 0.25, FRONT_SIGHT_Z - 0.25), (0.5, SIGHT_Y - RAIL_Y, 0.5), "phosphate")

    rs = m.bone("rear_sight", pivot=(0, RAIL_Y, REAR_SIGHT_Z))                      # rotating aperture drum
    z = REAR_SIGHT_Z - 0.25
    rs.box((-1, RAIL_Y, z - 0.25), (2, SIGHT_Y - 0.5 - RAIL_Y, 1), "phosphate")     # base = bottom of the aperture
    rs.box((-1, SIGHT_Y - 0.5, z), (0.5, 1, 0.5), "phosphate")                      # 1 x 1 hole centred on SIGHT_Y
    rs.box((0.5, SIGHT_Y - 0.5, z), (0.5, 1, 0.5), "phosphate")
    rs.box((-1, SIGHT_Y + 0.5, z), (2, 0.25, 0.5), "phosphate")                     # top of the drum
    rs.box((1, SIGHT_Y - 0.25, z), (0.25, 0.5, 0.5), "phosphate", pattern="knurl")  # drum knob (right)


def _stock(m: Model) -> None:
    """Side-folding skeletal stock, shown unfolded: hinge, top beam, sloping lower strut, tall butt.
    The `stock` bone pivot is the hinge (right rear corner of the receiver)."""
    z0, z1 = RECEIVER_REAR_Z, STOCK_REAR_Z
    st = T.default_part(m, "stock", STOCK_PIVOT)
    st.box((-1, 4.5, z0), (2, 2, 0.5), "phosphate")                                 # hinge block
    st.box((-0.5, 5.5, z0 + 0.5), (1, 1, z1 - z0 - 1.25), "poly")                   # top beam
    st.box((-0.6, 5.4, z0 + 3.5), (1.2, 1.2, 0.75), "poly", pattern="lines")        # length-adjustment collar
    st.wedge((0, 4.75, z0 + 0.6), (0, 2.75, z1 - 1.0), 1, 0.75, "poly")             # lower strut
    st.box((-0.75, 2.0, z1 - 0.75), (1.5, 4.75, 0.5), "poly", pattern="lines")      # butt plate
    st.box((-0.75, 2.0, z1 - 0.25), (1.5, 4.75, 0.25), "rubber")                    # butt pad


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "poly", pattern="stipple",
           rot=(-20, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                         # pistol grip
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.5, 2.5, -1), (1, 1, 0.75), "barrel_steel")


def _magazine(m: Model) -> None:
    """Slightly curved 30-round magazine in translucent smoke polymer; 1.5 deep, 4 below the well."""
    knee = (0, 0.75, -3.0)                                                          # rear edge where the curve starts
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    mg.box((-0.5, 0.75, -4.5), (1, 1.5, 1.5), "smoke")                              # straight part below the well
    mg.box((-0.55, 1.0, -4.0), (1.1, 1, 0.5), "rounds")                             # rounds seen through the body
    mg.box((-0.5, -1.75, -4.5), (1, 2.5, 1.5), "smoke", pattern="lines",
           rot=(8, 0, 0), pivot=knee, inflate=-0.05)                                # curved lower part
    mg.box((-0.5, -2.0, -4.5), (1, 0.25, 1.5), "phosphate", rot=(8, 0, 0), pivot=knee, inflate=0.1)  # floor plate


def _animations(m: Model) -> None:
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_LENGTH, rise=MUZZLE_RISE)
    T.reload_mag(m, length=RELOAD_LENGTH)
    # empty: after the swap the LEFT hand racks the charging handle on the left of the fore-end
    T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, empty_action="cycle", hand="left",
                 bolt_bone="charging_handle", lift_deg=0, pull=CHARGE_PULL, knob=CHARGE_KNOB,
                 also=("bolt",))


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_receiver, _barrel, _handguard, _sights, _stock, _grip_trigger, _magazine):
        build(m)
    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style=ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT, muzzle=MUZZLE_MOUNT, underbarrel=UNDERBARREL_MOUNT, side=SIDE_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, BORE_Y, -3.25),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "loadout_a": att.loadout(m, "holographic", "suppressor_9mm", "vertical_grip", "laser") | arms,
        "loadout_b": att.loadout(m, "micro_red_dot", "suppressor_9mm", "angled_grip"),
        "loadout_acog": att.loadout(m, "acog") | arms,
    }
    return m
