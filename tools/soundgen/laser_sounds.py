"""
Laser turret sounds (Java: ModSounds.LASER_*; laser/TurretSeat).

    python tools/soundgen/laser_sounds.py      # writes the laser .ogg files + their sounds.json entries

A high-energy laser makes no "pew": the beam is silent. What you hear is the system working hard:
  * laser_hum (1 s, replayed while it fires): power electronics whine (a few kHz with a little FM), the
    thermal-management pumps and fans (low hum and air), steady enough to repeat without a seam.
  * laser_burn: the target's skin cooking under the beam: crackle and sizzle.
  * laser_lock: the operator's tracker chirp (rising pair); pitched up for a kill, down when boarding.
  * laser_overheat: three low warning tones.
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf

import make_sounds as ms

SR = ms.SR
SOUNDS_JSON = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/sounds.json"


def _noise(n, rng, lo, hi):
    spec = np.zeros(n // 2 + 1, complex)
    f = np.fft.rfftfreq(n, 1 / SR)
    band = (f >= lo) & (f <= hi)
    spec[band] = np.exp(1j * rng.uniform(0, 2 * np.pi, band.sum()))
    x = np.fft.irfft(spec, n)
    return x / np.abs(x).max()


def hum(seed, length=1.0):
    """Seamless 1 s loop: every tone a whole number of cycles, noise built periodic."""
    rng = np.random.default_rng(seed)
    n = int(SR * length)
    tt = np.arange(n) / SR
    snap = lambda f: round(f * length) / length
    x = 0.55 * np.sin(2 * np.pi * snap(60) * tt) + 0.3 * np.sin(2 * np.pi * snap(120) * tt + 0.7)  # pumps
    x += 0.18 * np.sin(2 * np.pi * snap(180) * tt + 1.3)
    whine = snap(3200)
    x += 0.16 * np.sin(2 * np.pi * whine * tt + 2.0 * np.sin(2 * np.pi * snap(6) * tt))  # power electronics
    x += 0.07 * np.sin(2 * np.pi * snap(6400) * tt)
    x += 0.22 * _noise(n, rng, 200, 2500)  # fans
    return ms.norm(x, 0.7)


def burn(seed, length=0.6):
    rng = np.random.default_rng(seed)
    n = int(SR * length)
    x = 0.25 * _noise(n, rng, 2000, 9000) * (0.6 + 0.4 * rng.random(n))
    for _ in range(26):  # crackles
        at = int(rng.uniform(0, n - 600))
        k = int(rng.integers(80, 500))
        x[at:at + k] += rng.uniform(0.3, 0.9) * rng.standard_normal(k) * np.exp(-np.arange(k) / (k / 4))
    x *= ms.env(n, 0.02, 0.5)
    return ms.norm(ms.highpass(x, 900), 0.8)


def tones(seq, gap=0.03):
    parts = []
    for f, d in seq:
        n = int(SR * d)
        tt = np.arange(n) / SR
        e = np.minimum(1, np.minimum(tt / 0.005, (d - tt) / 0.01))
        parts.append(np.sin(2 * np.pi * f * tt) * e * 0.6)
        parts.append(np.zeros(int(SR * gap)))
    return ms.norm(np.concatenate(parts), 0.7)


def sounds():
    return {
        "laser_hum": hum(1100),
        "laser_burn": burn(1110),
        "laser_lock": tones([(1800, 0.05), (2700, 0.09)], gap=0.015),
        "laser_overheat": tones([(520, 0.16), (520, 0.16), (520, 0.16)], gap=0.06),
    }


# event -> volume
EVENTS = {"laser_hum": 0.8, "laser_burn": 0.7, "laser_lock": 0.5, "laser_overheat": 0.6}


def update_sounds_json():
    data = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    for event, vol in EVENTS.items():
        data[event] = {"sounds": [{"name": "modernarmory:" + event, "volume": vol}], "subtitle": "subtitles.modernarmory." + event}
    json.dump(data, open(SOUNDS_JSON, "w", encoding="utf-8"), indent=2)
    __import__("sound_ranges").main()  # keep other players' hearing distances (sound_ranges.py)


def main():
    ms.OUT.mkdir(parents=True, exist_ok=True)
    for name, x in sounds().items():
        sf.write(ms.OUT / f"{name}.ogg", x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", name)
    update_sounds_json()
    print(f"sounds.json: {len(EVENTS)} laser events")


if __name__ == "__main__":
    main()
