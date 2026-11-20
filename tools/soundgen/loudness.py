"""
In-game loudness ranking of every shot sound. sounds.json volumes are computed from this table AFTER the
files are written (make_sounds.main runs it last), so changing a sound style never flattens the ranking:
a .338 stays louder than a 5.56 whatever the files look like.

    python tools/soundgen/loudness.py      # re-measure the current files and rewrite the volumes

Loudness = ear-weighted level (200 Hz - 6 kHz band, loudest 100 ms of the first 0.4 s, i.e. the shot and
not a bolt cycle after it) x the sounds.json volume. Ranks are dB relative to the M4 (suppressed sounds:
relative to the suppressed M4). Minecraft caps volume at 1.0, so a calibre that needs more than that gets
extra level baked into its files instead (shot_styles.Calibre.loud_db); the script warns when it falls short.
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt

ROOT = Path(__file__).resolve().parents[2]
SOUNDS = ROOT / "src/main/resources/assets/modernarmory/sounds"
SOUNDS_JSON = ROOT / "src/main/resources/assets/modernarmory/sounds.json"

# where the two reference guns sit in game. loud: ~0.65x the original voicing (2026-11-19). suppressed: 4 dB
# under the loud M4 (raised from 9.5 dB under after "suppressed noises are too quiet", 2026-11-19)
REF_LEVEL = {"loud": 0.114, "suppressed": 0.114 * 10 ** (-4 / 20)}

# event -> (reference, dB relative to it)
RANK_DB = {
    "m4_fire": ("loud", 0.0),
    "fire_9mm_pistol": ("loud", -3.4),
    "fire_9mm_smg": ("loud", -2.5),
    "fire_68": ("loud", 1.0),
    "fire_762": ("loud", 1.0),
    "fire_12ga": ("loud", 1.5),
    "fire_300wm": ("loud", 3.0),
    "m2010_fire_loud": ("loud", 3.0),
    "fire_338": ("loud", 3.5),
    "fire_50bmg": ("loud", 5.0),
    "fire_50ae": ("loud", 2.5),
    "fire_44mag": ("loud", 2.0),
    "m4_fire_suppressed": ("suppressed", 0.0),
    "fire_9mm_suppressed": ("suppressed", -6.0),
    "fire_68_suppressed": ("suppressed", 1.0),
    "fire_762_suppressed": ("suppressed", 1.0),
    "fire_300wm_suppressed": ("suppressed", 3.0),
    "m2010_fire": ("suppressed", 3.0),
    "fire_338_suppressed": ("suppressed", 3.5),
}


def level(path: Path) -> float:
    x, sr = sf.read(path)
    if x.ndim > 1:
        x = x.mean(axis=1)
    x = sosfilt(butter(2, [200, 6000], btype="band", fs=sr, output="sos"), x)
    w = int(0.1 * sr)
    e = np.convolve(x ** 2, np.ones(w) / w, "valid")
    return float(np.sqrt(e[: int(0.4 * sr)].max()))


def apply(verbose=True):
    data = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    for event, (ref, db) in RANK_DB.items():
        if event not in data:
            continue
        sounds = data[event]["sounds"]
        names = [(s["name"] if isinstance(s, dict) else s).split(":")[1] for s in sounds]
        lv = float(np.median([level(SOUNDS / f"{n}.ogg") for n in names]))
        want = REF_LEVEL[ref] * 10 ** (db / 20)
        vol = round(min(1.0, want / lv), 2)
        got_db = 20 * np.log10(lv * vol / REF_LEVEL[ref])
        data[event]["sounds"] = [dict(s, volume=vol) if isinstance(s, dict) else {"name": s, "volume": vol}
                                 for s in sounds]
        if verbose:
            short = "" if abs(got_db - db) < 0.3 else f"   WARNING: {db - got_db:.1f} dB short, raise loud_db"
            print(f"  {event:24s} vol {vol:.2f}  -> {got_db:+5.1f} dB vs {ref} M4 (want {db:+.1f}){short}")
    json.dump(data, open(SOUNDS_JSON, "w", encoding="utf-8"), indent=2)
    __import__("sound_ranges").main()  # keep other players' hearing distances (sound_ranges.py)


if __name__ == "__main__":
    apply()
