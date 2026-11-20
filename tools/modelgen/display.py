"""
Write every gun's item model JSON (models/item/<gun>.json) display transforms.

    python tools/modelgen/display.py            # write all guns in display_items.json
    python tools/modelgen/display.py m4 m17     # just these
    python tools/modelgen/display.py --check    # exit 1 if any file is out of date (writes nothing)

Single source of truth:
  * First / third person framing per VIEW CLASS (RIFLE, PISTOL, HEAVY) lives in
    src/main/resources/assets/modernarmory/view_classes.json. The game reads the same file
    (ViewClass.java) for the aim-down-sights maths, so a mod-wide framing change is one edit
    there + running this script.
  * Which class each gun uses, and optional hand-tuned inventory / item-frame / ground transforms,
    are in tools/modelgen/display_items.json:
        "m4":   {"view": "RIFLE", "gui": {...}, "ground": {...}, "fixed": {...}}
        "m4a1": {"parent": "m4"}          # same model and framing as another gun
        "m17":  {"view": "PISTOL"}        # gui / ground / fixed fitted from the model's bounds
    The "view" must match the gun's GunSpec view class in Guns.java; the autotest logs
    "[autotest] framing MISMATCH ..." for any gun whose baked item model disagrees.
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "modernarmory"
VIEW_CLASSES = ASSETS / "view_classes.json"
ITEMS = HERE / "display_items.json"
HAND_SLOTS = ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand")
ICON_SLOTS = ("gui", "ground", "fixed")
# bones that are not part of the bare gun when fitting icons
SKIP_PREFIXES = ("att_", "flash_")
SKIP_BONES = ("right_arm", "left_arm")


def _transform(t: dict) -> dict:
    return {"rotation": t["rotation"], "translation": t["translation"], "scale": t["scale"]}


def gun_bounds(model_id: str):
    """(lo, hi) of the bare gun's cubes (no arms / attachments), model units."""
    geo = json.loads((ASSETS / "geo" / "item" / f"{model_id}.geo.json").read_text())
    bones = geo["minecraft:geometry"][0]["bones"]
    parent = {b["name"]: b.get("parent") for b in bones}

    def skipped(name):
        while name:
            if name in SKIP_BONES or name.startswith(SKIP_PREFIXES):
                return True
            name = parent.get(name)
        return False

    lo, hi = [1e9] * 3, [-1e9] * 3
    for b in bones:
        if skipped(b["name"]):
            continue
        for c in b.get("cubes", []):
            for i in range(3):
                lo[i] = min(lo[i], c["origin"][i])
                hi[i] = max(hi[i], c["origin"][i] + c["size"][i])
    return lo, hi


def fitted_icons(model_id: str) -> dict:
    """Inventory (side view, muzzle right), item frame and dropped-item transforms fitted to the
    model's length / height, calibrated so the M4 comes out close to its hand-tuned values."""
    lo, hi = gun_bounds(model_id)
    length, height = hi[2] - lo[2], hi[1] - lo[1]
    cy, cz = (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2
    s = round(min(15.0 / length, 15.0 / height, 1.6), 2)
    sf = round(s * 1.67, 2)
    sg = round(s * 0.67, 2)
    return {
        "gui": {"rotation": [0, -90, 0], "translation": [round(s * cz, 2), round(-s * cy, 2), 0], "scale": [s, s, s]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [sg, sg, sg]},
        "fixed": {"rotation": [0, 90, 0], "translation": [round(-sf * cz, 2), round(-sf * cy, 2), 0], "scale": [sf, sf, sf]},
    }


SPRITE_LOADER = "neoforge:separate_transforms"


def sprite_model(item: str) -> dict:
    """models/item/icon/<item>.json: the flat inventory sprite (textures/item/icon/<item>.png, item_icons.py)."""
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"modernarmory:item/icon/{item}"}}


def item_model(gun: str, entry: dict, classes: dict) -> dict:
    """The item model: the 3D GeckoLib model everywhere except GUIs (inventory, hotbar, ...), which show the
    2D sprite. 1.21.1 reads NeoForge's separate_transforms loader directly; the 26.3 build turns it into an
    item definition that selects on the display context (versions/26.3/build.gradle)."""
    if "parent" in entry:
        return {"parent": f"modernarmory:item/{entry['parent']}"}
    return {"loader": SPRITE_LOADER, "gui_light": "front", "base": geo_model(gun, entry, classes),
            "perspectives": {"gui": {"parent": f"modernarmory:item/icon/{gun}"}}}


def geo_model(gun: str, entry: dict, classes: dict) -> dict:
    """The 3D model: GeckoLib renders it (builtin/entity) with the view class's hand transforms; its "gui"
    transform frames the gunsmith's big 3D preview on 26.3 (1.21.1 turns the "fixed" one round instead)."""
    view = entry["view"]
    if view not in classes:
        raise SystemExit(f"{gun}: unknown view class {view!r} (have {', '.join(classes)})")
    cls = classes[view]
    icons = fitted_icons(entry.get("model", gun)) if any(k not in entry for k in ICON_SLOTS) else {}
    display = {k: _transform(entry[k]) if k in entry else icons[k] for k in ICON_SLOTS}
    display.update({k: _transform(cls[k]) for k in HAND_SLOTS})
    return {"parent": "builtin/entity", "gui_light": "front", "display": display}


def load():
    classes = {k: v for k, v in json.loads(VIEW_CLASSES.read_text()).items() if not k.startswith("_")}
    items = {k: v for k, v in json.loads(ITEMS.read_text()).items() if not k.startswith("_")}
    return classes, items


def main(args):
    check = "--check" in args
    names = [a for a in args if not a.startswith("--")]
    classes, items = load()
    if names:
        missing = set(names) - set(items)
        if missing:
            raise SystemExit(f"not in display_items.json: {', '.join(sorted(missing))}")
        items = {k: items[k] for k in names}
    stale = []
    files = {}
    for gun, entry in items.items():
        files[ASSETS / "models" / "item" / f"{gun}.json"] = item_model(gun, entry, classes)
        if "parent" not in entry:
            files[ASSETS / "models" / "item" / "icon" / f"{gun}.json"] = sprite_model(gun)
    for path, model in files.items():
        text = json.dumps(model, indent=1)
        old = path.read_text() if path.exists() else None
        if old == text:
            continue
        stale.append(path.stem if path.parent.name != "icon" else "icon/" + path.stem)
        if not check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            print(f"wrote {path.relative_to(ROOT)}")
    if check and stale:
        raise SystemExit(f"out of date (run python tools/modelgen/display.py): {', '.join(stale)}")
    if not stale:
        print("all item models up to date")


if __name__ == "__main__":
    main(sys.argv[1:])
