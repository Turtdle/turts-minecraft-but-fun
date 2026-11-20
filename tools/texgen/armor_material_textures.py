"""
16x16 item textures for what body armour is made of, plus their item models:

  aramid_fabric  - a folded square of woven para-aramid (Kevlar): its natural golden yellow, basket weave
  rifle_plate    - a Level III plate: SAPI-cut (clipped top corners), dark grey coated steel / polyethylene
  ceramic_plate  - a Level IV ESAPI-style plate: SAPI cut, olive-grey cover, white label patch

    python tools/texgen/armor_material_textures.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "modernarmory"
TEX = ASSETS / "textures" / "item"
MODELS = ASSETS / "models" / "item"


def shade(c, k):
    return tuple(max(0, min(255, round(v * k))) for v in c[:3]) + (255,)


def fabric():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    base = (214, 168, 42)
    rnd = random.Random(7)
    for y in range(2, 14):
        for x in range(2, 14):
            weave = 1.08 if (x // 2 + y // 2) % 2 == 0 else 0.9   # 2x2 basket weave
            px[x, y] = shade(base, weave + rnd.uniform(-0.04, 0.04))
    for x in range(2, 14):                                        # folded edge along the bottom
        px[x, 13] = shade(base, 0.68)
        px[x, 12] = shade(base, 0.82)
    for y in range(2, 14):
        px[13, y] = shade(base, 0.72)
    for x in range(2, 14):
        px[x, 2] = shade(base, 1.18)
    for i in range(3, 13, 3):                                     # loose threads at the cut edge
        px[i, 1] = shade(base, 0.95)
    return img


def plate(face, rim, label=None):
    """A SAPI-cut plate seen from the front: 10 wide, 13 tall, top corners clipped."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    x0, x1, y0, y1 = 3, 12, 1, 14
    rnd = random.Random(3)
    for y in range(y0, y1 + 1):
        clip = max(0, 3 - (y - y0))                               # shooter's cut: corners off the top
        for x in range(x0 + clip, x1 - clip + 1):
            edge = x in (x0 + clip, x1 - clip) or y in (y0, y1)
            c = rim if edge else shade(face, 1 + rnd.uniform(-0.05, 0.05) + 0.08 * (1 - (y - y0) / (y1 - y0)))
            px[x, y] = c if len(c) == 4 else c + (255,)
    for y in range(y0 + 1, y1):                                   # bevel: light left, dark right
        clip = max(0, 3 - (y - y0))
        px[x0 + clip + 1, y] = shade(px[x0 + clip + 1, y], 1.15)
        px[x1 - clip - 1, y] = shade(px[x1 - clip - 1, y], 0.8)
    if label:
        for y in range(8, 11):
            for x in range(6, 10):
                px[x, y] = label + (255,)
        px[7, 9] = px[8, 9] = (60, 60, 60, 255)
    return img


def item_model(name):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"modernarmory:item/{name}"}}


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    items = {
        "aramid_fabric": fabric(),
        "rifle_plate": plate((70, 72, 74), (34, 35, 36, 255)),
        "ceramic_plate": plate((104, 108, 88), (52, 54, 44, 255), label=(226, 226, 218)),
    }
    for name, img in items.items():
        img.save(TEX / f"{name}.png")
        (MODELS / f"{name}.json").write_text(json.dumps(item_model(name), indent=1))
        print("wrote", name)


if __name__ == "__main__":
    main()
