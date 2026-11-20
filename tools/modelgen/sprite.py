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


def render(model: "ag.Model", view: View, size: int = 32, ss: int = 8, hidden=None, pose=None) -> Image.Image:
    """`size` x `size` RGBA sprite: flat-shaded faces (each face the average colour of its texture),
    painter-sorted, supersampled `ss` times then reduced with NEAREST, plus a 1-px dark outline."""
    tw, th = model._pack()
    tex = model._paint(tw, th)
    d = ag._norm(view.direction)
    right = ag._norm(ag._cross(d, (0, 1, 0)))
    up = ag._cross(right, d)
    ca, sa = math.cos(math.radians(view.tilt)), math.sin(math.radians(view.tilt))
    proj = []
    for _b, c, f, pts in model.world_faces(pose, hidden):
        n = ag._norm(ag._cross(tuple(pts[1][i] - pts[0][i] for i in range(3)),
                               tuple(pts[2][i] - pts[0][i] for i in range(3))))
        if ag._dot(n, d) >= -1e-6:
            continue  # facing away
        sp = []
        for p in pts:
            x, y = ag._dot(p, right), ag._dot(p, up)
            sp.append((x * ca - y * sa, x * sa + y * ca))
        proj.append((sum(ag._dot(p, d) for p in pts) / 4, sp, n, c, f))
    xs = [p[0] for _, sp, *_ in proj for p in sp]
    ys = [p[1] for _, sp, *_ in proj for p in sp]
    big = size * ss
    margin = view.margin * ss
    scale = min((big - 2 * margin) / max(max(xs) - min(xs), 1e-3), (big - 2 * margin) / max(max(ys) - min(ys), 1e-3))
    cx = big / 2 - (min(xs) + max(xs)) / 2 * scale
    cy = big / 2 + (min(ys) + max(ys)) / 2 * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for _depth, sp, n, c, f in sorted(proj, key=lambda q: -q[0]):
        base = ag._avg_tex(tex, c, f)
        k = 0.62 + 0.55 * max(0.0, ag._dot(n, LIGHT))
        col = tuple(min(255, int(v * k + 18)) for v in base[:3]) + (255,)
        draw.polygon([(cx + x * scale, cy - y * scale) for x, y in sp], fill=col)
    return outline(img.resize((size, size), Image.NEAREST))


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
