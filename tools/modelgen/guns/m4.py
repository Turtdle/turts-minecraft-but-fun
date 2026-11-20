"""M4 carbine - chunky "vanilla item made 3D" style.

Scale: 1 unit ~ 3.5 cm  ->  ~25 units long with the stock extended.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.
Everything is whole-unit boxes; the only half units are the X/Y centring of
1-wide parts on a 2-wide gun, the iron sights and a few 0.25 inflates.
"""
import attachments as att
from armorygen import Model

# ======================================================================================
# IN-GAME TUNING NOTES (verified with ./gradlew runAutotest screenshots, NeoForge 1.21.1,
# GeckoLib 4.9.3). Read these before tuning another gun.
#
# * armorygen now exports in GeckoLib's convention (X mirrored, X/Y rotations negated), so
#   the game shows exactly what the previews show: +X = right side of the gun, -Z = muzzle,
#   rotation/position signs as in the preview. No sign fiddling per gun.
# * GeckoLib puts the MODEL ORIGIN at the centre of the vanilla item transform. So in
#   models/item/<gun>.json every display "translation" moves the model origin (16 = 1 block,
#   applied before rotation/scale) and "scale" scales around the origin.
#   1 model unit on screen = scale / 16 blocks.
# * firstperson_righthand: vanilla already puts the item at view (0.56, -0.52, -0.72) blocks
#   (x right, y up, -z forward). origin_view = that + translation/16.
#   +tx = right, +ty = up, +tz = toward the camera. Keep rotation 0: a gun parallel to the
#   view axis already "points at the crosshair" by perspective, and ADS stays aligned.
#   M4: translation (-4.0, 2.72, 0.64), scale 0.6 -> origin at view (0.31, -0.35, -0.68).
# * ADS (ClientState.adsX/Y/Z, blocks, same view axes) is added to the hip position:
#     adsX = -origin_view.x
#     adsY = -(sight_line.y * scale/16 + 0.01) - origin_view.y
#     adsZ = -(eye_z * scale/16) - origin_view.z     eye_z = model z where the eye sits
#   M4: sight_line.y = 7.5, eye_z = 10.5 (over the stock, 9 units behind the rear sight)
#   -> (-0.31, 0.059, 0.286). Smaller eye_z = closer to the rear sight (it gets huge).
# * thirdperson_righthand (arm in CROSSBOW_HOLD / BOW_AND_ARROW pose, set in ClientState):
#   rotation 0 = muzzle along the arm. +ty = up, +tz = back toward the shoulder,
#   ry = -17 cancels the arm's inward yaw so the gun points straight ahead (+17 for lefthand).
# * gui: rotation (0, -90, 0) = right side toward the viewer, muzzle to the right;
#   +tx right, +ty up (in 1/16 of the slot). Centre with tx = -(z_centre * scale),
#   ty = -(y_centre * scale). In-plane tilt is impossible at ry = +-90 (gimbal lock).
# * fixed (item frame): rotation (0, 90, 0) gives the same side-on view; the frame halves it.
#   Here the centring sign is the other way round: tx = +(z_centre * scale).
# * Arms: bone pivot = hand; R/L_ARM_ROT (rx, ry): rx 90 = forearm straight back, 180 =
#   straight down; ry + = swings toward +X. Aim the forearm at the elbow, which sits roughly
#   6-7 units outside, 6 below and 6-10 behind the hand at this scale.
# ======================================================================================

BORE_Y = 5.5
SIGHT_Y = 8.0          # the sight line: front post tip and centre of the rear aperture hole
REAR_SIGHT_Z = 1.75    # z centre of the rear aperture frame

