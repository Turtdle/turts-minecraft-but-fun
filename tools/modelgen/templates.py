"""Shared gun templates: first-person arms, attachment mounts and the standard animations.

Every gun file should build its arms, mounts and animations through this module, so a mod-wide
change (arm size, recoil feel, reload choreography, a new mount slot...) is ONE edit here and a
rebuild, not 27 edits. Typical gun file::

    import attachments as att
    import templates as T
    from armorygen import Model

    def model() -> Model:
        m = Model("mygun")
        ...receiver / barrel / handguard / grip / trigger / bolt bones...
        T.default_part(m, "magazine", MAG_PIVOT).box(...)          # magazine_default
        T.arms(m, right_hand=(0, 2, 2), left_hand=(0, 4.5, -8.5), style="rifle")
        T.mounts(m, optic=(0, 7, -1), muzzle=(0, 5.5, -14), underbarrel=(0, 4, -7), side=(1.25, 5.5, -6.5))
        T.attach(m, "red_dot", "acog", "suppressor_556", "vertical_grip", "laser")
        T.locators(m, muzzle_flash=(0, 5.5, -16.25), shell_eject=(1.1, 5.5, -1), sight_line=(0, 8, 1.75))
        T.idle(m)
        T.fire_auto(m)
        T.reload_mag(m)
        T.reload_mag(m, empty=True)
        m.preview_configs = {"bare": att.loadout(m)}
        return m

CONVENTIONS (verified in game, see the IN-GAME TUNING NOTES in guns/m4.py)
  * 1 model unit ~ 3.35 cm (16 units = 1 block). Muzzle toward -Z, +X = right side of the gun,
    +Y up, bore axis at y ~ 5.5, trigger near z ~ 0, gun centred on X = 0.
  * Positions are model units, rotations are degrees (X, then Y, then Z), times are seconds.
  * Previews == game: no per-gun sign flipping. Root X-rotation * PITCH_UP raises the muzzle,
    root Z-rotation * ROLL_RIGHT tips the top of the gun toward +X.
  * Arms: bones `right_arm` / `left_arm`, direct children of root, pivot AT THE HAND. An arm is a
    vertical ARM_W x ARM_LEN x ARM_W player-skin box laid down by the bone rotation
    (rx 90 = forearm straight back, 180 = straight down; ry + = swings toward +X).
  * Animation names are the ones the Java code plays: `idle`, `fire`, `reload`, `reload_empty`.
    Every template starts and ends at the exact rest pose (checked: a violation raises).
  * Animation templates silently skip OPTIONAL bones that the gun does not have (trigger, bolt,
    shell...). Required bones are listed in each docstring and raise if missing.

STRUCTURE
  * tuning constants          - module level, one place for the whole mod
  * ArmStyle / ARM_STYLES     - how each hold lays the arms (add a style = add an entry)
  * Rig                       - what `arms()` built; stored on the model as `m.rig`
  * Clip                      - an Animation with a time scale, optional bones and a rest check
  * HandCycle                 - a hand leaving its grip to work a bolt / charging handle
  * public template functions - arms, mounts, attach, default_part, locators, idle, fire_*, reload_*
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import attachments as att
from armorygen import Bone, Model, _rotate

Vec = tuple[float, float, float]
ZERO: Vec = (0.0, 0.0, 0.0)

# ======================================================================================
# MOD-WIDE TUNING CONSTANTS
# ======================================================================================
BORE_Y = 5.5            # bore axis height every gun is modelled around
ARM_W, ARM_LEN = 3, 26  # arm thickness (X and Z) and length (Y) before the bone rotation
PITCH_UP = 1.0          # sign of a root X-rotation that RAISES the muzzle
ROLL_RIGHT = -1.0       # sign of a root Z-rotation that tips the top of the gun toward +X

IO, OUT, IN = "easeInOutSine", "easeOutQuad", "easeInQuad"

# bone names (the Java side and attachments.py rely on these)
ROOT, RIGHT_ARM, LEFT_ARM = "root", "right_arm", "left_arm"
TRIGGER = "trigger"
MUZZLE_DEFAULT, STOCK_DEFAULT, MAGAZINE_DEFAULT = "muzzle_default", "stock_default", "magazine_default"
# slot -> (mount bone, bone it hangs off)
MOUNTS = {
    "optic": ("optic_mount", "receiver"),
    "muzzle": ("muzzle_mount", "barrel"),
    "underbarrel": ("underbarrel_mount", "handguard"),
    "side": ("side_mount", "handguard"),
}
# slot -> bone an attachment of that slot is parented to
SLOT_BONES = {**{s: b for s, (b, _) in MOUNTS.items()}, "stock": "stock", "magazine": "magazine"}
# slot -> (default-part bone, its parent, create the parent if missing?)
DEFAULT_PARTS = {
    "muzzle": (MUZZLE_DEFAULT, "barrel", False),
    "stock": (STOCK_DEFAULT, "stock", True),
    "magazine": (MAGAZINE_DEFAULT, "magazine", True),
}


# ---- tiny vector helpers ---------------------------------------------------------------
def _add(a, b) -> Vec:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _sub(a, b) -> Vec:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _pitch_roll(pitch: float, roll: float) -> Vec:
    """Root rotation that raises the muzzle by `pitch` deg and rolls the top toward +X by `roll`."""
    return (pitch * PITCH_UP, 0.0, roll * ROLL_RIGHT)


# ======================================================================================
# ARMS
# ======================================================================================
@dataclass(frozen=True)
class ArmStyle:
    """How one hold lays the two arms. Rotations are bone rotations in degrees."""
    right_rot: Vec
    left_rot: Vec
    finger: bool = True                    # trigger finger along the right side of the gun
    finger_offset: Vec = ZERO              # finger box offset, arm-local (before the bone rotation)
    left_dz: float = -1.0                  # z of the left arm box relative to the left hand
    left_default: Vec | None = None        # left hand = right hand + this, when left_hand is None
    # reload_mag choreography for this hold
    cant: tuple[float, float] = (12.0, 20.0)     # (pitch up, roll right) while swapping the mag
    cant_pos: Vec = (0.0, 0.6, 0.4)              # root offset while canted
    mag_drop: Vec = (0.0, -1.5, 0.0)             # magazine travel that clears the well
    grab_dy: float = 0.5                         # left fist height above the bottom of the mag
    swap_arm_rx: float = -20.0                   # left forearm levels out by this while swapping
    catch: bool = True                           # left hand slaps a bolt catch after an empty reload


ARM_STYLES: dict[str, ArmStyle] = {
    # exactly the M4 / M2010 setup: right hand on the pistol grip, left under the fore-end
    "rifle": ArmStyle(right_rot=(135, 40, 0), left_rot=(128, -36, 0)),
    # two-handed pistol hold: arms pushed out, forearms run back more level; the right fist sits
    # HIGH on the grip (striker_pistols: about (0, 3.75, 1.25)) and the left fist cups it from
    # below / the left, its top under the slide. The index finger lies on the right side of the
    # frame at the trigger guard (finger_offset), not up inside the slide. The reload cant
    # is kept mild (was 14 / 24 deg with a 0.5 lift: the gun rolled away from the mag swap).
    "pistol": ArmStyle(right_rot=(118, 30, 0), left_rot=(116, -34, 0), left_dz=-1.5,
                       finger_offset=(0.5, 0.75, 0.75),
                       left_default=(-1.0, -1.0, -0.25), cant=(9.0, 14.0), cant_pos=(0.0, 0.3, 0.2),
                       mag_drop=(0.0, -3.5, 0.8), grab_dy=-0.25, swap_arm_rx=-8.0, catch=False),
    # like a rifle, the left hand sits on the pump (it rides with the pump in fire_pump)
    "shotgun_pump": ArmStyle(right_rot=(135, 40, 0), left_rot=(128, -36, 0)),
    # belt-fed MG: left hand rests ON TOP of the gun (feed cover / top of the handguard); the
    # forearm comes in flatter and from further left
    "mg": ArmStyle(right_rot=(135, 40, 0), left_rot=(116, -48, 0), cant=(6.0, 10.0),
                   cant_pos=(0.0, 0.4, 0.3)),
}


@dataclass
class Rig:
    """What `arms()` built. Stored as `m.rig`; the animation templates read the hand positions
    from it. `right_hand` / `left_hand` are the arm bone pivots (model units)."""
    right_hand: Vec
    left_hand: Vec
    style_name: str
    style: ArmStyle = field(repr=False)

    def hand(self, side: str) -> Vec:
        return self.right_hand if side == "right" else self.left_hand

    @staticmethod
    def of(m: Model) -> "Rig":
        rig = getattr(m, "rig", None)
        if rig is None:
            raise ValueError(f"{m.name}: call templates.arms(...) before the animation templates")
        return rig


def arms(m: Model, right_hand: Vec, left_hand: Vec | None = None, style: str = "rifle") -> Rig:
    """Build the first-person arm bones `right_arm` / `left_arm` (player skin, children of root).

    right_hand  centre of the pistol grip, e.g. (0, 2, 2) (units).
    left_hand   where the left hand holds the gun; the fist is 3 wide, so put it about 1 below
                the surface it grips:
                  'rifle'         under the fore-end, e.g. (0, 4.5, -8.5)
                  'pistol'        may be None -> cups the right hand from below-left
                  'shotgun_pump'  under the pump (the hand rides with it in fire_pump)
                  'mg'            ON TOP of the feed cover / handguard, about 1 above the surface
    style       key of ARM_STYLES.
    Returns the Rig (also stored as `m.rig`)."""
    if style not in ARM_STYLES:
        raise ValueError(f"unknown arm style {style!r}; use one of {sorted(ARM_STYLES)}")
    s = ARM_STYLES[style]
    if left_hand is None:
        if s.left_default is None:
            raise ValueError(f"arm style {style!r} needs an explicit left_hand")
        left_hand = _add(right_hand, s.left_default)
    rh, lh = tuple(map(float, right_hand)), tuple(map(float, left_hand))
    hw = ARM_W / 2
    ra = m.bone(RIGHT_ARM, pivot=rh, rotation=s.right_rot)
    ra.skin_box((rh[0] - hw, rh[1] - 1.5, rh[2] - hw), (ARM_W, ARM_LEN, ARM_W), side="right")
    if s.finger:  # index finger: continues past the fist along the right side to the trigger
        fo = s.finger_offset
        ra.skin_box((rh[0] + 1 + fo[0], rh[1] - 3.5 + fo[1], rh[2] - 0.5 + fo[2]), (1, 2, 1), side="right",
                    sleeve=False)
    la = m.bone(LEFT_ARM, pivot=lh, rotation=s.left_rot)
    la.skin_box((lh[0] - hw, lh[1] - 1.5, lh[2] + s.left_dz), (ARM_W, ARM_LEN, ARM_W), side="left")
    m.rig = Rig(rh, lh, style, s)
    return m.rig


# ======================================================================================
# MOUNTS, DEFAULT PARTS, ATTACHMENTS, LOCATORS
# ======================================================================================
def mounts(m: Model, optic: Vec | None = None, muzzle: Vec | None = None,
           underbarrel: Vec | None = None, side: Vec | None = None) -> dict[str, Vec]:
    """Create the attachment mount bones (pivot = mount point, units) for the slots given:
      optic        `optic_mount`        child of `receiver`   top of the rail, middle of the optic base
      muzzle       `muzzle_mount`       child of `barrel`     end of the barrel on the bore axis
      underbarrel  `underbarrel_mount`  child of `handguard`  bottom face of the bottom rail
      side         `side_mount`         child of `handguard`  outer face of the RIGHT rail
    The parent bones must already exist. Returns {slot: point} of the mounts created."""
    made = {}
    for slot, point in (("optic", optic), ("muzzle", muzzle), ("underbarrel", underbarrel), ("side", side)):
        if point is None:
            continue
        bone, parent = MOUNTS[slot]
        if parent not in m.bones:
            raise ValueError(f"{m.name}: mount {bone!r} needs a {parent!r} bone")
        m.bone(bone, parent=parent, pivot=point)
        made[slot] = tuple(map(float, point))
    return made


def default_part(m: Model, slot: str, pivot: Vec) -> Bone:
    """The bone of a factory part the game HIDES while an attachment of `slot` is fitted:
      'muzzle'    -> `muzzle_default`   (child of `barrel`; pivot = the muzzle mount point)
      'stock'     -> `stock_default`    (child of `stock`, which is created at `pivot` if missing)
      'magazine'  -> `magazine_default` (child of `magazine`, created at `pivot` if missing)
    Returns the bone; add the boxes to it. Animate `stock` / `magazine`, never the *_default bone."""
    if slot not in DEFAULT_PARTS:
        raise ValueError(f"no default part for slot {slot!r}; use one of {sorted(DEFAULT_PARTS)}")
    name, parent, create = DEFAULT_PARTS[slot]
    if parent not in m.bones:
        if not create:
            raise ValueError(f"{m.name}: {name!r} needs a {parent!r} bone")
        m.bone(parent, pivot=pivot)
    return m.bone(name, parent=parent, pivot=pivot)


def attach(m: Model, *ids: str) -> None:
    """Add shared attachments by id (see attachments.SLOTS) to the right bone of their slot, at
    that bone's pivot. The mount bones (`mounts`) / `stock` / `magazine` must exist."""
    for i in ids:
        if i not in att.SLOTS:
            raise ValueError(f"unknown attachment {i!r}; known: {sorted(att.SLOTS)}")
        bone = SLOT_BONES[att.SLOTS[i]]
        if bone not in m.bones:
            raise ValueError(f"{m.name}: attachment {i!r} needs the {bone!r} bone")
        getattr(att, f"add_{i}")(m, bone, m.bones[bone].pivot)


