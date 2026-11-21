"""
armorygen - a tiny "OpenSCAD for Minecraft" toolkit.

You describe a gun as code (bones + boxes + materials). armorygen then:
  * auto-UV-packs every face into a texture atlas,
  * procedurally paints the texture from materials (noise, edge shading, patterns),
  * writes a GeckoLib / Bedrock ``.geo.json`` (opens in Blockbench too),
  * renders a multi-view preview PNG so you (or an AI) can check the shape.

Coordinates are Blockbench/Bedrock model units (16 units = 1 block), Y up.
Convention for guns in this mod:
  * the muzzle points toward -Z (Blockbench "north"),
  * the gun is centred on X = 0,
  * the pistol grip / trigger area sits near the origin.
"""
from __future__ import annotations

import colorsys
import hashlib
import json
import math
import random
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

Vec = tuple[float, float, float]

# name -> (base RGB, noise amplitude, edge darkening factor)
MATERIALS: dict[str, tuple[tuple[int, int, int], int, float]] = {
    "black_metal": ((38, 38, 42), 6, 0.72),
    "gunmetal": ((62, 64, 70), 7, 0.72),
    "polymer": ((44, 44, 46), 4, 0.80),
    "steel": ((128, 130, 136), 8, 0.70),
    "bright_steel": ((172, 174, 180), 8, 0.70),
    "brass": ((184, 146, 62), 10, 0.70),
    "copper": ((176, 104, 64), 10, 0.70),
    "fde": ((168, 142, 104), 7, 0.80),
    "od_green": ((86, 92, 62), 7, 0.80),
    "wood": ((122, 74, 40), 12, 0.75),
    "wood_dark": ((84, 50, 28), 10, 0.75),
    "rubber": ((28, 28, 28), 3, 0.85),
    "glass": ((70, 110, 130), 6, 0.80),
    "red": ((200, 40, 40), 6, 0.80),
    "orange": ((230, 120, 30), 6, 0.80),
    "white": ((220, 220, 215), 5, 0.80),
}

# Patterns that can be painted on top of a material
PATTERNS = ("none", "rails", "stipple", "slots", "lines", "knurl")

FACES = ("north", "south", "east", "west", "up", "down")

# Anti z-fighting (Model.fix_coplanar_overlaps): a detail face lying in the plane of another
# same-facing face is pushed out by ZFIGHT_EPS model units (16 units = 1 block).
ZFIGHT_EPS = 0.02
COPLANAR_TOL = 1e-4      # faces closer than this (along the normal) count as one plane
MIN_OVERLAP_AREA = 1e-4  # square units; smaller overlaps (edge / corner contact) are ignored
ARM_BONES = ("right_arm", "left_arm")  # player-skin hands: drawn in their own pass, not checked
# default part -> the attachment slot that hides it while fitted (GunRenderer.applyVisibility)
DEFAULT_PARTS = {"rear_sight": "optic", "muzzle_default": "muzzle", "stock_default": "stock",
                 "magazine_default": "magazine"}


def _r(x: float) -> float:
    return round(float(x), 4) + 0.0  # + 0.0 turns -0.0 into 0.0


def _flip_pos(v) -> list:
    """Authoring -> Bedrock file space for a point / offset (X mirrored)."""
    return [_r(-v[0]), _r(v[1]), _r(v[2])]


def _flip_rot(v) -> list:
    """Authoring -> Bedrock file space for a rotation (X and Y negated)."""
    return [_r(-v[0]), _r(-v[1]), _r(v[2])]


@dataclass
class Cube:
    origin: Vec
    size: Vec
    material: str
    pattern: str = "none"
    rotation: Vec = (0.0, 0.0, 0.0)
    pivot: Vec | None = None
    inflate: float = 0.0
    uv: dict = field(default_factory=dict)  # filled by packer
    # ("right"|"left", "base"|"sleeve"[, "hand"]) -> player-skin UVs; "hand" = only the bare-hand
    # end of the arm texture (fingers / thumb boxes), not the whole arm squeezed onto the box
    skin: tuple | None = None
    # face -> extra thickness grown on that side only (anti z-fighting nudges, see
    # `Model.fix_coplanar_overlaps`). Geometry only: UV packing / painting use the nominal `size`,
    # so the texture stays pixel-identical (per-face UVs just map onto a face 0.02 units longer).
    grow: dict = field(default_factory=dict)

    def extent(self) -> tuple[Vec, Vec]:
        """Effective (min, max) corners in the cube's own frame: origin/size plus `grow`
        (not `inflate`, which is exported separately)."""
        g = self.grow
        lo = (self.origin[0] - g.get("west", 0.0), self.origin[1] - g.get("down", 0.0),
              self.origin[2] - g.get("north", 0.0))
        hi = (self.origin[0] + self.size[0] + g.get("east", 0.0),
              self.origin[1] + self.size[1] + g.get("up", 0.0),
              self.origin[2] + self.size[2] + g.get("south", 0.0))
        return lo, hi

    def face_size(self, face: str) -> tuple[float, float]:
        """NOMINAL face size (no `grow`): drives UV packing and painting."""
        sx, sy, sz = self.size
        return {
            "north": (sx, sy), "south": (sx, sy),
            "east": (sz, sy), "west": (sz, sy),
            "up": (sx, sz), "down": (sx, sz),
        }[face]


