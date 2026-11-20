"""
Thermite fire (block.ThermiteFireBlock, from the AN-M14): two animated 16x16 flame textures,
textures/block/thermite_fire_0.png and _1.png (+ .mcmeta), redder and hotter-looking than vanilla fire.

    python tools/texgen/thermite_fire_textures.py

Original art, not a recolour: a classic "pixel fire" simulation (heat rises from a white-hot bottom row,
cooling at random and drifting sideways), looped by blending the run into its own continuation, mapped
through a red-heavy palette. Alpha is all-or-nothing, so 26.x renders it as cutout like vanilla fire
(1.21.1 is told so by the block models' render_type).
"""
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/textures/block"
SIZE = 16
FRAMES = 32
LEVELS = 36                        # heat levels; 0 = no flame
CUTOFF = 8                         # heat below this is transparent
# heat -> colour: deep crimson tips, red body, orange then a white-hot core at the base
PALETTE_STOPS = [
    (0.00, (70, 4, 2)),
    (0.20, (128, 10, 4)),
    (0.40, (190, 22, 6)),
    (0.60, (232, 54, 10)),
    (0.78, (255, 104, 24)),
    (0.90, (255, 168, 70)),
    (1.00, (255, 236, 196)),
]


def palette() -> np.ndarray:
    xs = np.array([s for s, _ in PALETTE_STOPS])
    cs = np.array([c for _, c in PALETTE_STOPS], float)
    t = np.linspace(0, 1, LEVELS)
    return np.stack([np.interp(t, xs, cs[:, k]) for k in range(3)], axis=1)


def simulate(seed: int, steps: int) -> np.ndarray:
    """Heat fields, one per step: shape (steps, H, W), H includes rows below the visible 16."""
    rng = np.random.default_rng(seed)
    h, w = SIZE + 2, SIZE
    heat = np.zeros((h, w), float)
    out = []
    for _ in range(steps + 40):  # 40 warm-up steps
        heat[-1, :] = LEVELS - 1
        # the base flickers a little
        heat[-1, :] -= rng.integers(0, 6, w) * (rng.random(w) < 0.3)
        new = heat.copy()
        for y in range(h - 1):
            for x in range(w):
                src = heat[y + 1, x]
                decay = rng.integers(0, 5)                 # mean 2 per row: flames ~16 px tall
                dx = rng.integers(-1, 2)                   # drift sideways
                new[y, (x + dx) % w] = max(0, src - decay)
        # narrower towards the edges so the sprite reads as flames, not a wall
        edge = np.minimum(np.arange(w), w - 1 - np.arange(w))
        new[:-1] -= (edge < 1) * 3
        heat = np.clip(new, 0, LEVELS - 1)
        out.append(heat.copy())
    return np.array(out[40:])


def frames(seed: int) -> list[np.ndarray]:
    run = simulate(seed, FRAMES * 2)
    pal = palette()
    imgs = []
    for i in range(FRAMES):
        wgt = i / FRAMES  # blend into the continuation so frame 31 flows into frame 0
        hf = (1 - wgt) * run[i + FRAMES] + wgt * run[i]
        # a light blur: clean flame tongues instead of speckle (wraps sideways, like the simulation)
        sm = hf.copy()
        sm[1:-1] = 0.7 * hf[1:-1] + 0.15 * (hf[:-2] + hf[2:])
        hf = 0.7 * sm + 0.15 * (np.roll(sm, 1, axis=1) + np.roll(sm, -1, axis=1))
        hf = hf[:SIZE]
        lvl = np.clip(np.round(hf), 0, LEVELS - 1).astype(int)
        rgba = np.zeros((SIZE, SIZE, 4), np.uint8)
        rgba[..., :3] = pal[lvl].astype(np.uint8)
        rgba[..., 3] = np.where(lvl >= CUTOFF, 255, 0)
        imgs.append(rgba)
    return imgs


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sheets = []
    for k, seed in enumerate((1907, 2203)):
        fr = frames(seed)
        sheet = np.concatenate(fr, axis=0)
        name = f"thermite_fire_{k}"
        Image.fromarray(sheet, "RGBA").save(OUT / f"{name}.png")
        (OUT / f"{name}.png.mcmeta").write_text('{\n  "animation": {\n    "frametime": 1\n  }\n}\n')
        sheets.append(fr)
        print("wrote", name, sheet.shape[1], "x", sheet.shape[0])
    # preview: every 4th frame of both, 8x up, on a dark background
    prev = Path(__file__).resolve().parents[1] / "modelgen/previews/thermite_fire.png"
    tiles = []
    for fr in sheets:
        row = []
        for f in fr[::4]:
            bg = np.zeros((SIZE, SIZE, 3), np.uint8) + 30
            a = f[..., 3:4] / 255.0
            row.append((f[..., :3] * a + bg * (1 - a)).astype(np.uint8))
        tiles.append(np.concatenate(row, axis=1))
    img = Image.fromarray(np.concatenate(tiles, axis=0)).resize((SIZE * 8 * len(sheets[0][::4]), SIZE * 8 * 2), Image.NEAREST)
    img.save(prev)
    print("preview", prev)


if __name__ == "__main__":
    main()