def locators(m: Model, muzzle_flash: Vec, shell_eject: Vec, sight_line: Vec, **extra: Vec) -> None:
    """The locators the game reads: `muzzle_flash` (BARE muzzle, bore axis), `shell_eject`
    (ejection port, right side), `sight_line` (iron-sight line at the rear sight: its y is the
    eye height for ADS). `extra` adds more by name, e.g. load_port=(0, 3.5, -3)."""
    for name, pos in dict(muzzle_flash=muzzle_flash, shell_eject=shell_eject, sight_line=sight_line,
                          **extra).items():
        m.locator(name, pos)


def part_bounds(m: Model, bone: str) -> tuple[Vec, Vec]:
    """(min, max) corners at rest of `bone` and its children, ignoring `att_*` attachments.
    A bone without cubes gives its pivot twice."""
    if bone not in m.bones:
        raise ValueError(f"{m.name}: no bone {bone!r}")
    pts = []
    for b, _, _, quad in m.world_faces():
        chain = [c.name for c in m._bone_chain(b)]
        if bone in chain and not any(n.startswith("att_") for n in chain):
            pts.extend(quad)
    if not pts:
        p = m.bones[bone].pivot
        return p, p
    return (tuple(min(p[i] for p in pts) for i in range(3)),
            tuple(max(p[i] for p in pts) for i in range(3)))


# ======================================================================================
# ANIMATION PLUMBING
# ======================================================================================
class Clip:
    """An armorygen Animation authored on a BASE timeline and scaled to the real length.

    `keys()` takes times in base seconds (the timings of the M4 / M2010 originals) and maps them
    to `t * length / base`. Bones that are None or absent from the model are skipped, which is
    what makes a bone optional. `done()` verifies the rest-pose rule and returns the Animation."""

    def __init__(self, m: Model, name: str, length: float | None, base: float, loop: bool = False):
        self.m, self.base = m, float(base)
        self.length = float(length) if length else self.base
        if self.length <= 0:
            raise ValueError(f"animation {name}: length must be > 0")
        self.k = self.length / self.base
        self.a = m.anim(name, self.length, loop)

    def has(self, bone: str | None) -> bool:
        return bone is not None and bone in self.m.bones

    def need(self, *bones: str) -> None:
        for b in bones:
            if not self.has(b):
                raise ValueError(f"{self.m.name}: animation {self.a.name!r} needs a bone {b!r}")

    def t(self, base_t: float) -> float:
        return round(min(max(base_t, 0.0) * self.k, self.length), 4)

    def keys(self, bone: str | None, channel: str, frames: dict, easing: str = "linear") -> "Clip":
        """frames = {base_time: (x, y, z)} or {base_time: ((x, y, z), easing)}."""
        if self.has(bone):
            self.a.keys(bone, channel, {self.t(t): v for t, v in frames.items()}, easing)
        return self

    def pos(self, bone, frames, easing="linear") -> "Clip":
        return self.keys(bone, "position", frames, easing)

    def rot(self, bone, frames, easing="linear") -> "Clip":
        return self.keys(bone, "rotation", frames, easing)

    def done(self):
        for bone, chans in self.a.tracks.items():
            for ch, frames in chans.items():
                ts = sorted(frames)
                for t in (ts[0], ts[-1]):
                    if any(abs(v) > 1e-6 for v in frames[t][0]):
                        raise ValueError(f"{self.m.name}: animation {self.a.name!r} leaves "
                                         f"{bone}.{ch} off the rest pose at t={t}")
        return self.a