@dataclass
class Bone:
    model: "Model"
    name: str
    parent: str | None
    pivot: Vec
    rotation: Vec
    cubes: list[Cube] = field(default_factory=list)

    # ---- primitives -------------------------------------------------
    def box(self, origin: Vec, size: Vec, mat: str = "black_metal", *, pattern: str = "none",
            rot: Vec | None = None, pivot: Vec | None = None, inflate: float = 0.0) -> "Bone":
        """Axis-aligned box from its min corner `origin` with `size` (like OpenSCAD translate+cube)."""
        self.model._check_mat(mat)
        if pattern not in PATTERNS:
            raise ValueError(f"unknown pattern {pattern!r}; use one of {PATTERNS}")
        if min(size) < 0:
            raise ValueError(f"negative size {size} in bone {self.name}")
        c = Cube(tuple(map(float, origin)), tuple(map(float, size)), mat, pattern,
                 tuple(map(float, rot)) if rot else (0.0, 0.0, 0.0),
                 tuple(map(float, pivot)) if pivot else None, inflate)
        if rot and c.pivot is None:  # default pivot: cube centre
            c.pivot = tuple(o + s / 2 for o, s in zip(c.origin, c.size))
        self.cubes.append(c)
        return self

    def skin_box(self, origin: Vec, size: Vec, side: str = "right", sleeve: bool = True,
                 rot: Vec | None = None, pivot: Vec | None = None, part: str = "arm") -> "Bone":
        """A first-person arm box textured with the *player's skin* (box UV of the skin's
        right/left arm, 4x12x4 px). Model it VERTICAL like a player arm: X = width, Y = length,
        Z = depth; the hand/fist is the BOTTOM (min Y) end, the shoulder the top. Orient it by
        rotating the bone. `sleeve` adds the skin's outer (jacket) layer slightly inflated.
        Only rendered in first person; put these in bones named `right_arm` / `left_arm`.
        `part="hand"` maps only the hand end of the skin's arm (the bottom 4 px) onto the box:
        for finger / thumb boxes of any size and orientation (use with sleeve=False)."""
        if side not in ("right", "left"):
            raise ValueError("side must be 'right' or 'left'")
        if part not in ("arm", "hand"):
            raise ValueError("part must be 'arm' or 'hand'")
        # vanilla sleeve = 0.25px on a 4px-wide arm -> 1/16 of the arm THICKNESS (not its length)
        layers = [("base", 0.0)] + ([("sleeve", 0.0625 * min(size[0], size[2]))] if sleeve else [])
        for layer, inf in layers:
            c = Cube(tuple(map(float, origin)), tuple(map(float, size)), "__skin", "none",
                     tuple(map(float, rot)) if rot else (0.0, 0.0, 0.0),
                     tuple(map(float, pivot)) if pivot else None, inf,
                     skin=(side, layer) if part == "arm" else (side, layer, part))
            if rot and c.pivot is None:
                c.pivot = tuple(o + s / 2 for o, s in zip(c.origin, c.size))
            self.cubes.append(c)
        return self

    def cbox(self, center: Vec, size: Vec, mat: str = "black_metal", **kw) -> "Bone":
        """Box positioned by its centre (like OpenSCAD cube(center=true))."""
        origin = tuple(c - s / 2 for c, s in zip(center, size))
        return self.box(origin, size, mat, **kw)

    def span(self, a: Vec, b: Vec, mat: str = "black_metal", **kw) -> "Bone":
        """Box between two opposite corners, in any order."""
        lo = tuple(min(p, q) for p, q in zip(a, b))
        hi = tuple(max(p, q) for p, q in zip(a, b))
        return self.box(lo, tuple(h - l for h, l in zip(hi, lo)), mat, **kw)

    def cyl(self, center: Vec, diameter: float, length: float, mat: str = "black_metal",
            axis: str = "z", sides: int = 8, **kw) -> "Bone":
        """Cylinder approximation: sides=4 -> one box, 8 -> two boxes (one rotated 45 deg),
        12 -> three boxes (0/30/60 deg); sides="round" -> a stepped pixel circle: three boxes kept square to
        the axis (the rectangles that fit in the circle at 30, 45 and 60 degrees), so the end reads as a circle
        with no corners poking out the way the turned boxes make a star. `center` is the centre of the cylinder."""
        if sides == "round":
            r = diameter / 2 * 1.1  # the steps cut in a little: grown so it looks as wide as the other kinds
            for k, ang in enumerate((30.0, 45.0, 60.0)):
                a = math.radians(ang)
                cw, ch = 2 * r * math.cos(a), 2 * r * math.sin(a)
                ln = length + (0.0, 0.04, 0.02)[k]  # the ends a hair apart, so they don't flicker into each other
                size = {"x": (ln, ch, cw), "y": (cw, ln, ch), "z": (cw, ch, ln)}[axis]
                self.cbox(center, size, mat, **kw)
            return self
        n = {4: 1, 8: 2, 12: 3}.get(sides)
        if n is None:
            raise ValueError('sides must be 4, 8, 12 or "round"')
        w = diameter if n == 1 else diameter * (0.86 if n == 2 else 0.92)
        size = {"x": (length, w, w), "y": (w, length, w), "z": (w, w, length)}[axis]
        for i in range(n):
            ang = i * (90.0 / n)
            rot = {"x": (ang, 0, 0), "y": (0, ang, 0), "z": (0, 0, ang)}[axis] if ang else None
            self.cbox(center, size, mat, rot=rot, pivot=center if rot else None, **kw)
        return self

    def wedge(self, start: Vec, end: Vec, width: float, height: float, mat: str = "black_metal",
              **kw) -> "Bone":
        """A box stretched from `start` to `end` in the Y/Z plane (centred on X), rotated about X
        to follow the slope. Handy for slanted grips, stock tubes, magazine curves."""
        (x0, y0, z0), (x1, y1, z1) = start, end
        dy, dz = y1 - y0, z1 - z0
        length = math.hypot(dy, dz)
        ang = math.degrees(math.atan2(dy, -dz))  # 0 deg = pointing -Z
        cx, cy, cz = (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2
        return self.cbox((cx, cy, cz), (width, height, length), mat,
                         rot=(ang, 0, 0) if abs(ang) > 1e-6 else None, pivot=(cx, cy, cz), **kw)


class Model:
    def __init__(self, name: str, density: int = 1):
        """`density` = texels per model unit (1 = vanilla-like chunky pixels, 2 = finer)."""
        self.name = name
        self.density = density
        self.bones: dict[str, Bone] = {}
        self.materials = dict(MATERIALS)
        self.animations: dict[str, "Animation"] = {}
        # bone -> (slot, variant) for parts never shown together with another variant of the
        # same slot (attachments register here, see attachments._Att; DEFAULT_PARTS adds the
        # default parts an attachment replaces). Used by the z-fighting check.
        self.exclusive: dict[str, tuple[str, str]] = {}
        self.bone("root", parent=None)

    def anim(self, name: str, length: float, loop: bool = False) -> "Animation":
        """Create a keyframed animation (Bedrock/GeckoLib format)."""
        a = Animation(self, name, length, loop)
        self.animations[name] = a
        return a

    def material(self, name: str, rgb: tuple[int, int, int], noise: int = 6, edge: float = 0.75):
        self.materials[name] = (rgb, noise, edge)

    def _check_mat(self, mat: str):
        if mat not in self.materials:
            raise ValueError(f"unknown material {mat!r}; known: {sorted(self.materials)}")

    def bone(self, name: str, parent: str | None = "root", pivot: Vec = (0, 0, 0),
             rotation: Vec = (0, 0, 0)) -> Bone:
        if name in self.bones:
            return self.bones[name]
        if parent is not None and parent not in self.bones:
            raise ValueError(f"parent bone {parent!r} for {name!r} does not exist yet")
        b = Bone(self, name, parent, tuple(map(float, pivot)), tuple(map(float, rotation)))
        self.bones[name] = b
        return b

    def locator(self, name: str, pos: Vec, parent: str = "root") -> Bone:
        """Empty bone used as a point (muzzle flash, shell ejection, sight line...)."""
        return self.bone(name, parent=parent, pivot=pos)

    # ---- texture ----------------------------------------------------
    def _pack(self) -> tuple[int, int]:
        rects = []
        for b in self.bones.values():
            for ci, c in enumerate(b.cubes):
                if c.skin:
                    continue  # uses the player's skin texture, not the atlas
                for f in FACES:
                    fw, fh = c.face_size(f)
                    pw = max(1, math.ceil(fw * self.density - 1e-6))
                    ph = max(1, math.ceil(fh * self.density - 1e-6))
                    rects.append((pw, ph, c, f))
        rects.sort(key=lambda r: (-r[1], -r[0]))
        for tw, th in [(32, 32), (64, 32), (64, 64), (128, 64), (128, 128), (256, 128),
                       (256, 256), (512, 256), (512, 512), (1024, 1024)]:
            x = y = row_h = 0
            placed = []
            ok = True
            for pw, ph, c, f in rects:
                if pw > tw:
                    ok = False
                    break
                if x + pw > tw:
                    x, y, row_h = 0, y + row_h, 0
                if y + ph > th:
                    ok = False
                    break
                placed.append((x, y, pw, ph, c, f))
                x += pw
                row_h = max(row_h, ph)
            if ok:
                for px, py, pw, ph, c, f in placed:
                    c.uv[f] = (px, py, pw, ph)
                return tw, th
        raise ValueError("model too big to pack into 1024x1024")

    def _paint(self, tw: int, th: int) -> Image.Image:
        img = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
        px = img.load()
        for b in self.bones.values():
            for ci, c in enumerate(b.cubes):
                if c.skin:
                    continue
                (base, noise, edge) = self.materials[c.material]
                seed = int(hashlib.md5(f"{self.name}/{b.name}/{ci}".encode()).hexdigest()[:8], 16)
                rng = random.Random(seed)
                for f, (u, v, w, h) in c.uv.items():
                    face_light = {"up": 1.10, "down": 0.80}.get(f, 1.0)
                    for j in range(h):
                        for i in range(w):
                            n = rng.randint(-noise, noise)
                            k = face_light
                            on_edge = w >= 3 and h >= 3 and (i in (0, w - 1) or j in (0, h - 1))
                            if on_edge:
                                k *= edge
                            k *= _pattern_factor(c.pattern, i, j, w, h, rng)
                            col = tuple(max(0, min(255, int(ch * k) + n)) for ch in base)
                            px[u + i, v + j] = (*col, 255)
        return img

    # ---- export -----------------------------------------------------
    def to_geo(self, tw: int, th: int) -> dict:
        bones = []
        for b in self.bones.values():
            d: dict = {"name": b.name}
            if b.parent:
                d["parent"] = b.parent
            # Authoring space (what the previews show) -> Bedrock file space. GeckoLib mirrors X
            # and negates X/Y rotations when it loads a .geo.json / .animation.json, so we
            # pre-apply the same flip here; in game the model then matches the previews exactly
            # (+X = right side of the gun, rotations as in `_rotate`).
            d["pivot"] = _flip_pos(b.pivot)
            if any(b.rotation):
                d["rotation"] = _flip_rot(b.rotation)
            cubes = []
            for c in b.cubes:
                lo, hi = c.extent()  # includes the anti z-fighting `grow`
                cd: dict = {"origin": [_r(-hi[0]), _r(lo[1]), _r(lo[2])],
                            "size": [_r(h - l) for l, h in zip(lo, hi)]}
                if c.inflate:
                    cd["inflate"] = _r(c.inflate)
                if any(c.rotation):
                    cd["pivot"] = _flip_pos(c.pivot)
                    cd["rotation"] = _flip_rot(c.rotation)
                if c.skin:
                    cd["uv"] = _skin_uv(c.skin, tw, th)
                else:
                    cd["uv"] = {f: {"uv": [u, v], "uv_size": [w, h]} for f, (u, v, w, h) in c.uv.items()}
                cubes.append(cd)
            if cubes:
                d["cubes"] = cubes
            bones.append(d)
        lo, hi = self.bounds()
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{self.name}",
                    "texture_width": tw, "texture_height": th,
                    "visible_bounds_width": _r(max(hi[0] - lo[0], hi[2] - lo[2]) / 16 + 1),
                    "visible_bounds_height": _r((hi[1] - lo[1]) / 16 + 1),
                    "visible_bounds_offset": [0, _r((hi[1] + lo[1]) / 32), 0],
                },
                "bones": bones,
            }],
        }

    # ---- geometry helpers for preview --------------------------------
    def _bone_chain(self, b: Bone) -> list[Bone]:
        chain = []
        while b is not None:
            chain.append(b)
            b = self.bones.get(b.parent) if b.parent else None
        return chain  # leaf first

    def hidden_closure(self, hidden) -> set:
        """Names of every bone that is in `hidden` or has a hidden ancestor."""
        hidden = set(hidden or ())
        unknown = hidden - set(self.bones)
        if unknown:
            raise ValueError(f"hidden bones that do not exist: {sorted(unknown)}")
        return {b.name for b in self.bones.values()
                if any(c.name in hidden for c in self._bone_chain(b))} if hidden else set()

    def world_faces(self, pose: dict | None = None, hidden=None):
        """Yield (bone, cube, face_name, [4 world points]) for preview rendering.
        `pose` = {bone: (position_offset, rotation_offset[, scale])} from an animation frame.
        `hidden` = bone names to skip (with all their children), e.g. unattached attachments."""
        pose = pose or {}
        skip = self.hidden_closure(hidden)
        for b in self.bones.values():
            if b.name in skip:
                continue
            chain = self._bone_chain(b)
            for c in b.cubes:
                lo, hi = c.extent()
                (x0, y0, z0) = (o - c.inflate for o in lo)
                (x1, y1, z1) = (o + c.inflate for o in hi)
                quads = {
                    "north": [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)],
                    "south": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
                    "west": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
                    "east": [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)],
                    "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
                    "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
                }
                for f, pts in quads.items():
                    out = []
                    for p in pts:
                        if any(c.rotation):
                            p = _rotate(p, c.pivot, c.rotation)
                        for bb in chain:
                            apos, arot, *asc = pose.get(bb.name, ((0, 0, 0), (0, 0, 0)))
                            if asc and asc[0] != (1.0, 1.0, 1.0):
                                p = tuple(v + (q - v) * k for q, v, k in zip(p, bb.pivot, asc[0]))
                            rot = tuple(r + a for r, a in zip(bb.rotation, arot))
                            if any(rot):
                                p = _rotate(p, bb.pivot, rot)
                            if any(apos):
                                p = (p[0] + apos[0], p[1] + apos[1], p[2] + apos[2])
                        out.append(p)
                    yield b, c, f, out

    def bounds(self, hidden=None) -> tuple[Vec, Vec]:
        pts = [p for _, _, _, q in self.world_faces(hidden=hidden) for p in q]
        if not pts:
            return (0, 0, 0), (0, 0, 0)
        lo = tuple(min(p[i] for p in pts) for i in range(3))
        hi = tuple(max(p[i] for p in pts) for i in range(3))
        return lo, hi

    # ---- z-fighting (coplanar faces) ------------------------------------
    def _visibility_tags(self, b: Bone):
        """(is_arm, {slot: variant}) from the bone and its ancestors."""
        tags, arm = {}, False
        for bb in self._bone_chain(b):
            if bb.name in ARM_BONES:
                arm = True
            slot = self.exclusive.get(bb.name)
            if slot is None and bb.name in DEFAULT_PARTS:
                slot = (DEFAULT_PARTS[bb.name], "default")
            if slot is not None:
                tags.setdefault(slot[0], slot[1])
        return arm, tags

    def find_coplanar_overlaps(self, hidden=None) -> list[dict]:
        """Pairs of box faces that z-fight at the rest pose: same facing direction, same plane
        (within COPLANAR_TOL) and overlapping with positive area. Works on world-space quads, so
        rotated cubes / bones are handled generically; faces on the same bone count too. Pairs
        that are never visible together are skipped: arm bones (player skin), and two variants
        of one slot (two optics, an attachment and the default part it replaces; see
        `exclusive` / DEFAULT_PARTS). Opposite-facing touching faces are fine (each hides inside
        the other box) and ignored.
        Returns [{"a": face, "b": face, "overlap": area}], face = (bone, cube, face_name, area),
        "a" being the earlier-defined cube."""
        order, info = {}, {}
        for b in self.bones.values():
            info[b.name] = self._visibility_tags(b)
            for c in b.cubes:
                order[id(c)] = len(order)
        clusters: list[tuple[Vec, list]] = []  # (unit normal, [(plane offset, face, quad)])
        for b, c, f, pts in self.world_faces(hidden=hidden):
            if c.skin or info[b.name][0]:
                continue
            n = _cross(tuple(pts[1][i] - pts[0][i] for i in range(3)),
                       tuple(pts[2][i] - pts[0][i] for i in range(3)))
            area = math.sqrt(_dot(n, n))  # quads are rectangles: |cross| = area
            if area < MIN_OVERLAP_AREA:
                continue
            n = tuple(a / area for a in n)
            for cn, members in clusters:
                if _dot(cn, n) > 1 - 1e-9:
                    break
            else:
                cn, members = n, []
                clusters.append((cn, members))
            members.append((_dot(cn, pts[0]), (b, c, f, area), pts))
        out = []
        for cn, members in clusters:
            q0 = members[0][2]
            u = _norm(tuple(q0[1][i] - q0[0][i] for i in range(3)))
            v = _cross(cn, u)
            members.sort(key=lambda m_: m_[0])
            polys = [[(_dot(p, u), _dot(p, v)) for p in m_[2]] for m_ in members]
            boxes = [(min(p[0] for p in q), max(p[0] for p in q),
                      min(p[1] for p in q), max(p[1] for p in q)) for q in polys]
            for i, (di, fi, _) in enumerate(members):
                for j in range(i + 1, len(members)):
                    dj, fj, _ = members[j]
                    if dj - di > COPLANAR_TOL:
                        break
                    if fi[1] is fj[1]:
                        continue
                    bi, bj = boxes[i], boxes[j]
                    if (min(bi[1], bj[1]) - max(bi[0], bj[0]) <= 1e-6
                            or min(bi[3], bj[3]) - max(bi[2], bj[2]) <= 1e-6):
                        continue
                    ti, tj = info[fi[0].name][1], info[fj[0].name][1]
                    if any(s_ in tj and tj[s_] != var for s_, var in ti.items()):
                        continue  # never shown together
                    ov = _poly_area(_clip_convex(polys[i], polys[j]))
                    if ov > MIN_OVERLAP_AREA:
                        a, b2 = (fi, fj) if order[id(fi[1])] < order[id(fj[1])] else (fj, fi)
                        out.append({"a": a, "b": b2, "overlap": ov})
        out.sort(key=lambda r: (order[id(r["a"][1])], FACES.index(r["a"][2]),
                                order[id(r["b"][1])], FACES.index(r["b"][2])))
        return out

    def fix_coplanar_overlaps(self, eps: float = ZFIGHT_EPS, max_rounds: int = 12) -> dict:
        """Remove z-fighting, mod-wide (Model.build calls it for every gun): for every coplanar
        overlapping pair (`find_coplanar_overlaps`), grow the box of the SMALLER face (the
        detail; on a tie the later-defined box) outward on that side only by `eps`, so the detail
        renders on top. Repeats until nothing is left (a face pushed onto another pushed face, or
        a side face lengthened into a neighbour's plane, is handled in the next round), so it is
        deterministic and idempotent: on an already-fixed model it finds nothing and changes
        nothing. Geometry only (`Cube.grow`); the texture is untouched. Returns a report."""
        found = None
        pushes: dict[tuple[int, str], int] = {}
        pairs: list = []
        for _ in range(max_rounds):
            pairs = self.find_coplanar_overlaps()
            if found is None:
                found = len(pairs)
            if not pairs:
                break
            cubes = [c for b in self.bones.values() for c in b.cubes]
            order = {id(c): k for k, c in enumerate(cubes)}
            this_round = {}
            for r in pairs:
                a, b = r["a"], r["b"]
                detail = a if a[3] < b[3] - 1e-6 else b  # "a" is the earlier cube: ties -> b
                this_round[(order[id(detail[1])], detail[2])] = detail[1]
            for (k, face), cube in sorted(this_round.items(), key=lambda kv: kv[0]):
                cube.grow[face] = cube.grow.get(face, 0.0) + eps
                pushes[(k, face)] = pushes.get((k, face), 0) + 1
        remaining = [f"{r['a'][0].name}:{r['a'][2]} ~ {r['b'][0].name}:{r['b'][2]} "
                     f"({r['overlap']:.3g} u2)" for r in pairs]
        return {"found": found or 0, "faces_pushed": len(pushes),
                "pushes": sum(pushes.values()), "remaining": remaining}

    # ---- main entry ---------------------------------------------------
    def build(self, assets_dir: Path, preview_dir: Path | None = None,
              preview_configs: dict | None = None) -> dict:
        """Write geo.json, texture, (empty) animation file and item model JSON.
        The geo / texture / animations always contain ALL bones. `preview_configs` =
        {config_name: set of bone names to hide} renders one extra preview PNG per config
        (`<name>.<config>.preview.png`), e.g. attachment loadouts. The animation strips use the
        FIRST config (normally the bare gun) so overlapping attachments don't clutter them."""
        preview_configs = preview_configs or getattr(self, "preview_configs", None) or {}
        zfight = self.fix_coplanar_overlaps()  # before export: geo + previews get the nudges
        tw, th = self._pack()
        tex = self._paint(tw, th)
        geo = self.to_geo(tw, th)
        assets_dir = Path(assets_dir)
        paths = {
            "geo": assets_dir / "geo" / "item" / f"{self.name}.geo.json",
            "texture": assets_dir / "textures" / "item" / f"{self.name}.png",
            "anim": assets_dir / "animations" / "item" / f"{self.name}.animation.json",
            "item_model": assets_dir / "models" / "item" / f"{self.name}.json",
        }
        for p in paths.values():
            p.parent.mkdir(parents=True, exist_ok=True)
        paths["geo"].write_text(json.dumps(geo, indent=1))
        tex.save(paths["texture"])
        if self.animations:  # animations defined in code own the file
            paths["anim"].write_text(json.dumps(self.anim_json(), indent=1))
        elif not paths["anim"].exists():  # never overwrite hand-made animations
            paths["anim"].write_text(json.dumps({"format_version": "1.8.0", "animations": {}}, indent=1))
        if not paths["item_model"].exists():  # display transforms are tuned by hand afterwards
            paths["item_model"].write_text(json.dumps(_default_item_model(), indent=1))
        lo, hi = self.bounds()
        stats = {
            "name": self.name,
            "bones": len(self.bones),
            "cubes": sum(len(b.cubes) for b in self.bones.values()),
            "texture": f"{tw}x{th}",
            "bounds_min": [_r(v) for v in lo],
            "bounds_max": [_r(v) for v in hi],
            "size_xyz": [_r(h - l) for l, h in zip(lo, hi)],
            "zfight": zfight,
        }
        if preview_dir is not None:
            preview_dir = Path(preview_dir)
            preview_dir.mkdir(parents=True, exist_ok=True)
            render_preview(self, preview_dir / f"{self.name}.preview.png", tex)
            stats["preview"] = str(preview_dir / f"{self.name}.preview.png")
            stats["previews"] = {}
            for cfg, hidden in preview_configs.items():
                out = preview_dir / f"{self.name}.{cfg}.preview.png"
                render_preview(self, out, tex, hidden=hidden, label=cfg)
                stats["previews"][cfg] = str(out)
            anim_hidden = next(iter(preview_configs.values()), None)
            for a in self.animations.values():
                out = preview_dir / f"{self.name}.anim.{a.name}.preview.png"
                render_anim_strip(self, a, out, tex, hidden=anim_hidden)
            if self.animations:
                stats["animations"] = {a.name: a.length for a in self.animations.values()}
        return stats

    def anim_json(self) -> dict:
        return {"format_version": "1.8.0",
                "animations": {a.name: a.to_json() for a in self.animations.values()}}


