"""Colt Anaconda (2021), .44 Magnum, 6 in barrel - stainless double/single-action revolver, 6 rounds.

Scale as every gun in the mod: 1 unit ~ 3.35 cm -> 298 mm overall = 9 units, 152 mm barrel =
4.5 units (4 units of barrel proper + the 0.5 frame ring it screws into). Height is a little
chunky on purpose, like the other pistols. Muzzle toward -Z, right side is +X, trigger near
z = -0.75, bore axis at y = 5.5; the grip sits where the Desert Eagle's does, so the two-hand
"pistol" hold is the same.

Silhouette: slab barrel with the FULL-LENGTH underlug below and the ventilated rib on top (real
see-through vent slots), red-ramp front sight, fat fluted cylinder in the frame window, humped
frame with the exposed hammer spur, black rubber finger-groove grip, satin stainless everywhere.

Bones:
  `receiver`     frame (ring, top strap, lower strap, recoil shield, hump, trigger guard)
  `rear_sight`   adjustable rear sight with the white outline (child of receiver)
  `latch`        cylinder latch, LEFT of the frame behind the cylinder (pulled back to open)
  `barrel`       barrel + underlug + vent rib + front sight
  `crane`        the yoke: pivot = the hinge under the cylinder, +Z rotation swings the cylinder
                 out to the LEFT. Its visible part is the lower front corner of the frame.
  `cylinder`     child of crane, pivot on the cylinder axis (0, 5.0, z): turns 60 deg per shot
  `ejector`      child of cylinder: star on the rear face + the rod (hidden in the underlug when
                 closed); slides +Z to eject
  `rounds`       child of cylinder: the six cartridges in the chambers (rims show at the rear);
                 in the reload they are the cases that fall, then the fresh rounds
  `speedloader`  child of cylinder: 6-round speedloader, at rest HIDDEN inside the cylinder body;
                 it only appears (in the left hand) during the reload
  `hammer`, `trigger`, `grip`
No attachment mounts (the Anaconda has no rail) and no `shell_eject` locator: a revolver keeps its
cases when it fires. Arms and all animations come from templates.py (fire_revolver / reload_revolver).
"""
import attachments as att
import templates as T
from armorygen import Model

GUN_ID = "anaconda"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
MUZZLE_Z = -6.75           # muzzle face
RING_Z = -2.75             # barrel / frame seam (front of the frame ring)
WINDOW_Z = (-2.25, -0.75)  # cylinder window: rear of the frame ring / front of the recoil shield
SHIELD_REAR_Z = -0.25      # rear face of the recoil shield
FRAME_REAR_Z = 1.4         # rear end of the frame hump (backstrap shoulder)
FW = 0.5                   # frame half width
BW = 0.375                 # barrel / underlug half width (a slab, 0.75 wide)
FRAME_Y = 4.0              # bottom of the frame (roof of the trigger guard)
STRAP_Y = 5.75             # bottom of the top strap
TOP_Y = 6.25               # top of the top strap and of the vent rib
BARREL_Y = (5.0, 5.9)      # barrel proper, bottom / top
LUG_Y = 4.25               # bottom of the underlug
SIGHT_H = 0.5
SIGHT_Y = TOP_Y + SIGHT_H  # 6.75: front blade tip = top of the rear notch (as the M17)
GUARD_Z = (-2.0, 0.25)     # trigger guard front / rear
GUARD_Y = 2.5              # bottom of the trigger guard

# ---- cylinder / crane ------------------------------------------------------------------
CYL_AXIS_Y = 5.0           # the top chamber (0.5 above the axis) lines up with the bore
CYL_Z = (-2.1, -0.95)      # cylinder front / rear face (small gaps to the frame on both ends)
CYL_AF = 0.735             # 12-sided; half the distance across the flats
FACE = 0.1                 # chamber mouths stand this far off the end faces
CYL_CENTER = (0.0, CYL_AXIS_Y, sum(CYL_Z) / 2)
CHAMBER_R = 0.5            # chamber circle radius
CRANE_PIVOT = (0.0, 3.85, -2.5)   # hinge: under the cylinder axis, in the frame's lower front corner
CRANE_OPEN = 100.0         # degrees the crane swings out (cylinder ends left of and below the frame)
ROD_TIP_Z = -3.55          # front end of the ejector rod (inside the underlug)
EJECT_TRAVEL = 1.0
ROUND_LEN = 1.05           # cartridge length inside the chamber
RIM = 0.14                 # how far the rims / ejector star stand off the rear face

# ---- speedloader (hidden inside the cylinder at rest) ---------------------------------------
SL_BODY_Z = (-1.9, -1.5)   # body disc at rest
SL_KNOB_Z = (-1.5, -1.05)  # knob at rest
SL_PIVOT = (0.0, CYL_AXIS_Y, -1.3)
SL_SEAT = (CYL_Z[1] + RIM) - SL_BODY_Z[0]   # +Z travel that puts the body on the rims