@dataclass
class HandCycle:
    """A hand leaves its grip, works a handle and comes back: turn-bolt (lift, back, forward,
    down - the M2010 cycle) or, with lift 0, a straight pull / charging handle.

    side     'right' or 'left' arm.
    handle   bone that turns (about Z, around its pivot) and slides back (+Z). Its pivot must be
             on the bolt axis.
    knob     centre of the knob at rest; None = 2.5 units out from the handle pivot on `side`.
    lift     degrees the handle is lifted; 0 = straight pull.
    pull     units the handle travels back.
    also     more bones that slide back with the handle (e.g. the bolt behind a charging handle).
    """
    side: str = "right"
    handle: str = "bolt_handle"
    knob: Vec | None = None
    lift: float = 70.0
    pull: float = 3.0
    also: tuple = ()

    # key times as fractions of the cycle (M2010: 1.02 s)
    TURN = {k: v / 1.02 for k, v in dict(start=0, grab=0.20, mid_up=0.26, up=0.32, back=0.48,
                                         hold=0.52, fwd=0.66, mid_dn=0.72, down=0.78, end=1.02).items()}
    STRAIGHT = dict(start=0, grab=0.26, mid_up=0.26, up=0.26, back=0.48, hold=0.55, fwd=0.72,
                    mid_dn=0.72, down=0.72, end=1.0)

    def write(self, c: Clip, rig: Rig, t0: float, dur: float, start_pos: Vec = ZERO,
              start_rot: Vec = ZERO) -> dict:
        """Key the arm and the handle on clip `c` from base time `t0` for `dur` base seconds.
        `start_pos` / `start_rot` = where the arm is at t0 (offsets from rest) when the hand comes
        from somewhere else than its grip. Returns the base key times by name."""
        if self.side not in ("right", "left"):
            raise ValueError("HandCycle.side must be 'right' or 'left'")
        c.need(self.handle)
        arm = RIGHT_ARM if self.side == "right" else LEFT_ARM
        sgn = 1.0 if self.side == "right" else -1.0
        piv = c.m.bones[self.handle].pivot
        knob = self.knob or (piv[0] + sgn * 2.5, piv[1], piv[2])
        radius = math.hypot(knob[0] - piv[0], knob[1] - piv[1])
        home = rig.hand(self.side)

        def hand(angle: float, pull: float = 0.0) -> Vec:
            """arm offset that puts the fist on the knob (a bit outside / below it)."""
            r = math.radians(angle)
            kx, ky = piv[0] + sgn * radius * math.cos(r), piv[1] + radius * math.sin(r)
            ox = sgn * (0.9 * math.cos(r) + 0.3 * math.sin(r))
            oy = -0.3 * math.cos(r) - 0.2 * math.sin(r)
            return (kx + ox - home[0], ky + oy - home[1], knob[2] - home[2] + pull)

        fr = self.TURN if self.lift else self.STRAIGHT
        T = {k: round(t0 + v * dur, 4) for k, v in fr.items()}
        L, half, pull = self.lift, self.lift / 2, self.pull
        c.pos(arm, {T["start"]: start_pos, T["grab"]: (hand(0), IO)})
        if L:
            c.pos(arm, {T["mid_up"]: hand(half), T["up"]: (hand(L), OUT)})
        c.pos(arm, {T["back"]: (hand(L, pull), OUT), T["hold"]: hand(L, pull), T["fwd"]: (hand(L), IN)})
        if L:
            c.pos(arm, {T["mid_dn"]: hand(half), T["down"]: (hand(0), OUT)})
        c.pos(arm, {T["end"]: (ZERO, IO)})
        if L:
            c.rot(arm, {T["up"]: (10, 0, 0)})
        c.rot(arm, {T["start"]: start_rot, T["grab"]: ((6, 0, 0), IO), T["back"]: (10, 0, 0),
                    T["fwd"]: (10, 0, 0), T["down"]: (6, 0, 0), T["end"]: (ZERO, IO)})
        if L:
            c.rot(self.handle, {T["start"]: ZERO, T["grab"]: ZERO, T["up"]: ((0, 0, sgn * L), OUT),
                                T["fwd"]: (0, 0, sgn * L), T["down"]: (ZERO, OUT)})
        slide = {T["start"]: ZERO, T["up"]: ZERO, T["back"]: ((0, 0, pull), OUT),
                 T["hold"]: (0, 0, pull), T["fwd"]: (ZERO, IN)}
        for bone in (self.handle, *self.also):
            c.pos(bone, slide)
        return T


def _kick(c: Clip, recoil: float, rise: float, t_peak: float, t_ease: float, t_rest: float,
          lift: float = 0.15) -> None:
    """Root recoil: snap back `recoil` units / `rise` degrees at t_peak, mostly recovered at
    t_ease, at rest at t_rest (base times)."""
    c.pos(ROOT, {0: ZERO, t_peak: ((0, lift, recoil), OUT), t_ease: ((0, 0, recoil * 0.2), IO),
                 t_rest: (ZERO, IO)})
    c.rot(ROOT, {0: ZERO, t_peak: (_pitch_roll(rise, 0), OUT), t_ease: (_pitch_roll(rise * 0.2, 0), IO),
                 t_rest: (ZERO, IO)})
    c.rot(TRIGGER, {0: ZERO, t_peak * 0.75: ((-12, 0, 0), OUT), t_ease: (ZERO, IO)})


# ======================================================================================
# IDLE / FIRE
# ======================================================================================
def idle(m: Model, length: float = 3.0):
    """`idle` (looping): slow breathing sway of the whole gun. Bones: root."""
    c = Clip(m, "idle", length, 3.0, loop=True)
    c.pos(ROOT, {0: ZERO, 1.5: (0, -0.12, 0), 3.0: ZERO}, IO)
    c.rot(ROOT, {0: ZERO, 1.5: _pitch_roll(0.4, 0), 3.0: ZERO}, IO)
    return c.done()


def fire_auto(m: Model, recoil: float = 0.8, bolt_bone: str | None = "bolt", bolt_travel: float = 2.0,
              length: float = 0.07, rise: float = 2.0):
    """`fire` for automatic / semi-automatic guns (AR, AK, box-mag DMR, belt-fed): a short kick
    and one bolt-carrier cycle. Keep `length` below the gun's shot interval.

    recoil       units the gun kicks back (+Z).           rise  degrees of muzzle rise.
    bolt_bone    bone that slides back `bolt_travel` units (optional; e.g. 'bolt').
    Bones: root; optional `bolt_bone`, trigger."""
    c = Clip(m, "fire", length, 0.07)
    c.pos(ROOT, {0: ZERO, 0.02: ((0, 0, recoil), OUT), 0.07: (ZERO, IO)})
    c.rot(ROOT, {0: ZERO, 0.02: (_pitch_roll(rise, 0), OUT), 0.07: (ZERO, IO)})
    c.pos(bolt_bone, {0: ZERO, 0.03: ((0, 0, bolt_travel), OUT), 0.07: (ZERO, IO)})
    c.rot(TRIGGER, {0: ZERO, 0.02: ((-12, 0, 0), OUT), 0.07: (ZERO, IO)})
    return c.done()


