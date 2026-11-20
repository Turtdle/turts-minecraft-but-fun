"""Shared building blocks for the 12-gauge family (guns/m590.py, guns/benelli_m4.py, guns/m26mass.py).

Same idea as templates.py, one level down: geometry that every shotgun repeats lives here ONCE,
so a family-wide change (shell colour, port depth, how an aperture sight is drawn) is one edit
and a rebuild.

  * ShotgunPalette   - the family's materials (register once per model)
  * PortedReceiver   - a receiver block with a REAL ejection-port hole on the right side, so a
                       bolt that slides back uncovers a dark chamber instead of poking through
  * ShellBones       - the optional `shell` / `shell_chamber` bones templates.reload_shell moves
  * aperture_sight / post_sight - ghost-ring rear and blade front built round a sight line

Conventions are the mod's (see templates.py): units, muzzle toward -Z, +X = right, bore y 5.5.
"""
from __future__ import annotations

from dataclasses import dataclass

import templates as T
from armorygen import Bone, Model

Vec = tuple[float, float, float]

SHELL_LEN = 2.0      # 12 ga 2 3/4 in shell ~ 70 mm
SHELL_D = 0.7        # drawn slightly under 1 unit so it hides inside a 1-unit bolt


class ShotgunPalette:
    """Materials shared by the shotguns. `ShotgunPalette.register(m)` once per model."""
    PARKERIZED = "parkerized"      # dark grey phosphate steel (M590A1 metal)
    MATTE_BLACK = "matte_black"    # anodised / phosphate black
    FURNITURE = "sg_furniture"     # black synthetic stock / fore-end
    PORT = "port_dark"             # inside of an open action
    SHELL = "shell_red"            # plastic hull
    _DEFS = {
        PARKERIZED: ((58, 60, 64), 6, 0.74),
        MATTE_BLACK: ((40, 40, 44), 5, 0.74),
        FURNITURE: ((30, 30, 32), 4, 0.80),
        PORT: ((16, 16, 18), 2, 0.90),
        SHELL: ((158, 34, 30), 5, 0.80),
    }

    @classmethod
    def register(cls, m: Model) -> None:
        for name, (rgb, noise, edge) in cls._DEFS.items():
            m.material(name, rgb, noise, edge)


@dataclass(frozen=True)
class PortedReceiver:
    """A 2-wide receiver block z0..z1, y0..y1 with an ejection port (a real hole) in the right
    wall: port_z = (front, rear), port_y = (bottom, top). `add(bone)` writes 6 boxes."""
    z0: float
    z1: float
    y0: float
    y1: float
    port_z: tuple[float, float]
    port_y: tuple[float, float] = (5.0, 6.0)
    mat: str = ShotgunPalette.MATTE_BLACK
    half_w: float = 1.0

    @property
    def port_center(self) -> Vec:
        """Outer face of the port on the bore axis: use it for the `shell_eject` locator."""
        return (self.half_w + 0.1, sum(self.port_y) / 2, sum(self.port_z) / 2)

    def add(self, bone: Bone) -> Bone:
        w, (pz0, pz1), (py0, py1) = self.half_w, self.port_z, self.port_y
        length = self.z1 - self.z0
        bone.box((-w, self.y0, self.z0), (w, self.y1 - self.y0, length), self.mat)      # left half
        bone.box((0, self.y0, self.z0), (w, py0 - self.y0, length), self.mat)           # below the port
        bone.box((0, py1, self.z0), (w, self.y1 - py1, length), self.mat)               # above the port
        bone.box((0, py0, self.z0), (w, py1 - py0, pz0 - self.z0), self.mat)            # in front of it
        bone.box((0, py0, pz1), (w, py1 - py0, self.z1 - pz1), self.mat)                # behind it
        bone.box((0, py0, pz0), (0.05, py1 - py0, pz1 - pz0), ShotgunPalette.PORT)      # dark chamber wall
        return bone

    def bolt_box(self, travel_clear: float = 0.1) -> tuple[Vec, Vec]:
        """(origin, size) of a bolt that fills the port; `travel_clear` keeps its outer face
        inside the wall so it never z-fights when it slides back into the receiver."""
        (pz0, pz1), (py0, py1) = self.port_z, self.port_y
        return (0.0, py0, pz0), (self.half_w - travel_clear, py1 - py0, pz1 - pz0)


