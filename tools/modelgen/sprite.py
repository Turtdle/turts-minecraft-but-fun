"""
Flat pixel-art sprites of a model, drawn from the same geometry the 3D model is built from.
Shared by icons.py (attachments) and item_icons.py (guns, grenades), so every inventory icon in
the mod has one look: one camera, one light, one outline; change it here and re-run both.

A View says where the camera looks from and how the picture is turned afterwards:
    View((-1, 0, 0))            straight side-on (guns: from the right, muzzle pointing right)
    View((-1, 0, 0), tilt=35)   the same, turned 35 degrees anticlockwise (muzzle up and right)
"""
import math
from dataclasses import dataclass

from PIL import Image, ImageDraw

import armorygen as ag

LIGHT = ag._norm((0.35, 1.0, -0.45))
OUTLINE = (20, 20, 22, 200)


@dataclass(frozen=True)
class View:
    direction: tuple            # the way the camera looks (model space; +Y is up)
    tilt: float = 0.0           # turn the picture this many degrees anticlockwise
    margin: float = 1.5         # empty border, in final pixels


@dataclass(frozen=True)
class Face:
    """One visible face, ready to draw: its bone, flat colour and corners on the picture plane (model units)."""
    bone: str
    color: tuple                # (r, g, b)
    points: list                # 4 x (x, y), +y up


def project(model: "ag.Model", view: View, hidden=None, pose=None) -> list:
    """The model's faces that face the camera, shaded and projected, painter-sorted far to near.
    The game draws gun icons from this list too (item_icons.py --geometry; client.GunIcons), hiding the
    bones of attachments that aren't fitted, so it must stay the one place faces are shaded."""
    tw, th = model._pack()
    tex = model._paint(tw, th)
    d = ag._norm(view.direction)
    right = ag._norm(ag._cross(d, (0, 1, 0)))
    up = ag._cross(right, d)
    ca, sa = math.cos(math.radians(view.tilt)), math.sin(math.radians(view.tilt))
    proj = []
    for b, c, f, pts in model.world_faces(pose, hidden):
        n = ag._norm(ag._cross(tuple(pts[1][i] - pts[0][i] for i in range(3)),
                               tuple(pts[2][i] - pts[0][i] for i in range(3))))
        if ag._dot(n, d) >= -1e-6:
            continue  # facing away
        sp = []
        for p in pts:
            x, y = ag._dot(p, right), ag._dot(p, up)
            sp.append((x * ca - y * sa, x * sa + y * ca))
        base = ag._avg_tex(tex, c, f)
        k = 0.62 + 0.55 * max(0.0, ag._dot(n, LIGHT))
        col = tuple(min(255, int(v * k + 18)) for v in base[:3])
        proj.append((sum(ag._dot(p, d) for p in pts) / 4, Face(b.name if hasattr(b, "name") else b, col, sp)))
    return [face for _depth, face in sorted(proj, key=lambda q: -q[0])]


EDGE_SLACK = 0.25  # supersampled pixels: a sample this close outside a face's edge still counts as on it


def rasterize(faces: list, size: int = 32, ss: int = 8, margin: float = 1.5) -> Image.Image:
    """Draws `project`ed faces fitted into a `size` x `size` sprite (`margin` px clear round it), plus a 1-px
    dark outline. Each sprite pixel takes the nearest face covering the centre of its middle sample in an `ss`
    times supersampled picture. The game paints gun icons with the very same arithmetic
    (client.GunIconPainter), from the face corners rounded to 4 decimals as icon_geometry stores them, so a
    gun's runtime icon and this sprite match pixel for pixel: change one, change both."""
    pts = [[(round(x, 4), round(y, 4)) for x, y in f.points] for f in faces]
    xs = [p[0] for q in pts for p in q]
    ys = [p[1] for q in pts for p in q]
    big = size * ss
    margin = margin * ss
    scale = min((big - 2 * margin) / max(max(xs) - min(xs), 1e-3), (big - 2 * margin) / max(max(ys) - min(ys), 1e-3))
    cx = big / 2 - (min(xs) + max(xs)) / 2 * scale
    cy = big / 2 + (min(ys) + max(ys)) / 2 * scale
    px = [[None] * size for _ in range(size)]
    for f, q in zip(faces, pts):  # far to near: nearer faces paint over
        sx = [cx + x * scale for x, _ in q]
        sy = [cy - y * scale for _, y in q]
        area = 0.0
        for k in range(4):
            j = (k + 1) & 3
            area += sx[k] * sy[j] - sx[j] * sy[k]
        sign = 1.0 if area >= 0 else -1.0
        x0, x1, y0, y1 = min(sx), max(sx), min(sy), max(sy)
        for y in range(max(0, math.floor((y0 - ss / 2 - 1) / ss)), min(size - 1, math.ceil((y1 - ss / 2 + 1) / ss)) + 1):
            sample_y = y * ss + ss // 2 + 0.5
            for x in range(max(0, math.floor((x0 - ss / 2 - 1) / ss)), min(size - 1, math.ceil((x1 - ss / 2 + 1) / ss)) + 1):
                if _inside(sx, sy, sign, x * ss + ss // 2 + 0.5, sample_y):
                    px[y][x] = f.color
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            if px[y][x] is not None:
                img.putpixel((x, y), px[y][x] + (255,))
    return outline(img)


def _inside(sx, sy, sign, x, y) -> bool:
    """Point in the convex quad, edges included (EDGE_SLACK)."""
    for k in range(4):
        j = (k + 1) & 3
        ex, ey = sx[j] - sx[k], sy[j] - sy[k]
        length = math.sqrt(ex * ex + ey * ey)
        if length < 1e-9:
            continue
        d = sign * (ex * (y - sy[k]) - ey * (x - sx[k])) / length
        if d < -EDGE_SLACK:
            return False
    return True


def render(model: "ag.Model", view: View, size: int = 32, ss: int = 8, hidden=None, pose=None) -> Image.Image:
    """`size` x `size` RGBA sprite: flat-shaded faces (each face the average colour of its texture),
    painter-sorted, supersampled `ss` times then reduced with NEAREST, plus a 1-px dark outline."""
    return rasterize(project(model, view, hidden, pose), size, ss, view.margin)


def outline(small: Image.Image) -> Image.Image:
    """1-px dark border round the sprite so it reads on any slot background."""
    w, h = small.size
    px = small.load()
    out = small.copy()
    po = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] == 0 and any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] > 0
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = OUTLINE
    return out
