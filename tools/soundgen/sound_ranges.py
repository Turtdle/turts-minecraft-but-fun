"""
How far away other players hear each sound: writes "attenuation_distance" into sounds.json from the ranges the Java code
registers (registry/ModSounds.java: fixed("name", range)), so the code stays the one place a range is set.

Why: the server sends a positional sound to players within the registered range, but each client fades it out over
max(volume, 1) x attenuation_distance, which defaults to 16 blocks. Without this, other players stop hearing a rifle
at 16 blocks however far the packet went (singleplayer hides it: the shooter hears their own copy at full volume).
Sounds played louder than 1 (VOLUME below) already carry volume x further, so their distance is divided by it, making
them fade out where the packet range ends.

    python tools/soundgen/sound_ranges.py        # run after any script that rewrites sounds.json (make_sounds.py runs it)
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MOD_SOUNDS = ROOT / "src/main/java/com/modernarmory/registry/ModSounds.java"
SOUNDS_JSON = ROOT / "src/main/resources/assets/modernarmory/sounds.json"
# loudest volume each of these is played at (grep playSound ... ModSounds.X, <volume>)
VOLUME = {"flashbang": 4.0, "drone_detonation": 4.0}


def ranges() -> dict:
    src = MOD_SOUNDS.read_text(encoding="utf-8")
    return {name: float(r) for name, r in re.findall(r'fixed\("([a-z0-9_]+)",\s*([0-9.]+)f\)', src)}


def main():
    data = json.loads(SOUNDS_JSON.read_text(encoding="utf-8"))
    done = []
    for name, rng in ranges().items():
        entry = data.get(name)
        if not entry:
            print(f"warning: {name} has a range in ModSounds but no sounds.json entry")
            continue
        dist = max(16, round(rng / max(1.0, VOLUME.get(name, 1.0))))
        for s in entry.get("sounds", []):
            if isinstance(s, dict):
                s["attenuation_distance"] = dist
        done.append(f"{name}={dist}")
    SOUNDS_JSON.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print(f"attenuation_distance set on {len(done)} sounds: " + ", ".join(done))


if __name__ == "__main__":
    main()
