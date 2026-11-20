"""
Worn body armour (kevlar) models: one GeckoLib armour model per tier, holding every piece of that tier
(helmet, vest / jacket, trousers). In game GeckoLib shows only the bones of the slot an item is worn in
(head -> armorHead, chest -> armorBody + arms, legs -> armorRightLeg / armorLeftLeg, feet -> boots), so
every item of a tier points at the same model.

    python tools/modelgen/armor.py              # build every tier in tools/modelgen/armor/
    python tools/modelgen/armor.py kevlar_eod   # just this one

Each file in armor/ defines `MODEL` (the tier's model name, e.g. "kevlar_police"), `PIECES`
({item id: slot}, slot one of "head", "chest", "legs", "feet") and `def model() -> armorygen.Model`
(start it with `new_armor(MODEL)`, hang boxes off the armour bones). The build writes:
  * geo / texture / (empty) animation file under the mod's assets, like the guns;
  * per item: a flat inventory icon (textures/item/icon/<id>.png, rendered from the model with only
    that piece's bones) and an item/generated item model pointing at it;
  * previews in tools/modelgen/previews/: <model>.preview.png (the armour alone) and
    <model>.worn.preview.png (on a Steve-shaped mannequin, front / side / back / 3-4).

Coordinates: armorygen authoring space, 16 units = 1 block, Y up, the wearer faces -Z, +X is the
WEARER'S RIGHT. The vanilla player body (what the armour is built around) is in BODY below; vanilla
armour sits 1 unit off the skin on the helmet / chest and 0.5 on the legs, so keep plates and pouches
at least ~0.5 off the body boxes or they clip through the skin when limbs swing.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import armorygen as ag  # noqa: E402
import sprite  # noqa: E402

ROOT = HERE.parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "modernarmory"
PREVIEWS = HERE / "previews"
ICON_DIR = ASSETS / "textures" / "item" / "icon"
ICON_SIZE = 32

# GeckoLib armour bones (names fixed by GeckoLib) and their pivots: the vanilla HumanoidModel joints
# (neck, shoulders, hips), in authoring space (+X = wearer's right).
HEAD, BODY_BONE = "armorHead", "armorBody"
R_ARM, L_ARM = "armorRightArm", "armorLeftArm"
R_LEG, L_LEG = "armorRightLeg", "armorLeftLeg"
R_BOOT, L_BOOT = "armorRightBoot", "armorLeftBoot"
PIVOTS = {
    HEAD: (0, 24, 0), BODY_BONE: (0, 24, 0),
    R_ARM: (5, 22, 0), L_ARM: (-5, 22, 0),
    R_LEG: (2, 12, 0), L_LEG: (-2, 12, 0),
    R_BOOT: (2, 12, 0), L_BOOT: (-2, 12, 0),
}
# which bones each slot shows in game
SLOT_BONES = {
    "head": (HEAD,),
    "chest": (BODY_BONE, R_ARM, L_ARM),
    "legs": (R_LEG, L_LEG),
    "feet": (R_BOOT, L_BOOT),
}

# The vanilla player body the armour goes round: (min corner, max corner) per part, authoring space.
BODY = {
    "head": ((-4, 24, -4), (4, 32, 4)),
    "torso": ((-4, 12, -2), (4, 24, 2)),
    "right_arm": ((4, 12, -2), (8, 24, 2)),
    "left_arm": ((-8, 12, -2), (-4, 24, 2)),
    "right_leg": ((0, 0, -2), (4, 12, 2)),
    "left_leg": ((-4, 0, -2), (0, 12, 2)),
}
# body part -> the armour bone that moves with it
PART_BONE = {"head": HEAD, "torso": BODY_BONE, "right_arm": R_ARM, "left_arm": L_ARM,
             "right_leg": R_LEG, "left_leg": L_LEG}

ICON_VIEW = sprite.View((-0.35, -0.3, 1.0), margin=1.0)   # from the front, a little right and above


def new_armor(name: str, density: int = 1) -> ag.Model:
    """A model with the eight GeckoLib armour bones at the player's joints, nothing in them yet."""
    m = ag.Model(name, density)
    for bone, pivot in PIVOTS.items():
        m.bone(bone, "root", pivot)
    return m