# ---- hammer / trigger / grip ---------------------------------------------------------------
HAMMER_PIVOT = (0.0, 5.0, 0.15)
TRIGGER_PIVOT = (0.0, FRAME_Y, -0.65)
GRIP_PIVOT = (0.0, 4.2, 0.95)          # the grip is raked about this
GRIP_RAKE = 16.0
GRIP_LEN = 2.7                         # along the raked axis
GRIP_DEPTH = 1.75
GRIP_W = 0.65                          # half width

# ---- locators --------------------------------------------------------------------------
REAR_SIGHT_Z = (-0.65, -0.3)           # rear sight blade
SIGHT_LINE = (0, SIGHT_Y, sum(REAR_SIGHT_Z) / 2)
MUZZLE_FLASH = (0, BORE_Y, MUZZLE_Z - 0.25)

# ---- first-person arms -------------------------------------------------------------------
ARM_STYLE = "pistol"
R_HAND = (0, 3.25, 1.0)                # same as the Desert Eagle

# ---- animation tuning (seconds / units / degrees) --------------------------------------------
FIRE_LENGTH = 0.25         # keep the shot interval at or above this
RECOIL = 1.1
MUZZLE_FLIP = 15.0         # .44 Magnum: clearly more than the Desert Eagle's 9
RELOAD_LENGTH = 2.6


def _materials(m: Model) -> None:
    m.material("an_frame", (176, 178, 185), 7, 0.76)     # satin stainless frame
    m.material("an_barrel", (182, 184, 191), 7, 0.76)    # barrel / lug / rib, a shade brighter
    m.material("an_cyl", (166, 168, 176), 7, 0.74)       # cylinder
    m.material("an_flute", (122, 124, 132), 5, 0.84)     # cylinder flutes (shaded)
    m.material("an_parts", (150, 152, 160), 6, 0.74)     # hammer, trigger, latch, ejector
    m.material("an_dark", (28, 28, 31), 3, 0.85)         # chambers, bore, sight blades
    m.material("an_grip", (27, 27, 29), 3, 0.84)         # Hogue rubber
    m.material("an_grip_hi", (40, 40, 43), 3, 0.84)      # finger-groove ridges
    m.material("an_loader", (34, 34, 38), 4, 0.80)       # speedloader body
    m.material("an_knob", (120, 122, 128), 6, 0.74)      # speedloader knob (aluminium)


def _frame(m: Model) -> None:
    (w0, w1), (gf, gr) = WINDOW_Z, GUARD_Z
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-FW, 4.6, RING_Z), (2 * FW, TOP_Y - 4.6, w0 - RING_Z), "an_frame")               # frame ring round the barrel
    rc.box((-0.4, STRAP_Y, w0), (0.8, TOP_Y - STRAP_Y, w1 - w0), "an_frame")                 # top strap
    rc.box((-FW, FRAME_Y, w0), (2 * FW, 0.25, w1 - w0), "an_frame")                          # lower strap under the cylinder
    rc.box((-0.65, FRAME_Y, w1), (1.3, TOP_Y - FRAME_Y, SHIELD_REAR_Z - w1), "an_frame")     # recoil shield
    rc.box((-FW, FRAME_Y, SHIELD_REAR_Z), (2 * FW, 1.4, 0.9), "an_frame")                    # hump behind the shield
    rc.box((-FW, FRAME_Y, SHIELD_REAR_Z + 0.9), (2 * FW, 0.9, FRAME_REAR_Z - SHIELD_REAR_Z - 0.9), "an_frame")  # backstrap shoulder
    rc.box((-0.4, GUARD_Y, gf), (0.8, 0.3, gr - gf), "an_frame")                             # trigger guard: bottom bar
    rc.box((-0.4, GUARD_Y + 0.3, gf), (0.8, FRAME_Y - GUARD_Y - 0.3, 0.35), "an_frame")      # front bar
    rc.box((-0.4, GUARD_Y + 0.3, gr - 0.35), (0.8, FRAME_Y - GUARD_Y - 0.3, 0.6), "an_frame")  # rear, runs into the grip

    (s0, s1) = REAR_SIGHT_Z
    rs = m.bone("rear_sight", parent="receiver", pivot=(0, TOP_Y, (s0 + s1) / 2))
    rs.box((-0.3, TOP_Y, -1.5), (0.6, 0.2, s0 + 1.5), "an_dark")                             # sight leaf along the top strap
    rs.box((-0.45, TOP_Y, s0), (0.9, SIGHT_H / 2, s1 - s0), "an_dark")                       # blade base
    rs.box((-0.45, TOP_Y + SIGHT_H / 2, s0), (0.3, SIGHT_H / 2, s1 - s0), "an_dark")         # left ear
    rs.box((0.15, TOP_Y + SIGHT_H / 2, s0), (0.3, SIGHT_H / 2, s1 - s0), "an_dark")          # right ear (0.3 notch)
    rs.box((-0.23, TOP_Y + SIGHT_H / 2, s1), (0.08, SIGHT_H / 2, 0.03), "white")             # white outline: left
    rs.box((0.15, TOP_Y + SIGHT_H / 2, s1), (0.08, SIGHT_H / 2, 0.03), "white")              # right
    rs.box((-0.23, TOP_Y + SIGHT_H / 2 - 0.08, s1), (0.46, 0.08, 0.03), "white")             # bottom

    lt = m.bone("latch", parent="receiver", pivot=(-0.7, 5.05, -0.5))
    lt.box((-0.77, 4.85, -0.72), (0.12, 0.4, 0.45), "an_parts", pattern="knurl")


