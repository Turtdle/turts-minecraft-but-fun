"""KRISS Vector Gen II SMG - .45 ACP, 25-round extended Glock 21 magazine, ~1200 rpm.

Same scale and conventions as every gun (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 620 mm with the stock out = 18.5 units (modelled 18.5: z -9.5 .. 9).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette (front to back): a short barrel stub poking out LOW under a tall flat-topped upper
with a full-length rail; under the front the deep lower: a short railed nose, the magazine well
with the long straight Glock stick hanging out of it, and right behind the well the angular
Super V housing that drops BELOW the well and slants back up to the trigger guard; then the
trigger, an upright grip and a skeletal side-folding stock (modelled unfolded). The body is thin
over the grip and deep in front of the trigger - that step is what makes it read as a Vector.

Slots: OPTIC (top rail), MUZZLE, UNDERBARREL (the short bottom rail of the nose, in front of the
magazine well: vertical_grip / angled_grip) and SIDE (right rail of the nose).

Bones: receiver, bolt, charging_handle (folding lever on the LEFT of the front, racked by the
left hand in reload_empty), barrel, muzzle_default, handguard (the railed nose; carries the side
and underbarrel mounts), front_sight / rear_sight (flip-up irons), stock + stock_default, grip, trigger,
magazine + magazine_default.
Arms, mounts, attachments and all animations come from templates.py.
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "vector"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
UPPER_Y = 5.5              # seam between the polymer lower and the aluminium upper
RAIL_Y = 7.5               # top of the picatinny rail (the Vector's rail sits high above its low bore)
SIGHT_Y = 8.25             # iron sight line: front post tip and centre of the rear aperture
FRONT_Z = -8.5             # front face of upper and lower
REAR_Z = 3.0               # rear face of the upper (the stock hinge sits behind it)
BARREL_END_Z = -9.0        # end of the bare threaded barrel = muzzle mount
MUZZLE_Z = -9.5            # front of the thread protector
NOSE_REAR_Z = -6.75        # railed nose / magazine well seam
WELL_REAR_Z = -4.75        # magazine well / Super V housing seam
HOUSING_REAR_Z = -2.0      # rear of the Super V housing = front of the trigger guard
LOWER_Y = 4.0              # bottom of the slim lower over the grip
WELL_Y = 2.0               # mouth of the magazine well
HOUSING_Y = 1.0            # bottom of the Super V housing (below the well)
FRONT_SIGHT_Z = -7.75      # z centre of the front post
REAR_SIGHT_Z = 2.25        # z centre of the rear aperture
STOCK_REAR_Z = 9.0         # rear face of the butt pad (stock unfolded / extended)

# ---- pivots / mount points -------------------------------------------------------------
MAG_PIVOT = (0, WELL_Y, -5.75)         # centre of the mouth of the magazine well
MAG_RAKE = 6.0                         # the Glock stick leans back a little
STOCK_PIVOT = (1.0, 6.25, REAR_Z)      # the folding hinge, on the RIGHT rear corner
OPTIC_MOUNT = (0, RAIL_Y, -2.75)       # middle of the top rail, clear of both iron sights
MUZZLE_MOUNT = (0, BORE_Y, BARREL_END_Z)
SIDE_MOUNT = (1.25, 4.75, -7.5)        # outer face of the right side rail on the nose
UNDERBARREL_MOUNT = (0, 3.0, -7.75)    # bottom face of the nose's bottom rail; the 2-long grip clamp
                                       # ends flush with the front of the magazine well
# the 4-long AFG cannot centre on the 1.75-long rail without running into the magazine: it clamps
# 0.85 further forward (rear 0.1 clear of the magazine) and its finger shelf overhangs the nose
ANGLED_GRIP_AT = (0, 3.0, -8.6)
CHARGE_Z = -6.5                        # z centre of the charging-handle knob at rest
CHARGE_KNOB = (-1.6, 6.25, CHARGE_Z)   # knob centre (left side of the front)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "rifle"
R_HAND = (0, 2, 2)                     # centre of the pistol grip (same as the M4)
L_HAND = (0, 2.0, -8.25)               # round the railed nose, in front of the magazine well; a fitted
                                       # vertical grip / AFG hangs right where this fist is

# ---- attachments -------------------------------------------------------------------------
# there is no .45 can in attachments.py: the shared pistol / SMG can (suppressor_9mm) is used
ATTACHMENTS = ("red_dot", "holographic", "acog", "micro_red_dot", "suppressor_9mm", "vertical_grip", "laser")
DEFAULT_ATTACHMENTS: dict = {}         # issued with the flip-up irons only

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.05         # 1200 rpm -> 0.05 s between shots
RECOIL = 0.35              # the Super V system: very little kick and rise
MUZZLE_RISE = 0.8
BOLT_TRAVEL = 1.5
CHARGE_PULL = 2.0          # charging-handle travel in its slot
RELOAD_LENGTH = 2.2
RELOAD_EMPTY_LENGTH = 2.7


def _materials(m: Model) -> None:
    m.material("v_upper", (36, 37, 41), 5, 0.74)        # black anodised upper
    m.material("v_poly", (46, 46, 49), 4, 0.80)         # polymer lower, grip, stock
    m.material("v_housing", (41, 41, 44), 4, 0.78)      # Super V housing, a shade darker
    m.material("barrel_steel", (84, 86, 94), 6, 0.72)
    m.material("phosphate", (28, 28, 32), 4, 0.78)      # sights, controls, thread protector
    m.material("v_mag", (32, 32, 35), 4, 0.80)          # Glock magazine body
    m.material("v_magext", (54, 54, 58), 4, 0.80)       # the +12 extension
    m.material("port", (14, 14, 16), 2, 0.9)            # ejection port, slots, the hole over the trigger


def _receiver(m: Model) -> None:
    length = REAR_Z - FRONT_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, UPPER_Y, FRONT_Z), (2, 1.5, length), "v_upper")                     # flat-topped upper
    rc.box((-1, 7.0, FRONT_Z), (2, RAIL_Y - 7.0, length), "v_upper", pattern="rails")  # full-length top rail
    rc.box((-1, LOWER_Y, NOSE_REAR_Z), (2, UPPER_Y - LOWER_Y, REAR_Z - 0.5 - NOSE_REAR_Z), "v_poly")  # slim lower
    # magazine well: deep block under the front, open at the bottom
    rc.box((-1, WELL_Y, NOSE_REAR_Z), (2, LOWER_Y - WELL_Y, WELL_REAR_Z - NOSE_REAR_Z), "v_poly", pattern="lines")
    # Super V housing: drops below the well, its rear face slants back up to the trigger guard
    hz = HOUSING_REAR_Z - WELL_REAR_Z
    rc.box((-1, WELL_Y, WELL_REAR_Z), (2, LOWER_Y - WELL_Y, hz), "v_housing")
    rc.box((-1, HOUSING_Y, WELL_REAR_Z), (2, WELL_Y - HOUSING_Y, hz - 1.0), "v_housing")
    rc.wedge((0, HOUSING_Y + 0.43, WELL_REAR_Z + 0.75), (0, WELL_Y + 0.43, HOUSING_REAR_Z - 0.25),
             1.9, 1.0, "v_housing")                                                 # the slanted rear face
    rc.box((-1.04, 2.6, WELL_REAR_Z + 0.4), (2.08, 0.9, 1.4), "v_housing", pattern="slots")  # lightening cuts
    rc.box((-0.5, 2.0, HOUSING_REAR_Z), (1, 0.5, 2.5), "v_poly")                    # trigger guard bar
    rc.box((0.5, 4.75, -5.9), (0.55, 1, 1.5), "port")                             # ejection port (right, low)
    rc.box((-1.1, 4.4, 0.25), (2.2, 0.5, 0.5), "phosphate")                         # ambidextrous safety
    rc.box((-1.1, 6.0, 0.5), (2.2, 0.4, 0.5), "phosphate")                          # fire selector on the upper
    rc.box((-1.1, 4.5, -4.25), (0.1, 0.6, 1.0), "phosphate")                        # bolt release (left)
    rc.box((-1.1, 2.4, -6.5), (0.12, 0.6, 0.6), "phosphate")                        # magazine release (left)
    rc.box((-1.03, 6.1, CHARGE_Z - 0.5), (0.05, 0.3, CHARGE_PULL + 1.0), "port")    # charging-handle slot

    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -5.15))
    bo.box((0.1, 4.85, -5.85), (1, 0.8, 1.4), "bright_steel")

    # non-reciprocating folding charging lever on the LEFT of the front
    ch = m.bone("charging_handle", pivot=(0, CHARGE_KNOB[1], CHARGE_Z))
    ch.box((-1.3, 6.05, CHARGE_Z - 0.25), (0.3, 0.4, 0.5), "phosphate")             # root
    ch.box((-1.85, 6.0, CHARGE_Z - 0.3), (0.55, 0.5, 0.6), "phosphate", pattern="lines")  # lever


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, FRONT_Z))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, FRONT_Z - BARREL_END_Z), "barrel_steel")
    md = T.default_part(m, "muzzle", MUZZLE_MOUNT)                                  # thread protector
    md.box((-0.5, 5, MUZZLE_Z), (1, 1, BARREL_END_Z - MUZZLE_Z), "phosphate", pattern="knurl", inflate=0.1)


def _handguard(m: Model) -> None:
    """The railed nose in front of the magazine well (Gen II: short bottom and side rails)."""
    length = NOSE_REAR_Z - FRONT_Z
    hg = m.bone("handguard", pivot=(0, BORE_Y, NOSE_REAR_Z))
    hg.box((-1, 3.5, FRONT_Z), (2, UPPER_Y - 3.5, length), "v_poly")                # nose body round the barrel
    hg.box((-0.5, 3.0, FRONT_Z), (1, 0.5, length), "v_poly", pattern="rails")       # bottom rail
    hg.box((-1.25, 4.5, FRONT_Z + 0.25), (2.5, 0.5, length - 0.5), "v_poly", pattern="rails")  # side rails
    hg.box((-0.75, 4.75, FRONT_Z - 0.05), (1.5, 1.5, 0.05), "port")                 # front opening round the barrel


def _sights(m: Model) -> None:
    fs = m.bone("front_sight", pivot=(0, RAIL_Y, FRONT_SIGHT_Z))                    # flip-up post, raised
    fs.box((-1, RAIL_Y, FRONT_SIGHT_Z - 0.5), (2, 0.25, 1), "phosphate")            # base
    fs.box((-0.25, RAIL_Y + 0.25, FRONT_SIGHT_Z - 0.25), (0.5, SIGHT_Y - RAIL_Y - 0.25, 0.5),
           "phosphate")                                                             # post: TOP at SIGHT_Y
    for x in (-1, 0.5):                                                             # protective ears
        fs.box((x, RAIL_Y + 0.25, FRONT_SIGHT_Z - 0.25), (0.5, SIGHT_Y - RAIL_Y, 0.5), "phosphate")

    rs = m.bone("rear_sight", pivot=(0, RAIL_Y, REAR_SIGHT_Z))                      # flip-up aperture, raised
    z = REAR_SIGHT_Z - 0.25
    rs.box((-1, RAIL_Y, z - 0.25), (2, SIGHT_Y - 0.5 - RAIL_Y, 1), "phosphate")     # base = bottom of the aperture
    rs.box((-1, SIGHT_Y - 0.5, z), (0.5, 1, 0.5), "phosphate")                      # 1 x 1 hole centred on SIGHT_Y
    rs.box((0.5, SIGHT_Y - 0.5, z), (0.5, 1, 0.5), "phosphate")
    rs.box((-1, SIGHT_Y + 0.5, z), (2, 0.25, 0.5), "phosphate")                     # top of the frame


def _stock(m: Model) -> None:
    """Side-folding skeletal stock, shown unfolded: hinge, top beam, sloping lower strut, butt."""
    z0, z1 = REAR_Z, STOCK_REAR_Z
    st = T.default_part(m, "stock", STOCK_PIVOT)
    st.box((-1, UPPER_Y, z0), (2, 1.5, 0.5), "phosphate")                           # hinge block
    st.box((-0.5, 6.0, z0 + 0.5), (1, 1, z1 - z0 - 1.25), "v_poly")                 # top beam
    st.box((-0.6, 5.9, z0 + 2.5), (1.2, 1.2, 0.75), "v_poly", pattern="lines")      # length-adjustment collar
    st.wedge((0, 5.6, z0 + 0.75), (0, 3.25, z1 - 1.0), 1, 0.75, "v_poly")           # lower strut
    st.box((-0.75, 2.5, z1 - 0.75), (1.5, 4.75, 0.5), "v_poly", pattern="lines")    # butt plate
    st.box((-0.75, 2.5, z1 - 0.25), (1.5, 4.75, 0.25), "rubber")                    # butt pad


def _grip_trigger(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "v_poly", pattern="stipple",
           rot=(-14, 0, 0), pivot=(0, 4, 1), inflate=-0.05)                         # upright Vector grip
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.5, 2.5, -1), (1, 1, 0.75), "barrel_steel")


def _magazine(m: Model) -> None:
    """Glock 21 magazine with the KRISS +12 extension (25 rounds): a long straight stick,
    1.5 deep, 4 below the mouth of the well."""
    kw = dict(rot=(-MAG_RAKE, 0, 0), pivot=MAG_PIVOT)
    z = MAG_PIVOT[2] - 0.75
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    mg.box((-0.5, WELL_Y - 2.0, z), (1, 2.0, 1.5), "v_mag", pattern="lines", inflate=-0.03, **kw)   # magazine body
    mg.box((-0.6, WELL_Y - 3.75, z - 0.1), (1.2, 1.75, 1.7), "v_magext", **kw)      # +12 extension sleeve
    mg.box((-0.65, WELL_Y - 4.0, z - 0.15), (1.3, 0.25, 1.8), "phosphate", **kw)    # floor plate


def _animations(m: Model) -> None:
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_LENGTH, rise=MUZZLE_RISE)
    T.reload_mag(m, length=RELOAD_LENGTH)
    # empty: after the swap the LEFT hand racks the charging lever on the left of the front
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
    att.add_angled_grip(m, "underbarrel_mount", ANGLED_GRIP_AT)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=(1.1, 5.25, -5.15),
               sight_line=(0, SIGHT_Y, REAR_SIGHT_Z))
    _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "loadout_a": att.loadout(m, "holographic", "suppressor_9mm", "laser") | arms,
        "loadout_micro": att.loadout(m, "micro_red_dot", "suppressor_9mm"),
        "vertical_grip": att.loadout(m, "vertical_grip", "red_dot") | arms,
        "angled_grip": att.loadout(m, "angled_grip", "holographic", "laser") | arms,
        "vertical_grip_arms": att.loadout(m, "vertical_grip"),
        "angled_grip_arms": att.loadout(m, "angled_grip"),
    }
    return m
