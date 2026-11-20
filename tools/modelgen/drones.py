"""Shared blocky device platform, 1 unit = 3.35 cm; +X right, +Y up, -Z nose.

Drones rest unfolded on Y=0. Player-skin arms are siblings of the device, never
children: the entity hides them. Java owns rotor Y and gimbal X; no clip keys
either. Locator positions use authoring coordinates (the exporter mirrors X).
Optional blur discs are omitted so the rest/inventory silhouette stays crisp.
"""
from __future__ import annotations

import math

from armorygen import Bone, Model, _rotate
import templates as T

ZERO = (0, 0, 0)
ONE = (1, 1, 1)
CORNERS = (("fl", -1, -1), ("fr", 1, -1), ("rl", -1, 1), ("rr", 1, 1))


def new_model(name: str) -> Model:
    m = Model(name, density=4)
    for name, rgb, noise in (
        ("d_shell", (42, 47, 52), 3), ("d_edge", (68, 74, 80), 3),
        ("d_carbon", (26, 29, 32), 5), ("d_prop", (30, 33, 38), 2),
        ("d_orange", (224, 91, 27), 3), ("d_lens", (23, 75, 101), 2),
        ("d_thermal", (76, 55, 94), 2), ("d_glint", (115, 194, 208), 0),
        ("d_red", (247, 47, 35), 0), ("d_green", (49, 232, 122), 0),
        ("d_white", (227, 236, 222), 0), ("d_screen", (20, 42, 49), 1),
        ("d_map", (43, 77, 80), 1), ("d_route", (95, 181, 151), 0),
        ("d_strap", (49, 50, 43), 3), ("d_battery", (65, 69, 73), 3),
    ):
        m.material(name, rgb, noise, 0.88)
    return m


def octagonal(b: Bone, center, diameter, length, mat, axis="y"):
    """Convex eight-sided section from four slim boxes, as the grenade layers.

    Diameter is across flats; stagger end faces to avoid coplanar overlaps.
    Unlike two superimposed squares this has no outward star points.
    """
    side = diameter * math.tan(math.pi / 8)
    for i in range(4):
        depth = length + 0.008 * i
        size = {"x": (depth, diameter, side), "y": (diameter, depth, side),
                "z": (diameter, side, depth)}[axis]
        rot = {"x": (45 * i, 0, 0), "y": (0, 45 * i, 0), "z": (0, 0, 45 * i)}[axis]
        b.cbox(center, size, mat, rot=rot, pivot=center)


def beam(b: Bone, start, end, width, height, mat="d_carbon"):
    """Horizontal box between two X/Z points; pivots stay at the fold hinges."""
    dx, dz = end[0] - start[0], end[2] - start[2]
    center = tuple((a + c) / 2 for a, c in zip(start, end))
    b.cbox(center, (width, height, math.hypot(dx, dz)), mat,
           rot=(0, math.degrees(math.atan2(-dx, -dz)), 0), pivot=center,
           pattern="lines" if mat == "d_carbon" else "none")


def led(m: Model, name, parent, pos, mat, size=(0.22, 0.14, 0.20)):
    m.bone(name, parent=parent, pivot=pos).cbox(pos, size, mat)


