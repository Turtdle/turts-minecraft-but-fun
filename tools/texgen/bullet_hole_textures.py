"""
Bullet-hole decals: one atlas, textures/misc/bullet_holes.png, read by client.BulletHoles.

    python tools/texgen/bullet_hole_textures.py

Layout: rows = materials (ImpactMaterial order: STONE, WOOD, METAL, GLASS, EARTH, FABRIC), columns = styles
x variants (STYLES x VARIANTS), each cell CELL x CELL pixels, transparent where there is no hole. The decal is
drawn with the texture's own colours (lit by the block light), so every hole carries its own dark core,
chipped rim and highlights; nothing is tinted by the block underneath.

Styles (the user picks one; BulletHoles.style):
  A "pixel"     small, crisp, few colours: Minecraft-like
  B "crater"    chunkier hole with a ring of debris and chips
  C "detailed"  soft shading, scorch halo, more texture
"""
import math
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/textures/misc/bullet_holes.png"
CELL = 32
MATERIALS = ["stone", "wood", "metal", "glass", "earth", "fabric"]
STYLES = ["pixel", "crater", "detailed"]
VARIANTS = 4

# base colours per material: core (the hole), rim (chipped / torn edge), light (highlight / dust / splinters)
COLOURS = {
    "stone": ((22, 22, 24), (70, 70, 72), (150, 148, 142)),
    "wood": ((24, 16, 10), (78, 54, 32), (196, 160, 104)),
    "metal": ((18, 18, 20), (96, 98, 104), (222, 224, 230)),
    "glass": ((30, 34, 38), (200, 220, 230), (245, 250, 255)),
    "earth": ((26, 20, 14), (60, 46, 32), (112, 92, 66)),
    "fabric": ((20, 18, 18), (52, 46, 44), (90, 84, 80)),
}


def cell(material: str, style: str, variant: int) -> np.ndarray:
    rng = np.random.default_rng(zlib.crc32(f"{material}/{style}/{variant}".encode()))
    n = CELL
    y, x = np.mgrid[0:n, 0:n].astype(float)
    c = (n - 1) / 2 + rng.uniform(-0.5, 0.5, 2)
    dx, dy = x - c[0], y - c[1]
    r = np.sqrt(dx * dx + dy * dy)
    a = np.arctan2(dy, dx)
    core_c, rim_c, light_c = (np.array(v, float) for v in COLOURS[material])
    img = np.zeros((n, n, 4), float)

    def paint(mask, colour, alpha):
        m = np.clip(mask, 0, 1) * alpha
        img[..., :3] = img[..., :3] * (1 - m[..., None]) + colour * m[..., None]
        img[..., 3] = np.maximum(img[..., 3], m * 255)

    # a ragged outline: radius wobbles with angle (different per variant)
    wob = sum(rng.uniform(0.04, 0.12) * np.sin(k * a + rng.uniform(0, 6.28)) for k in (3, 5, 7))
    if style == "pixel":
        R = n * rng.uniform(0.11, 0.14)
        rr = r / (1 + wob)
        paint(rr < R * 1.9, rim_c, 0.9)
        paint(rr < R, core_c, 1.0)
        chips = rng.integers(3, 6)
        for _ in range(chips):  # a few pixel chips round the rim
            t = rng.uniform(0, 6.28); d = R * rng.uniform(2.0, 2.6)
            cx, cy = c[0] + math.cos(t) * d, c[1] + math.sin(t) * d
            paint((np.abs(x - cx) < 1.0) & (np.abs(y - cy) < 1.0), light_c, 0.8)
        img[..., 3] = np.where(img[..., 3] > 100, 255, 0)  # crisp edges, no soft alpha
    elif style == "crater":
        R = n * rng.uniform(0.14, 0.17)
        rr = r / (1 + wob * 1.4)
        paint(np.clip((R * 2.9 - rr) / 2.5, 0, 1) * (rr > R * 1.6), light_c, 0.55)   # debris ring
        paint(rr < R * 1.9, rim_c, 1.0)
        paint(rr < R * 1.15, core_c, 1.0)
        for _ in range(rng.integers(6, 10)):  # thrown-out chips
            t = rng.uniform(0, 6.28); d = R * rng.uniform(2.2, 3.4); s = rng.uniform(0.6, 1.4)
            cx, cy = c[0] + math.cos(t) * d, c[1] + math.sin(t) * d
            paint(((x - cx) ** 2 + (y - cy) ** 2) < s * s, rim_c if rng.random() < 0.5 else light_c, 0.9)
    else:  # detailed
        R = n * rng.uniform(0.10, 0.13)
        rr = r / (1 + wob)
        halo = np.clip(1 - rr / (R * 4.2), 0, 1) ** 1.6
        paint(halo, core_c * 0.9 + 10, 0.55)                                         # scorch / dirt halo
        paint(np.clip((R * 2.1 - rr) / 1.5, 0, 1), rim_c, 0.95)
        paint(np.clip((R * 1.2 - rr) / 1.2, 0, 1), core_c, 1.0)
        shade = np.clip(np.cos(a - 2.3) * 0.5 + 0.5, 0, 1) * (rr > R * 1.1) * (rr < R * 2.0)
        paint(shade, light_c, 0.35)                                                   # lit edge of the crater
        img[..., :3] *= (0.92 + 0.16 * rng.random((n, n)))[..., None]                 # grain

    # material specifics on top
    if material == "wood":  # splinters along the grain (vertical), torn outward
        for _ in range(rng.integers(4, 8)):
            sx = c[0] + rng.uniform(-3, 3); length = rng.uniform(3, 8); w = rng.uniform(0.4, 0.9)
            up = rng.random() < 0.5
            y0, y1 = (c[1] - length - 2, c[1] - 2) if up else (c[1] + 2, c[1] + length + 2)
            paint((np.abs(x - sx) < w) & (y > y0) & (y < y1), light_c, 0.9)
    elif material == "metal":  # bright dented ring, no debris
        paint((r > n * 0.16) & (r < n * 0.21), light_c, 0.75)
    elif material == "glass":  # radial cracks and a crack ring, mostly see-through
        img[..., 3] *= 0.6
        for _ in range(rng.integers(6, 10)):
            t = rng.uniform(0, 6.28); L = n * rng.uniform(0.25, 0.48)
            ux, uy = math.cos(t), math.sin(t)
            along = dx * ux + dy * uy; across = np.abs(-dx * uy + dy * ux)
            paint((along > 0) & (along < L) & (across < 0.55), light_c, 0.85)
        paint(np.abs(r - n * rng.uniform(0.2, 0.28)) < 0.5, light_c, 0.6)
    elif material == "fabric":
        img[..., 3] *= 0.85

    img[..., 3] *= np.clip((n * 0.49 - r) / 2, 0, 1)  # nothing reaches the cell edge
    return np.clip(img, 0, 255).astype(np.uint8)


def main():
    atlas = np.zeros((CELL * len(MATERIALS), CELL * len(STYLES) * VARIANTS, 4), np.uint8)
    for mi, m in enumerate(MATERIALS):
        for si, s in enumerate(STYLES):
            for v in range(VARIANTS):
                col = si * VARIANTS + v
                atlas[mi * CELL:(mi + 1) * CELL, col * CELL:(col + 1) * CELL] = cell(m, s, v)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(atlas, "RGBA").save(OUT)
    print("wrote", OUT.relative_to(OUT.parents[5]), atlas.shape[1], "x", atlas.shape[0])


if __name__ == "__main__":
    main()
