"""M2010 Enhanced Sniper Rifle - bolt action, .300 Win Mag. The Leupold Mark 4 scope, the
AAC Titan-QD suppressor and the Harris bipod are ATTACHMENTS now (see attachments.py).

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 46.5 in rifle = ~35 units (+ 7.5 units with the suppressor).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.
"""
import attachments as att
from armorygen import Model

BORE_Y = 5.5
SIGHT_Y = 7.5          # optical axis of the sniper scope (centre of the 1-unit main tube)
EYEPIECE_Z = 2.0       # rear end of the sniper scope's eyepiece
MUZZLE_Z = -24.0       # bare muzzle; a suppressor sits in front of it

# ---- attachment mount points (pivots of the mount bones; see attachments.py) ---------
RAIL_Y = 6.5                           # top of the scope rail
OPTIC_MOUNT = (0, RAIL_Y, -4)          # sniper scope: axis 1.0 above -> SIGHT_Y, eyepiece 6 behind -> EYEPIECE_Z
MUZZLE_MOUNT = (0, BORE_Y, MUZZLE_Z)
UNDERBARREL_MOUNT = (0, 3.5, -15)      # bottom face of the fore-end's bottom rail (left hand is 4 behind)
SIDE_MOUNT = (1.25, BORE_Y, -15)       # outer face of the right side rail
assert RAIL_Y + att.OPTIC_AXIS_Y["sniper_scope"] == SIGHT_Y

# ---- first-person arms (identical size and right-hand setup to the M4) ---------------
ARM_W, ARM_LEN = 3, 26
R_HAND = (0, 2, 2)                     # centre of the pistol grip
R_ARM_ROT = (135, 40, 0)
L_HAND = (0, 4, -11)                   # under the front half of the fore-end, behind the underbarrel mount
L_ARM_ROT = (128, -36, 0)

# ---- animation constants (previews == game) -------------------------------------------
PITCH_UP = 1.0      # sign of root X-rotation that RAISES the muzzle
ROLL_RIGHT = -1.0   # sign of root Z-rotation that tips the top of the gun toward +X
RECOIL = 1.5        # units the gun kicks back when firing
RECOIL_RISE = 4.0   # degrees of muzzle rise
BOLT_PIVOT = (0, BORE_Y, 2.75)   # bolt_handle pivot: on the bore axis, at the handle
BOLT_LIFT = 70.0    # degrees about +Z that lift the handle (it sticks out to +X)
BOLT_PULL = 3.0     # units the bolt travels back (+Z)
KNOB = (2.5, BORE_Y, 2.75)       # centre of the bolt knob at rest