@dataclass(frozen=True)
class ShellBones:
    """The two optional shell bones of templates.reload_shell, modelled at rest INSIDE the gun:
      `shell`          above the loading port (pivot = the port, so it doubles as the game's
                       `shell` locator)
      `shell_chamber`  in the ejection port, hidden inside the closed bolt
    """
    load_port: Vec                 # bottom of the receiver, centre of the loading port
    eject_port: Vec | None = None  # PortedReceiver.port_center; None = no chamber shell

    def add(self, m: Model) -> None:
        x, y, z = self.load_port
        self._shell(m.bone("shell", pivot=self.load_port), (x, y + 0.25 + SHELL_D / 2, z))
        if self.eject_port is not None:
            ex, ey, ez = self.eject_port
            self._shell(m.bone("shell_chamber", pivot=(ex - 0.6, ey, ez)), (ex - 0.6, ey, ez))

    @staticmethod
    def _shell(bone: Bone, c: Vec) -> None:
        d, half = SHELL_D, SHELL_LEN / 2
        bone.box((c[0] - d / 2, c[1] - d / 2, c[2] - half), (d, d, SHELL_LEN - 0.5), ShotgunPalette.SHELL)
        bone.box((c[0] - d / 2, c[1] - d / 2, c[2] + half - 0.5), (d, d, 0.5), "brass")   # head, toward the rear


def aperture_sight(bone: Bone, base_y: float, sight_y: float, z: float, mat: str, depth: float = 1.0) -> Bone:
    """Ghost ring / flip-up aperture: a 2-wide frame whose 1 x 1 hole is centred on `sight_y`.
    base_y = surface it stands on, z = front of its base. The frame sits in the middle of the
    base; `sight_z(z, depth)` gives the z for the `sight_line` locator."""
    zf = sight_z(z, depth) - 0.25
    bone.box((-1, base_y, z), (2, sight_y - 0.5 - base_y, depth), mat)       # base = bottom of the frame
    bone.box((-1, sight_y - 0.5, zf), (0.5, 1, 0.5), mat)                    # left ear
    bone.box((0.5, sight_y - 0.5, zf), (0.5, 1, 0.5), mat)                   # right ear
    bone.box((-1, sight_y + 0.5, zf), (2, 0.5, 0.5), mat)                    # top bar
    return bone


def sight_z(z: float, depth: float = 1.0) -> float:
    """z of the aperture plane of `aperture_sight(..., z, depth)`."""
    return z + depth / 2


def post_sight(bone: Bone, base_y: float, sight_y: float, z: float, mat: str, base_h: float = 0.5,
               wings: bool = False) -> Bone:
    """Front blade: a 1-long base on `base_y` and a 0.5 post whose TOP is exactly `sight_y`.
    wings=True adds the two protective ears of a flip-up / A2-style front sight."""
    top = base_y + base_h
    bone.box((-1 if wings else -0.5, base_y, z), (2 if wings else 1, base_h, 1), mat)
    bone.box((-0.25, top, z + 0.25), (0.5, sight_y - top, 0.5), mat)
    if wings:
        bone.box((-1, top, z), (0.5, sight_y + 0.5 - top, 1), mat)
        bone.box((0.5, top, z), (0.5, sight_y + 0.5 - top, 1), mat)
    return bone


def tube_reloads(m: Model, shells: ShellBones, reload_s: float, empty_reload_s: float, *,
                 pump_bone: str | None, bolt_bone: str | None = "bolt", travel: float = 3.0) -> None:
    """`reload` (one shell, looped by the game) and `reload_empty` for a tube-fed gun, wired to
    the shell bones above. pump_bone=None = a semi-auto: the bolt is held open and released."""
    common = dict(pump_bone=pump_bone, bolt_bone=bolt_bone, travel=travel)
    T.reload_shell(m, length=reload_s, port=shells.load_port, **common)
    T.reload_shell(m, length=empty_reload_s, empty=True, **common)