def fire_bolt(m: Model, bolt_bone: str = "bolt_handle", length: float = 1.30, lift_deg: float = 70.0,
              pull: float = 3.0, recoil: float = 1.5, rise: float = 4.0, knob: Vec | None = None):
    """`fire` for a turn-bolt rifle (the M2010 cycle): recoil, then the RIGHT hand leaves the
    grip, lifts the handle `lift_deg`, pulls it `pull` units back, pushes it home, turns it down
    and returns. `length` should equal the gun's shot interval.

    bolt_bone  handle + bolt body in one bone, pivot on the bolt axis at the handle; the handle
               sticks out to +X. `knob` = knob centre (default 2.5 right of the pivot).
    Bones: root, right_arm, `bolt_bone`; optional trigger."""
    c, rig = Clip(m, "fire", length, 1.30), Rig.of(m)
    T = HandCycle("right", bolt_bone, knob, lift_deg, pull).write(c, rig, 0.22, 1.02)
    _kick(c, recoil, rise, 0.04, 0.12, 0.22)
    c.pos(ROOT, {T["grab"]: ZERO, T["up"]: ((0, 0.15, 0), IO), T["back"]: ((0, 0.15, 0.4), OUT),
                 T["fwd"]: ((0, 0.1, -0.2), IN), T["down"]: ((0, -0.1, 0), OUT), T["end"]: (ZERO, IO)})
    c.rot(ROOT, {T["grab"]: ZERO, T["up"]: (_pitch_roll(0.6, -3), IO), T["back"]: (_pitch_roll(1.2, -3), OUT),
                 T["fwd"]: (_pitch_roll(0.3, -2), IN), T["down"]: (_pitch_roll(0, 2), OUT),
                 T["end"]: (ZERO, IO)})
    return c.done()


def fire_straight_pull(m: Model, bolt_bone: str = "bolt_handle", pull: float = 3.0, length: float = 0.9,
                       recoil: float = 1.5, rise: float = 4.0, hand: str = "right",
                       knob: Vec | None = None):
    """`fire` for a straight-pull action (M26 MASS): recoil, then `hand` ('right' leaves the
    grip, 'left' leaves the fore-end) pulls the handle `pull` units straight back and shoves it
    home. `knob` = knob centre (default 2.5 out from the bone pivot on the hand's side).
    Bones: root, the arm of `hand`, `bolt_bone`; optional trigger."""
    c, rig = Clip(m, "fire", length, 0.9), Rig.of(m)
    T = HandCycle(hand, bolt_bone, knob, 0.0, pull).write(c, rig, 0.18, 0.68)
    _kick(c, recoil, rise, 0.04, 0.11, 0.18)
    c.pos(ROOT, {T["grab"]: ZERO, T["back"]: ((0, 0.1, 0.4), OUT), T["fwd"]: ((0, 0.05, -0.25), IN),
                 T["end"]: (ZERO, IO)})
    c.rot(ROOT, {T["grab"]: ZERO, T["back"]: (_pitch_roll(1.0, 0), OUT), T["fwd"]: (_pitch_roll(-0.4, 0), IN),
                 T["end"]: (ZERO, IO)})
    return c.done()


def fire_pump(m: Model, pump_bone: str = "pump", travel: float = 3.0, length: float = 0.9,
              recoil: float = 1.5, rise: float = 5.0, bolt_bone: str | None = "bolt"):
    """`fire` for a pump shotgun: kick, then the LEFT hand racks the pump `travel` units back and
    forward (the hand rides with the pump; `bolt_bone`, if present, opens with it).
    Use arms(style='shotgun_pump') with the left hand on the pump.
    Bones: root, left_arm, `pump_bone`; optional `bolt_bone`, trigger."""
    c = Clip(m, "fire", length, 0.9)
    Rig.of(m)
    c.need(pump_bone, LEFT_ARM)
    _kick(c, recoil, rise, 0.04, 0.14, 0.24)
    rack = {0: ZERO, 0.26: ZERO, 0.44: ((0, 0, travel), OUT), 0.50: (0, 0, travel), 0.64: (ZERO, IN)}
    for bone in (pump_bone, LEFT_ARM, bolt_bone):
        c.pos(bone, rack)
    c.pos(ROOT, {0.26: ZERO, 0.44: ((0, -0.1, 0.35), OUT), 0.50: ((0, -0.1, 0.3), IO),
                 0.64: ((0, 0.05, -0.3), IN), 0.82: (ZERO, IO)})
    c.rot(ROOT, {0.26: ZERO, 0.44: (_pitch_roll(-1.0, 0), OUT), 0.64: (_pitch_roll(0.8, 0), IN),
                 0.82: (ZERO, IO)})
    return c.done()


def fire_pistol(m: Model, slide_bone: str | None = "slide", travel: float = 2.0, length: float = 0.1,
                recoil: float = 0.5, flip: float = 5.0):
    """`fire` for a pistol: the slide cycles `travel` units and the muzzle flips `flip` degrees
    (about the model origin, so keep the grip near the origin). Both hands stay on the gun.
    Bones: root; optional `slide_bone`, trigger."""
    c = Clip(m, "fire", length, 0.1)
    c.pos(ROOT, {0: ZERO, 0.025: ((0, 0.1, recoil), OUT), 0.1: (ZERO, IO)})
    c.rot(ROOT, {0: ZERO, 0.03: (_pitch_roll(flip, 0), OUT), 0.1: (ZERO, IO)})
    c.pos(slide_bone, {0: ZERO, 0.03: ((0, 0, travel), OUT), 0.045: (0, 0, travel), 0.085: (ZERO, IN)})
    c.rot(TRIGGER, {0: ZERO, 0.02: ((-12, 0, 0), OUT), 0.1: (ZERO, IO)})
    return c.done()


def fire_revolver(m: Model, length: float = 0.25, recoil: float = 1.0, flip: float = 14.0, *,
                  cylinder_bone: str = "cylinder", hammer_bone: str | None = "hammer",
                  step_deg: float = 60.0, cock_deg: float = 40.0):
    """`fire` for a double-action revolver: the hammer flicks back and falls, the cylinder turns
    one chamber (`step_deg`, clockwise seen from behind) and the muzzle flips `flip` degrees about
    the model origin. Nothing is ejected. The cylinder ends one chamber further and snaps back to
    0 in the last 2 ms, which is invisible as long as the cylinder looks the same every
    `step_deg` (six chambers: 60).
    Bones: root, `cylinder_bone` (pivot on the cylinder axis); optional `hammer_bone` (pivot at
    its pin, +X rotation cocks it), trigger."""
    c = Clip(m, "fire", length, 0.25)
    c.need(cylinder_bone)
    c.pos(ROOT, {0: ZERO, 0.045: ((0, 0.15, recoil), OUT), 0.13: ((0, 0, recoil * 0.25), IO),
                 0.25: (ZERO, IO)})
    c.rot(ROOT, {0: ZERO, 0.055: (_pitch_roll(flip, 0), OUT), 0.14: (_pitch_roll(flip * 0.25, 0), IO),
                 0.25: (ZERO, IO)})
    c.rot(hammer_bone, {0: ZERO, 0.012: ((cock_deg, 0, 0), OUT), 0.03: (ZERO, IN)})
    turn = (0, 0, -step_deg)
    c.rot(cylinder_bone, {0: ZERO, 0.03: (turn, OUT), 0.248: turn, 0.25: (ZERO, "step")})
    c.rot(TRIGGER, {0: ZERO, 0.02: ((-14, 0, 0), OUT), 0.15: (-14, 0, 0), 0.25: (ZERO, IO)})
    return c.done()


