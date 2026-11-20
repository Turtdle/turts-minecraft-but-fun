"""
Drone sounds (Java: ModSounds.DRONE_*; catalog: drone/Drones.java).

    python tools/soundgen/drone_sounds.py      # writes the drone .ogg files + their sounds.json entries

  * Rotor loops (2 s, seamless): four rotors at slightly different speeds beat against each other. The
    tone is the blade-pass frequency (blades x revs/s) with its harmonics, the motors' electrical whine
    (pole pairs x revs/s) and broadband airflow noise pulsing with the blade passes.
      - drone_rotor_scout: Skydio X2D-like, big slow two-blade props (~95 rev/s -> ~190 Hz blade pass),
        a soft hum with a faint whine.
      - drone_rotor_fpv: 7-inch FPV, three-blade props at ~300 rev/s (~900 Hz blade pass) and hard-driven
        motors: the angry buzz.
    In game the loop's pitch and volume follow the rotor effort (client/DroneSounds).
  * Controller chirps: drone_beep (link / deploy / pickup; pitched down = refused), drone_spot (target
    tagged), drone_arm (charge live), drone_warning (low battery / link going).
  * drone_crash: a plastic airframe and props hitting something.
  * drone_detonation: the FPV's charge: a sharp, heavy blast with debris (on top of the explosion).

Loops are made periodic (every tone a whole number of cycles in the loop; noise built in the frequency
domain) so they repeat without a click.
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf

import make_sounds as ms
import shot_styles as ss

SR = ms.SR
LOOP = 2.0
SOUNDS_JSON = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/sounds.json"


def _periodic_noise(n, rng, lo, hi):
    """Band-limited noise that loops seamlessly over n samples."""
    spec = np.zeros(n // 2 + 1, complex)
    f = np.fft.rfftfreq(n, 1 / SR)
    band = (f >= lo) & (f <= hi)
    spec[band] = np.exp(1j * rng.uniform(0, 2 * np.pi, band.sum())) / np.sqrt(np.maximum(f[band], 1) / lo)
    x = np.fft.irfft(spec, n)
    return x / np.abs(x).max()


def rotor_loop(seed, rps, blades, pole_pairs, harmonics, whine, air, air_band, detune=(0.0, 0.021, 0.043, 0.064)):
    """A quad's rotor noise, LOOP seconds, seamless. rps: rotor revs/s; detune: per-rotor speed spread."""
    rng = np.random.default_rng(seed)
    n = int(SR * LOOP)
    tt = np.arange(n) / SR
    snap = lambda f: round(f * LOOP) / LOOP  # whole cycles per loop
    x = np.zeros(n)
    air_noise = _periodic_noise(n, rng, *air_band)
    for k, d in enumerate(detune):
        rev = snap(rps * (1 + d))
        bpf = snap(rev * blades)
        for h in range(1, harmonics + 1):
            amp = 1.0 / h ** 1.1 * rng.uniform(0.8, 1.1)
            x += amp * np.sin(2 * np.pi * snap(bpf * h) * tt + rng.uniform(0, 2 * np.pi))
        # motor whine: electrical frequency and its 2nd harmonic
        ef = snap(rev * pole_pairs)
        x += whine * (np.sin(2 * np.pi * ef * tt + rng.uniform(0, 6.3)) + 0.4 * np.sin(2 * np.pi * snap(2 * ef) * tt))
        # airflow pulsing with this rotor's blade passes
        pulse = 0.6 + 0.4 * np.sin(2 * np.pi * bpf * tt + rng.uniform(0, 6.3))
        x += air * np.roll(air_noise, k * n // 4) * pulse
    # a slow wobble as the flight controller trims the motors (whole cycles in the loop)
    x *= 1 + 0.06 * np.sin(2 * np.pi * snap(1.5) * tt) + 0.04 * np.sin(2 * np.pi * snap(3.5) * tt + 1.0)
    x = np.tanh(x / np.abs(x).max() * 1.4)
    return ms.norm(x, 0.8)


def tone(freq, dur, attack=0.004, release=0.03, harm=0.25):
    tt = ms.t(dur)
    shape = np.minimum(1, tt / attack) * np.minimum(1, (dur - tt) / release)
    return (np.sin(2 * np.pi * freq * tt) + harm * np.sin(2 * np.pi * 2 * freq * tt)) * shape


def beeps(seq, gap=0.04):
    """[(freq, dur), ...] played one after another."""
    parts = []
    for f, d in seq:
        parts += [tone(f, d), np.zeros(int(SR * gap))]
    return ms.norm(np.concatenate(parts), 0.7)


def crash(seed):
    rng = np.random.default_rng(seed)
    n = int(SR * 0.6)
    x = np.zeros(n)
    ms.place(x, ms.thud(seed, 0.8), 0.0)
    for i in range(14):  # plastic and carbon cracking, prop blades snapping
        burst = ms.highpass(rng.standard_normal(int(SR * rng.uniform(0.004, 0.02))), rng.uniform(1500, 4000))
        burst *= np.exp(-np.arange(len(burst)) / (SR * 0.006))
        ms.place(x, burst * rng.uniform(0.3, 0.9), rng.uniform(0, 0.25))
    for i in range(3):
        ms.place(x, ms.metal_click(seed + 10 + i, pitch=rng.uniform(1.2, 1.8), weight=0.4) * 0.5, rng.uniform(0.02, 0.3))
    return ms.norm(ms.room(x, 0.12)[:n], 0.9)


def detonation(seed):
    style = ss.replace(ss.SHOT_STYLE, snap=1.5, thwack=1.2, body=1.2, sub=1.0, tail=0.8, tail_decay=0.8, top=8000)
    x = style.shot(ss.Calibre(body_hz=70, weight=1.6, snap=1.6, sub_hz=32, length=2.4, mech=0.0, loud_db=9), seed)
    rng = np.random.default_rng(seed)
    for i in range(40):  # debris and fragments landing
        ms.place(x, ms.metal_click(int(rng.integers(1000, 9999)), pitch=rng.uniform(0.6, 1.4), weight=0.5)
                 * rng.uniform(0.03, 0.12), rng.uniform(0.15, 1.6))
    return ms.norm(x, 0.85)  # vorbis overshoots a little


def sounds():
    return {
        "drone_rotor_scout": rotor_loop(950, rps=95, blades=2, pole_pairs=7, harmonics=7, whine=0.12, air=0.9, air_band=(150, 3500)),
        "drone_rotor_fpv": rotor_loop(960, rps=300, blades=3, pole_pairs=7, harmonics=5, whine=0.35, air=0.8, air_band=(300, 9000)),
        "drone_beep": beeps([(2200, 0.07), (2200, 0.07)]),
        "drone_spot": beeps([(1600, 0.06), (2400, 0.11)], gap=0.02),
        "drone_arm": beeps([(1200, 0.08), (1600, 0.08), (2000, 0.16)]),
        "drone_warning": beeps([(900, 0.12), (700, 0.12), (900, 0.12), (700, 0.12)], gap=0.02),
        "drone_crash": crash(970),
        "drone_detonation": detonation(980),
    }


# event -> (volume, subtitle key)
EVENTS = {
    "drone_rotor_scout": (0.7, "drone_rotor"),
    "drone_rotor_fpv": (0.75, "drone_rotor"),
    "drone_beep": (0.5, "drone_beep"),
    "drone_spot": (0.6, "drone_spot"),
    "drone_arm": (0.6, "drone_arm"),
    "drone_warning": (0.5, "drone_warning"),
    "drone_crash": (0.9, "drone_crash"),
    "drone_detonation": (1.0, "drone_detonation"),
}


def update_sounds_json():
    data = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    for event, (vol, sub) in EVENTS.items():
        entry = {"name": "modernarmory:" + event, "volume": vol}
        if event.startswith("drone_rotor"):
            entry["stream"] = False
        data[event] = {"sounds": [entry], "subtitle": "subtitles.modernarmory." + sub}
    json.dump(data, open(SOUNDS_JSON, "w", encoding="utf-8"), indent=2)


def main():
    ms.OUT.mkdir(parents=True, exist_ok=True)
    for name, x in sounds().items():
        sf.write(ms.OUT / f"{name}.ogg", x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", name)
    update_sounds_json()
    print(f"sounds.json: {len(EVENTS)} drone events")


if __name__ == "__main__":
    main()