EASINGS = ("linear", "easeInSine", "easeOutSine", "easeInOutSine", "easeInQuad", "easeOutQuad",
           "easeInOutQuad", "easeInCubic", "easeOutCubic", "easeInOutCubic", "easeOutBack",
           "easeInBack", "easeOutElastic", "easeOutBounce", "step")


class Animation:
    """Keyframes per bone channel. Channels: "position" (model units, added to the bone's rest
    position), "rotation" (degrees, added to the rest rotation) and "scale" (factor about the
    bone's pivot, rest = 1; (0, 0, 0) hides the bone and its children). Values are interpolated
    linearly in time with an optional easing on the keyframe being moved *into*."""

    def __init__(self, model: Model, name: str, length: float, loop: bool):
        self.model, self.name, self.length, self.loop = model, name, float(length), loop
        self.tracks: dict[str, dict[str, dict[float, tuple[Vec, str]]]] = {}

    def key(self, bone: str, channel: str, t: float, value: Vec, easing: str = "linear") -> "Animation":
        if bone not in self.model.bones:
            raise ValueError(f"animation {self.name}: unknown bone {bone!r}")
        if channel not in ("position", "rotation", "scale"):
            raise ValueError("channel must be 'position', 'rotation' or 'scale'")
        if easing not in EASINGS:
            raise ValueError(f"easing must be one of {EASINGS}")
        if not 0 <= t <= self.length + 1e-9:
            raise ValueError(f"animation {self.name}: t={t} outside 0..{self.length}")
        self.tracks.setdefault(bone, {}).setdefault(channel, {})[round(float(t), 4)] = (
            tuple(map(float, value)), easing)
        return self

    def keys(self, bone: str, channel: str, frames: dict, easing: str = "linear") -> "Animation":
        """frames = {time: (x, y, z)} or {time: ((x, y, z), easing)}"""
        for t, v in frames.items():
            if len(v) == 2 and isinstance(v[1], str):
                self.key(bone, channel, t, v[0], v[1])
            else:
                self.key(bone, channel, t, v, easing)
        return self

    def sample(self, t: float) -> dict:
        """Pose at time t: {bone: (position_offset, rotation_offset, scale)}."""
        pose = {}
        for bone, chans in self.tracks.items():
            vals = {}
            for ch in ("position", "rotation", "scale"):
                frames = sorted(chans.get(ch, {}).items())
                if not frames:
                    vals[ch] = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
                    continue
                if t <= frames[0][0]:
                    vals[ch] = frames[0][1][0]
                elif t >= frames[-1][0]:
                    vals[ch] = frames[-1][1][0]
                else:
                    for (t0, (v0, _)), (t1, (v1, ease)) in zip(frames, frames[1:]):
                        if t0 <= t <= t1:
                            k = _ease((t - t0) / max(t1 - t0, 1e-9), ease)
                            vals[ch] = tuple(a + (b - a) * k for a, b in zip(v0, v1))
                            break
            pose[bone] = (vals["position"], vals["rotation"], vals["scale"])
        return pose

    def to_json(self) -> dict:
        bones = {}
        for bone, chans in self.tracks.items():
            bones[bone] = {}
            for ch, frames in chans.items():
                out = {}
                for t, (v, ease) in sorted(frames.items()):
                    # see Model.to_geo; a scale has no handedness
                    vec = (_flip_rot(v) if ch == "rotation" else [_r(a) for a in v] if ch == "scale"
                           else _flip_pos(v))
                    out[f"{t:g}"] = vec if ease == "linear" else {"vector": vec, "easing": ease}
                bones[bone][ch] = out
        d = {"animation_length": _r(self.length), "bones": bones}
        if self.loop:
            d["loop"] = True
        return d