def model() -> Model:
    m = Model("m2010", density=1)
    m.material("chassis", (150, 127, 92), 6, 0.80)      # dark FDE aluminium chassis
    m.material("chassis_dark", (128, 108, 78), 6, 0.78)  # rails / accents on the chassis
    m.material("action", (46, 46, 51), 5, 0.74)         # Remington 700 action
    m.material("barrel_black", (40, 40, 45), 5, 0.74)
    m.material("mag_steel", (116, 118, 124), 6, 0.72)

    # ---- receiver (action in the chassis) ------------------------------------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4, -6), (2, 1, 9), "chassis")                        # chassis under the action
    rc.box((-1, 5, -6), (1, 1, 9), "action")                         # action, left half
    rc.box((0, 5, -6), (1, 1, 2), "action")                          # right side, front ring
    rc.box((0, 5, -1.5), (1, 1, 4.5), "action")                      # right side, rear bridge
    rc.box((-1, 6, -6), (2, 0.5, 7), "action", pattern="rails")      # scope rail
    rc.box((-1, 3, -5.5), (2, 1, 4), "chassis")                      # magazine well
    rc.box((-0.5, 2, -2), (1, 1, 3), "chassis_dark")                 # trigger guard

    # ---- bolt handle (+ bolt body; visible in the ejection port z -4..-1.5) ---------
    bh = m.bone("bolt_handle", pivot=BOLT_PIVOT)
    bh.box((-0.5, 5, -4), (1, 1, 7.5), "bright_steel")               # bolt body + shroud
    bh.box((0.5, 5.25, 2.5), (1.5, 0.5, 0.5), "action")              # handle arm, out to +X
    bh.box((2, 5, 2.25), (1, 1, 1), "action", pattern="knurl")       # knob

    # ---- barrel -----------------------------------------------------------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -6))
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 7), "barrel_black", inflate=0.1)       # free-floated barrel
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 0.5), "action", inflate=0.3)           # brake / QD mount

    # ---- fore-end (aluminium chassis handguard with rails) ----------------------------
    hg = m.bone("handguard", pivot=(0, BORE_Y, -6))
    hg.box((-1, 4, -17), (2, 2, 11), "chassis", pattern="slots")
    hg.box((-1, 6, -17), (2, 0.5, 11), "chassis_dark", pattern="rails")       # full-length top rail
    hg.box((-1.25, 5.25, -16.5), (2.5, 0.5, 4), "chassis_dark", pattern="rails")  # side rails
    hg.box((-0.5, 3.5, -16.5), (1, 0.5, 3), "chassis_dark", pattern="rails")  # bottom rail

    # ---- stock: right-folding chassis stock with cheek riser -------------------------
    st = m.bone("stock", pivot=(1, 4, 3.5))
    st.box((-1, 3, 3), (2, 2, 1), "action")                                   # folding hinge
    st.box((-1, 4, 4), (2, 1, 6), "chassis")                                  # upper beam
    st.box((-1, 5, 6.5), (2, 1.5, 3.5), "chassis_dark", pattern="lines")      # adjustable cheek riser
    st.wedge((0, 3.75, 4.25), (0, 2.25, 10), 1, 1, "chassis")                 # lower strut
    st.box((-1, 1.5, 10), (2, 5, 0.5), "chassis")                             # butt plate
    st.box((-1, 1.5, 10.5), (2, 5, 0.5), "rubber")                            # recoil pad

    # ---- grip / trigger -------------------------------------------------------------
    gr = m.bone("grip", pivot=(0, 4, 1))
    gr.box((-1, 0, 0), (2, 4, 2), "polymer", pattern="stipple",
           rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)
    tr = m.bone("trigger", pivot=(0, 4, -0.5))
    tr.box((-0.5, 3, -1), (1, 1, 1), "bright_steel")

    # ---- magazine: 5-round detachable box ---------------------------------------------
    mg = m.bone("magazine", pivot=(0, 4, -3.5))
    mg.box((-0.75, 1.5, -5), (1.5, 2.5, 3), "mag_steel", pattern="lines")
    mg.box((-1, 1, -5.25), (2, 0.5, 3.5), "action")                           # floor plate

    # ---- first-person arms (player skin; direct children of root) -------------------
    hw = ARM_W / 2
    ra = m.bone("right_arm", pivot=R_HAND, rotation=R_ARM_ROT)
    ra.skin_box((R_HAND[0] - hw, R_HAND[1] - 1.5, R_HAND[2] - hw), (ARM_W, ARM_LEN, ARM_W), side="right")
    ra.skin_box((R_HAND[0] + 1, R_HAND[1] - 3.5, R_HAND[2] - 0.5), (1, 2, 1), side="right", sleeve=False)
    la = m.bone("left_arm", pivot=L_HAND, rotation=L_ARM_ROT)
    la.skin_box((L_HAND[0] - hw, L_HAND[1] - 1.5, L_HAND[2] - 1), (ARM_W, ARM_LEN, ARM_W), side="left")

    # ---- attachments (bones att_<id>, hidden in game unless fitted) --------------------
    m.bone("optic_mount", parent="receiver", pivot=OPTIC_MOUNT)
    m.bone("muzzle_mount", parent="barrel", pivot=MUZZLE_MOUNT)
    m.bone("underbarrel_mount", parent="handguard", pivot=UNDERBARREL_MOUNT)
    m.bone("side_mount", parent="handguard", pivot=SIDE_MOUNT)
    for add in (att.add_red_dot, att.add_holographic, att.add_acog, att.add_sniper_scope):
        add(m, "optic_mount", OPTIC_MOUNT)
    att.add_suppressor_300(m, "muzzle_mount", MUZZLE_MOUNT)
    for add in (att.add_vertical_grip, att.add_angled_grip, att.add_bipod):
        add(m, "underbarrel_mount", UNDERBARREL_MOUNT)
    att.add_laser(m, "side_mount", SIDE_MOUNT)
    m.preview_configs = {
        "bare": att.loadout(m),
        "loadout_sniper": att.loadout(m, "sniper_scope", "suppressor_300", "bipod", "laser"),
    }

    # ---- locators ---------------------------------------------------------------------
    m.locator("muzzle_flash", (0, BORE_Y, MUZZLE_Z - 0.3))    # BARE muzzle (suppressors have no flash)
    m.locator("shell_eject", (1.1, BORE_Y, -2.75))
    m.locator("sight_line", (0, SIGHT_Y, EYEPIECE_Z))
    _animations(m)
    return m


