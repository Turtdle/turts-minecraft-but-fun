"""
Inventory sprites for the mod's multiblock emplacements (placed whole as blocks, carried as flat items),
rendered from their models with the same sprite renderer and 32 px size as item_icons.py, seen from the
right side, a little from the front and above.

    python tools/modelgen/multiblock_icons.py     # every emplacement below
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import item_icons  # noqa: E402
import sprite  # noqa: E402

MULTIBLOCKS = ["laser_turret"]
VIEW = sprite.View((-1.0, -0.3, -0.35), margin=0.5)


def main():
    item_icons.OUT.mkdir(parents=True, exist_ok=True)
    for v in MULTIBLOCKS:
        item_icons.render(v, view=VIEW, defaults={}).save(item_icons.OUT / f"{v}.png")
        print("sprite", v)


if __name__ == "__main__":
    main()