def _ease(k: float, ease: str) -> float:
    if ease == "step":
        return 0.0 if k < 1 else 1.0
    if ease == "linear":
        return k
    s = math.sin
    c = math.cos
    pi = math.pi
    table = {
        "easeInSine": lambda x: 1 - c(x * pi / 2),
        "easeOutSine": lambda x: s(x * pi / 2),
        "easeInOutSine": lambda x: -(c(pi * x) - 1) / 2,
        "easeInQuad": lambda x: x * x,
        "easeOutQuad": lambda x: 1 - (1 - x) ** 2,
        "easeInOutQuad": lambda x: 2 * x * x if x < 0.5 else 1 - (-2 * x + 2) ** 2 / 2,
        "easeInCubic": lambda x: x ** 3,
        "easeOutCubic": lambda x: 1 - (1 - x) ** 3,
        "easeInOutCubic": lambda x: 4 * x ** 3 if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2,
        "easeOutBack": lambda x: 1 + 2.70158 * (x - 1) ** 3 + 1.70158 * (x - 1) ** 2,
        "easeInBack": lambda x: 2.70158 * x ** 3 - 1.70158 * x * x,
        "easeOutElastic": lambda x: 0 if x == 0 else 1 if x == 1 else 2 ** (-10 * x) * s((x * 10 - 0.75) * (2 * pi / 3)) + 1,
        "easeOutBounce": _bounce,
    }
    return table[ease](k)