# ======================================================================================
# RELOADS
# ======================================================================================
def _reload_cant(c: Clip, s: ArmStyle, base: float, t0: float = 0.0) -> tuple[Vec, Vec]:
    """Root keys of a magazine reload on clip `c` (base times): cant the gun in the first 0.3 s,
    bump it when the magazine base is slapped (t0 + 1.6), level out in the last 0.37 s.
    `t0` = when the magazine swap (`_mag_swap`) starts. Returns (cant rotation, cant offset)."""
    cant, cp = _pitch_roll(*s.cant), s.cant_pos
    slap_r, slap_p = _pitch_roll(s.cant[0] + 1.5, s.cant[1]), _add(cp, (0, 0.3, 0))
    level = base - 0.37
    c.rot(ROOT, {0: ZERO, 0.3: (cant, IO), t0 + 1.45: cant, t0 + 1.6: (slap_r, OUT), t0 + 1.7: (cant, IO),
                 level: cant, base: (ZERO, IO)})
    c.pos(ROOT, {0: ZERO, 0.3: (cp, IO), t0 + 1.45: cp, t0 + 1.6: (slap_p, OUT), t0 + 1.7: (cp, IO),
                 level: cp, base: (ZERO, IO)})
    return cant, cp


def _mag_swap(c: Clip, m: Model, s: ArmStyle, lh: Vec, mag_bone: str, grab: Vec | None,
              t0: float = 0.0, start_pos: Vec = ZERO, start_rot: Vec = ZERO) -> tuple[Vec, Vec]:
    """The magazine swap itself, 1.68 base seconds from `t0`: the left hand goes from where it is
    at t0 (`start_pos` / `start_rot`, arm offsets from rest) to the magazine, strips it down and
    out of view, brings a fresh one up, seats it and slaps the base. Keys LEFT_ARM and `mag_bone`.
    Returns (g, arm_r): the arm offset / rotation the hand is left with at t0 + 1.68."""
    if grab is None:
        lo, hi = part_bounds(m, mag_bone)
        grab = (0.0, lo[1] + s.grab_dy, (lo[2] + hi[2]) / 2)
    g = _sub(grab, lh)                              # arm offset with the fist on the magazine
    clear = _add(g, s.mag_drop)                     # mag just clear of the well
    away = _add(clear, (-1, -4, 1.5))               # down and out of view
    wind = _add(g, (0, -2, 0))                      # wind-up for the slap
    arm_r = (s.swap_arm_rx, 0, 0)
    c.pos(LEFT_ARM, {t0: start_pos, t0 + 0.3: (g, IO), t0 + 0.38: g, t0 + 0.5: (clear, IN),
                     t0 + 0.72: (away, IN), t0 + 1.0: away, t0 + 1.3: (clear, OUT), t0 + 1.45: (g, OUT),
                     t0 + 1.52: (wind, OUT), t0 + 1.6: (g, IN), t0 + 1.68: g})
    c.rot(LEFT_ARM, {t0: start_rot, t0 + 0.3: (arm_r, IO), t0 + 1.68: arm_r})
    rel = lambda v: _sub(v, g)                      # the magazine rides with the hand
    c.pos(mag_bone, {t0 + 0.38: ZERO, t0 + 0.5: (rel(clear), IN), t0 + 0.72: (rel(away), IN),
                     t0 + 1.0: rel(away), t0 + 1.3: (rel(clear), OUT), t0 + 1.45: (ZERO, OUT)})
    c.rot(mag_bone, {t0 + 0.5: ZERO, t0 + 0.72: ((-15, 0, 0), IN), t0 + 1.0: (-15, 0, 0),
                     t0 + 1.3: (ZERO, OUT)})
    return g, arm_r


def reload_mag(m: Model, mag_bone: str = "magazine", length: float | None = None, empty: bool = False,
               bolt_action: bool = False, *, style: str | None = None, bolt_bone: str | None = None,
               empty_action: str | None = None, lift_deg: float | None = None, pull: float | None = None,
               hand: str = "right", knob: Vec | None = None, also: tuple = (),
               grab: Vec | None = None, catch: Vec | None = None):
    """`reload` (empty=False) / `reload_empty` (empty=True): box-magazine swap with the LEFT hand,
    ported from the M4: cant the gun, strip the mag down and out of view, bring a fresh one up,
    seat it, slap the base, level out.

    mag_bone      the bone that moves (animate `magazine`, not `magazine_default`).
    length        seconds; None = the base length (2.2 / empty: 2.6, or 3.0 with a hand cycle).
    style         reload choreography = an ARM_STYLES key; None = the style given to arms().
                  'pistol': the mag drops out of the grip, palm under the base plate.
    empty         adds the step that chambers a round, chosen by `empty_action`:
        'bolt_release' (default)  the bolt/slide `bolt_bone` (default 'bolt', pistol: 'slide') is
                      held `pull` units back (default 2) for the whole swap and snaps home at the
                      end; with a rifle hold the left hand slaps the bolt catch at `catch`
                      (default: left side of the receiver above the magazine).
        'cycle'       `hand` works the handle `bolt_bone` (default 'bolt_handle'): lifts it
                      `lift_deg` (0 = straight pull / charging handle), pulls it `pull` units
                      (default 3) back and returns it. `knob`, `also`: see HandCycle.
    bolt_action   shortcut for a turn-bolt rifle: empty_action='cycle', lift_deg=70.
    grab          where the left fist takes the magazine (default: bottom centre of the mag).
    Bones: root, left_arm, `mag_bone`; empty also `bolt_bone` (+ the arm of `hand` for 'cycle')."""
    rig = Rig.of(m)
    s = ARM_STYLES[style] if style else rig.style
    pistol = (style or rig.style_name) == "pistol"
    if empty_action is None:
        empty_action = "cycle" if bolt_action else "bolt_release"
    if empty_action not in ("bolt_release", "cycle"):
        raise ValueError("empty_action must be 'bolt_release' or 'cycle'")
    cycle = empty and empty_action == "cycle"
    base = 2.2 if not empty else 3.0 if cycle else 2.6
    c = Clip(m, "reload_empty" if empty else "reload", length, base)
    c.need(mag_bone, LEFT_ARM)
    lh = rig.left_hand

    cant, cp = _reload_cant(c, s, base)             # gun cant; the slap lands at 1.6
    g, arm_r = _mag_swap(c, m, s, lh, mag_bone, grab)

    # ---- after the swap -------------------------------------------------------------------
    if cycle:
        bolt = bolt_bone or "bolt_handle"
        hc = HandCycle(hand, bolt, knob, 70.0 if lift_deg is None and bolt_action else (lift_deg or 0.0),
                       3.0 if pull is None else pull, tuple(also))
        if hand == "left":                          # straight from the magazine to the handle
            hc.write(c, rig, 1.68, 1.0, start_pos=g, start_rot=arm_r)
        else:
            c.pos(LEFT_ARM, {2.0: (ZERO, IO)})
            c.rot(LEFT_ARM, {2.0: (ZERO, IO)})
            hc.write(c, rig, 1.72, 1.0)
    elif empty:
        bolt = bolt_bone or ("slide" if pistol else "bolt")
        c.need(bolt)
        back = (0, 0, 2.0 if pull is None else pull)
        # locked open: opens in the first 0.05 s (the rest-pose rule), held until the release
        c.pos(bolt, {0: ZERO, 0.05: (back, OUT), 2.05: back, 2.1: (ZERO, IN)})
        c.pos(ROOT, {2.1: (_add(cp, (0, 0, -0.3)), OUT), 2.2: (cp, IO)})
        if s.catch:
            if catch is None:
                p = m.bones[mag_bone].pivot
                catch = (-1.5, p[1] - 0.5, p[2] - 1.0)
            k = _sub(catch, lh)
            c.pos(LEFT_ARM, {1.95: (_add(k, (-2, 0, 0)), IO), 2.05: (k, IN), 2.12: (_add(k, (-1, 0, 0)), OUT),
                             2.45: (ZERO, IO)})
            c.rot(LEFT_ARM, {1.95: ((-10, 0, 0), IO), 2.12: (-10, 0, 0), 2.45: (ZERO, IO)})
        else:
            c.pos(LEFT_ARM, {2.0: (ZERO, IO)})
            c.rot(LEFT_ARM, {2.0: (ZERO, IO)})
    else:
        c.pos(LEFT_ARM, {2.0: (ZERO, IO)})
        c.rot(LEFT_ARM, {2.0: (ZERO, IO)})
    return c.done()


