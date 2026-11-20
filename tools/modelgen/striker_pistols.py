"""Polymer striker-fired service pistols (Glock 17 / Glock 19 / SIG M17) built from ONE class.

Why this module exists: the three pistols are the same machine with different numbers and a few
different details. So the shared shape lives here once and a gun file is only data::

    # guns/glock19.py
    from striker_pistols import GLOCK_PALETTE, Glock, PistolSpec
    SPEC = PistolSpec(id="glock19", slide_front_z=-3.0, grip_bottom_y=3.0, ...)
    PISTOL = Glock(SPEC)
    SIGHT_Y = PISTOL.sight_y
    def model(): return PISTOL.build()

STRUCTURE
  * Palette / Handling / PistolSpec  - frozen dataclasses: everything that differs per gun
  * StrikerPistol                    - the builder. `build()` is a template method that calls one
                                       small method per part (frame, slide, barrel, sights, ...)
  * Glock, SigP320                   - subclasses that override only the parts that look different

HOW TO MAKE A CHANGE
  * every pistol of this family (e.g. thicker slide, other arm hold)  -> StrikerPistol
  * every Glock (e.g. new sights)                                     -> Glock
  * one gun (length, grip height, timings, colours)                   -> its PistolSpec in guns/
  * every gun in the mod (arms, recoil feel, reload choreography)     -> templates.py

LAYOUT (model units, 1 unit ~ 3.35 cm; muzzle -Z, +X right, bore y 5.5)
  y 5.0-6.0  slide            z slide_front_z .. 2.5
  y 4.5-5.0  frame            y 4.0-4.5 accessory rail under the dust cover (bone `handguard`)
  y 3.0-4.5  trigger guard    z -2 .. 0, trigger at z ~ -0.75
  grip       raked box hanging from (0, 5, 1) down to grip_bottom_y, 2 deep; the magazine sits
             inside it with only the base plate showing.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import attachments as att
import templates as T
from armorygen import Bone, Model, _rotate

Vec = tuple[float, float, float]
RGB = tuple[int, int, int]


# ======================================================================================
# DATA: what differs from gun to gun
# ======================================================================================
@dataclass(frozen=True)
class Palette:
    """Colours of one pistol: name -> (rgb, noise, edge darkening). Registered on the model as
    materials `p_<name>`, so a builder says `self.mat("slide")`."""
    slide: RGB
    frame: RGB
    controls: RGB = (30, 30, 32)       # levers, mag catch, trigger
    barrel: RGB = (84, 86, 92)         # chamber hood seen through the ejection port
    magazine: RGB = (40, 40, 43)       # magazine body
    baseplate: RGB = (30, 30, 32)      # magazine floor plate
    sights: RGB = (24, 24, 26)
    accent: RGB = (40, 40, 43)         # rail slots, cover plates

    def register(self, m: Model) -> None:
        for name in self.__dataclass_fields__:
            m.material(f"p_{name}", getattr(self, name), 5, 0.76)


@dataclass(frozen=True)
class Handling:
    """Animation numbers (seconds / units / degrees). `fire` must stay below the shot interval."""
    reload: float = 1.8
    reload_empty: float = 2.3
    fire: float = 0.1
    recoil: float = 0.5
    flip: float = 5.0
    slide_travel: float = 1.5


@dataclass(frozen=True)
class PistolSpec:
    """One pistol. Lengths come from the real gun: mm / 33.5 = units."""
    id: str
    palette: Palette
    slide_front_z: float = -3.5        # muzzle end of the slide (rear end is SLIDE_REAR_Z)
    grip_bottom_y: float = 2.5         # bottom of the grip frame (the base plate hangs below)
    grip_rake: float = 20.0            # degrees the grip leans back
    handling: Handling = field(default_factory=Handling)
    attachments: tuple[str, ...] = ("suppressor_9mm", "laser")
    previews: dict[str, tuple[str, ...]] = field(default_factory=dict)   # name -> attachment ids


# ======================================================================================
# BUILDER
# ======================================================================================
class StrikerPistol:
    """Builds a Model from a PistolSpec. Subclass and override a part method to change a look."""

    # ---- family-wide layout: change here and every pistol follows --------------------
    BORE_Y = T.BORE_Y
    HALF_W = 0.75                      # half width of slide and frame
    SLIDE_Y, SLIDE_H = 5.0, 1.0
    SLIDE_REAR_Z = 2.5
    FRAME_Y, RAIL_Y = 4.5, 4.0         # bottom of the frame strip / bottom of the rail
    GUARD_Z = (-2.0, 0.0)              # trigger guard front / rear (= front of the grip)
    GUARD_Y = 3.0                      # bottom of the trigger guard
    GRIP_DEPTH = 2.0
    GRIP_PIVOT = (0.0, 5.0, 1.0)       # the grip (and the magazine in it) is raked about this
    MAG_PIVOT = (0.0, 4.5, 1.0)        # `magazine` bone: top centre of the magazine well
    PORT_Z = (-1.25, -0.25)            # ejection port, top right of the slide
    SIGHT_H = 0.5                      # sight height above the slide top (chunky on purpose)
    REAR_SIGHT_LEN = 0.5
    BASEPLATE_H = 0.25
    ARM_STYLE = "pistol"
    HAND_DROP = 0.75                   # right fist centre below the frame strip (high grip; was 1.5)
    HAND_DZ = -0.25                    # ...and this far ahead of the grip's centre line
    OPTIC_MOUNT_Z: float | None = None  # set by subclasses whose slide has an optic cut
    # the shared laser box is 1 x 2 x 3 and grows to +X from its mount point; this offset from
    # the rail's bottom centre hangs it centred under the dust cover, its rear at the guard
    LIGHT_MOUNT_OFFSET = (-0.5, -1.0, -1.5)

    def __init__(self, spec: PistolSpec):
        self.spec = spec
        self.m: Model | None = None

    # ---- derived numbers ---------------------------------------------------------------
    @property
    def front_z(self) -> float:
        return self.spec.slide_front_z

    @property
    def slide_top(self) -> float:
        return self.SLIDE_Y + self.SLIDE_H

    @property
    def sight_deck_y(self) -> float:
        """Surface the sights stand on."""
        return self.slide_top

    @property
    def sight_y(self) -> float:
        """The sight line: front post tip = top of the rear notch ears."""
        return self.sight_deck_y + self.SIGHT_H

    @property
    def rear_sight_z(self) -> float:
        return self.SLIDE_REAR_Z - self.REAR_SIGHT_LEN / 2

    @property
    def sight_line(self) -> Vec:
        return (0.0, self.sight_y, self.rear_sight_z)

    @property
    def muzzle(self) -> Vec:
        """Bare muzzle on the bore axis (the barrel stands a hair proud of the slide)."""
        return (0.0, self.BORE_Y, self.front_z - 0.1)

    @property
    def grip_rot(self) -> Vec:
        return (-self.spec.grip_rake, 0.0, 0.0)

    @property
    def grip_len(self) -> float:
        """Length of the grip along its own (raked) axis, from GRIP_PIVOT down."""
        return (self.GRIP_PIVOT[1] - self.spec.grip_bottom_y) / math.cos(math.radians(self.spec.grip_rake))

    def on_grip(self, along: float, dz: float = 0.0) -> Vec:
        """World point `along` units down the grip axis, `dz` behind its centre line."""
        p = (0.0, self.GRIP_PIVOT[1] - along, self.GRIP_PIVOT[2] + dz)
        return _rotate(p, self.GRIP_PIVOT, self.grip_rot)

    @property
    def right_hand(self) -> Vec:
        """Centre of the fist (the arm bone pivot): on the grip axis, HAND_DROP below the frame
        and HAND_DZ ahead of the axis, i.e. high on the grip so the fist wraps it like a real
        high grip (in-game QA: at HAND_DROP 1.5 the fist hung under the grip). The fist is 3
        units (twice the gun's width) and its top edge, sleeve layer included, rises about 2.3
        above the pivot; at this height it is flush with the slide top, so the slide and the
        sight picture stay clear (a pivot at y 4.25 put skin across the sight line).
        Gives (0, 3.75, 1.25) for the Glocks, (0, 3.75, 1.0) for the M17."""
        along = (self.GRIP_PIVOT[1] - (self.FRAME_Y - self.HAND_DROP)) / math.cos(math.radians(self.spec.grip_rake))
        return tuple(round(v * 4) / 4 for v in self.on_grip(along, self.HAND_DZ))

    @property
    def rail_mount(self) -> Vec:
        """Bottom face of the accessory rail, at the trigger guard."""
        return (0.0, self.RAIL_Y, self.GUARD_Z[0])

    def mat(self, name: str) -> str:
        return f"p_{name}"

    # ---- template method ---------------------------------------------------------------
    def build(self) -> Model:
        self.m = m = Model(self.spec.id, density=1)
        self.spec.palette.register(m)
        self.frame(m.bone("receiver", pivot=(0, 5, 0)))
        self.dust_cover(m.bone("handguard", pivot=(0, self.FRAME_Y, self.GUARD_Z[0])))
        self.grip(m.bone("grip", pivot=self.GRIP_PIVOT))
        self.barrel(m.bone("barrel", pivot=(0, self.BORE_Y, -1)))
        slide = m.bone("slide", pivot=(0, self.BORE_Y, 0))
        self.slide(slide)
        self.sights(slide)
        self.trigger(m.bone("trigger", pivot=(0, self.FRAME_Y, -0.5)))
        self.magazine(T.default_part(m, "magazine", self.MAG_PIVOT))
        self.controls(m.bone("controls", parent="receiver", pivot=(0, self.FRAME_Y, 0)))
        T.arms(m, self.right_hand, None, self.ARM_STYLE)
        self.mounts()
        T.attach(m, *self.spec.attachments)
        T.locators(m, muzzle_flash=(0, self.BORE_Y, self.front_z - 0.25),
                   shell_eject=(self.HALF_W + 0.1, self.slide_top - 0.25, sum(self.PORT_Z) / 2),
                   sight_line=self.sight_line)
        self.animations()
        self.previews()
        return m

    # ---- parts (override in subclasses) ------------------------------------------------
    def frame(self, b: Bone) -> None:
        w, (gf, gr) = self.HALF_W, self.GUARD_Z
        b.box((-w, self.FRAME_Y, gf), (2 * w, 0.5, self.SLIDE_REAR_Z - gf), self.mat("frame"))   # frame strip
        b.box((-0.5, self.GUARD_Y, gf), (1, 0.5, gr - gf + 1), self.mat("frame"))                # guard: bottom bar (runs into the raked grip)
        b.box((-0.5, self.GUARD_Y + 0.5, gf), (1, self.FRAME_Y - self.GUARD_Y - 0.5, 0.5), self.mat("frame"))  # front bar

    def dust_cover(self, b: Bone) -> None:
        """Frame in front of the trigger guard with the accessory rail (bone `handguard`)."""
        w, z0 = self.HALF_W, self.front_z
        length = self.GUARD_Z[0] - z0
        b.box((-w, self.FRAME_Y, z0), (2 * w, 0.5, length), self.mat("frame"))
        self.rail(b, z0, length)

    def rail(self, b: Bone, z0: float, length: float) -> None:
        b.box((-self.HALF_W, self.RAIL_Y, z0), (2 * self.HALF_W, 0.5, length), self.mat("frame"), pattern="rails")

    def grip(self, b: Bone) -> None:
        w, (px, py, pz) = self.HALF_W, self.GRIP_PIVOT
        L, d = self.grip_len, self.GRIP_DEPTH
        b.box((-w, py - L, pz - d / 2), (2 * w, L, d), self.mat("frame"), pattern="stipple",
              rot=self.grip_rot, pivot=self.GRIP_PIVOT, inflate=-0.05)

    def barrel(self, b: Bone) -> None:
        z0, pz1 = self.front_z - 0.1, self.PORT_Z[1]
        b.box((-0.4, 5.1, z0), (0.8, 0.8, pz1 - z0), self.mat("barrel"))                       # barrel
        b.box((0, 5.2, self.PORT_Z[0] + 0.05), (0.7, 0.75, pz1 - self.PORT_Z[0] - 0.1), self.mat("barrel"))  # chamber hood
        b.box((-0.2, 4.95, z0 + 0.1), (0.4, 0.2, 0.5), self.mat("controls"))                   # recoil spring guide rod

    def slide(self, b: Bone) -> None:
        w, y, h, z0 = self.HALF_W, self.SLIDE_Y, self.SLIDE_H, self.front_z
        (p0, p1), mat = self.PORT_Z, self.mat("slide")
        b.box((-w, y, z0), (2 * w, h, p0 - z0), mat)                       # in front of the port
        b.box((-w, y, p1), (2 * w, h, self.SLIDE_REAR_Z - p1), mat)        # behind the port
        b.box((-w, y, p0), (w, h, p1 - p0), mat)                           # left of the port
        b.box((0, y, p0), (w, h / 2, p1 - p0), mat)                        # below the port
        b.box((w, y + 0.5, p1), (0.06, 0.2, 0.6), self.mat("barrel"))      # extractor
        self.serrations(b)

    def serrations(self, b: Bone) -> None:
        """Front and rear cocking serrations: plates a hair proud of the slide flats."""
        w, y = self.HALF_W + 0.05, self.SLIDE_Y + 0.1
        size = (2 * w, self.SLIDE_H - 0.2, 1.0)
        b.box((-w, y, self.SLIDE_REAR_Z - 1.5), size, self.mat("slide"), pattern="lines")
        b.box((-w, y, self.front_z + 0.25), size, self.mat("slide"), pattern="lines")

    def rear_sight_bone(self, slide: Bone) -> Bone:
        """Bone the rear sight is built in (the slide, unless an optic replaces the sight)."""
        return slide

    def sights(self, slide: Bone) -> None:
        y, h, mat = self.sight_deck_y, self.SIGHT_H, self.mat("sights")
        fz = self.front_z + 0.25
        slide.box((-0.25, y, fz), (0.5, h, 0.5), mat)                                  # front post: TOP = sight_y
        rs, rz, n = self.rear_sight_bone(slide), self.SLIDE_REAR_Z - self.REAR_SIGHT_LEN, self.REAR_SIGHT_LEN
        rs.box((-0.75, y, rz), (1.5, h / 2, n), mat)                                   # rear sight body
        rs.box((-0.75, y + h / 2, rz), (0.5, h / 2, n), mat)                           # left ear
        rs.box((0.25, y + h / 2, rz), (0.5, h / 2, n), mat)                            # right ear (0.5 notch)
        self.sight_marks(slide, rs, fz + 0.5, rz + n)

    def sight_marks(self, front: Bone, rear: Bone, front_face_z: float, rear_face_z: float) -> None:
        """White / tritium markings on the faces toward the shooter."""

    def trigger(self, b: Bone) -> None:
        b.box((-0.25, 3.6, -1.0), (0.5, 0.9, 0.5), self.mat("controls"))

    def magazine(self, b: Bone) -> None:
        """Magazine body inside the grip + the base plate under it (same rake as the grip)."""
        w, (px, py, pz) = self.HALF_W, self.GRIP_PIVOT
        L, d, bp = self.grip_len, self.GRIP_DEPTH, self.BASEPLATE_H
        kw = dict(rot=self.grip_rot, pivot=self.GRIP_PIVOT)
        b.box((-0.5, py - L, pz - d / 2 + 0.25), (1, L - 0.5, d - 0.5), self.mat("magazine"), pattern="lines", **kw)
        b.box((-w, py - L - bp, pz - d / 2 - 0.1), (2 * w, bp, d + 0.1), self.mat("baseplate"), **kw)

    def controls(self, b: Bone) -> None:
        """Levers and buttons (bone `controls`, child of `receiver`)."""

    # ---- mounts / animations / previews ---------------------------------------------------
    def mounts(self) -> None:
        """Muzzle (threaded barrel) and the rail under the dust cover; the rail takes the shared
        `side` slot devices. An optic rides ON THE SLIDE, so its mount is parented to `slide`
        (templates.mounts would hang it off the receiver and it would not reciprocate)."""
        T.mounts(self.m, muzzle=self.muzzle, side=T._add(self.rail_mount, self.LIGHT_MOUNT_OFFSET))
        if self.OPTIC_MOUNT_Z is not None:
            self.m.bone(T.MOUNTS["optic"][0], parent="slide", pivot=(0, self.sight_deck_y, self.OPTIC_MOUNT_Z))

    def animations(self) -> None:
        m, h = self.m, self.spec.handling
        T.idle(m)
        T.fire_pistol(m, travel=h.slide_travel, length=h.fire, recoil=h.recoil, flip=h.flip)
        T.reload_mag(m, length=h.reload)
        T.reload_mag(m, length=h.reload_empty, empty=True, pull=h.slide_travel)

    def previews(self) -> None:
        """`bare` first (the animation strips use it); `*_noarms` shows the gun itself."""
        m, arms = self.m, {T.RIGHT_ARM, T.LEFT_ARM}
        cfg = {"bare": att.loadout(m), "noarms": att.loadout(m) | arms}
        for name, ids in self.spec.previews.items():
            cfg[name] = att.loadout(m, *ids) | arms
        m.preview_configs = cfg


# ======================================================================================
# GLOCK (17 / 19): flat-topped boxy slide, one-slot rail, white dot + white U sights
# ======================================================================================
GLOCK_PALETTE = Palette(slide=(48, 49, 54), frame=(33, 33, 35), barrel=(92, 94, 100))


class Glock(StrikerPistol):
    def rail(self, b: Bone, z0: float, length: float) -> None:
        w = self.HALF_W
        b.box((-w, self.RAIL_Y, z0), (2 * w, 0.5, length), self.mat("frame"))                 # Glock rail: plain
        b.box((-w - 0.05, self.RAIL_Y, z0 + length / 2 - 0.15), (2 * w + 0.1, 0.3, 0.3), self.mat("accent"))  # ONE cross-slot

    def grip(self, b: Bone) -> None:
        super().grip(b)
        w, (px, py, pz) = self.HALF_W, self.GRIP_PIVOT
        L, d = self.grip_len, self.GRIP_DEPTH
        b.box((-w, py - L, pz - d / 2), (2 * w, 0.25, d), self.mat("frame"),
              rot=self.grip_rot, pivot=self.GRIP_PIVOT, inflate=0.08)                         # flared magwell lip

    def sight_marks(self, front, rear, front_face_z, rear_face_z) -> None:
        y = self.sight_deck_y
        front.box((-0.125, y + 0.2, front_face_z), (0.25, 0.25, 0.03), "white")               # white dot
        rear.box((-0.3, y + 0.2, rear_face_z), (0.6, 0.05, 0.03), "white")                    # U: floor
        for x in (-0.3, 0.25):                                                                # U: sides
            rear.box((x, y + 0.25, rear_face_z), (0.05, 0.25, 0.03), "white")

    def trigger(self, b: Bone) -> None:
        super().trigger(b)
        b.box((-0.1, 3.65, -1.08), (0.2, 0.6, 0.1), self.mat("accent"))                       # trigger safety blade

    def controls(self, b: Bone) -> None:
        w, c = self.HALF_W, self.mat("controls")
        b.box((-w - 0.1, 4.65, 0.5), (0.1, 0.2, 0.8), c)                                      # slide stop lever (left)
        b.box((-w - 0.06, 4.6, -1.3), (2 * w + 0.12, 0.2, 0.3), c)                            # slide lock tabs
        b.box((-w - 0.1, 3.9, 0.05), (0.15, 0.4, 0.4), c)                                     # magazine catch (left)
        b.box((-0.5, 5.1, self.SLIDE_REAR_Z - 0.02), (1, 0.8, 0.04), c)                       # slide cover plate


# ======================================================================================
# SIG P320 family (M17): taller slide with a bevelled top, optic cut, Picatinny rail, safety
# ======================================================================================
M17_PALETTE = Palette(slide=(150, 124, 88), frame=(168, 142, 104), barrel=(40, 40, 44),
                      magazine=(58, 58, 62), accent=(134, 110, 78))


class SigP320(StrikerPistol):
    RIB_H = 0.25                       # narrower top rib = the bevelled slide top
    OPTIC_MOUNT_Z = 1.0                # middle of the optic cut, just ahead of the rear sight
    PLATE_Z = (0.5, 2.5)               # optic cover plate; it carries the rear sight

    @property
    def sight_deck_y(self) -> float:
        return self.slide_top + self.RIB_H

    def slide(self, b: Bone) -> None:
        super().slide(b)
        y, (p0, p1), mat = self.slide_top, self.PORT_Z, self.mat("slide")
        b.box((-0.5, y, self.front_z), (1, self.RIB_H, p0 - self.front_z), mat)               # rib, front
        b.box((-0.5, y, p1), (1, self.RIB_H, self.PLATE_Z[0] - p1), mat)                      # rib, behind the port
        b.box((-0.5, y, self.PLATE_Z[0]), (1, self.RIB_H - 0.1, self.PLATE_Z[1] - self.PLATE_Z[0]), mat)  # optic cut floor

    def rear_sight_bone(self, slide: Bone) -> Bone:
        """`rear_sight` is hidden by the game while an optic is fitted - like the real M17, where
        the rear sight comes off with the cover plate."""
        rs = self.m.bone("rear_sight", parent="slide", pivot=(0, self.sight_deck_y, self.rear_sight_z))
        z0, z1 = self.PLATE_Z
        rs.box((-0.5, self.sight_deck_y - 0.1, z0), (1, 0.1, z1 - z0), self.mat("accent"))    # optic cover plate
        return rs

    def sight_marks(self, front, rear, front_face_z, rear_face_z) -> None:
        y = self.sight_deck_y
        front.box((-0.125, y + 0.2, front_face_z), (0.25, 0.25, 0.03), "white")               # SIGLITE front dot
        for x in (-0.625, 0.375):                                                             # two rear dots
            rear.box((x, y + 0.2, rear_face_z), (0.25, 0.25, 0.03), "white")

    def frame(self, b: Bone) -> None:
        super().frame(b)
        b.box((-0.5, self.FRAME_Y - 0.25, self.SLIDE_REAR_Z), (1, 0.5, 0.4), self.mat("frame"))  # beavertail

    def controls(self, b: Bone) -> None:
        w, c = self.HALF_W, self.mat("controls")
        b.box((-w - 0.2, 4.6, 1.4), (2 * w + 0.4, 0.3, 0.9), c)                               # ambidextrous thumb safety
        b.box((-w - 0.12, 4.6, -1.8), (0.12, 0.3, 0.9), c)                                    # takedown lever (left)
        b.box((-w - 0.08, 4.75, 0.2), (2 * w + 0.16, 0.2, 0.8), c)                            # slide catch levers
        b.box((-w - 0.1, 3.9, 0.05), (2 * w + 0.2, 0.4, 0.4), c)                              # magazine catch