def _bounce(x: float) -> float:
    n1, d1 = 7.5625, 2.75
    if x < 1 / d1:
        return n1 * x * x
    if x < 2 / d1:
        x -= 1.5 / d1
        return n1 * x * x + 0.75
    if x < 2.5 / d1:
        x -= 2.25 / d1
        return n1 * x * x + 0.9375
    x -= 2.625 / d1
    return n1 * x * x + 0.984375


# Player skin (64x64) arm regions: (u, v) of the 4x12x4 box UV layout.
_SKIN_ARM = {("right", "base"): (40, 16), ("right", "sleeve"): (40, 32),
             ("left", "base"): (32, 48), ("left", "sleeve"): (48, 48)}


def _skin_uv(skin: tuple[str, str], tw: int, th: int) -> dict:
    """Per-face UVs that land on the skin's arm, expressed in this model's texture pixels
    (GeckoLib normalises UVs by the geometry's texture size; the skin is 64x64)."""
    u, v = _SKIN_ARM[skin[:2]]
    w, h, d = 4, 12, 4
    sx, sy = tw / 64.0, th / 64.0
    faces = {
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
    }
    if len(skin) > 2 and skin[2] == "hand":  # bare hand only: bottom 4 rows of the sides, the hand end
        faces = {f: ((a, b + h - 4, c, 4) if f not in ("up", "down") else faces["down"])
                 for f, (a, b, c, e) in faces.items()}
    return {f: {"uv": [_r(a * sx), _r(b * sy)], "uv_size": [_r(c * sx), _r(e * sy)]}
            for f, (a, b, c, e) in faces.items()}