def reload_hk_slap(m: Model, mag_bone: str = "magazine", length: float | None = None, *,
                   handle: str = "charging_handle", knob: Vec, pull: float = 2.5, lock_deg: float = 40.0,
                   also: tuple = ("bolt",), grab: Vec | None = None, name: str = "reload_empty") -> dict:
    """`reload_empty` for the roller-delayed H&K guns (MP5, G3, HK33...): the "HK slap".
    Base length 3.0 s; the magazine swap in the middle is the one `reload_mag` plays.

      0.00-0.30  the gun cants, the left hand goes from the handguard to the cocking handle
      0.30-0.48  it pulls the handle `pull` units back (the `also` bones, e.g. the bolt, follow)
      0.48-0.58  and turns it `lock_deg` up into the notch of the cocking tube: LOCKED at 0.58
      0.58-2.26  magazine swap (`_mag_swap`): old one out, fresh one in, slap on the base at 2.18
      2.26-2.48  the hand comes up above the locked handle
      2.48-2.54  SLAP: the palm knocks the handle down out of the notch (at 2.54) ...
      2.54-2.60  ... and the spring throws handle and bolt home (at 2.60)
      2.60-3.00  hand back on the handguard, gun levels

    handle    non-reciprocating cocking handle on the LEFT; its pivot is on the cocking-tube axis,
              it turns about Z and slides +Z.          knob  centre of its knob at rest.
    Returns the key times in REAL seconds (already scaled to `length`) for the docs / sound
    timing: locked, mag_out, mag_in, mag_slap, slap, home.
    Bones: root, left_arm, `mag_bone`, `handle`; optional `also`."""
    rig = Rig.of(m)
    s, lh = rig.style, rig.left_hand
    base, t_swap = 3.0, 0.58
    c = Clip(m, name, length, base)
    c.need(mag_bone, LEFT_ARM, handle)
    piv = m.bones[handle].pivot
    radius = math.hypot(knob[0] - piv[0], knob[1] - piv[1])

    def hand(angle: float, back: float = 0.0, lift: float = 0.0) -> Vec:
        """arm offset with the fist on the knob (outside / below it), `lift` units above that."""
        r = math.radians(angle)
        kx, ky = piv[0] - radius * math.cos(r), piv[1] + radius * math.sin(r)
        return (kx - 0.9 * math.cos(r) - 0.3 * math.sin(r) - lh[0],
                ky - 0.3 * math.cos(r) - 0.2 * math.sin(r) + lift - lh[1], knob[2] - lh[2] + back)

    t_grab, t_back, t_lock = 0.30, 0.48, t_swap
    t_up, t_slap, t_home = 2.48, 2.54, 2.60
    cant, cp = _reload_cant(c, s, base, t_swap)
    # the gun is tugged back by the pull and knocked down by the slap
    c.pos(ROOT, {t_grab: cp, t_back: (_add(cp, (0, 0, 0.3)), OUT), t_lock + 0.1: (cp, IO),
                 t_up: cp, t_slap: (_add(cp, (0, -0.35, 0)), IN), t_home: (_add(cp, (0, -0.2, -0.25)), OUT)})
    c.rot(ROOT, {t_up: cant, t_slap: (_pitch_roll(s.cant[0] - 2.0, s.cant[1] - 3.0), IN)})

    # ---- left hand: handguard -> handle, pull, lock ... swap ... wind up, slap, home ---------
    locked = hand(lock_deg, pull)
    grip_r = (6, 0, 0)
    c.pos(LEFT_ARM, {0: ZERO, t_grab: (hand(0), IO), t_back: (hand(0, pull), OUT), t_lock: (locked, OUT)})
    c.rot(LEFT_ARM, {0: ZERO, t_grab: (grip_r, IO), t_lock: grip_r})
    _mag_swap(c, m, s, lh, mag_bone, grab, t_swap, start_pos=locked, start_rot=grip_r)
    above = hand(lock_deg, pull, lift=2.0)
    through = _add(hand(0, pull, lift=-0.6), (0, 0, -0.5))          # the palm follows through
    c.pos(LEFT_ARM, {t_up: (above, IO), t_slap: (hand(0, pull, lift=0.3), IN), t_home: (through, OUT),
                     base - 0.1: (ZERO, IO)})
    c.rot(LEFT_ARM, {t_up: ((-12, 0, 0), IO), t_slap: ((4, 0, 0), IN), base - 0.1: (ZERO, IO)})

    # ---- handle (and bolt): back, up into the notch, knocked down, thrown home -----------------
    up = (0, 0, -lock_deg)                                           # a LEFT-side knob lifts with -Z
    c.rot(handle, {t_back: ZERO, t_lock: (up, OUT), t_slap - 0.03: up, t_slap: (ZERO, IN)})
    slide = {t_grab: ZERO, t_back: ((0, 0, pull), OUT), t_slap: (0, 0, pull), t_home: (ZERO, IN)}
    for bone in (handle, *also):
        c.pos(bone, slide)
    c.done()
    marks = dict(locked=t_lock, mag_out=t_swap + 0.5, mag_in=t_swap + 1.45, mag_slap=t_swap + 1.6,
                 slap=t_slap, home=t_home)
    return {k: round(c.t(v), 3) for k, v in marks.items()}


def reload_belt(m: Model, cover_bone: str = "feed_cover", box_bone: str = "ammo_box",
                length: float | None = None, empty: bool = False, *, open_deg: float = 65.0,
                charge_bone: str | None = "charging_handle", charge_pull: float = 3.0,
                charge_hand: str = "right", charge_also: tuple = ("bolt",), knob: Vec | None = None):
    """`reload` / `reload_empty` for a belt-fed MG: the left hand flips the feed cover up, swaps
    the ammo box (down and out of view, a fresh one back up), lays the belt on the feed tray,
    then slams the cover down. empty=True adds `charge_hand` racking the charging handle.

    cover_bone   feed cover; its PIVOT is the hinge at the REAR of the cover, it opens
                 `open_deg` degrees (front end up).
    box_bone     ammo box / drum (make the visible belt a child of it so it travels along).
    length       seconds; None = base length (4.2 / empty: 5.0).
    charge_bone  handle pulled `charge_pull` units straight back when empty (HandCycle with
                 lift 0; `charge_also` bones slide with it; `knob` = its knob centre).
    Bones: root, left_arm, `cover_bone`, `box_bone`; empty also `charge_bone` + arm of `charge_hand`."""
    rig = Rig.of(m)
    s, lh = rig.style, rig.left_hand
    base = 5.0 if empty else 4.2
    c = Clip(m, "reload_empty" if empty else "reload", length, base)
    c.need(cover_bone, box_bone, LEFT_ARM)
    cant, cp = _pitch_roll(*s.cant), s.cant_pos
    level = base - 0.4

    # ---- where the hand goes (absolute points, then arm offsets) ---------------------------
    clo, chi = part_bounds(m, cover_bone)
    hinge = m.bones[cover_bone].pivot
    latch = (0.0, chi[1] + 0.75, clo[2] + 1.0)                       # on top of the cover's front end
    open_rot = (open_deg * PITCH_UP, 0, 0)
    latch_open = _rotate(latch, hinge, open_rot)
    tray = (-0.25, clo[1] + 0.75, (clo[2] + chi[2]) / 2)             # feed tray under the cover
    blo, bhi = part_bounds(m, box_bone)
    box = ((blo[0] + bhi[0]) / 2, blo[1] + 0.5, (blo[2] + bhi[2]) / 2)   # fist round the bottom of the box
    o = lambda p: _sub(p, lh)
    L, LO, TR, BX = o(latch), o(latch_open), o(tray), o(box)
    clear = _add(BX, (-0.5, -2, 0))
    away = _add(BX, (-2, -7, 1.5))

    c.rot(ROOT, {0: ZERO, 0.35: (cant, IO), 3.45: cant, 3.55: (_pitch_roll(s.cant[0] - 1.5, s.cant[1]), OUT),
                 3.7: (cant, IO), level: cant, base: (ZERO, IO)})
    c.pos(ROOT, {0: ZERO, 0.35: (cp, IO), 2.35: cp, 2.45: (_add(cp, (0, 0.25, 0)), OUT), 2.55: (cp, IO),
                 3.45: cp, 3.55: (_add(cp, (0, -0.3, 0)), OUT), 3.7: (cp, IO), level: cp, base: (ZERO, IO)})
    c.rot(cover_bone, {0.35: ZERO, 0.7: (open_rot, OUT), 3.4: open_rot, 3.55: (ZERO, IN)})
    c.pos(LEFT_ARM, {0: ZERO, 0.35: (L, IO), 0.7: (LO, OUT), 1.1: (BX, IO), 1.2: BX,
                     1.35: (clear, IN), 1.6: (away, IN), 1.9: away, 2.25: (clear, OUT), 2.45: (BX, OUT),
                     2.55: BX, 2.95: (TR, IO), 3.02: (_add(TR, (0, 0.6, 0)), OUT), 3.1: (TR, IN),
                     3.4: (LO, IO), 3.55: (L, IN), 3.65: L, 3.95: (ZERO, IO)})
    arm_r = (s.swap_arm_rx, 0, 0)
    c.rot(LEFT_ARM, {0: ZERO, 0.35: ZERO, 1.1: (arm_r, IO), 2.55: arm_r, 2.95: (ZERO, IO)})
    rel = lambda v: _sub(v, BX)
    c.pos(box_bone, {1.2: ZERO, 1.35: (rel(clear), IN), 1.6: (rel(away), IN), 1.9: rel(away),
                     2.25: (rel(clear), OUT), 2.45: (ZERO, OUT)})
    c.rot(box_bone, {1.35: ZERO, 1.6: ((0, 0, -12), IN), 1.9: (0, 0, -12), 2.25: (ZERO, OUT)})
    if empty:
        hc = HandCycle(charge_hand, charge_bone, knob, 0.0, charge_pull, tuple(charge_also))
        if charge_hand == "left":
            hc.write(c, rig, 3.65, 0.9, start_pos=L)
        else:
            hc.write(c, rig, 3.62, 0.9)
    return c.done()