def around(part: str, gap: float, *, top: float | None = None, bottom: float | None = None):
    """(origin, size) of a shell `gap` units off the skin all round body part `part`, optionally
    cut to y in [bottom, top]. Handy for a vest / sleeve / trouser base layer."""
    (x0, y0, z0), (x1, y1, z1) = BODY[part]
    y0 = y0 if bottom is None else bottom
    y1 = y1 if top is None else top
    lo = (x0 - gap, y0 - (gap if bottom is None else 0), z0 - gap)
    hi = (x1 + gap, y1 + (gap if top is None else 0), z1 + gap)
    return lo, tuple(h - l for l, h in zip(lo, hi))


# ---- build ------------------------------------------------------------------------------------------

SKIN = {"mq_skin": (196, 150, 118), "mq_shirt": (64, 156, 172), "mq_pants": (62, 62, 140),
        "mq_hair": (68, 46, 30), "mq_shoe": (70, 70, 74)}


def _add_mannequin(m: ag.Model):
    """Steve-shaped body under the armour, for the worn preview only (never exported)."""
    for name, rgb in SKIN.items():
        m.material(name, rgb, 3, 0.85)
    parts = {"head": "mq_skin", "torso": "mq_shirt", "right_arm": "mq_skin", "left_arm": "mq_skin",
             "right_leg": "mq_pants", "left_leg": "mq_pants"}
    for part, mat in parts.items():
        lo, hi = BODY[part]
        b = m.bone(f"mq_{part}", PART_BONE[part], PIVOTS[PART_BONE[part]])
        b.box(lo, tuple(h - l for l, h in zip(lo, hi)), mat)
    lo, hi = BODY["head"]
    m.bones["mq_head"].box((lo[0], hi[1] - 2, lo[2]), (8, 2, 8), "mq_hair")


def _item_model(item_id: str) -> dict:
    return {"parent": "minecraft:item/generated", "textures": {"layer0": f"modernarmory:item/icon/{item_id}"}}


def build_one(path: Path) -> dict:
    spec = ag_load(path)
    m = spec.model()
    stats = m.build(ASSETS, PREVIEWS)
    # inventory icons: only the piece's own bones
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    armour_bones = {b for bones in SLOT_BONES.values() for b in bones}
    for item_id, slot in spec.PIECES.items():
        hidden = armour_bones - set(SLOT_BONES[slot])
        sprite.render(m, ICON_VIEW, ICON_SIZE, hidden=hidden).save(ICON_DIR / f"{item_id}.png")
        (ASSETS / "models" / "item" / f"{item_id}.json").write_text(json.dumps(_item_model(item_id), indent=1))
    # the shared model's own item model isn't an item: drop the default one build() wrote
    own = ASSETS / "models" / "item" / f"{m.name}.json"
    if m.name not in spec.PIECES and own.exists():
        own.unlink()
    # worn preview on the mannequin
    _add_mannequin(m)
    tw, th = m._pack()
    tex = m._paint(tw, th)
    out = PREVIEWS / f"{m.name}.worn.preview.png"
    ag.render_preview(m, out, tex, label="worn")
    stats["worn_preview"] = str(out)
    stats["pieces"] = spec.PIECES
    return stats


def ag_load(path: Path):
    import importlib.util
    s = importlib.util.spec_from_file_location(f"armor_{path.stem}", path)
    mod = importlib.util.module_from_spec(s)
    s.loader.exec_module(mod)
    return mod


def main(names):
    files = sorted((HERE / "armor").glob("*.py"))
    if names:
        files = [f for f in files if f.stem in names]
    for f in files:
        stats = build_one(f)
        z = stats.pop("zfight")
        print(json.dumps(stats))
        for r in z["remaining"]:
            print(f"WARNING: {f.stem} still z-fights: {r}")


if __name__ == "__main__":
    main(sys.argv[1:])