# ---- attachment mount points (pivots of the mount bones; see attachments.py) ---------
OPTIC_MOUNT = (0, 7, -1)             # top of the receiver rail, middle of the receiver
MUZZLE_MOUNT = (0, BORE_Y, -14)      # end of the barrel (the A2 birdcage sits in front of it)
UNDERBARREL_MOUNT = (0, 4, -7)       # bottom face of the handguard's bottom rail, rail centre
SIDE_MOUNT = (1.25, BORE_Y, -6.5)    # outer face of the right rail, toward the rear (hand is in front)
STOCK_PIVOT = (0, BORE_Y, 2)
MAG_PIVOT = (0, 4, -3)

# ---- first-person arms: tweak here -------------------------------------------------
ARM_W, ARM_LEN = 3, 26                 # arm thickness (X and Z) and length (Y) before rotation
# Bone pivots sit at the hand; bone rotation (deg, X then Y then Z) lays the vertical arm down.
# rx > 90 = forearm runs back (+Z) and down; ry = outward swing (+ = toward +X).
R_HAND = (0, 2, 2)                     # centre of the pistol grip
R_ARM_ROT = (135, 40, 0)
L_HAND = (0, 4.5, -8.5)                # hand wrapped round the front of the handguard, just behind the front sight
L_ARM_ROT = (128, -36, 0)

# ---- animation signs (previews == game since the exporter fix; no need to flip) ----
PITCH_UP = 1.0      # sign of root X-rotation that RAISES the muzzle
ROLL_RIGHT = -1.0   # sign of root Z-rotation that tips the top of the gun toward +X
RECOIL = 0.8        # units the gun kicks back when firing
BOLT_TRAVEL = 2.0   # units the bolt slides back (+Z)