def reload_shell(m: Model, length: float | None = None, empty: bool = False, *,
                 pump_bone: str | None = "pump", bolt_bone: str | None = "bolt", travel: float = 3.0,
                 port: Vec | None = None, eject: Vec | None = None, shell_bone: str | None = "shell",
                 chamber_shell_bone: str | None = "shell_chamber"):
    """Tube-magazine reload, one shell at a time.

    empty=False -> `reload`: ONE shell (the game loops it per shell): the left hand drops out of
        view, comes up under the receiver, thumbs a shell forward into the loading port and
        returns to the fore-end. Base length 0.8 s.
    empty=True  -> `reload_empty`: the action is opened (pump racked back, or `bolt_bone` held
        back when the gun has no `pump_bone`), the left hand drops one shell into the EJECTION
        port from below, then the action is closed (pump forward / bolt released) to chamber it.
        Base length 1.7 s.

    port     loading port (default: locator `load_port`, else (0, 3.5, -3)): bottom of the
             receiver in front of the trigger guard.
    eject    ejection port (default: locator `shell_eject`).
    travel   pump / bolt travel in units.
    shell_bone / chamber_shell_bone  OPTIONAL shell bones: model them at rest INSIDE the gun
             (hidden in the loading port / in the ejection port); during the animation they
             ride in the hand and end at rest.
    Bones: root, left_arm; empty: `pump_bone` or `bolt_bone`."""
    rig = Rig.of(m)
    lh = rig.left_hand
    o = lambda p: _sub(p, lh)
    if not empty:
        c = Clip(m, "reload", length, 0.8)
        c.need(LEFT_ARM)
        if port is None:
            port = m.bones["load_port"].pivot if "load_port" in m.bones else (0.0, 3.5, -3.0)
        at = o((port[0], port[1] - 1.25, port[2] + 0.75))           # fist under the port
        push = _add(at, (0, 0.5, -1.25))                            # shell thumbed up and forward
        away = _add(at, (-1.5, -6, 3))
        c.pos(LEFT_ARM, {0: ZERO, 0.24: (away, IN), 0.3: away, 0.5: (at, OUT), 0.6: (push, IO),
                         0.64: push, 0.8: (ZERO, IO)})
        c.rot(LEFT_ARM, {0: ZERO, 0.24: ((-12, 0, 0), IO), 0.6: (-12, 0, 0), 0.8: (ZERO, IO)})
        rel = lambda v: _sub(v, push)
        c.pos(shell_bone, {0: ZERO, 0.27: (rel(away), "step"), 0.3: rel(away), 0.5: (rel(at), OUT),
                           0.6: (ZERO, IO)})
        c.rot(ROOT, {0: ZERO, 0.24: (_pitch_roll(2, 8), IO), 0.5: _pitch_roll(2, 8),
                     0.6: (_pitch_roll(2.6, 8), OUT), 0.8: (ZERO, IO)})
        c.pos(ROOT, {0: ZERO, 0.24: ((0, 0.3, 0.2), IO), 0.5: (0, 0.3, 0.2), 0.6: ((0, 0.35, 0), OUT),
                     0.8: (ZERO, IO)})
        return c.done()

    c = Clip(m, "reload_empty", length, 1.7)
    c.need(LEFT_ARM)
    if eject is None:
        if "shell_eject" not in m.bones:
            raise ValueError(f"{m.name}: reload_shell(empty=True) needs eject= or a shell_eject locator")
        eject = m.bones["shell_eject"].pivot
    pump = pump_bone if c.has(pump_bone) else None
    if pump is None:
        c.need(bolt_bone)
    back = (0, 0, travel)
    under = o((eject[0] + 0.5, eject[1] - 2.25, eject[2] + 0.5))    # fist below the port, right side
    drop = _add(under, (-0.3, 1.0, 0))                              # shell tipped into the port
    away = _add(under, (-2, -6, 3))
    if pump:  # the hand racks the pump open, fetches a shell, comes back to the (open) pump, closes it
        c.pos(LEFT_ARM, {0: ZERO, 0.08: ZERO, 0.24: (back, OUT), 0.3: back, 0.55: (away, IN), 0.62: away,
                         0.9: (under, OUT), 1.0: (drop, IO), 1.06: drop, 1.3: (back, IO), 1.36: back,
                         1.48: (ZERO, IN)})
        act = {0: ZERO, 0.08: ZERO, 0.24: (back, OUT), 1.36: back, 1.48: (ZERO, IN)}
        c.pos(pump, act)
        c.pos(bolt_bone, act)
        t_close = 1.48
    else:     # bolt locked open; after the shell is in, the bolt is released and snaps home
        c.pos(LEFT_ARM, {0: ZERO, 0.3: (away, IN), 0.4: away, 0.75: (under, OUT), 0.88: (drop, IO),
                         0.95: drop, 1.3: (ZERO, IO)})
        c.pos(bolt_bone, {0: ZERO, 0.05: (back, OUT), 1.34: back, 1.4: (ZERO, IN)})
        t_close = 1.4
    t_in = 1.0 if pump else 0.88
    c.rot(LEFT_ARM, {0: ZERO, 0.3: ZERO, t_in - 0.1: ((-15, 0, 0), IO), t_in + 0.06: (-15, 0, 0),
                     t_close - 0.12: (ZERO, IO)})
    rel = lambda v: _sub(v, drop)
    c.pos(chamber_shell_bone, {0: ZERO, t_in - 0.4: (rel(away), "step"), t_in - 0.38: rel(away),
                               t_in - 0.1: (rel(under), OUT), t_in: (ZERO, IO)})
    tilt = _pitch_roll(4, -14)                                      # ejection port rolled toward the hand
    c.rot(ROOT, {0: ZERO, 0.3: (tilt, IO), t_close - 0.12: tilt, t_close: (_pitch_roll(3, -6), IN),
                 t_close + 0.2: (ZERO, IO)})
    c.pos(ROOT, {0: ZERO, 0.3: ((0, 0.4, 0.3), IO), t_close - 0.12: (0, 0.4, 0.3),
                 t_close: ((0, 0.2, -0.3), IN), t_close + 0.2: (ZERO, IO)})
    return c.done()