def _barrel(m: Model) -> None:
    z0, length = MUZZLE_Z, RING_Z - MUZZLE_Z
    (b0, b1) = BARREL_Y
    ba = m.bone("barrel", pivot=(0, BORE_Y, RING_Z))
    ba.box((-BW, b0, z0), (2 * BW, b1 - b0, length), "an_barrel")                            # barrel
    ba.box((-BW, LUG_Y, z0 + 0.15), (2 * BW, b0 - LUG_Y, length - 0.15), "an_barrel")        # full-length underlug
    ba.box((-0.28, LUG_Y + 0.12, z0), (0.56, b0 - LUG_Y - 0.12, 0.15), "an_barrel")          # its rounded nose
    ba.box((-0.2, BORE_Y - 0.2, z0 - 0.04), (0.4, 0.4, 0.04), "an_dark")                     # bore
    # ventilated rib: a top strip on posts, the gaps between the posts are the vent slots
    rib_w, strip = 0.5, 0.12
    ba.box((-rib_w / 2, TOP_Y - strip, z0), (rib_w, strip, length), "an_barrel")
    slots, slot = 5, 0.48
    post = (length - slots * slot) / (slots + 1)
    for i in range(slots + 1):
        ba.box((-rib_w / 2, b1, z0 + i * (post + slot)), (rib_w, TOP_Y - strip - b1, post), "an_barrel")
    # red-ramp front sight
    ba.box((-0.15, TOP_Y, z0 + 0.1), (0.3, SIGHT_H / 2, 0.85), "an_dark")                    # ramp base
    ba.box((-0.15, TOP_Y + SIGHT_H / 2, z0 + 0.3), (0.3, SIGHT_H / 2, 0.45), "an_dark")      # blade: TOP = SIGHT_Y
    ba.box((-0.15, TOP_Y + SIGHT_H / 2, z0 + 0.75), (0.3, SIGHT_H / 2, 0.06), "red")         # red insert facing the shooter


def _prism(b, center, across: float, z0: float, z1: float, mat: str) -> None:
    """Regular 12-sided prism along Z (`across` = half the distance across the flats) as six
    slim boxes, 30 deg apart: convex, no star points, and it looks the same every 30 deg. The
    boxes' end faces are staggered by 0.012 so they do not z-fight each other."""
    (cx, cy, _), side = center, 2 * across * 0.26795          # tan(15 deg)
    for i in range(6):
        e = 0.012 * i
        b.box((cx - side / 2, cy - across, z0 - e), (side, 2 * across, z1 - z0 + 2 * e), mat,
              rot=(0, 0, 30 * i), pivot=(cx, cy, (z0 + z1) / 2))


