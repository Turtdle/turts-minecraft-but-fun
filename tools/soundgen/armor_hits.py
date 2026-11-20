"""
Armour impact sounds: a bullet hitting body armour (armor.BallisticArmor), heard at the target by everyone near
and by the shooter as their hit marker. Two kinds, by what was hit:

  * armor_hit_soft  - soft armour (NIJ IIIA aramid vests and helmets): a dull, padded impact
  * armor_hit_plate - hard armour (rifle / ceramic plates, netherite, the bomb suit): a hard plate strike

    python tools/soundgen/armor_hits.py              # writes the two picks + sounds.json entries
    python tools/soundgen/armor_hits.py --candidates <dir>   # every style as <dir>/<kind>_<style>.wav

The styles below are the options; SOFT / PLATE pick the ones in the game.
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
class Impact:
    """An impact from four layers: a crack (band-limited noise burst, the strike), a ring (decaying sine partials,
    a struck plate), a thump (a low sine whose pitch drops, the blow into the body) and a cloth (low, slower noise:
    fabric and padding absorbing it). Every layer is scaled by its weight; 0 leaves it out."""
    crack: float = 1.0
    crack_lo: float = 1500
    crack_hi: float = 6000
    crack_decay: float = 0.006
    ring: float = 0.0
    ring_hz: tuple = (2400.0,)
    ring_decay: float = 0.12
    thump: float = 0.0
    thump_hz: float = 120
    thump_decay: float = 0.04
    cloth: float = 0.0
    cloth_hi: float = 900
    cloth_decay: float = 0.05
    length: float = 0.45
    peak: float = 0.8

    def render(self, seed: int = 1) -> np.ndarray:
        rng = np.random.default_rng(seed)
        n = int(SR * self.length)
        t = np.arange(n) / SR
        x = _band(rng.standard_normal(n), self.crack_lo, self.crack_hi) * _env(n, 0.0002, self.crack_decay) * self.crack * 2.5
        for k, f in enumerate(self.ring_hz if self.ring else ()):
            x += np.sin(2 * np.pi * f * t + k) * _env(n, 0.0004, self.ring_decay / (1 + 0.35 * k)) * self.ring / (1 + 0.6 * k)
        if self.thump:
            f = self.thump_hz * (1 + 0.8 * np.exp(-t / 0.01))
            x += np.sin(2 * np.pi * np.cumsum(f) / SR) * _env(n, 0.0008, self.thump_decay) * self.thump
        if self.cloth:
            x += _band(rng.standard_normal(n), 80, self.cloth_hi) * _env(n, 0.002, self.cloth_decay) * self.cloth * 2.0
        fade = int(0.015 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
        return x / (np.max(np.abs(x)) + 1e-9) * self.peak


SOFT_STYLES = {
    # a fist into a sandbag: deep thump, padding, almost no crack
    "punch": Impact(crack=0.25, crack_lo=600, crack_hi=2200, crack_decay=0.004, thump=1.0, thump_hz=85, thump_decay=0.06,
                    cloth=0.6, cloth_hi=700, cloth_decay=0.07, length=0.35),
    # a hard slap on thick canvas: a mid crack, a short knock, cloth
    "thwack": Impact(crack=0.7, crack_lo=500, crack_hi=2500, crack_decay=0.008, thump=0.7, thump_hz=140, thump_decay=0.035,
                     cloth=0.5, cloth_hi=1200, cloth_decay=0.05, length=0.3),
    # a muffled pop: a short bright snap swallowed by padding
    "muffled_pop": Impact(crack=0.9, crack_lo=1200, crack_hi=4500, crack_decay=0.003, thump=0.5, thump_hz=110, thump_decay=0.03,
                          cloth=0.8, cloth_hi=600, cloth_decay=0.09, length=0.35),
}
PLATE_STYLES = {
    # hardened steel plate: a bright, ringing ting
    "steel_ting": Impact(crack=0.8, crack_lo=2500, crack_hi=9000, crack_decay=0.003, ring=1.0, ring_hz=(1850.0, 2930.0, 4410.0),
                         ring_decay=0.28, thump=0.35, thump_hz=150, thump_decay=0.03, length=0.6),
    # heavier plate: lower, shorter clank with a body thump
    "clank": Impact(crack=0.7, crack_lo=1500, crack_hi=6000, crack_decay=0.005, ring=0.8, ring_hz=(920.0, 1460.0, 2310.0),
                    ring_decay=0.14, thump=0.7, thump_hz=120, thump_decay=0.04, length=0.45),
    # ceramic: a sharp crack that doesn't ring (ceramic shatters), a short tick, a thump
    "ceramic_crack": Impact(crack=1.0, crack_lo=2000, crack_hi=10000, crack_decay=0.012, ring=0.25, ring_hz=(3300.0, 5200.0),
                            ring_decay=0.03, thump=0.6, thump_hz=130, thump_decay=0.035, length=0.35),
    # a thunk then a quick ping: plate on a padded carrier
    "thunk_ping": Impact(crack=0.5, crack_lo=1000, crack_hi=5000, crack_decay=0.004, ring=0.7, ring_hz=(2600.0, 4100.0),
                         ring_decay=0.09, thump=0.9, thump_hz=100, thump_decay=0.045, cloth=0.3, cloth_hi=800, length=0.45),
}

# the picks in the game (2026-11-20, the user chose "pop and crack")
SOFT = SOFT_STYLES["muffled_pop"]
PLATE = PLATE_STYLES["ceramic_crack"]


def write_candidates(out: Path):
    out.mkdir(parents=True, exist_ok=True)
    for kind, styles in (("soft", SOFT_STYLES), ("plate", PLATE_STYLES)):
        for name, s in styles.items():
            sf.write(out / f"{kind}_{name}.wav", s.render().astype(np.float32), SR, subtype="PCM_16")
            print("wrote", kind, name)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, s in (("armor_hit_soft", SOFT), ("armor_hit_plate", PLATE)):
        sf.write(OUT / f"{name}.ogg", s.render().astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", name)
    data = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    for name in ("armor_hit_soft", "armor_hit_plate"):
        data[name] = {"subtitle": f"subtitles.modernarmory.{name}", "sounds": [{"name": f"modernarmory:{name}", "volume": 0.8}]}
    json.dump(data, open(SOUNDS_JSON, "w", encoding="utf-8"), indent=2)
    __import__("sound_ranges").main()  # keep other players' hearing distances (sound_ranges.py)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--candidates":
        write_candidates(Path(sys.argv[2]))
    else:
        main()