def _pattern_factor(pattern: str, i: int, j: int, w: int, h: int, rng: random.Random) -> float:
    if pattern == "none":
        return 1.0
    long_i = w >= h
    a = i if long_i else j
    if pattern == "rails":  # picatinny: alternating slots across the long axis
        return 0.55 if a % 2 == 1 else 1.08
    if pattern == "slots":  # vent slots
        return 0.45 if (a % 3 == 1 and 0 < (j if long_i else i) < (h if long_i else w) - 1) else 1.0
    if pattern == "lines":
        return 0.8 if a % 2 == 0 else 1.0
    if pattern == "stipple":
        return rng.choice((0.8, 0.9, 1.0, 1.1))
    if pattern == "knurl":
        return 0.7 if (i + j) % 2 == 0 else 1.05
    return 1.0


def _clip_convex(subject: list, clip: list) -> list:
    """Sutherland-Hodgman: intersection of two convex 2D polygons (any winding)."""
    def ccw(poly):
        return poly if _poly_signed_area(poly) >= 0 else poly[::-1]
    out, clip = ccw(subject), ccw(clip)
    for k in range(len(clip)):
        if not out:
            break
        (ax, ay), (bx, by) = clip[k], clip[(k + 1) % len(clip)]

        def side(p):
            return (bx - ax) * (p[1] - ay) - (by - ay) * (p[0] - ax)
        inp, out = out, []
        for i in range(len(inp)):
            p, q = inp[i], inp[(i + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                t = sp / (sp - sq)
                out.append((p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t))
    return out


def _poly_signed_area(poly: list) -> float:
    n = len(poly)
    return sum(poly[i][0] * poly[(i + 1) % n][1] - poly[(i + 1) % n][0] * poly[i][1]
               for i in range(n)) / 2


def _poly_area(poly: list) -> float:
    return abs(_poly_signed_area(poly)) if len(poly) >= 3 else 0.0


def _rotate(p: Vec, pivot: Vec, rot: Vec) -> Vec:
    x, y, z = (p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2])
    rx, ry, rz = (math.radians(a) for a in rot)
    # X, then Y, then Z (Bedrock order)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return (x + pivot[0], y + pivot[1], z + pivot[2])


def _default_item_model() -> dict:
    # builtin/entity -> GeckoLib renders it. Display transforms are first guesses; tune in-game
    # or in Blockbench's Display tab.
    return {
        "parent": "builtin/entity",
        "gui_light": "front",
        "display": {
            "gui": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, -3], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, -3], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [-2, 2, -2], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [-2, 2, -2], "scale": [0.5, 0.5, 0.5]},
        },
    }


