"""
Hit-marker sounds: what the shooter hears when their bullet lands, one for a body hit and one for a headshot.
Short, dry, interface-like sounds (not world sounds): played only to the shooter, not positional.

    python tools/soundgen/hit_markers.py     # writes hit_body.ogg / hit_head.ogg + sounds.json entries

The styles below are the options; BODY / HEAD pick the ones in the game.
"""
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_sounds import OUT, SR  # noqa: E402
from shot_styles import _band, _env  # noqa: E402

SOUNDS_JSON = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/sounds.json"


@dataclass(frozen=True)
class Marker:
    """A hit marker built from up to three layers: a click (band-limited noise burst), a ring (decaying sine
    partials, like a struck plate) and a knock (a low, short sine thump). `repeat` > 1 plays it again after
    `gap` seconds (a double tick)."""
    click: float = 1.0
    click_lo: float = 2000
    click_hi: float = 6000
    click_decay: float = 0.004
    ring: float = 0.0
    ring_hz: tuple = (2400.0,)
    ring_decay: float = 0.12
    knock: float = 0.0
    knock_hz: float = 180
    knock_decay: float = 0.02
    repeat: int = 1
    gap: float = 0.045
    length: float = 0.3
    peak: float = 0.7

    def render(self, seed: int = 1) -> np.ndarray:
        rng = np.random.default_rng(seed)
        n = int(SR * self.length)
        t = np.arange(n) / SR
        one = _band(rng.standard_normal(n), self.click_lo, self.click_hi) * _env(n, 0.0002, self.click_decay) * self.click * 2.5
        if self.ring:
            for k, f in enumerate(self.ring_hz):
                one += np.sin(2 * np.pi * f * t + k) * _env(n, 0.0005, self.ring_decay / (1 + 0.4 * k)) * self.ring / (1 + k)
        if self.knock:
            f = self.knock_hz * (1 + 0.6 * np.exp(-t / 0.008))
            one += np.sin(2 * np.pi * np.cumsum(f) / SR) * _env(n, 0.0005, self.knock_decay) * self.knock
        x = np.zeros(n + int(SR * self.gap * (self.repeat - 1)))
        for r in range(self.repeat):
            i = int(SR * self.gap * r)
            x[i:i + n] += one * (1.0 if r == 0 else 0.8)
        x = x[:n]
        fade = int(0.01 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
        return x / (np.max(np.abs(x)) + 1e-9) * self.peak


BODY_STYLES = {
    "tick": Marker(click_lo=2500, click_hi=6500, click_decay=0.003, length=0.12),
    "thock": Marker(click=0.5, click_lo=900, click_hi=2500, click_decay=0.006, knock=0.8, knock_hz=420, knock_decay=0.018, length=0.15),
    "thud": Marker(click=0.35, click_lo=600, click_hi=1800, click_decay=0.008, knock=1.0, knock_hz=150, knock_decay=0.035, length=0.2),
    "snap": Marker(click_lo=1800, click_hi=5000, click_decay=0.005, ring=0.25, ring_hz=(3100.0,), ring_decay=0.03, length=0.15),
}
HEAD_STYLES = {
    "ding": Marker(click=0.6, click_lo=3000, click_hi=8000, click_decay=0.003, ring=1.0, ring_hz=(2350.0, 3720.0, 5410.0),
                   ring_decay=0.16, length=0.45),
    "crack": Marker(click=1.0, click_lo=2500, click_hi=9000, click_decay=0.006, ring=0.45, ring_hz=(4100.0, 6300.0),
                    ring_decay=0.05, knock=0.4, knock_hz=300, knock_decay=0.02, length=0.25),
    "double": Marker(click_lo=2800, click_hi=7500, click_decay=0.003, repeat=2, gap=0.05, length=0.2),
    "ping_thud": Marker(click=0.4, click_lo=2500, click_hi=7000, click_decay=0.003, ring=0.9, ring_hz=(2900.0, 4600.0),
                        ring_decay=0.12, knock=0.8, knock_hz=170, knock_decay=0.03, length=0.4),
}

# the picks in the game (2026-11-19, the user chose "tick and ding")
BODY = BODY_STYLES["tick"]
HEAD = HEAD_STYLES["ding"]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, m in (("hit_body", BODY), ("hit_head", HEAD)):
        sf.write(OUT / f"{name}.ogg", m.render().astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", name)
    data = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    for name in ("hit_body", "hit_head"):
        data[name] = {"subtitle": f"subtitles.modernarmory.{name}", "sounds": [{"name": f"modernarmory:{name}", "volume": 0.6}]}
    json.dump(data, open(SOUNDS_JSON, "w", encoding="utf-8"), indent=2)
    __import__("sound_ranges").main()  # keep other players' hearing distances (sound_ranges.py)


if __name__ == "__main__":
    main()