def _cylinder(m: Model) -> None:
    (c0, c1), (cx, cy, cz) = CYL_Z, CYL_CENTER
    length = c1 - c0
    cr = m.bone("crane", pivot=CRANE_PIVOT)
    cr.box((-FW, 3.85, RING_Z), (2 * FW, 0.75, WINDOW_Z[0] - RING_Z), "an_frame")           # yoke: lower front corner of the frame

    cy_b = m.bone("cylinder", parent="crane", pivot=CYL_CENTER)
    _prism(cy_b, CYL_CENTER, CYL_AF, c0, c1, "an_cyl")
    for a in (0, 60, 120):                                                                   # six flutes, between the chambers
        cy_b.cbox((cx, cy, c0 + 0.5), (2 * CYL_AF + 0.05, 0.3, 0.9), "an_flute", rot=(0, 0, a), pivot=CYL_CENTER)
    for i in range(6):                                                                       # chamber mouths, front and rear
        a = 60 * i
        cy_b.box((-0.14, cy + CHAMBER_R - 0.14, c0 - FACE), (0.28, 0.28, FACE), "an_dark", rot=(0, 0, a), pivot=(cx, cy, c0))
        cy_b.box((-0.14, cy + CHAMBER_R - 0.14, c1), (0.28, 0.28, FACE), "an_dark", rot=(0, 0, a), pivot=(cx, cy, c1))

    ej = m.bone("ejector", parent="cylinder", pivot=(cx, cy, c1))
    ej.box((-0.2, cy - 0.2, c1), (0.4, 0.4, RIM - 0.04), "an_parts", rot=(0, 0, 45), pivot=(cx, cy, c1))   # star
    ej.box((-0.1, cy - 0.1, ROD_TIP_Z + 0.3), (0.2, 0.2, c1 - ROD_TIP_Z - 0.3), "an_parts")                       # rod (in the lug)
    ej.box((-0.14, cy - 0.14, ROD_TIP_Z), (0.28, 0.28, 0.3), "an_parts", pattern="knurl")             # knurled tip

    rd = m.bone("rounds", parent="cylinder", pivot=(cx, cy, c1))
    for i in range(6):
        kw = dict(rot=(0, 0, 60 * i), pivot=(cx, cy, c1))
        rd.box((-0.13, cy + CHAMBER_R - 0.13, c1 - ROUND_LEN), (0.26, 0.26, ROUND_LEN), "brass", **kw)    # case
        rd.box((-0.17, cy + CHAMBER_R - 0.17, c1), (0.34, 0.34, RIM), "brass", **kw)                      # rim

    (s0, s1), (k0, k1) = SL_BODY_Z, SL_KNOB_Z
    sl = m.bone("speedloader", parent="cylinder", pivot=SL_PIVOT)
    _prism(sl, (cx, cy, 0), 0.62, s0, s1, "an_loader")                                       # body disc
    sl.cyl((cx, cy, (k0 + k1) / 2), 0.5, k1 - k0, "an_knob", sides=8)                        # release knob


def _hammer_trigger(m: Model) -> None:
    hm = m.bone("hammer", pivot=HAMMER_PIVOT)
    hm.box((-0.15, 4.7, -0.2), (0.3, 1.5, 0.45), "an_parts")                                 # hammer body, in the frame slot
    hm.box((-0.2, 5.8, 0.25), (0.4, 0.3, 0.55), "an_parts", pattern="lines")                 # checkered spur
    tr = m.bone("trigger", pivot=TRIGGER_PIVOT)
    tr.box((-0.15, 3.05, -0.9), (0.3, 0.95, 0.35), "an_parts")
    tr.box((-0.15, 3.05, -1.05), (0.3, 0.3, 0.15), "an_parts")                               # curved toe


def _grip(m: Model) -> None:
    (px, py, pz), L, d, w = GRIP_PIVOT, GRIP_LEN, GRIP_DEPTH, GRIP_W
    kw = dict(rot=(-GRIP_RAKE, 0, 0), pivot=GRIP_PIVOT)
    front = pz - d / 2
    gr = m.bone("grip", pivot=GRIP_PIVOT)
    gr.box((-w, py - L, front), (2 * w, L, d), "an_grip", pattern="stipple", **kw)          # rubber body
    gr.box((-w - 0.05, py - L - 0.05, front - 0.1), (2 * w + 0.1, 0.5, d + 0.2), "an_grip", **kw)  # flared butt
    for i in range(3):                                                                       # finger-groove ridges on the front strap
        gr.box((-w + 0.1, py - 0.75 - 0.62 * i, front - 0.15), (2 * w - 0.2, 0.3, 0.15), "an_grip_hi", **kw)
    gr.box((-w, py, pz + 0.1), (2 * w, 0.55, d / 2 - 0.1), "an_grip", **kw)                   # rubber up the backstrap


def _animations(m: Model) -> None:
    T.idle(m)
    T.fire_revolver(m, length=FIRE_LENGTH, recoil=RECOIL, flip=MUZZLE_FLIP)
    kw = dict(length=RELOAD_LENGTH, rod_tip=(0, CYL_AXIS_Y, ROD_TIP_Z), seat=SL_SEAT, clear=ROUND_LEN + 0.2,
              open_deg=CRANE_OPEN, eject_travel=EJECT_TRAVEL)
    T.reload_revolver(m, **kw)
    T.reload_revolver(m, empty=True, **kw)    # a revolver reloads the same way empty or not


def model() -> Model:
    m = Model(GUN_ID, density=1)
    _materials(m)
    for build in (_frame, _barrel, _cylinder, _hammer_trigger, _grip):
        build(m)
    T.arms(m, R_HAND, None, ARM_STYLE)
    m.locator("muzzle_flash", MUZZLE_FLASH)
    m.locator("sight_line", SIGHT_LINE)
    _animations(m)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "open": att.loadout(m) | arms | {"receiver", "barrel", "grip", "hammer", "trigger"},   # crane assembly only
    }
    return m