def rotor(m: Model, corner, hinge, motor, radius, blades=2, phase=45):
    """Stationary motor on arm; hub and blades alone inside the driven rotor bone."""
    arm = m.bone("arm_" + corner, parent="drone", pivot=hinge)
    beam(arm, hinge, (motor[0], hinge[1], motor[2]),
         0.55 if blades == 2 else 0.37, 0.34 if blades == 2 else 0.16,
         "d_shell" if blades == 2 else "d_carbon")
    x, y, z = motor
    mount_top = y - 0.34
    arm.cbox((x, (hinge[1] + mount_top) / 2, z),
             (0.48, mount_top - hinge[1], 0.48), "d_shell")
    octagonal(arm, (x, y - 0.46, z), 0.76, 0.24, "d_edge")
    octagonal(arm, (x, y - 0.23, z), 0.70, 0.22,
              "d_shell" if blades == 2 else "bright_steel")
    if blades == 3:
        for sx in (-1, 1):
            arm.cbox((x + sx * 0.26, y - 0.23, z), (0.07, 0.13, 0.35), "d_carbon")
    r = m.bone("rotor_" + corner, parent=arm.name, pivot=motor)
    octagonal(r, motor, 0.40, 0.18, "d_edge")
    for i in range(blades):
        ang = phase + i * 360 / blades
        # Slightly swept rectangular paddles, each ending short of the spinner.
        r.box((x + 0.16, y - 0.045, z - 0.19), (radius - 0.16, 0.09, 0.38),
              "d_prop" if blades == 2 else "d_orange", rot=(0, ang, 0), pivot=motor)
        r.box((x + radius - 0.30, y + 0.047, z - 0.19), (0.28, 0.025, 0.38),
              "d_edge" if blades == 2 else "d_white", rot=(0, ang, 0), pivot=motor)
    return arm


def skin_arm(m: Model, side, hand, rot):
    """Same 3 x 26 player-skin forearms as grenades, pivot at the wrist/fist."""
    b = m.bone(side + "_arm", pivot=hand, rotation=rot)
    b.skin_box((hand[0] - 1.5, hand[1] - 1.5, hand[2] - 1.5),
               (T.ARM_W, T.ARM_LEN, T.ARM_W), side=side)


def blink(anim, bone, length, phase=0):
    # Near-step keys keep the light visibly on/off in GeckoLib and preview sampling.
    frames = {0: ONE, length: ONE}
    if phase:
        frames.update({0: ZERO, phase: ZERO, phase + 0.001: ONE,
                       phase + length * 0.15: ONE, phase + length * 0.15 + 0.001: ZERO,
                       length: ZERO})
    else:
        frames.update({length * 0.16: ONE, length * 0.16 + 0.001: ZERO,
                       length - 0.001: ZERO})
    anim.keys(bone, "scale", frames)


GRIP = (1.6, 1.5, 0.7)  # top of the right fist = underside of the frame, rest pose
FOLD_DEG = 110          # arm hinge swing of the packed scout
PROP_LIFT = 0.15        # packed front props ride above the rear ones (no coplanar blades)


def fold_props(a, hinge_dx, hinge_dz):
    """Packed two-blade props: each rotor turns so its blades lie along its (folded) arm."""
    for corner, sx, sz in CORNERS:
        arm_deg = math.degrees(math.atan2(-sz * hinge_dz, sx * hinge_dx))
        turn = (arm_deg - 45 + 90) % 180 - 90  # blades sit at phase 45; either end will do
        a.key("rotor_" + corner, "rotation", 0, (0, round(turn, 2), 0))
        if sz < 0:
            a.key("rotor_" + corner, "position", 0, (0, PROP_LIFT, 0))


def finish_drone(m: Model, folded=False, carry_scale=1.0, carry=(3.0, -0.9, 0.0),
                 carry_yaw=0.0, fold=None) -> Model:
    """`carry_scale` / `carry` = the `item` clip's size of the drone and position of the
    hands (model units) in every display context: the shared DEVICE framing suits the
    two-handed controller, so the carried drones shrink and drop to the lower right here.
    `carry_yaw` turns the nose in toward the crosshair (degrees). The drone scales and turns
    about its origin and is shifted so GRIP stays in the right fist.
    `fold` = (hinge-to-motor dx, dz) of a folding airframe: arms and props pack."""
    # Palm reaches under the right side of the central frame. Left arm is relaxed.
    skin_arm(m, "right", (1.6, 0.0, 0.7), (147, 23, 0))
    skin_arm(m, "left", (-7.0, -4.5, 2.8), (155, -35, 0))
    a = m.anim("item", 3.0, loop=True)
    k = carry_scale
    held = _rotate(tuple(k * g for g in GRIP), ZERO, (0, carry_yaw, 0))
    body = tuple(round(c + g - h, 3) for c, g, h in zip(carry, GRIP, held))
    # One translation curve for body and both arms keeps the grip locked.
    for bone, base in (("drone", body), ("right_arm", carry), ("left_arm", carry)):
        a.keys(bone, "position", {0: base, 1.5: (base[0], base[1] - 0.09, base[2] + 0.04),
                                  3.0: base}, T.IO)
    if k != 1.0:
        a.key("drone", "scale", 0, (k, k, k))
    if carry_yaw:
        a.key("drone", "rotation", 0, (0, carry_yaw, 0))
    if folded:
        for corner, sx, sz in CORNERS:
            a.key("arm_" + corner, "rotation", 0, (0, sx * sz * FOLD_DEG, 0))
        if fold:
            fold_props(a, *fold)
    f = m.anim("flight", 1.0, loop=True)
    for bone in m.bones:
        if bone.startswith("led_") and bone != "led_arm":
            blink(f, bone, 1.0, 0.5 if bone.endswith("rear") else 0)
    if "led_arm" in m.bones:
        blink(m.anim("armed", 0.5, loop=True), "led_arm", 0.5)
        a.key("led_arm", "scale", 0, ZERO)
    m.preview_configs = {"held": set(), "noarms": {"right_arm", "left_arm"}}
    return m