def reload_revolver(m: Model, length: float | None = None, empty: bool = False, *, rod_tip: Vec,
                    seat: float, clear: float, open_deg: float = 95.0, eject_travel: float = 1.0,
                    cylinder_bone: str = "cylinder", crane_bone: str = "crane",
                    ejector_bone: str = "ejector", rounds_bone: str | None = "rounds",
                    speedloader_bone: str | None = "speedloader", latch_bone: str | None = "latch",
                    tip: tuple[float, float] = (32.0, -14.0)):
    """`reload` / `reload_empty` (the same choreography under both names, `empty` only picks the
    name) for a swing-out revolver loaded with a speedloader. Base length 2.6 s:

      0.00-0.20  left hand comes up to the latch (left of the frame), thumbs it back
      0.20-0.40  the cylinder swings out to the LEFT on the crane (`open_deg` about the crane pivot)
      0.22-0.50  muzzle tips up `tip[0]` degrees
      0.60-0.70  left palm drives the ejector rod `eject_travel` back; 0.70-0.95 the cases fall
      0.85-1.10  muzzle tips down (`tip[1]`), the left hand drops out of view
      1.20-1.50  it comes back with the speedloader behind the cylinder
      1.50-1.68  rounds pushed into the chambers; 1.70-1.80 knob twisted to release
      1.82-2.10  speedloader pulled off and dropped
      2.12-2.24  left hand shuts the cylinder; 2.24-2.60 back on the grip, gun levels

    crane_bone        parent of `cylinder_bone`; pivot = the crane hinge (an axis parallel to the
                      bore, below the cylinder); + Z rotation swings the cylinder to -X.
    ejector_bone      star + rod, child of the cylinder; `rod_tip` = front end of the rod at rest.
    rounds_bone       OPTIONAL six cartridges, child of the cylinder, modelled at rest in the
                      chambers: they are the cases that fall and then the fresh rounds.
    speedloader_bone  OPTIONAL, child of the cylinder, modelled at rest HIDDEN inside the cylinder,
                      its pivot at the knob; `seat` = +Z travel from there to seated on the rear
                      face, `clear` = extra +Z the fresh rounds start behind the chambers
                      (a cartridge length). It teleports ("step" key) between hidden and the hand
                      while the hand is out of view.
    latch_bone        OPTIONAL cylinder latch (slides 0.2 back), pivot on the latch.
    Bones: root, left_arm, `crane_bone`, `cylinder_bone`, `ejector_bone`."""
    rig = Rig.of(m)
    lh = rig.left_hand
    c = Clip(m, "reload_empty" if empty else "reload", length, 2.6)
    c.need(crane_bone, cylinder_bone, ejector_bone, LEFT_ARM)
    hinge = m.bones[crane_bone].pivot
    axis = m.bones[cylinder_bone].pivot
    opened = (0.0, 0.0, open_deg)
    W = lambda p: _rotate(p, hinge, opened)                   # a cylinder point with the crane open
    local = lambda v: _rotate(v, ZERO, (0.0, 0.0, -open_deg))  # gun-space vector -> open-crane space
    o = lambda p: _sub(p, lh)

    # ---- gun: tip up to dump the cases, tip down to load ------------------------------------
    up_r, up_p = _pitch_roll(tip[0], -10), (0.0, 0.6, 0.6)
    dn_r, dn_p = _pitch_roll(tip[1], 12), (0.0, 1.0, 0.4)
    c.rot(ROOT, {0: ZERO, 0.22: (_pitch_roll(4, -3), IO), 0.5: (up_r, IO), 0.85: up_r, 1.1: (dn_r, IO),
                 2.12: dn_r, 2.24: (_pitch_roll(tip[1] + 3, 6), IN), 2.6: (ZERO, IO)})
    c.pos(ROOT, {0: ZERO, 0.22: ((0, 0.2, 0.1), IO), 0.5: (up_p, IO), 0.63: up_p,
                 0.7: (_add(up_p, (0, 0, 0.25)), IN), 0.85: (up_p, IO), 1.1: (dn_p, IO), 1.6: dn_p,
                 1.68: (_add(dn_p, (0, -0.1, -0.2)), IN), 1.8: (dn_p, IO), 2.12: dn_p,
                 2.24: (_add(dn_p, (0.25, 0, 0)), IN), 2.6: (ZERO, IO)})

    # ---- action ------------------------------------------------------------------------------
    c.pos(latch_bone, {0.14: ZERO, 0.2: ((0, 0, 0.2), OUT), 0.36: (0, 0, 0.2), 0.42: (ZERO, IO)})
    c.rot(crane_bone, {0.2: ZERO, 0.4: (opened, OUT), 2.12: opened, 2.24: (ZERO, IN)})
    back = (0.0, 0.0, eject_travel)
    c.pos(ejector_bone, {0.6: ZERO, 0.7: (back, IN), 0.78: back, 0.88: (ZERO, OUT)})

    # ---- left hand ---------------------------------------------------------------------------
    grip = (-0.5, -1.5, 2.0)                                  # fist below / behind the speedloader knob
    knob = m.bones[speedloader_bone].pivot if c.has(speedloader_bone) else axis
    a0, a1 = (0.0, 0.0, seat + clear), (0.0, 0.0, seat)       # speedloader: behind the cylinder / seated
    latch = m.bones[latch_bone].pivot if c.has(latch_bone) else (axis[0] - 0.75, axis[1], axis[2] + 1.5)
    h_latch = o(_add(latch, (-1.3, -1.1, 1.4)))
    h_cradle = o(_add(W(axis), (-1.0, -1.5, 1.2)))            # under the opened cylinder
    h_rod0 = o(_add(W(rod_tip), (-0.6, -0.9, 0.5)))           # palm in front of the ejector rod
    h_rod1 = _add(h_rod0, back)
    h_away = (-2.5, -7.0, 3.0)
    h_load0 = o(_add(W(_add(knob, a0)), grip))
    h_load1 = o(_add(W(_add(knob, a1)), grip))
    h_off = _add(h_load1, (-0.3, -0.4, 1.0))                  # speedloader pulled off
    h_shut0 = o(_add(W(axis), (-1.6, -0.4, 0.9)))             # palm on the open cylinder
    h_shut1 = o(_add(axis, (-2.1, -0.9, 0.9)))                # ... and on the closed one
    c.pos(LEFT_ARM, {0: ZERO, 0.18: (h_latch, IO), 0.22: h_latch, 0.42: (h_cradle, OUT),
                     0.6: (h_rod0, IO), 0.7: (h_rod1, IN), 0.78: h_rod1, 0.88: (h_rod0, OUT),
                     1.1: (h_away, IN), 1.2: h_away, 1.5: (h_load0, OUT), 1.68: (h_load1, IO),
                     1.82: h_load1, 1.92: (h_off, OUT), 2.1: (h_shut0, IO), 2.24: (h_shut1, IN),
                     2.3: h_shut1, 2.6: (ZERO, IO)})
    c.rot(LEFT_ARM, {0: ZERO, 0.18: ((-8, 0, 0), IO), 2.3: (-8, 0, 0), 2.6: (ZERO, IO)})

    # ---- cases out, fresh rounds in ------------------------------------------------------------
    fallen = _add(back, local((-1.5, -12.0, 5.0)))            # down and back (the muzzle is up)
    ride = local(_sub(h_away, h_load0))                       # with the hand while it is out of view
    c.pos(rounds_bone, {0.6: ZERO, 0.7: (back, IN), 0.95: (fallen, IN), 1.18: fallen,
                        1.2: (_add((0, 0, clear), ride), "step"), 1.5: ((0, 0, clear), OUT),
                        1.68: (ZERO, IO)})
    dropped = _add(a1, local((-1.0, -12.0, -1.0)))
    c.pos(speedloader_bone, {1.18: ZERO, 1.2: (_add(a0, ride), "step"), 1.5: (a0, OUT), 1.68: (a1, IO),
                             1.82: a1, 1.92: (_add(a1, (0, 0, 1.0)), OUT), 2.1: (dropped, IN),
                             2.12: (ZERO, "step")})
    c.rot(speedloader_bone, {1.7: ZERO, 1.8: ((0, 0, -45), IO), 2.1: (0, 0, -45), 2.12: (ZERO, "step")})
    return c.done()