# ---------------------------------------------------------------------------
# Preview renderer (orthographic + isometric, painter's algorithm, flat shading)
# ---------------------------------------------------------------------------
def _norm(v):
    l = math.sqrt(sum(a * a for a in v)) or 1.0
    return tuple(a / l for a in v)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _dot(a, b):
    return sum(x * y for x, y in zip(a, b))


VIEWS = [
    # title, view direction (camera looks along this), up hint, ortho?
    ("RIGHT SIDE (+X)  muzzle ->", (-1, 0, 0), (0, 1, 0), True, "material"),
    ("RIGHT SIDE - coloured by bone", (-1, 0, 0), (0, 1, 0), True, "bone"),
    ("TOP (+Y)  muzzle ->", (0, -1, 0), (-1, 0, 0), True, "material"),
    ("FRONT (looking down the muzzle)", (0, 0, 1), (0, 1, 0), True, "material"),
    ("3/4 front-right, above", (-1, -0.55, 0.65), (0, 1, 0), False, "material"),
    ("3/4 rear-left, above", (1, -0.55, -0.75), (0, 1, 0), False, "material"),
]


def _bone_color(model: Model, name: str):
    idx = [n for n in model.bones if model.bones[n].cubes].index(name)
    h = (idx * 0.618034) % 1.0  # golden-ratio hues: neighbours always differ
    r, g, b = colorsys.hsv_to_rgb(h, 0.65, 0.95)
    return (int(r * 255), int(g * 255), int(b * 255))


def _avg_tex(tex: Image.Image, cube: Cube, face: str):
    if cube.skin:  # Steve-ish preview colours: skin-tone hand, cyan sleeve
        return (196, 142, 112) if cube.skin[1] == "base" else (40, 160, 165)
    u, v, w, h = cube.uv[face]
    region = tex.crop((u, v, u + w, v + h)).convert("RGB").resize((1, 1), Image.BOX)
    return region.getpixel((0, 0))


