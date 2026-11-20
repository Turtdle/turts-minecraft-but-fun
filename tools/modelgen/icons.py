"""
Inventory icons for attachment items, rendered from the same geometry the guns use
(tools/modelgen/attachments.py): a 3/4 side view drawn as 32x32 pixel art (sprite.py).

    python tools/modelgen/icons.py            # every attachment
    python tools/modelgen/icons.py <id> ...   # just these
"""
import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import armorygen as ag  # noqa: E402
import attachments as at  # noqa: E402
import sprite  # noqa: E402

ASSETS = HERE.parent.parent / "src" / "main" / "resources" / "assets" / "modernarmory"
IDS = ["red_dot", "holographic", "acog", "sniper_scope", "lpvo_m157", "micro_red_dot",
       "suppressor_556", "compensator_556", "suppressor_300",
       "suppressor_9mm", "suppressor_762", "suppressor_762_short", "suppressor_68_short", "compensator_762", "suppressor_338", "muzzle_brake_50",
       "vertical_grip", "angled_grip", "bipod", "laser", "stock_ctr", "pmag_40"]


VIEW = sprite.View((-1.0, -0.45, 0.55))  # from the right side, slightly above and in front


def icon(att_id: str, size: int = 32, ss: int = 8) -> Image.Image:
    m = ag.Model("icon_" + att_id)
    getattr(at, "add_" + att_id)(m, "root", (0, 0, 0))
    return sprite.render(m, VIEW, size, ss)


def main():
    (ASSETS / "textures" / "item").mkdir(parents=True, exist_ok=True)
    (ASSETS / "models" / "item").mkdir(parents=True, exist_ok=True)
    for i in sys.argv[1:] or IDS:
        icon(i).save(ASSETS / "textures" / "item" / f"{i}.png")
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"modernarmory:item/{i}"}},
                  open(ASSETS / "models" / "item" / f"{i}.json", "w"), indent=1)
        print("icon", i)


if __name__ == "__main__":
    main()