def _bolt_cycle(a, t0):
    """Right hand leaves the grip, works the bolt (lift, back, forward, down) and returns.
    Keys `right_arm` and `bolt_handle`; takes 1.02 s from t0. Returns the key times."""
    import math
    io, out = "easeInOutSine", "easeOutQuad"

    def hand(angle, pull=0.0):
        """right_arm position offset that puts the fist on the knob (a bit outside/below it)."""
        r = math.radians(angle)
        kx, ky = KNOB[0] * math.cos(r), BORE_Y + KNOB[0] * math.sin(r)
        ox, oy = 0.9 * math.cos(r) + 0.3 * math.sin(r), -0.3 * math.cos(r) - 0.2 * math.sin(r)
        return (kx + ox - R_HAND[0], ky + oy - R_HAND[1], KNOB[2] - R_HAND[2] + pull)

    T = {k: round(t0 + v, 3) for k, v in dict(start=0, grab=0.20, mid_up=0.26, up=0.32, back=0.48,
                                             hold=0.52, fwd=0.66, mid_dn=0.72, down=0.78,
                                             end=1.02).items()}
    L, half = BOLT_LIFT, BOLT_LIFT / 2
    a.keys("right_arm", "position", {
        T["start"]: (0, 0, 0), T["grab"]: (hand(0), io), T["mid_up"]: hand(half),
        T["up"]: (hand(L), out), T["back"]: (hand(L, BOLT_PULL), out), T["hold"]: hand(L, BOLT_PULL),
        T["fwd"]: (hand(L), "easeInQuad"), T["mid_dn"]: hand(half), T["down"]: (hand(0), out),
        T["end"]: ((0, 0, 0), io)})
    a.keys("right_arm", "rotation", {
        T["start"]: (0, 0, 0), T["grab"]: ((6, 0, 0), io), T["up"]: (10, 0, 0),
        T["back"]: (10, 0, 0), T["fwd"]: (10, 0, 0), T["down"]: (6, 0, 0),
        T["end"]: ((0, 0, 0), io)})
    a.keys("bolt_handle", "rotation", {
        T["start"]: (0, 0, 0), T["grab"]: (0, 0, 0), T["up"]: ((0, 0, L), out), T["fwd"]: (0, 0, L),
        T["down"]: ((0, 0, 0), out)})
    a.keys("bolt_handle", "position", {
        T["start"]: (0, 0, 0), T["up"]: (0, 0, 0), T["back"]: ((0, 0, BOLT_PULL), out),
        T["hold"]: (0, 0, BOLT_PULL), T["fwd"]: ((0, 0, 0), "easeInQuad")})
    return T