def render_preview(model: Model, out: Path, tex: Image.Image, cell=(640, 400), hidden=None,
                   label: str | None = None):
    """`hidden` = bone names to leave out (children too); `label` is added to the title."""
    skip = model.hidden_closure(hidden)
    faces = list(model.world_faces(hidden=hidden))
    cw, ch = cell
    cols = 2
    rows = math.ceil(len(VIEWS) / cols)
    img = Image.new("RGB", (cw * cols, ch * rows + 28), (232, 232, 228))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 14)
        small = ImageFont.truetype("arial.ttf", 11)
    except OSError:
        font = small = ImageFont.load_default()
    light = _norm((0.35, 1.0, -0.45))
    lo, hi = model.bounds(hidden)
    size = tuple(round(h - l, 2) for l, h in zip(lo, hi))
    shown = [b for b in model.bones.values() if b.name not in skip]
    draw.text((8, 6), f"{model.name}{' [' + label + ']' if label else ''}   size X{size[0]} Y{size[1]} Z{size[2]} units "
                      f"(16u = 1 block)   bones={len(shown)}  cubes="
                      f"{sum(len(b.cubes) for b in shown)}", fill=(20, 20, 20), font=font)

    for vi, (title, d, up, ortho, mode) in enumerate(VIEWS):
        ox, oy = (vi % cols) * cw, (vi // cols) * ch + 28
        d = _norm(d)
        right = _norm(_cross(d, up))
        upv = _cross(right, d)
        proj = []
        for b, c, f, pts in faces:
            n = _norm(_cross(tuple(pts[1][i] - pts[0][i] for i in range(3)),
                             tuple(pts[2][i] - pts[0][i] for i in range(3))))
            if _dot(n, d) >= -1e-6:
                continue  # back face
            sp = [(_dot(p, right), _dot(p, upv)) for p in pts]
            depth = sum(_dot(p, d) for p in pts) / 4
            proj.append((depth, sp, n, b, c, f))
        if not proj:
            continue
        xs = [p[0] for _, sp, *_ in proj for p in sp]
        ys = [p[1] for _, sp, *_ in proj for p in sp]
        minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
        margin = 30
        scale = min((cw - 2 * margin) / max(maxx - minx, 1e-3),
                    (ch - 2 * margin - 18) / max(maxy - miny, 1e-3))
        cx = ox + cw / 2 - (minx + maxx) / 2 * scale
        cy = oy + 18 + (ch - 18) / 2 + (miny + maxy) / 2 * scale

        def to_px(p):
            return (cx + p[0] * scale, cy - p[1] * scale)

        draw.rectangle([ox + 2, oy + 2, ox + cw - 3, oy + ch - 3], outline=(180, 180, 175))
        if ortho:  # unit grid, every 2 units; heavier every 8 and on the origin
            gx0, gx1 = math.floor(minx) - 2, math.ceil(maxx) + 2
            gy0, gy1 = math.floor(miny) - 2, math.ceil(maxy) + 2
            for gx in range(gx0, gx1 + 1):
                if gx % 2:
                    continue
                col = (150, 150, 200) if gx == 0 else (205, 205, 200) if gx % 8 else (185, 185, 180)
                draw.line([to_px((gx, gy0)), to_px((gx, gy1))], fill=col)
            for gy in range(gy0, gy1 + 1):
                if gy % 2:
                    continue
                col = (150, 150, 200) if gy == 0 else (205, 205, 200) if gy % 8 else (185, 185, 180)
                draw.line([to_px((gx0, gy)), to_px((gx1, gy))], fill=col)
        for depth, sp, n, b, c, f in sorted(proj, key=lambda t: -t[0]):
            if mode == "bone":
                base = _bone_color(model, b.name)
            else:  # lift dark colours so black guns stay readable in the preview
                base = tuple(int(48 + ch_ * 0.82) for ch_ in _avg_tex(tex, c, f))
            k = 0.55 + 0.5 * max(0.0, _dot(n, light))
            col = tuple(min(255, int(ch_ * k)) for ch_ in base)
            outline = tuple(int(ch_ * 0.45) for ch_ in col)
            draw.polygon([to_px(p) for p in sp], fill=col, outline=outline)
        draw.text((ox + 8, oy + 6), f"{title}   ({scale:.1f}px/unit)", fill=(30, 30, 30), font=font)
        if mode == "bone":
            ly = oy + 24
            for name in model.bones:
                if not model.bones[name].cubes or name in skip:
                    continue
                draw.rectangle([ox + 8, ly + 2, ox + 18, ly + 12], fill=_bone_color(model, name))
                draw.text((ox + 22, ly), name, fill=(30, 30, 30), font=small)
                ly += 13
    img.save(out)


def render_anim_strip(model: Model, anim: "Animation", out: Path, tex: Image.Image, frames: int = 7,
                      cell=(300, 230), hidden=None):
    """Two rows of frames across the animation's length: right side view, and a "shooter's view"
    from behind and above the stock (roughly what first person sees). Same scale in every frame,
    so motion reads correctly. Frame times are printed above each cell."""
    times = sorted({round(anim.length * i / (frames - 1), 3) for i in range(frames)} |
                   {t for ch in anim.tracks.values() for fr in ch.values() for t in fr})
    if len(times) > 12:  # keep it readable: even spacing wins
        times = [round(anim.length * i / 11, 3) for i in range(12)]
    views = [("side (+X)", (-1, 0, 0)), ("shooter view", (-0.35, -0.3, -1))]
    poses = [anim.sample(t) for t in times]
    cw, ch = cell
    img = Image.new("RGB", (cw * len(times), ch * len(views) + 24), (232, 232, 228))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    light = _norm((0.35, 1.0, -0.45))
    draw.text((6, 5), f"{model.name}  animation '{anim.name}'  length {anim.length}s  loop={anim.loop}",
              fill=(20, 20, 20), font=font)
    for vi, (title, d) in enumerate(views):
        d = _norm(d)
        right = _norm(_cross(d, (0, 1, 0)))
        upv = _cross(right, d)
        all_faces = [list(model.world_faces(p, hidden)) for p in poses]
        xs, ys = [], []
        for faces in all_faces:
            for _, _, _, pts in faces:
                for p in pts:
                    xs.append(_dot(p, right))
                    ys.append(_dot(p, upv))
        minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
        scale = min((cw - 20) / max(maxx - minx, 1e-3), (ch - 40) / max(maxy - miny, 1e-3))
        for fi, (t, faces) in enumerate(zip(times, all_faces)):
            ox, oy = fi * cw, 24 + vi * ch
            cx = ox + cw / 2 - (minx + maxx) / 2 * scale
            cy = oy + 18 + (ch - 18) / 2 + (miny + maxy) / 2 * scale
            draw.rectangle([ox + 1, oy + 1, ox + cw - 2, oy + ch - 2], outline=(185, 185, 180))
            proj = []
            for b, c, f, pts in faces:
                n = _norm(_cross(tuple(pts[1][i] - pts[0][i] for i in range(3)),
                                 tuple(pts[2][i] - pts[0][i] for i in range(3))))
                if _dot(n, d) >= -1e-6:
                    continue
                sp = [(cx + _dot(p, right) * scale, cy - _dot(p, upv) * scale) for p in pts]
                proj.append((sum(_dot(p, d) for p in pts) / 4, sp, n, c, f))
            for depth, sp, n, c, f in sorted(proj, key=lambda q: -q[0]):
                base = tuple(int(48 + v * 0.82) for v in _avg_tex(tex, c, f))
                k = 0.55 + 0.5 * max(0.0, _dot(n, light))
                col = tuple(min(255, int(v * k)) for v in base)
                draw.polygon(sp, fill=col, outline=tuple(int(v * 0.45) for v in col))
            draw.text((ox + 5, oy + 4), f"{title}  t={t:g}s", fill=(30, 30, 30), font=font)
    img.save(out)
