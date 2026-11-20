"""
2D inventory sprites for the mod's 3D items (guns, grenades, drones), rendered from each item's model
(tools/modelgen/guns/<id>.py) with the shared sprite renderer (sprite.py), like the attachment icons.
The inventory, hotbar and other GUIs draw the sprite; hands, the ground and item frames keep the 3D
model (display.py writes the item models that pick between them).

    python tools/modelgen/item_icons.py            # every item in display_items.json
    python tools/modelgen/item_icons.py m4 deagle  # just these
    python tools/modelgen/item_icons.py --sheet    # also write previews/item_icons.sheet.png

What is drawn: the bare item with its default attachments (docs/firearms/models.json
"default_attachments"), no arms, no muzzle flash, nothing that is only shown in first person, and
the default parts an attachment replaces hidden the same way GunRenderer hides them.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build  # noqa: E402
import display  # noqa: E402
import sprite  # noqa: E402

ROOT = HERE.parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "modernarmory"
OUT = ASSETS / "textures" / "item" / "icon"
MODELS_DOC = ROOT / "docs" / "firearms" / "models.json"
SIZE = 32

# One camera per kind of item; change the look of every sprite of a kind here.
VIEWS = {
    # guns: muzzle is -Z, seen from the right (+X) so the muzzle points right, turned up a little
    "gun": sprite.View((-1.0, -0.12, 0.0), tilt=30.0, margin=1.0),
    # grenades: standing up, 3/4 from the front right and a little above (fuze, pin and spoon all show)
    "grenade": sprite.View((-0.8, -0.45, -0.6), margin=2.0),
    "drone": sprite.View((-0.7, -0.6, -0.5), margin=1.0),
}
ALWAYS_HIDDEN = ("right_arm", "left_arm", "tripod")  # tripod: first person only (PartVisibility)


def kind(item_id: str) -> str:
    return "grenade" if item_id.startswith("grenade_") else "drone" if item_id.startswith("drone_") else "gun"


def default_attachments() -> dict:
    return {g["id"]: g.get("default_attachments") or {} for g in json.loads(MODELS_DOC.read_text())}


def hidden_bones(model, defaults: dict) -> set:
    """Everything GunRenderer would hide for a gun carrying exactly `defaults` (slot -> attachment id)."""
    fitted = set(defaults.values())
    slots = set(defaults)
    hide = set()
    for name in model.bones:
        if name in ALWAYS_HIDDEN or name.startswith("flash_") or name == "muzzle_flash":
            hide.add(name)
        elif name.startswith("att_") and name[4:] not in fitted:
            hide.add(name)
        elif name == "muzzle_default" and "MUZZLE" in slots:
            hide.add(name)
        elif name == "stock_default" and "STOCK" in slots:
            hide.add(name)
        elif name == "magazine_default" and "MAGAZINE" in slots:
            hide.add(name)
        elif name in ("rear_sight", "front_sight") and "OPTIC" in slots:
            hide.add(name)  # flip-up irons fold under an optic
    return hide


def render(item_id: str, view: sprite.View | None = None, defaults: dict | None = None):
    model = build.load(HERE / "guns" / f"{item_id}.py")
    if defaults is None:
        defaults = default_attachments().get(item_id, {})
    return sprite.render(model, view or VIEWS[kind(item_id)], SIZE, hidden=hidden_bones(model, defaults))


def items() -> list:
    """Every item with its own model (entries that reuse another gun's model share its sprite)."""
    _, entries = display.load()
    return [k for k, v in entries.items() if "parent" not in v]


def sheet(sprites: dict, path: Path, scale: int = 4) -> None:
    """Contact sheet: each sprite on a vanilla-looking slot, drawn `scale` times bigger."""
    cols = 8
    cell = SIZE * scale + 16
    rows = (len(sprites) + cols - 1) // cols
    img = Image.new("RGBA", (cols * cell, rows * (cell + 14)), (198, 198, 198, 255))
    d = ImageDraw.Draw(img)
    for i, (name, spr) in enumerate(sprites.items()):
        x, y = (i % cols) * cell, (i // cols) * (cell + 14)
        d.rectangle([x + 6, y + 6, x + cell - 6, y + cell - 6], fill=(139, 139, 139, 255))
        d.line([x + 6, y + 6, x + cell - 6, y + 6], fill=(55, 55, 55, 255), width=3)
        d.line([x + 6, y + 6, x + 6, y + cell - 6], fill=(55, 55, 55, 255), width=3)
        big = spr.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
        img.alpha_composite(big, (x + 8, y + 8))
        d.text((x + 8, y + cell - 2), name, fill=(40, 40, 40, 255))
    img.save(path)


def main(args):
    want_sheet = "--sheet" in args
    names = [a for a in args if not a.startswith("--")] or items()
    OUT.mkdir(parents=True, exist_ok=True)
    defaults = default_attachments()
    done = {}
    for n in names:
        spr = render(n, defaults=defaults.get(n, {}))
        spr.save(OUT / f"{n}.png")
        done[n] = spr
        print("sprite", n)
    if want_sheet:
        sheet(done, HERE / "previews" / "item_icons.sheet.png")
        print("sheet tools/modelgen/previews/item_icons.sheet.png")


if __name__ == "__main__":
    main(sys.argv[1:])