def model() -> Model:
    m = Model("m4", density=1)
    m.material("anodised", (52, 54, 61), 5, 0.72)      # receiver
    m.material("furniture", (68, 68, 69), 5, 0.76)     # handguard / stock / grip polymer
    m.material("barrel_steel", (98, 101, 110), 6, 0.72)
    m.material("mag_grey", (124, 126, 130), 6, 0.72)      # aluminium STANAG mag
    m.material("phosphate", (36, 36, 41), 4, 0.78)     # flash hider, sights

    # ---- receiver ---------------------------------------------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 5, -4), (2, 1, 6), "anodised")                       # upper receiver
    rc.box((-1, 6, -4), (2, 1, 6), "anodised", pattern="rails")      # flat-top picatinny rail
    rc.box((-1, 4, -4), (2, 1, 6), "anodised")                       # lower receiver
    rc.box((-1, 2, -4), (2, 2, 2), "anodised")                       # magazine well
    rc.box((-0.5, 2, -2), (1, 1, 3), "anodised")                     # trigger guard
    rc.box((0.5, 5, 0), (1, 1, 1), "anodised")                       # brass deflector
    rc.box((0.5, 5, 1), (1, 1, 1), "barrel_steel", pattern="knurl")  # forward assist

    # ---- bolt (visible through the ejection port, right side) --------
    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -1))
    bo.box((0.1, 5, -2), (1, 1, 2), "bright_steel")

    # ---- charging handle ---------------------------------------------
    ch = m.bone("charging_handle", pivot=(0, 6.5, 2))
    ch.box((-1, 6, 2), (2, 1, 1), "barrel_steel")

    # ---- barrel ------------------------------------------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -4))
    ba.box((-0.5, 5, -14), (1, 1, 5), "barrel_steel")                # barrel in front of handguard
    ba.box((-0.5, 5, -11), (1, 1, 1), "barrel_steel", inflate=0.25)  # thick section before M203 cut
    ba.box((-0.5, 5, -10), (1, 2, 1), "phosphate", inflate=0.25)     # gas block / sight base
    md = m.bone("muzzle_default", parent="barrel", pivot=MUZZLE_MOUNT)  # hidden when a muzzle device is fitted
    md.box((-0.5, 5, -16), (1, 1, 2), "phosphate", pattern="lines", inflate=0.25)  # A2 birdcage

    # ---- front sight (A2: post in a U between two protective wings) ----
    fs = m.bone("front_sight", pivot=(0, 6, -9.5))
    fs.box((-1, 7, -10), (2, 0.5, 1), "phosphate")                   # sight base, top = bottom of the aperture
    fs.box((-1, 7.5, -10), (0.5, SIGHT_Y + 0.5 - 7.5, 1), "phosphate")   # left wing, 0.5 above the post tip
    fs.box((0.5, 7.5, -10), (0.5, SIGHT_Y + 0.5 - 7.5, 1), "phosphate")  # right wing
    fs.box((-0.25, 7.5, -9.75), (0.5, SIGHT_Y - 7.5, 0.5), "phosphate")  # post: TOP exactly at SIGHT_Y
    fs.wedge((0, 5.75, -7.5), (0, 7.0, -8.75), 1, 1, "phosphate")    # 45 deg rear leg (stays below the post)

    # ---- handguard ----------------------------------------------------
    hg = m.bone("handguard", pivot=(0, BORE_Y, -4))
    # quad-rail RIS/RAS: picatinny on all four sides. The top rail stops 1 short of the front
    # so the front sight's rear leg stays visible.
    hg.box((-1, 4.5, -9), (2, 2, 4), "anodised")                             # rail body
    hg.box((-1, 6.5, -8), (2, 0.5, 3), "anodised", pattern="rails")          # top rail (flush with the receiver rail)
    hg.box((-1.25, 5.25, -9), (2.5, 0.5, 4), "anodised", pattern="rails")    # side rails
    hg.box((-0.5, 4, -9), (1, 0.5, 4), "anodised", pattern="rails")          # bottom rail
    hg.box((-1, 4.5, -5), (2, 2, 1), "barrel_steel", inflate=0.25)   # delta ring

    # ---- rear sight (flip-up aperture: 1x1 hole centred on SIGHT_Y) ------
    rs = m.bone("rear_sight", pivot=(0, 7, 1.5))
    rs.box((-1, 7, 1), (2, SIGHT_Y - 0.5 - 7, 1), "phosphate")               # base on the rail = bottom of the frame
    rs.box((-1, SIGHT_Y - 0.5, REAR_SIGHT_Z - 0.25), (0.5, 1, 0.5), "phosphate")   # left side of the frame
    rs.box((0.5, SIGHT_Y - 0.5, REAR_SIGHT_Z - 0.25), (0.5, 1, 0.5), "phosphate")  # right side
    rs.box((-1, SIGHT_Y + 0.5, REAR_SIGHT_Z - 0.25), (2, 0.5, 0.5), "phosphate")   # top bar

    # ---- stock ----------------------------------------------------------
    m.bone("stock", pivot=STOCK_PIVOT)
    st = m.bone("stock_default", parent="stock", pivot=STOCK_PIVOT)   # hidden when a stock is fitted
    st.box((-0.5, 5, 2), (1, 1, 3), "barrel_steel", pattern="lines")  # buffer tube
    st.box((-1, 4.5, 5), (2, 2, 4), "furniture")                      # stock body around the tube
    st.box((-1, 3.5, 7), (2, 1, 2), "furniture", pattern="lines")     # stepped underside
    st.box((-1, 2.5, 8), (2, 1, 1), "furniture")                      # toe of the butt

    # ---- grip / trigger ---------------------------------------------------
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "furniture", pattern="stipple",
           rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)
    tr = m.bone("trigger", pivot=(0, 4, -0.5))
    tr.box((-0.5, 3, -1), (1, 1, 1), "barrel_steel")

    # ---- magazine ----------------------------------------------------------
    m.bone("magazine", pivot=MAG_PIVOT)
    mg = m.bone("magazine_default", parent="magazine", pivot=MAG_PIVOT)  # hidden when a magazine is fitted
    mg.box((-0.5, 1, -4), (1, 1, 2), "mag_grey", pattern="lines")
    mg.box((-0.5, -2, -4), (1, 3, 2), "mag_grey", pattern="lines",
           rot=(22.5, 0, 0), pivot=(0, 1, -2), inflate=-0.05)

    # ---- first-person arms (player skin; direct children of root) ---------------
    hw = ARM_W / 2
    ra = m.bone("right_arm", pivot=R_HAND, rotation=R_ARM_ROT)
    ra.skin_box((R_HAND[0] - hw, R_HAND[1] - 1.5, R_HAND[2] - hw), (ARM_W, ARM_LEN, ARM_W), side="right")
    # index finger: continues past the fist along the right side of the receiver to the trigger
    ra.skin_box((R_HAND[0] + 1, R_HAND[1] - 3.5, R_HAND[2] - 0.5), (1, 2, 1), side="right", sleeve=False)
    la = m.bone("left_arm", pivot=L_HAND, rotation=L_ARM_ROT)
    la.skin_box((L_HAND[0] - hw, L_HAND[1] - 1.5, L_HAND[2] - 1), (ARM_W, ARM_LEN, ARM_W), side="left")

    # ---- attachments (bones att_<id>, hidden in game unless fitted) --------------
    m.bone("optic_mount", parent="receiver", pivot=OPTIC_MOUNT)
    m.bone("muzzle_mount", parent="barrel", pivot=MUZZLE_MOUNT)
    m.bone("underbarrel_mount", parent="handguard", pivot=UNDERBARREL_MOUNT)
    m.bone("side_mount", parent="handguard", pivot=SIDE_MOUNT)
    for add in (att.add_red_dot, att.add_holographic, att.add_acog, att.add_sniper_scope):
        add(m, "optic_mount", OPTIC_MOUNT)
    for add in (att.add_suppressor_556, att.add_compensator_556):
        add(m, "muzzle_mount", MUZZLE_MOUNT)
    for add in (att.add_vertical_grip, att.add_angled_grip, att.add_bipod):
        add(m, "underbarrel_mount", UNDERBARREL_MOUNT)
    att.add_laser(m, "side_mount", SIDE_MOUNT)
    att.add_stock_ctr(m, "stock", STOCK_PIVOT)
    att.add_pmag_40(m, "magazine", MAG_PIVOT)
    m.preview_configs = {
        "bare": att.loadout(m),
        "loadout_a": att.loadout(m, "red_dot", "suppressor_556", "vertical_grip", "laser",
                                 "stock_ctr", "pmag_40"),
        "loadout_b": att.loadout(m, "acog", "compensator_556", "angled_grip"),
    }

    # ---- locators ------------------------------------------------------------
    m.locator("muzzle_flash", (0, BORE_Y, -16.25))
    m.locator("shell_eject", (1.1, BORE_Y, -1))
    m.locator("sight_line", (0, SIGHT_Y, REAR_SIGHT_Z))
    _animations(m)
    return m