def scout_airframe(m: Model):
    b = m.bone("drone")
    b.cbox((0, 2.25, 0), (3.15, 1.05, 5.4), "d_shell")
    b.cbox((0, 2.82, 0.2), (2.65, 0.20, 4.4), "d_edge")
    b.cbox((0, 1.62, 0.25), (2.5, 0.18, 4.0), "d_carbon")
    b.cbox((0, 2.96, 0.7), (1.9, 0.08, 2.4), "d_shell", pattern="slots")
    for sx in (-1, 1):
        b.cbox((sx * 1.60, 2.34, 0.35), (0.09, 0.35, 2.3), "d_carbon", pattern="slots")
    for corner, sx, sz in CORNERS:
        hinge = (sx * 1.2, 2.55, sz * 2.0)
        arm = rotor(m, corner, hinge, (sx * 6.5, 3.48, sz * 5.15), 4.5)
        octagonal(arm, hinge, 0.69, 0.52, "d_edge")
        # Inboard feet fold with the arm, ground contact exactly Y=0.
        fx, fz = sx * 3.5, sz * 3.37
        arm.box((fx - 0.17, 0.16, fz - 0.20), (0.34, 2.27, 0.40), "d_shell")
        arm.box((fx - 0.25, 0, fz - 0.36), (0.50, 0.16, 0.72), "rubber")
        led(m, "led_" + corner, arm.name, (sx * 6.80, 2.83, sz * 5.15),
            "d_green" if sx > 0 else "d_red")
    led(m, "led_rear", "drone", (0, 2.28, 2.75), "d_white")
    # Downward and upward navigation cameras.
    for y, out in ((3.05, 1), (1.42, -1)):
        octagonal(b, (0, y, -0.8), 0.62, 0.17, "d_carbon")
        octagonal(b, (0, y + out * 0.10, -0.8), 0.35, 0.035, "d_lens")
    for sx in (-1, 1):
        b.cbox((sx * 1.02, 1.65, -2.65), (0.25, 1.0, 0.45), "d_edge")
    m.bone("gimbal", parent="drone", pivot=(0, 1.32, -2.72))
    c = m.bone("camera", parent="gimbal", pivot=(0, 1.32, -2.72))
    c.cbox((0, 1.32, -2.82), (1.85, 0.91, 1.0), "d_shell")
    for x, mat in ((-0.46, "d_lens"), (0.46, "d_thermal")):
        octagonal(c, (x, 1.32, -3.36), 0.70, 0.14, "d_edge", "z")
        octagonal(c, (x, 1.32, -3.45), 0.49, 0.04, mat, "z")
        c.cbox((x - 0.10, 1.43, -3.477), (0.10, 0.08, 0.012), "d_glint")
    m.locator("camera_eye", (-0.46, 1.32, -3.48), parent="camera")


