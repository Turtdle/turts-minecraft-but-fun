"""Desert Eagle Mark XIX, .50 AE, 6 in barrel - gas-operated, rotating-bolt semi-auto pistol, 7 rounds.

Scale as every gun in the mod: 1 unit ~ 3.35 cm -> 267 mm overall = 8 units, 152 mm barrel =
4.5 units. Height is a little chunky on purpose (5 units for 149 mm, y 1.5 .. 6.5), like the striker pistols.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: the long FIXED slab-sided barrel with its triangular cross-section (wide at the
bottom, narrow rail on top) fills the front 4.5 units; the short slide sits behind it and reaches
forward under the barrel as two side arms along the frame; big squared trigger guard, deep
upright grip with a black rubber wrap-around, brushed-steel finish.

Bones: `barrel` (fixed, carries the top rail and the front sight), `slide` (rear block + side
arms + bolt + `rear_sight`), `receiver` (frame), `grip`, `trigger`, `magazine`. The optic sits on
the BARREL rail, so `optic_mount` hangs off `receiver` (fixed) and does not reciprocate.
Arms, mounts, attachments and all animations come from templates.py.
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "deagle"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
W = 1.0                    # half width of barrel base, slide and frame (a chunky 2 units)
MUZZLE_Z = -5.5            # muzzle face of the barrel
BARREL_REAR_Z = -1.0       # barrel / slide seam (the chamber end)
REAR_Z = 2.5               # rear face of slide and frame
FRAME_FRONT_Z = -5.25      # front of the frame and of the slide arms
FRAME_Y = 4.0              # bottom of the frame strip
ARM_Y = 4.5                # bottom of the slide arms (top of the frame strip)
BARREL_Y = 4.75            # bottom of the barrel / of the slide's rear block
SHOULDER_Y = 5.5           # where barrel and slide step in to the narrower top
TOP_Y = 6.0                # top of barrel and slide
RAIL_Y = 6.25              # top of the barrel's scope rail
SIGHT_H = 0.5
SIGHT_Y = TOP_Y + SIGHT_H  # 6.5: front blade tip = top of the rear notch
REAR_SIGHT_LEN = 0.5
GUARD_Z = (-2.25, 0.0)     # trigger guard front / rear
GUARD_Y = 2.5              # bottom of the trigger guard
PORT_Z = (-1.0, 0.0)       # ejection port: top right of the slide, right behind the barrel

# ---- grip / magazine -------------------------------------------------------------------
GRIP_PIVOT = (0.0, 4.15, 1.0)          # the grip (and the magazine in it) is raked about this
GRIP_RAKE = 14.0
GRIP_LEN = 2.5                         # along the raked axis
GRIP_DEPTH = 2.25
MAG_PIVOT = (0.0, FRAME_Y, 1.0)        # top centre of the magazine well

# ---- mounts / locators -----------------------------------------------------------------
OPTIC_MOUNT = (0, RAIL_Y, -3.0)        # middle of the barrel rail
SIGHT_LINE = (0, SIGHT_Y, REAR_Z - REAR_SIGHT_LEN / 2)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "pistol"
R_HAND = (0, 3.25, 1.0)                # same height below the trigger guard roof as the M17's hand

# ---- attachments -------------------------------------------------------------------------
ATTACHMENTS = ("red_dot", "micro_red_dot")

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.16         # heavy, slow cycle; keep the shot interval at or above 0.2 s
RECOIL = 0.9
MUZZLE_FLIP = 9.0
SLIDE_TRAVEL = 1.5
RELOAD_LENGTH = 2.0
RELOAD_EMPTY_LENGTH = 2.4


def _materials(m: Model) -> None:
    m.material("de_barrel", (158, 160, 166), 7, 0.74)   # brushed steel barrel
    m.material("de_slide", (150, 152, 158), 7, 0.74)    # slide
    m.material("de_frame", (134, 136, 143), 6, 0.76)    # frame, a shade darker
    m.material("de_inner", (72, 74, 80), 5, 0.78)       # gas cylinder / rails seen with the slide back
    m.material("de_flute", (112, 114, 121), 5, 0.85)    # shaded step along the barrel flanks
    m.material("de_black", (26, 26, 29), 4, 0.78)       # sights, controls, hammer
    m.material("de_grip", (30, 30, 32), 3, 0.84)        # rubber grip
    m.material("de_mag", (92, 94, 100), 6, 0.76)        # magazine body / base plate
    m.material("de_bore", (12, 12, 14), 2, 0.9)


def _frame(m: Model) -> None:
    z0, length = FRAME_FRONT_Z, REAR_Z - FRAME_FRONT_Z
    (gf, gr) = GUARD_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-W, FRAME_Y, z0), (2 * W, ARM_Y - FRAME_Y, length), "de_frame")         # frame strip / dust cover
    rc.box((-0.75, ARM_Y, z0), (1.5, BARREL_Y - ARM_Y, length), "de_inner")         # gas cylinder + rails between the slide arms
    rc.box((-0.6, BARREL_Y, BARREL_REAR_Z), (1.2, 0.6, 1.5), "de_inner")            # barrel extension / feed way (seen with the slide back)
    rc.box((-0.5, GUARD_Y, gf), (1, 0.5, gr - gf + 1), "de_frame")                  # trigger guard: bottom bar (runs into the grip)
    rc.box((-0.5, GUARD_Y + 0.5, gf), (1, FRAME_Y - GUARD_Y - 0.5, 0.5), "de_frame")  # front bar
    rc.box((-0.5, 3.85, REAR_Z), (1, 0.6, 0.45), "de_frame")                         # beavertail
    rc.box((-0.25, 4.85, REAR_Z), (0.5, 0.9, 0.3), "de_black", pattern="lines")     # hammer spur
    ct = m.bone("controls", parent="receiver", pivot=(0, FRAME_Y, 0))
    ct.box((-W - 0.1, 4.1, -1.9), (0.1, 0.3, 1.2), "de_black")                     # slide catch (left)
    ct.box((-W - 0.1, 4.1, -3.4), (2 * W + 0.2, 0.3, 0.4), "de_black")             # takedown lever / pin
    ct.box((-W - 0.1, 3.5, 0.0), (0.15, 0.4, 0.4), "de_black")                     # magazine catch (left)


def _grip(m: Model) -> None:
    (px, py, pz), L, d = GRIP_PIVOT, GRIP_LEN, GRIP_DEPTH
    kw = dict(rot=(-GRIP_RAKE, 0, 0), pivot=GRIP_PIVOT)
    gr = m.bone("grip", pivot=GRIP_PIVOT)
    gr.box((-0.75, py - L, pz - d / 2), (1.5, L, d), "de_frame", **kw)              # grip frame (steel front strap)
    gr.box((-W, py - L + 0.2, pz - d / 2 + 0.5), (2 * W, L - 0.65, d - 0.4), "de_grip",
           pattern="stipple", **kw)                                                 # rubber wrap-around


def _magazine(m: Model) -> None:
    """Single-stack 7-round magazine inside the grip; only the base plate shows."""
    (px, py, pz), L, d = GRIP_PIVOT, GRIP_LEN, GRIP_DEPTH
    kw = dict(rot=(-GRIP_RAKE, 0, 0), pivot=GRIP_PIVOT)
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    mg.box((-0.5, py - L, pz - d / 2 + 0.25), (1, L - 0.4, d - 0.5), "de_mag", pattern="lines", **kw)
    mg.box((-0.75, py - L - 0.25, pz - d / 2 - 0.1), (1.5, 0.25, d + 0.2), "de_mag", **kw)   # base plate


def _barrel(m: Model) -> None:
    """Fixed barrel, triangular in cross-section: 2 wide base, 1.5 wide shoulder, 1 wide rail."""
    z0, length = MUZZLE_Z, BARREL_REAR_Z - MUZZLE_Z
    ba = m.bone("barrel", pivot=(0, BORE_Y, BARREL_REAR_Z))
    ba.box((-W, BARREL_Y, z0), (2 * W, SHOULDER_Y - BARREL_Y, length), "de_barrel")           # base
    ba.box((-0.75, SHOULDER_Y, z0), (1.5, TOP_Y - SHOULDER_Y, length), "de_barrel")           # shoulder
    ba.box((-0.5, TOP_Y, z0 + 0.75), (1, RAIL_Y - TOP_Y, length - 0.75), "de_barrel", pattern="rails")  # scope rail
    ba.box((-W - 0.03, 5.0, z0 + 0.5), (2 * W + 0.06, 0.25, length - 1.0), "de_flute")        # flank step
    ba.box((-0.3, 5.2, z0 - 0.04), (0.6, 0.6, 0.04), "de_bore")                               # bore
    ba.box((-0.25, TOP_Y, z0 + 0.1), (0.5, SIGHT_H, 0.5), "de_black")                         # front blade: TOP = SIGHT_Y


def _slide(m: Model) -> None:
    z0, (p0, p1) = BARREL_REAR_Z, PORT_Z
    length = REAR_Z - z0
    sl = m.bone("slide", pivot=(0, BORE_Y, 0))
    for x in (-W, W - 0.25):                                                        # side arms along the frame, under the barrel
        sl.box((x, ARM_Y, FRAME_FRONT_Z), (0.25, BARREL_Y - ARM_Y, REAR_Z - FRAME_FRONT_Z), "de_slide")
    sl.box((-W, BARREL_Y, z0), (2 * W, SHOULDER_Y - BARREL_Y, length), "de_slide")  # rear block
    sl.box((-0.75, SHOULDER_Y, p1), (1.5, TOP_Y - SHOULDER_Y, REAR_Z - p1), "de_slide")   # top, behind the port
    sl.box((-0.75, SHOULDER_Y, p0), (0.75, TOP_Y - SHOULDER_Y, p1 - p0), "de_slide")      # top, left of the port
    sl.box((0, SHOULDER_Y, p0 + 0.05), (0.65, TOP_Y - SHOULDER_Y - 0.05, p1 - p0 - 0.05), "bright_steel")       # bolt in the ejection port
    sl.box((-W - 0.05, BARREL_Y + 0.1, 1.0), (2 * W + 0.1, 0.55, 1.25), "de_slide", pattern="lines")  # cocking serrations
    sl.box((-W - 0.12, 5.0, 0.2), (2 * W + 0.24, 0.3, 0.6), "de_black")             # ambidextrous safety on the slide

    rz, n = REAR_Z - REAR_SIGHT_LEN, REAR_SIGHT_LEN
    rs = m.bone("rear_sight", parent="slide", pivot=(0, TOP_Y, REAR_Z - n / 2))
    rs.box((-0.75, TOP_Y, rz), (1.5, SIGHT_H / 2, n), "de_black")                   # rear sight body
    rs.box((-0.75, TOP_Y + SIGHT_H / 2, rz), (0.5, SIGHT_H / 2, n), "de_black")     # left ear
    rs.box((0.25, TOP_Y + SIGHT_H / 2, rz), (0.5, SIGHT_H / 2, n), "de_black")      # right ear (0.5 notch)


def _trigger(m: Model) -> None:
    tr = m.bone("trigger", pivot=(0, FRAME_Y, -0.5))
    tr.box((-0.25, 3.1, -1.0), (0.5, 0.9, 0.5), "de_black")


def _animations(m: Model) -> None:
    T.idle(m)
    T.fire_pistol(m, travel=SLIDE_TRAVEL, length=FIRE_LENGTH, recoil=RECOIL, flip=MUZZLE_FLIP)
    T.reload_mag(m, length=RELOAD_LENGTH)
    T.reload_mag(m, length=RELOAD_EMPTY_LENGTH, empty=True, pull=SLIDE_TRAVEL)   # slide locked back, released at the end


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_frame, _grip, _barrel, _slide, _trigger, _magazine):
        build(m)
    T.arms(m, R_HAND, None, ARM_STYLE)
    T.mounts(m, optic=OPTIC_MOUNT)
    T.attach(m, *ATTACHMENTS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z - 0.25),
               shell_eject=(W + 0.1, TOP_Y - 0.25, sum(PORT_Z) / 2), sight_line=SIGHT_LINE)
    _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "loadout": att.loadout(m, "red_dot") | arms,
        "micro": att.loadout(m, "micro_red_dot"),
    }
    return m