def _reload_common(a, t_end_hand, level_start, level_end):
    """Shared part of both reloads: cant the gun, left hand strips the mag, brings up a fresh
    one, seats it and slaps the base (slap lands at t=1.6)."""
    P, R = PITCH_UP, ROLL_RIGHT
    cant = (12 * P, 0, 20 * R)
    io, out = "easeInOutSine", "easeOutQuad"
    a.keys("root", "rotation", {0: (0, 0, 0), 0.3: (cant, io), 1.45: cant,
                                1.6: ((13.5 * P, 0, 20 * R), out), 1.7: (cant, io),
                                level_start: cant, level_end: ((0, 0, 0), io)})
    a.keys("root", "position", {0: (0, 0, 0), 0.3: ((0, 0.6, 0.4), io), 1.45: (0, 0.6, 0.4),
                                1.6: ((0, 0.9, 0.4), out), 1.7: ((0, 0.6, 0.4), io),
                                level_start: (0, 0.6, 0.4), level_end: ((0, 0, 0), io)})
    # left hand: handguard -> magazine -> down and out of view -> back up -> seat -> slap
    grab = (0, -4.5, 4.5)          # hand under the magazine (offset from its rest on the handguard)
    away = (-1, -10, 6)
    a.keys("left_arm", "position", {0: (0, 0, 0), 0.3: (grab, io), 0.38: grab,
                                    0.7: (away, "easeInQuad"), 1.0: away,
                                    1.35: ((0, -6, 4.5), out), 1.45: (grab, out),
                                    1.52: ((0, -6.5, 4.5), out), 1.6: (grab, "easeInQuad"),
                                    1.68: grab})
    a.keys("left_arm", "rotation", {0: (0, 0, 0), 0.3: ((-20, 0, 0), io), 1.68: (-20, 0, 0)})
    # the magazine rides with the hand from the grab to the seat
    d = lambda v: (v[0] - grab[0], v[1] - grab[1], v[2] - grab[2])
    a.keys("magazine", "position", {0.38: (0, 0, 0), 0.7: (d(away), "easeInQuad"), 1.0: d(away),
                                    1.35: (d((0, -6, 4.5)), out), 1.45: ((0, 0, 0), out)})
    a.keys("magazine", "rotation", {0.38: (0, 0, 0), 0.7: ((-15, 0, 0), "easeInQuad"),
                                    1.0: (-15, 0, 0), 1.35: ((0, 0, 0), out)})
    return grab