def fpv_airframe(m: Model):
    b = m.bone("drone")
    b.cbox((0, 1.50, 0), (2.15, 0.17, 3.2), "d_carbon", pattern="lines")
    b.cbox((0, 2.20, 0.1), (1.82, 0.14, 2.8), "d_carbon", pattern="lines")
    for sx in (-1, 1):
        for sz in (-1, 1):
            b.cbox((sx * 0.76, 1.85, sz * 1.1), (0.14, 0.58, 0.14), "d_orange")
    b.cbox((0, 1.80, 0.15), (1.25, 0.24, 1.45), "d_edge", pattern="slots")
    for corner, sx, sz in CORNERS:
        arm = rotor(m, corner, (sx * 0.62, 1.48, sz * 0.83),
                    (sx * 3.17, 2.13, sz * 3.17), 2.65, blades=3, phase=30 if sx > 0 else -30)
        arm.box((sx * 3.17 - 0.24, 0, sz * 3.17 - 0.28), (0.48, 1.29, 0.56), "rubber")
        led(m, "led_" + corner, arm.name, (sx * 3.17, 1.38, sz * 3.17),
            "d_green" if sx > 0 else "d_red")
    # LiPo, two wraparound fabric straps and visible battery lead.
    b.cbox((0, 2.76, 0.35), (1.62, 0.92, 2.75), "d_battery", pattern="lines")
    for z in (-0.4, 1.08):
        b.cbox((0, 3.25, z), (1.76, 0.10, 0.31), "d_strap")
        for sx in (-1, 1):
            b.cbox((sx * 0.86, 2.78, z), (0.10, 0.86, 0.31), "d_strap")
        b.cbox((0.52, 3.33, z), (0.40, 0.08, 0.39), "d_edge")
    b.cbox((0, 3.24, 0.35), (0.72, 0.03, 0.70), "d_white")
    b.cbox((0.98, 2.58, 1.30), (0.13, 0.55, 0.13), "d_red")
    b.cbox((0.98, 2.29, 1.30), (0.27, 0.16, 0.26), "orange")
    a = m.bone("antenna", parent="drone", pivot=(0, 2.0, 1.6))
    a.wedge((0, 2.0, 1.6), (0, 3.8, 2.8), 0.14, 0.14, "d_carbon")
    octagonal(a, (0, 3.85, 2.85), 0.65, 0.33, "d_shell")
    a.cbox((0, 2.25, 2.05), (1.4, 0.11, 0.11), "d_carbon")
    cp = (0, 1.98, -1.44)
    for sx in (-1, 1):
        b.cbox((sx * 0.57, 1.91, -1.50), (0.15, 0.70, 0.95), "d_orange")
    c = m.bone("camera", parent="drone", pivot=cp, rotation=(25, 0, 0))
    c.cbox(cp, (0.95, 0.76, 0.65), "d_shell")
    octagonal(c, (0, 1.98, -1.85), 0.60, 0.25, "d_edge", "z")
    octagonal(c, (0, 1.98, -1.995), 0.44, 0.04, "d_lens", "z")
    m.locator("camera_eye", (0, 1.98, -2.02), parent="camera")
    # Cosmetic payload only: simple olive pod, straps and a forward contact nub.
    q = m.bone("charge", parent="drone", pivot=(0, 0.72, -1.05))
    octagonal(q, (0, 0.72, -1.05), 1.26, 2.60, "od_green", "z")
    octagonal(q, (0, 0.72, -2.42), 0.92, 0.14, "od_green", "z")
    q.cbox((0, 0.72, -2.62), (0.15, 0.15, 0.28), "steel")
    for z in (-1.7, -0.3):
        q.cbox((0, 0.72, z), (0.96, 1.30, 0.18), "d_strap")
    led(m, "led_arm", "drone", (1.12, 1.72, -0.6), "d_red", (0.12, 0.20, 0.26))
    led(m, "led_rear", "drone", (0, 1.77, 1.63), "d_white")