def _reload_common(a, level_start, level_end):
    """Cant the gun, left hand strips the mag, brings it back from below, seats it and slaps
    the floor plate (slap lands at t=1.86), then returns to the fore-end by t=2.35."""
    P, R = PITCH_UP, ROLL_RIGHT
    io, out = "easeInOutSine", "easeOutQuad"
    cant, cant_p = (8 * P, 0, 14 * R), (0, 0.5, 0.4)
    a.keys("root", "rotation", {0: (0, 0, 0), 0.35: (cant, io), 1.76: cant,
                                1.86: ((9.5 * P, 0, 14 * R), out), 1.96: (cant, io),
                                level_start: cant, level_end: ((0, 0, 0), io)})
    a.keys("root", "position", {0: (0, 0, 0), 0.35: (cant_p, io), 1.76: cant_p,
                                1.86: ((0, 0.8, 0.4), out), 1.96: (cant_p, io),
                                level_start: cant_p, level_end: ((0, 0, 0), io)})
    grab = (0, 1.5 - L_HAND[1], -3.5 - L_HAND[2])     # fist round the bottom of the magazine
    below = (grab[0], grab[1] - 2.5, grab[2])          # mag clear of the well
    away = (-1, grab[1] - 7, grab[2] + 1.5)            # down and out of view
    a.keys("left_arm", "position", {0: (0, 0, 0), 0.35: (grab, io), 0.45: grab,
                                    0.8: (away, "easeInQuad"), 1.15: away,
                                    1.55: (below, out), 1.68: (grab, out),
                                    1.76: ((grab[0], grab[1] - 2, grab[2]), out),
                                    1.86: (grab, "easeInQuad"), 1.95: grab, 2.35: ((0, 0, 0), io)})
    a.keys("left_arm", "rotation", {0: (0, 0, 0), 0.35: ((-20, 0, 0), io), 1.95: (-20, 0, 0),
                                    2.35: ((0, 0, 0), io)})
    d = lambda v: (v[0] - grab[0], v[1] - grab[1], v[2] - grab[2])
    a.keys("magazine", "position", {0.45: (0, 0, 0), 0.8: (d(away), "easeInQuad"), 1.15: d(away),
                                    1.55: (d(below), out), 1.68: ((0, 0, 0), out)})
    a.keys("magazine", "rotation", {0.45: (0, 0, 0), 0.8: ((-15, 0, 0), "easeInQuad"),
                                    1.15: (-15, 0, 0), 1.55: ((0, 0, 0), out)})


def _animations(m: Model):
    P, R = PITCH_UP, ROLL_RIGHT
    io, out = "easeInOutSine", "easeOutQuad"

    a = m.anim("idle", 3.0, loop=True)
    a.keys("root", "position", {0: (0, 0, 0), 1.5: (0, -0.12, 0), 3.0: (0, 0, 0)}, io)
    a.keys("root", "rotation", {0: (0, 0, 0), 1.5: (0.4 * P, 0, 0), 3.0: (0, 0, 0)}, io)

    # ---- fire: recoil, then the full bolt-action cycle (1.30 s) -------------------------
    a = m.anim("fire", 1.30)
    T = _bolt_cycle(a, 0.22)
    a.keys("root", "position", {
        0: (0, 0, 0), 0.04: ((0, 0.15, RECOIL), out), 0.12: ((0, 0, 0.3), io), 0.22: ((0, 0, 0), io),
        T["grab"]: (0, 0, 0), T["up"]: ((0, 0.15, 0), io), T["back"]: ((0, 0.15, 0.4), out),
        T["fwd"]: ((0, 0.1, -0.2), "easeInQuad"), T["down"]: ((0, -0.1, 0), out),
        T["end"]: ((0, 0, 0), io), 1.30: (0, 0, 0)})
    a.keys("root", "rotation", {
        0: (0, 0, 0), 0.04: ((RECOIL_RISE * P, 0, 0), out), 0.12: ((0.8 * P, 0, 0), io),
        0.22: ((0, 0, 0), io),
        T["grab"]: (0, 0, 0), T["up"]: ((0.6 * P, 0, -3 * R), io), T["back"]: ((1.2 * P, 0, -3 * R), out),
        T["fwd"]: ((0.3 * P, 0, -2 * R), "easeInQuad"), T["down"]: ((0, 0, 2 * R), out),
        T["end"]: ((0, 0, 0), io), 1.30: (0, 0, 0)})
    a.keys("trigger", "rotation", {0: (0, 0, 0), 0.03: ((-12, 0, 0), out), 0.12: ((0, 0, 0), io)})

    # ---- reload: mag swap only (2.60 s) -----------------------------------------------
    a = m.anim("reload", 2.60)
    _reload_common(a, 2.2, 2.6)

    # ---- reload_empty: mag swap, then cycle the bolt (3.20 s) --------------------------
    a = m.anim("reload_empty", 3.20)
    _reload_common(a, 2.9, 3.2)
    _bolt_cycle(a, 1.95)        # ends at 2.97