def _animations(m: Model):
    P = PITCH_UP
    io, out = "easeInOutSine", "easeOutQuad"

    a = m.anim("idle", 3.0, loop=True)
    a.keys("root", "position", {0: (0, 0, 0), 1.5: (0, -0.12, 0), 3.0: (0, 0, 0)}, io)
    a.keys("root", "rotation", {0: (0, 0, 0), 1.5: (0.4 * P, 0, 0), 3.0: (0, 0, 0)}, io)

    a = m.anim("fire", 0.07)
    a.keys("root", "position", {0: (0, 0, 0), 0.02: ((0, 0, RECOIL), out), 0.07: ((0, 0, 0), io)})
    a.keys("root", "rotation", {0: (0, 0, 0), 0.02: ((2 * P, 0, 0), out), 0.07: ((0, 0, 0), io)})
    a.keys("bolt", "position", {0: (0, 0, 0), 0.03: ((0, 0, BOLT_TRAVEL), out), 0.07: ((0, 0, 0), io)})
    a.keys("trigger", "rotation", {0: (0, 0, 0), 0.02: ((-12, 0, 0), out), 0.07: ((0, 0, 0), io)})

    a = m.anim("reload", 2.2)
    grab = _reload_common(a, 2.0, 1.85, 2.2)
    a.keys("left_arm", "position", {2.0: ((0, 0, 0), io)})
    a.keys("left_arm", "rotation", {2.0: ((0, 0, 0), io)})

    a = m.anim("reload_empty", 2.6)
    grab = _reload_common(a, 2.4, 2.2, 2.6)
    catch = (-1.5, -1, 4.5)        # hand beside the bolt catch, left side of the receiver
    a.keys("left_arm", "position", {1.95: ((-3.5, -1, 4.5), io), 2.05: (catch, "easeInQuad"),
                                    2.12: ((-2.5, -1, 4.5), out), 2.45: ((0, 0, 0), io)})
    a.keys("left_arm", "rotation", {1.95: ((-10, 0, 0), io), 2.12: (-10, 0, 0), 2.45: ((0, 0, 0), io)})
    back = (0, 0, BOLT_TRAVEL)     # bolt locked open from the first frame
    a.keys("bolt", "position", {0: back, 2.05: back, 2.1: ((0, 0, 0), "easeInQuad")})
    a.keys("root", "position", {2.1: ((0, 0.6, 0.1), out), 2.2: ((0, 0.6, 0.4), io)})
