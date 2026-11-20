"""Shared hand-grenade platform: fuze / spoon / pin assembly, the throwing arms, the five
animations and the locators. A grenade file in guns/ only models its BODY and calls `finish`::

    import grenades as G
    from armorygen import Model

    def model() -> Model:
        m = G.new_model("grenade_xyz")
        body = G.body(m)                       # the `grenade` bone, pivot = centre of mass = origin
        G.octagon(body, 0.95, -2.2, 2.2, "g_body")
        G.fuze(m, G.FuzeSpec(top_y=2.2, side_x=0.95, ...))
        return G.finish(m, grip_h=1.0)

CONVENTIONS (as the guns: 1 unit ~ 3.35 cm, +X = right, +Y = up, -Z = away from the shooter)
  * The grenade stands UPRIGHT with its CENTRE OF MASS AT THE MODEL ORIGIN (0, 0, 0): that is the
    `grenade` bone's pivot, so the thrown entity can spin the same geo about it.
  * The spoon (safety lever) runs down the +X side (under the right palm); the pull ring hangs on
    the shooter's side (+Z), face-on to the player, left of the fuze; the split pin runs along Z.
  * Bones: `grenade` (body + fuze; parent of everything below), `pin` (ring + split pin + safety
    clip / second ring), `spoon`, `right_arm`, `left_arm`. Locators: `fuze_top` (child of
    `grenade`), `release_point` / `release_point_under` (children of root: where the grenade's
    centre is when it leaves the hand in `throw` / `throw_under`).
  * The thrown grenade is this geo with `right_arm`, `left_arm`, `pin` and `spoon` hidden.
  * Animations (names the Java side plays): `idle` (loop), `pull_pin`, `hold` (loop), `throw`,
    `throw_under`. `hold` / `throw*` keep `pin` at scale 0; `throw*` set `grenade` to scale 0 at
    RELEASE_T. The grenade is not a child of the right arm (the thrown entity hides the arms), so
    its keys are BAKED from the arm's keys (`_follow`): it stays rigidly in the fist.

Everything mod-wide about grenades (arm pose, grip, timings, throw arcs) is a constant here.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import templates as T
from armorygen import Animation, Bone, Model, _rotate

Vec = tuple[float, float, float]
ZERO: Vec = (0.0, 0.0, 0.0)
ONE: Vec = (1.0, 1.0, 1.0)
HIDDEN: Vec = (0.0, 0.0, 0.0)
IO, OUT, IN = T.IO, T.OUT, T.IN

GRENADE, PIN, SPOON = "grenade", "pin", "spoon"
COM: Vec = ZERO                  # centre of mass = pivot of the `grenade` bone = model origin
DENSITY = 4                      # texels per unit: a 2-unit grenade needs finer pixels than a gun

# ---- arms ---------------------------------------------------------------------------------
R_ROT: Vec = (158.0, 18.0, 0.0)  # right forearm comes up from below / behind, fist end up
L_ROT: Vec = (135.0, -60.0, 0.0) # left forearm comes up from the lower left, under the line of sight
L_END: Vec = (-3.0, -1.9, 1.4)   # centre of the left fist's end face at rest

# ---- animation constants shared by every grenade (seconds, units, degrees) ---------------------
BAKE_STEP = 0.0125               # spacing of the baked grenade keys in the short animations
SPOON_SQUEEZED: Vec = (0.0, 0.0, -3.0)   # spoon pressed home once the pin is out
FLIP_LEAD = 0.05                 # the spoon starts to fly this long before the release
L_HOLD = ((1.0, 2.0, -4.5), (-22.0, 28.0, 0.0))             # left arm forward for balance (pos, rot offsets)
L_AWAY = ((-3.0, -6.0, 3.0), (8.0, 0.0, 0.0))               # swept back / down, out of view


@dataclass
class Handling:
    """How ONE grenade is held and thrown: authored per grenade in its guns/ file.

    Arm poses are (position offset, rotation offset) of `right_arm` from its rest pose; the
    throw tables are {time: (position, rotation, easing)} and must start at `hold_pose`. The
    last key's time is the animation length. The grenade's own keys are baked from them."""
    grip_h: float                 # height of the centre of mass above the end of the right fist
    hold_pose: tuple              # cocked-back pose
    throw: dict                   # overhand: wind-up, whip, release, wrist snap, follow-through
    release_t: float              # when the grenade leaves the hand in `throw` (a key of `throw`)
    under: dict                   # underhand pendulum
    under_release_t: float
    idle_len: float = 3.0
    idle_sway: float = 0.12       # breathing drop (units)
    pull_len: float = 0.4
    tug: float = 0.22             # the short tug that straightens the split pin (units)
    twist: float = 35.0           # the ring's twist as the pin slides out (degrees)
    squeeze: float = 4.0          # right hand tilts / clamps down while the pin is pulled (degrees)
    hold_len: float = 2.0
    bob: float = 0.15             # anticipation bob of the cocked arm (units)
    flip: float = 80.0            # spoon flip at release (degrees about its hinge)



# ======================================================================================
# GEOMETRY HELPERS
# ======================================================================================
def new_model(name: str) -> Model:
    m = Model(name, density=DENSITY)
    m.material("g_wire", (176, 178, 184), 6, 0.78)       # pull ring, split pin, safety clip
    m.material("g_hole", (16, 16, 17), 2, 0.9)           # emission / vent holes
    return m


def body(m: Model) -> Bone:
    """The `grenade` bone (pivot = centre of mass = the model origin)."""
    return m.bone(GRENADE, pivot=COM)


def _ngon(b: Bone, n: int, a: float, y0: float, y1: float, mat: str, pattern: str = "none",
          center: tuple[float, float] = (0.0, 0.0)) -> None:
    """Regular 2n-sided prism about a vertical axis (`a` = half the distance across the flats)
    as n slim boxes; convex, no star points. End faces are staggered by 0.012 per box so they
    do not z-fight each other."""
    side = 2 * a * math.tan(math.pi / (2 * n))
    cx, cz = center
    for i in range(n):
        e = 0.012 * i
        b.box((cx - a, y0 - e, cz - side / 2), (2 * a, y1 - y0 + 2 * e, side), mat, pattern=pattern,
              rot=(0, 180.0 / n * i, 0) if i else None, pivot=(cx, (y0 + y1) / 2, cz))


def octagon(b: Bone, a: float, y0: float, y1: float, mat: str, **kw) -> None:
    """Octagonal prism about the Y axis from y0 to y1; flats face 0 / 45 / 90 ... degrees."""
    _ngon(b, 4, a, y0, y1, mat, **kw)


def hexagon(b: Bone, a: float, y0: float, y1: float, mat: str, **kw) -> None:
    _ngon(b, 3, a, y0, y1, mat, **kw)


def plate(b: Bone, a: float, theta: float, x: float, y: float, w: float, h: float, mat: str,
          t: float = 0.03) -> None:
    """A thin marking plate lying on the flat of a prism of half-width `a` that faces `theta`
    degrees about Y (0 = toward the shooter (+Z), 90 = +X / spoon side, 180 = front, 270 = -X).
    (x, y) = its centre on the flat (x across, y up), w x h its size, `t` how far it stands off."""
    b.box((x - w / 2, y - h / 2, a - 0.01), (w, h, t + 0.01), mat,
          rot=(0, theta, 0) if theta else None, pivot=(0, y, 0))


def text_row(b: Bone, a: float, y: float, thetas, mat: str, h: float = 0.14,
             words=((0.14, 0.1, 0.18), (0.2, 0.12, 0.1), (0.1, 0.2, 0.14))) -> None:
    """A line of stencil 'lettering': blocks of varying width on each flat in `thetas`."""
    gap = 0.07
    for k, th in enumerate(thetas):
        ws = words[k % len(words)]
        x = -(sum(ws) + gap * (len(ws) - 1)) / 2
        for w in ws:
            plate(b, a, th, x + w / 2, y, w, h, mat)
            x += w + gap


def band(b: Bone, a: float, y0: float, y1: float, mat: str, t: float = 0.03) -> None:
    """A painted band right round an octagonal body of half-width `a`."""
    octagon(b, a + t, y0, y1, mat)


def _seg(b: Bone, p0, p1, thick: float, width: float, mat: str, **kw) -> None:
    """Flat strip (`width` along Z, centred on z = 0) from p0 to p1 in the XY plane."""
    (x0, y0), (x1, y1) = p0, p1
    length = math.hypot(x1 - x0, y1 - y0)
    ang = math.degrees(math.atan2(y1 - y0, x1 - x0))
    c = ((x0 + x1) / 2, (y0 + y1) / 2, 0.0)
    b.cbox(c, (length, thick, width), mat, rot=(0, 0, ang) if abs(ang) > 1e-6 else None, pivot=c, **kw)


# ======================================================================================
# FUZE / SPOON / PIN
# ======================================================================================
@dataclass
class FuzeSpec:
    top_y: float                 # top of the body, where the fuze screws in
    side_x: float                # x of the body's +X flat, where the spoon runs down
    spoon_end_y: float           # y of the spoon's lower tip
    fuze_mat: str = "g_fuze"
    spoon_mat: str = "g_spoon"
    shoulder: tuple[float, float] | None = None   # (x, y) where the spoon's slope meets the side;
    #                                               None = straight to the body's top edge
    clip: bool = False           # M67-style wire safety clip round the fuze (part of `pin`)
    second_ring: bool = False    # M84-style secondary pin with a triangular ring (part of `pin`)
    height: float = 0.85         # fuze height above top_y

    @property
    def fuze_top(self) -> Vec:
        return (0.0, self.top_y + self.height, 0.0)

    @property
    def ring_center(self) -> Vec:
        return (-0.4, self.top_y + 0.5, 0.63)


def fuze(m: Model, s: FuzeSpec) -> None:
    """Fuze (on the `grenade` bone), the `spoon` and `pin` bones and the `fuze_top` locator."""
    g, top = m.bones[GRENADE], s.top_y
    octagon(g, 0.42, top, top + 0.15, s.fuze_mat)                                # threaded collar
    octagon(g, 0.3, top + 0.15, top + 0.7, s.fuze_mat)                           # fuze body
    g.box((-0.36, top + 0.7, -0.3), (0.72, s.height - 0.7, 0.6), s.fuze_mat)     # head (striker housing)
    g.box((-0.44, top + 0.62, -0.2), (0.1, s.height - 0.62, 0.4), s.fuze_mat)    # hinge lip the spoon hooks on
    m.locator("fuze_top", s.fuze_top, parent=GRENADE)

    ht = top + s.height
    sp = m.bone(SPOON, parent=GRENADE, pivot=(-0.4, ht, 0.0))
    sx, sy = s.shoulder or (s.side_x + 0.07, top - 0.05)
    sp.box((-0.4, ht, -0.25), (0.82, 0.1, 0.5), s.spoon_mat)                     # over the fuze head
    _seg(sp, (0.4, ht + 0.05), (sx, sy), 0.1, 0.5, s.spoon_mat)                  # slope down to the body
    sp.box((s.side_x + 0.02, s.spoon_end_y, -0.25), (0.1, sy + 0.04 - s.spoon_end_y, 0.5), s.spoon_mat)  # down the side
    sp.box((s.side_x + 0.02, s.spoon_end_y - 0.12, -0.3), (0.16, 0.14, 0.6), s.spoon_mat)  # flared tip

    rc = s.ring_center
    pn = m.bone(PIN, parent=GRENADE, pivot=rc)
    py = top + 0.5
    pn.box((-0.05, py, -0.46), (0.1, 0.1, 1.06), "g_wire")                       # split pin, through the fuze
    pn.box((-0.07, py - 0.02, -0.52), (0.14, 0.14, 0.06), "g_wire")              # its spread legs
    r, t, z = 0.45, 0.1, rc[2] - 0.05                                            # pull ring (a blocky square)
    x0, y0 = rc[0] - r, rc[1] - r
    pn.box((x0, y0, z), (2 * r, t, t), "g_wire")
    pn.box((x0, y0 + 2 * r - t, z), (2 * r, t, t), "g_wire")
    pn.box((x0, y0 + t, z), (t, 2 * r - 2 * t, t), "g_wire")
    pn.box((x0 + 2 * r - t, y0 + t, z), (t, 2 * r - 2 * t, t), "g_wire")
    if s.clip:                                                                   # wire safety clip
        cy = top + 0.3
        pn.box((-0.36, cy, -0.4), (0.9, 0.07, 0.07), "g_wire")                   # front leg
        pn.box((-0.36, cy, 0.33), (0.9, 0.07, 0.07), "g_wire")                   # rear leg
        pn.box((-0.43, cy, -0.4), (0.07, 0.07, 0.8), "g_wire")                   # bridge behind the hinge
        pn.box((0.5, cy, -0.4), (0.07, 0.3, 0.07), "g_wire")                     # hooks up over the spoon
        pn.box((0.5, cy, 0.33), (0.07, 0.3, 0.07), "g_wire")
    if s.second_ring:                                                            # secondary pin + triangle ring
        qy = top + 0.24
        pn.box((-0.04, qy, -0.9), (0.08, 0.08, 1.3), "g_wire")                   # second pin, out the FRONT
        zf = -0.95
        pn.box((-0.4, qy - 0.5, zf), (0.8, 0.09, 0.09), "g_wire")                # triangle: base
        for sgn in (-1, 1):                                                      # and its two legs
            c = (sgn * 0.2, qy - 0.2, zf + 0.045)
            pn.cbox(c, (0.09, 0.72, 0.09), "g_wire", rot=(0, 0, sgn * 33.7), pivot=c)


# ======================================================================================
# ARMS
# ======================================================================================
def _dir(rot: Vec, v: Vec) -> Vec:
    return _rotate(v, ZERO, rot)


def _add(a, b) -> Vec:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _sub(a, b) -> Vec:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _mul(a, k: float) -> Vec:
    return (a[0] * k, a[1] * k, a[2] * k)


L_TIP: Vec = (0.0, -T.ARM_W / 2 + 0.3, 1.0)   # where the left fist takes the ring: its end, on the side toward
#                                               the grenade (arm-local from the pivot), so the fist does not swallow it


def arms(m: Model, h: Handling) -> None:
    """Plain rectangular Minecraft arms, as the guns: one skin box per arm, no fingers. The
    grenade sits `h.grip_h` past the end of the right fist (it may clip into the hand a little);
    the left hand rests to its left and its end hooks the ring in `pull_pin`."""
    hw = T.ARM_W / 2
    n = _dir(R_ROT, (0, -1, 0))                       # the way the fist's end face looks
    rh = _sub(COM, _mul(n, hw + h.grip_h))
    ra = m.bone(T.RIGHT_ARM, pivot=rh, rotation=R_ROT)
    ra.skin_box((rh[0] - hw, rh[1] - hw, rh[2] - hw), (T.ARM_W, T.ARM_LEN, T.ARM_W), side="right")

    lh = _sub(L_END, _mul(_dir(L_ROT, (0, -1, 0)), hw))
    la = m.bone(T.LEFT_ARM, pivot=lh, rotation=L_ROT)
    la.skin_box((lh[0] - hw, lh[1] - hw, lh[2] - hw), (T.ARM_W, T.ARM_LEN, T.ARM_W), side="left")


# ======================================================================================
# ANIMATIONS
# ======================================================================================
def _matrix(rot: Vec):
    """Rows of the rotation matrix of `_rotate` (X, then Y, then Z)."""
    cols = [_dir(rot, e) for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1))]
    return [[cols[j][i] for j in range(3)] for i in range(3)]


def _euler(mat) -> Vec:
    """Inverse of `_matrix` (degrees)."""
    b = -math.asin(max(-1.0, min(1.0, mat[2][0])))
    a = math.atan2(mat[2][1], mat[2][2])
    c = math.atan2(mat[1][0], mat[0][0])
    return tuple(math.degrees(v) for v in (a, b, c))


def _follow(a: Animation, arm: str, child: str, t_end: float, step: float = BAKE_STEP) -> dict:
    """Bake position / rotation keys for `child` (a root-level bone) so that it moves rigidly with
    the bone `arm` from 0 to `t_end` of animation `a`, as if it were its child. Returns
    {time: child pivot position}."""
    m = a.model
    ab, cb = m.bones[arm], m.bones[child]
    r0 = _matrix(ab.rotation)
    v = _sub(cb.pivot, ab.pivot)
    local = tuple(sum(r0[i][j] * v[i] for i in range(3)) for j in range(3))          # R0^T v
    times = sorted({round(min(i * step, t_end), 4) for i in range(int(math.ceil(t_end / step)) + 1)}
                   | {t for ch in a.tracks.get(arm, {}).values() for t in ch if t <= t_end + 1e-9})
    out, prev = {}, None
    for t in times:
        apos, arot, _ = a.sample(t)[arm]
        rt = _matrix(_add(ab.rotation, arot))
        world = _add(_add(ab.pivot, tuple(sum(rt[i][j] * local[j] for j in range(3)) for i in range(3))), apos)
        rel = [[sum(rt[i][k] * r0[j][k] for k in range(3)) for j in range(3)] for i in range(3)]  # Rt R0^T
        e = _euler(rel)
        if prev is not None:  # keep the angles continuous
            e = tuple(x + 360.0 * round((p - x) / 360.0) for x, p in zip(e, prev))
        prev = e
        a.key(child, "position", t, _sub(world, cb.pivot))
        a.key(child, "rotation", t, e)
        out[t] = world
    return out


def _arm_keys(a: Animation, arm: str, keys: dict) -> None:
    for t, (pos, rot, ease) in keys.items():
        a.key(arm, "position", t, pos, ease)
        a.key(arm, "rotation", t, rot, ease)


def _reach(m: Model, arm: str, local: Vec, rot_off: Vec, target: Vec) -> Vec:
    """Position offset that puts the arm-local point `local` (from the pivot, before the bone
    rotation) of `arm` on `target`, given a rotation offset."""
    b = m.bones[arm]
    return _sub(target, _add(b.pivot, _dir(_add(b.rotation, rot_off), local)))


def _idle(m: Model, h: Handling) -> None:
    """`idle` (loop): breathing sway of the whole view model, the left hand hovering by the ring."""
    L, a = h.idle_len, m.anim("idle", h.idle_len, loop=True)
    a.keys(T.ROOT, "position", {0: ZERO, L / 2: (0, -h.idle_sway, 0), L: ZERO}, IO)
    a.keys(T.ROOT, "rotation", {0: ZERO, L / 2: (0.4, 0, 0), L: ZERO}, IO)
    a.keys(T.LEFT_ARM, "position", {0: ZERO, L * 0.3: (0.05, 0.07, 0), L * 0.75: (-0.04, -0.05, 0.03), L: ZERO}, IO)
    a.keys(T.LEFT_ARM, "rotation", {0: ZERO, L * 0.3: (-0.8, 0.5, 0), L * 0.75: (0.6, -0.4, 0), L: ZERO}, IO)


def _pull_pin(m: Model, s: FuzeSpec, h: Handling) -> None:
    """`pull_pin`: the left hand's end goes to the ring, a short tug straightens the split
    pin, the pin slides out with a twist and is carried off left (hidden at the end); the right
    hand clamps down on the spoon and resists the pull."""
    L = h.pull_len
    a = m.anim("pull_pin", L)
    t = {k: round(f * L, 4) for k, f in dict(hook=0.28, set=0.36, tug=0.45, free=0.52, out=0.72,
                                             gone=0.86, hid=0.93).items()}
    rot_hook = (8.0, 4.0, 0.0)
    hook = _reach(m, T.LEFT_ARM, L_TIP, rot_hook, _add(s.ring_center, (0.15, 0.1, 0.1)))  # hand's end at the ring
    tug, out, away = (0.0, 0.0, h.tug), (-0.6, 0.15, 1.6), (-3.0, -2.2, 2.6)
    path = [(0, ZERO, "linear"), (t["set"], ZERO, "linear"), (t["tug"], tug, OUT), (t["free"], tug, "linear"),
            (t["out"], out, OUT), (t["gone"], away, IO), (L, away, "linear")]
    a.keys(PIN, "position", {k: (v, e) for k, v, e in path})
    a.keys(PIN, "rotation", {0: ZERO, t["set"]: ZERO, t["tug"]: ((0, 0, 7), OUT), t["free"]: ZERO,
                             t["out"]: ((0, -10, -h.twist), OUT), t["gone"]: ((0, -30, -h.twist - 20), IO),
                             L: (0, -30, -h.twist - 20)})
    a.keys(PIN, "scale", {0: ONE, t["gone"]: ONE, t["hid"]: HIDDEN, L: HIDDEN})
    a.keys(T.LEFT_ARM, "position", {0: ZERO, t["hook"]: (hook, IO),
                                    **{k: (_add(hook, v), e) for k, v, e in path[1:]}})
    a.keys(T.LEFT_ARM, "rotation", {0: ZERO, t["hook"]: (rot_hook, IO), t["free"]: rot_hook,
                                    t["out"]: ((4, 0, 0), OUT), L: ((0, -6, 0), IO)})
    # right hand: clamp the spoon and lean against the tug, then settle
    q = h.squeeze
    a.keys(T.RIGHT_ARM, "position", {0: ZERO, t["set"]: ZERO, t["tug"]: ((0.05, -0.05, h.tug * 0.6), OUT),
                                     t["out"]: ((0.2, 0.05, 0.1), IO), L: (ZERO, IO)})
    a.keys(T.RIGHT_ARM, "rotation", {0: ZERO, t["hook"]: ZERO, t["tug"]: ((-q * 0.5, 0, q), OUT),
                                     t["out"]: ((q * 0.3, 0, q * 0.4), IO), L: (ZERO, IO)})
    _follow(a, T.RIGHT_ARM, GRENADE, L)
    a.keys(SPOON, "rotation", {0: ZERO, t["hook"]: ZERO, t["tug"]: (SPOON_SQUEEZED, OUT), L: SPOON_SQUEEZED})


def _hold(m: Model, h: Handling) -> None:
    """`hold` (loop): pin gone, right arm cocked back with a slow anticipation bob (it dips back
    and loads, then rises), left arm out front for balance."""
    L = h.hold_len
    a = m.anim("hold", L, loop=True)
    (p, r), b = h.hold_pose, h.bob
    dip = (_add(p, (0.05, -b, b * 0.7)), _add(r, (3.0, -0.5, 0.0)), IO)
    rise = (_add(p, (0.0, b * 0.35, -b * 0.2)), _add(r, (-1.0, 0.0, 0.0)), IO)
    _arm_keys(a, T.RIGHT_ARM, {0.0: (p, r, "linear"), round(L * 0.4, 4): dip, round(L * 0.75, 4): rise, L: (p, r, IO)})
    _follow(a, T.RIGHT_ARM, GRENADE, L, step=L / 40)
    (lp, lr) = L_HOLD
    _arm_keys(a, T.LEFT_ARM, {0.0: (lp, lr, "linear"),
                              round(L * 0.45, 4): (_add(lp, (0, b * 0.5, -b)), _add(lr, (-1.5, 0, 0)), IO),
                              L: (lp, lr, IO)})
    a.key(PIN, "scale", 0, HIDDEN)
    a.key(SPOON, "rotation", 0, SPOON_SQUEEZED)
    a.keys(T.ROOT, "position", {0: ZERO, L / 2: (0, -0.06, 0.04), L: ZERO}, IO)
    a.keys(T.ROOT, "rotation", {0: ZERO, L / 2: (0.3, 0, 0), L: ZERO}, IO)


def _throw(m: Model, h: Handling, name: str, keys: dict, release: float, locator: str) -> None:
    """`throw` / `throw_under` from the grenade's own right-arm key table. The spoon flips off its
    hinge as the fingers open, `grenade` (with spoon and pin) is hidden from `release` on, the
    left arm sweeps back. Adds the release locator (the grenade's centre at `release`)."""
    L = max(keys)
    if min(keys) != 0 or tuple(keys[0][:2]) != tuple(h.hold_pose):
        raise ValueError(f"{m.name}: {name} must start at the hold pose at t=0")
    if release not in keys:
        raise ValueError(f"{m.name}: {name} needs a key at the release time {release}")
    a = m.anim(name, L)
    _arm_keys(a, T.RIGHT_ARM, keys)
    path = _follow(a, T.RIGHT_ARM, GRENADE, release)
    a.keys(GRENADE, "scale", {0: ONE, round(release - 0.002, 4): ONE, release: HIDDEN, L: HIDDEN})
    a.key(PIN, "scale", 0, HIDDEN)
    t0 = round(release - FLIP_LEAD, 4)
    a.keys(SPOON, "rotation", {0: SPOON_SQUEEZED, t0: SPOON_SQUEEZED, release: ((0, 0, h.flip), OUT)})
    a.keys(SPOON, "position", {0: ZERO, t0: ZERO, release: ((0.3, 0.45, 0.0), OUT)})
    _arm_keys(a, T.LEFT_ARM, {0.0: (*L_HOLD, "linear"), release: ((-0.8, -0.5, -1.0), (-8.0, 10.0, 0.0), IO),
                              L: (*L_AWAY, IO)})
    m.locator(locator, tuple(round(v, 3) for v in path[round(release, 4)]))


def finish(m: Model, spec: FuzeSpec, h: Handling) -> Model:
    """Arms, animations, the release locators and the preview configs. Call last."""
    for i in range(3):  # the baked follow relies on these being true
        assert abs(_euler(_matrix((20.0, -35.0, 50.0)))[i] - (20.0, -35.0, 50.0)[i]) < 1e-6
    arms(m, h)
    _idle(m, h)
    _pull_pin(m, spec, h)
    _hold(m, h)
    _throw(m, h, "throw", h.throw, h.release_t, "release_point")
    _throw(m, h, "throw_under", h.under, h.under_release_t, "release_point_under")
    arm_bones = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "held": set(),                              # first: used for the animation strips
        "noarms": arm_bones,
        "thrown": arm_bones | {PIN, SPOON},         # what the thrown entity renders
    }
    return m