def controller(m: Model) -> Model:
    tilt = (-28, 0, 0)
    b = m.bone("controller", rotation=tilt)
    b.cbox((0, 0, 0), (7.8, 4.65, 0.70), "d_shell")
    b.cbox((0, 0, 0.39), (6.25, 3.65, 0.12), "d_carbon")
    for sx in (-1, 1):
        b.cbox((sx * 3.85, -0.25, 0), (1.25, 4.55, 1.10), "rubber", pattern="stipple")
        for y in (-1.45, -0.7, 0.05):
            b.cbox((sx * 4.49, y, 0), (0.09, 0.18, 0.85), "d_edge")
        b.cbox((sx * 3.45, 2.30, -0.05), (1.23, 0.45, 0.75), "d_edge")
        for y in (-1.95, 1.9):
            octagonal(b, (sx * 2.9, y, 0.39), 0.18, 0.08, "steel", "z")
    # Seven-inch face on its own bone, facing +Z toward the player.
    s = m.bone("screen", parent="controller", pivot=(0, 0.25, 0.48))
    s.cbox((0, 0.25, 0.48), (4.70, 2.70, 0.08), "d_screen")
    # Minimal generated map/HUD: muted blocks, route and crosshair, no external image.
    for x, y, w, h in ((-1.45, 0.55, 1.2, 0.8), (0.10, -0.35, 1.3, 0.8), (1.55, 0.4, 0.8, 1.25)):
        s.cbox((x, y, 0.527), (w, h, 0.012), "d_map")
    for x, y, w, h in ((-0.8, -0.5, 1.4, 0.055), (-0.12, 0.0, 0.055, 1.05), (0.6, 0.5, 1.4, 0.055)):
        s.cbox((x, y, 0.547), (w, h, 0.012), "d_route")
    s.cbox((0, 0.25, 0.57), (0.34, 0.06, 0.02), "d_white")
    s.cbox((0, 0.25, 0.57), (0.06, 0.34, 0.02), "d_white")
    s.cbox((0, 1.41, 0.55), (4.4, 0.16, 0.02), "d_edge")
    for i in range(4):
        s.cbox((1.65 + i * 0.12, 1.41, 0.57), (0.08, 0.09, 0.012), "d_green")
    for side, sx in (("l", -1), ("r", 1)):
        x, y = sx * 3.32, 0.83
        octagonal(b, (x, y, 0.61), 0.92, 0.12, "d_carbon", "z")
        k = m.bone("stick_" + side, parent="controller", pivot=(x, y, 0.64))
        k.cbox((x, y, 0.84), (0.17, 0.17, 0.38), "steel")
        octagonal(k, (x, y, 1.06), 0.46, 0.13, "d_edge", "z")
        b.cbox((sx * 3.30, -0.42, 0.61), (0.40, 0.38, 0.13), "d_edge")
        ant = m.bone("antenna_" + side, parent="controller", pivot=(sx * 2.6, 2.25, -0.15),
                     rotation=(0, 0, sx * -12))
        octagonal(ant, (sx * 2.6, 2.25, -0.15), 0.45, 0.50, "d_edge", "x")
        ant.cbox((sx * 2.6, 3.30, -0.15), (0.40, 1.95, 0.22), "d_shell")
        ant.cbox((sx * 2.6, 4.25, -0.15), (0.44, 0.17, 0.26), "rubber")
    led(m, "led_status", "controller", (0, -1.65, 0.49), "d_green", (0.28, 0.09, 0.06))
    # Position the skin wrists in the tilted device's space; both hands hug the grips.
    for side, sx in (("right", 1), ("left", -1)):
        hand = _rotate((sx * 4.05, -0.90, 0.6), ZERO, tilt)
        skin_arm(m, side, hand, (118, sx * 24, 0))
    a = m.anim("idle", 3.2, loop=True)
    for bone in ("controller", "right_arm", "left_arm"):
        a.keys(bone, "position", {0: ZERO, 1.6: (0, -0.07, 0.035), 3.2: ZERO}, T.IO)
    blink(a, "led_status", 3.2)
    m.preview_configs = {"held": set(), "noarms": {"right_arm", "left_arm"}}
    return m
