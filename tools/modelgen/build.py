"""
Build gun models from code.

    python tools/modelgen/build.py            # build every gun in tools/modelgen/guns/
    python tools/modelgen/build.py m4         # build just one
    python tools/modelgen/build.py --check    # only check for z-fighting, write nothing;
                                              # exits 1 if any coplanar overlap is left

Every build removes z-fighting automatically (armorygen `Model.fix_coplanar_overlaps`: a detail
face lying in the plane of a bigger same-facing face is pushed out by 0.02 units) and prints
per gun how many coplanar overlaps were found and fixed, warning about any left over.

Each file in guns/ defines `def model() -> armorygen.Model`.
Outputs go straight into the mod's assets; previews go to tools/modelgen/previews/.
The item model JSON's display transforms are then (re)written by display.py from the gun's view
class (display_items.json + view_classes.json), so framing is never hand-edited per gun.
The geo / texture / animations always contain every bone (all attachments). A gun may set
`model.preview_configs = {name: set_of_hidden_bones}` (see attachments.loadout) to get extra
`<gun>.<name>.preview.png` loadout previews.
"""
import importlib.util
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
ROOT = HERE.parent.parent
import display  # noqa: E402  (needs HERE on sys.path)

ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "modernarmory"
PREVIEWS = HERE / "previews"


def load(path: Path):
    spec = importlib.util.spec_from_file_location(f"gun_{path.stem}", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.model()


def zfight_line(name: str, z: dict) -> str:
    return (f"z-fighting {name}: {z['found']} coplanar overlap(s) found, fixed by pushing "
            f"{z['faces_pushed']} face(s) ({z['pushes']} push(es)); {len(z['remaining'])} remaining")


def main(names):
    check = "--check" in names
    names = [n for n in names if n != "--check"]
    files = sorted((HERE / "guns").glob("*.py"))
    if names:
        files = [f for f in files if f.stem in names]
        missing = set(names) - {f.stem for f in files}
        if missing:
            sys.exit(f"no such gun spec: {', '.join(sorted(missing))}")
    left = {}
    for f in files:
        m = load(f)
        if check:
            z = m.fix_coplanar_overlaps()  # in memory only
            print(zfight_line(f.stem, z))
        else:
            stats = m.build(ASSETS, PREVIEWS, preview_configs=getattr(m, "preview_configs", None))
            z = stats.pop("zfight")
            print(json.dumps(stats))
            print(zfight_line(f.stem, z))
        for r in z["remaining"]:
            print(f"WARNING: {f.stem} still z-fights: {r}")
        if z["remaining"]:
            left[f.stem] = len(z["remaining"])
        if check:
            continue
        if f.stem in display.load()[1]:
            display.main([f.stem])
        else:
            print(f"note: {f.stem} is not in display_items.json; add it to get view-class framing")
    if left:
        print(f"WARNING: coplanar overlaps left in {len(left)} gun(s): {left}")
        if check:
            sys.exit(1)
    elif check:
        print(f"z-fighting check ok: {len(files)} gun(s), no coplanar overlaps left")


if __name__ == "__main__":
    main(sys.argv[1:])
