"""
Gunshot "styles": tighter, game-like shots (less harsh than the raw noise + heavy saturation of the
original Report). A shot = calibre profile (what the gun is) x style (how it should feel), so a
style choice applies mod-wide and every calibre keeps its own weight.

Layers of one shot:
  snap    very short band-passed noise: the crack / attack (kept short and below ~6-10 kHz)
  thwack  a sine that drops fast in pitch: the punchy "dooff" body that makes game guns feel solid
  body    low-passed noise burst: the blast
  sub     a low sine: weight in the chest
  tail    low, dark noise with a few slap-back reflections: the space, kept short
  mech    a small action click right after the shot
then gentle saturation, a low-pass to take the edge off, and a compressor so every shot of a burst
sits at the same loudness.

    python tools/soundgen/shot_styles.py          # comparison samples into runs/sound_samples/
"""
import sys
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfilt

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_sounds import SR  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]


def _band(x, lo=None, hi=None, order=2):
    if lo and hi:
        sos = butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    elif lo:
        sos = butter(order, lo, btype="high", fs=SR, output="sos")
    else:
        sos = butter(order, hi, btype="low", fs=SR, output="sos")
    return sosfilt(sos, x)


def _env(n, attack, decay):
    tt = np.arange(n) / SR
    e = np.exp(-tt / max(decay, 1e-5))
    a = max(1, int(attack * SR))
    e[:a] *= np.linspace(0, 1, a) ** 2
    return e


def _compress(x, threshold=0.25, ratio=3.0, attack=0.002, release=0.08):
    """Feed-forward peak compressor: evens out shot loudness and tames the first transient."""
    a_att, a_rel = np.exp(-1 / (attack * SR)), np.exp(-1 / (release * SR))
    level = np.abs(x)
    e = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(level):
        acc = a_att * acc + (1 - a_att) * v if v > acc else a_rel * acc + (1 - a_rel) * v
        e[i] = acc
    gain = np.ones_like(x)
    over = e > threshold
    gain[over] = (threshold * (e[over] / threshold) ** (1 / ratio)) / e[over]
    return x * gain


def _louden(x, db):
    """Raise the body of a shot by soft-limiting its peaks (tanh knee); 0 dB = unchanged."""
    if db <= 0:
        return x
    g = 10 ** (db / 20)
    y = x / (np.max(np.abs(x)) + 1e-9)
    return np.tanh(y * g) / np.tanh(g)


def _slap(x, delays=(0.019, 0.037, 0.061), decay=0.3):
    y = np.concatenate([x, np.zeros(int(SR * 0.1))])
    for i, d in enumerate(delays):
        k = int(d * SR)
        y[k:k + len(x)] += _band(x, hi=2500) * decay ** (i + 1)
    return y


@dataclass(frozen=True)
class Calibre:
    """What the gun is: the body pitch and weight of its report."""
    body_hz: float = 170      # where the thwack lands; lower = bigger gun
    weight: float = 1.0       # stretches every decay
    snap: float = 1.0         # supersonic crack amount (shotguns ~0.5)
    sub_hz: float = 55
    length: float = 0.9
    mech: float = 0.25
    loud_db: float = 0.0      # big calibres: soft-limit this much extra level into the file (volume caps at 1.0)


@dataclass(frozen=True)
class ShotStyle:
    """How shots should feel, mod-wide."""
    name: str
    snap: float = 0.6
    snap_lo: float = 1500
    snap_hi: float = 7000
    snap_decay: float = 0.005
    attack: float = 0.0004
    thwack: float = 1.0
    thwack_drop: float = 6.0   # start pitch = body_hz * drop
    thwack_decay: float = 0.045
    body: float = 0.8
    body_cut: float = 2500
    body_decay: float = 0.05
    sub: float = 0.5
    tail: float = 0.3
    tail_cut: float = 900
    tail_decay: float = 0.22
    drive: float = 1.5
    top: float = 9000          # final low-pass
    compress: bool = True

    def shot(self, cal: Calibre, seed: int) -> np.ndarray:
        rng = np.random.default_rng(seed)
        n = int(SR * cal.length)
        tt = np.arange(n) / SR
        w = cal.weight
        jitter = rng.uniform(0.96, 1.04)  # tiny per-shot variation so bursts don't sound looped
        x = _band(rng.standard_normal(n), self.snap_lo, self.snap_hi) * _env(n, self.attack, self.snap_decay * w) \
            * self.snap * cal.snap * 1.6
        f0, f1 = cal.body_hz * self.thwack_drop * jitter, cal.body_hz * jitter
        freq = f1 + (f0 - f1) * np.exp(-tt / 0.012)
        phase = 2 * np.pi * np.cumsum(freq) / SR
        x += np.sin(phase) * _env(n, self.attack * 2, self.thwack_decay * w) * self.thwack
        x += _band(rng.standard_normal(n), hi=self.body_cut) * _env(n, self.attack * 2, self.body_decay * w) * self.body * 1.4
        x += np.sin(2 * np.pi * cal.sub_hz * tt) * _env(n, 0.003, 0.08 * w) * self.sub
        x += _band(rng.standard_normal(n), hi=self.tail_cut) * _env(n, 0.012, self.tail_decay * w) * self.tail
        if cal.mech:
            m = _band(rng.standard_normal(int(SR * 0.02)), 2500, 6000) * _env(int(SR * 0.02), 0.0002, 0.003) * cal.mech
            k = int(0.03 * SR)
            x[k:k + len(m)] += m
        x = np.tanh(x * self.drive) / np.tanh(self.drive)
        x = _slap(x)[:n]
        x = _band(x, hi=self.top, order=4)
        if self.compress:
            x = _compress(x)
        x = _louden(x, cal.loud_db)
        fade = int(0.03 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
        return x / (np.max(np.abs(x)) + 1e-9) * 0.8


STYLES = {
    # A: punchy and thick; a clear thwack, short room, edges rounded
    "A_punchy": ShotStyle("A_punchy"),
    # B: softest; slower attack, darker, more low body, almost no crack
    "B_soft": ShotStyle("B_soft", snap=0.25, attack=0.0015, top=5500, thwack=0.9, body=0.9, body_cut=1600,
                        sub=0.7, tail=0.35, tail_decay=0.25, drive=1.2),
    # C: crisp; a defined mid snap, leaner low end, shortest tail
    "C_crisp": ShotStyle("C_crisp", snap=0.9, snap_lo=2000, snap_hi=6000, top=10000, thwack=0.75, thwack_drop=8,
                         body=0.6, body_cut=3500, sub=0.3, tail=0.22, tail_decay=0.16, drive=1.6),
    # D: balanced; between A and C: present mids (1.8-5.5 kHz snap), solid thwack, short tail
    "D_balanced": ShotStyle("D_balanced", snap=0.75, snap_lo=1800, snap_hi=5500, top=8000, thwack=0.9, thwack_drop=7,
                            body=0.75, body_cut=3000, sub=0.45, tail=0.25, tail_decay=0.18, drive=1.6),
}

def _blend(name, a: ShotStyle, b: ShotStyle, k=0.5) -> ShotStyle:
    """A style k of the way from a to b (numbers interpolated, flags from a)."""
    vals = {}
    for f in a.__dataclass_fields__:
        va, vb = getattr(a, f), getattr(b, f)
        vals[f] = va + (vb - va) * k if isinstance(va, (int, float)) and not isinstance(va, bool) else va
    vals["name"] = name
    return ShotStyle(**vals)


# CD: halfway between crisp and balanced (the user's pick: "either c or d or in between")
STYLES["CD_mix"] = _blend("CD_mix", STYLES["C_crisp"], STYLES["D_balanced"])

CALIBRES = {
    "556": Calibre(body_hz=170, weight=1.0, snap=1.0),
    "9mm_pistol": Calibre(body_hz=240, weight=0.7, snap=0.85, length=0.7, mech=0.35),
    "762": Calibre(body_hz=140, weight=1.2, snap=1.1, sub_hz=50, length=1.0),
    "300wm": Calibre(body_hz=115, weight=1.45, snap=1.2, sub_hz=45, length=1.2, mech=0.0, loud_db=5),
    "12ga": Calibre(body_hz=100, weight=1.3, snap=0.45, sub_hz=48, length=1.0, mech=0.0),
    "9mm_smg": Calibre(body_hz=210, weight=0.8, snap=0.8, length=0.75, mech=0.3),
    "68": Calibre(body_hz=155, weight=1.1, snap=1.15, sub_hz=52, length=1.0, loud_db=1.5),
    "338": Calibre(body_hz=105, weight=1.6, snap=1.25, sub_hz=42, length=1.3, mech=0.0, loud_db=6),
    "50bmg": Calibre(body_hz=85, weight=1.9, snap=1.2, sub_hz=38, length=1.6, mech=0.2, loud_db=8),
    # magnum handguns: short barrels, a big blast and little snap (the bullets are only just supersonic)
    "50ae": Calibre(body_hz=135, weight=1.1, snap=0.6, sub_hz=48, length=0.9, mech=0.35, loud_db=3),
    "44mag": Calibre(body_hz=150, weight=1.0, snap=0.65, sub_hz=50, length=0.85, mech=0.0, loud_db=3),
    # 7.62x39 from an AK: between 5.56 and 7.62 NATO, a flatter, harsher bark and the rattle of the action
    "762x39": Calibre(body_hz=155, weight=1.1, snap=0.95, sub_hz=52, length=0.95, mech=0.4),
    # .45 ACP: subsonic, a deep thump with almost no crack
    "45acp": Calibre(body_hz=190, weight=0.9, snap=0.45, sub_hz=55, length=0.75, mech=0.35),
}

@dataclass(frozen=True)
class MagnumStyle:
    """A big-bore revolver shot as a microphone near the shooter hears it: a bright blast (bullet crack plus
    the cylinder-gap spit) over a low punch, then a bright ring and a low rumble. Fitted to a reference
    recording of a Colt Anaconda (2026-11-19: decay, tail level and band balance measured off the clip)."""
    name: str
    crack: float = 1.44         # 1.5-5.5 kHz blast
    crack_lo: float = 1500
    crack_hi: float = 5500
    crack_decay: float = 0.012
    thump: float = 0.27         # low punch: a falling sine
    thump_hz: float = 60
    thump_from: float = 160
    thump_decay: float = 0.094
    mid: float = 1.0            # 300-1500 Hz body
    mid_decay: float = 0.02
    ring: float = 0.32          # bright reverberant tail
    ring_lo: float = 1233
    ring_hi: float = 3386
    ring_decay: float = 0.25
    rumble: float = 0.38        # low tail
    rumble_hi: float = 110
    rumble_decay: float = 0.33
    drive: float = 2.1
    length: float = 1.4

    def shot(self, cal: Calibre, seed: int) -> np.ndarray:
        rng = np.random.default_rng(seed)
        n = int(SR * self.length)
        tt = np.arange(n) / SR
        j = rng.uniform(0.96, 1.04)
        x = _band(rng.standard_normal(n), self.crack_lo, self.crack_hi) * _env(n, 0.0003, self.crack_decay) * self.crack * 2.2
        f = self.thump_hz * j + (self.thump_from - self.thump_hz) * np.exp(-tt / 0.01)
        x += np.sin(2 * np.pi * np.cumsum(f) / SR) * _env(n, 0.001, self.thump_decay) * self.thump
        x += _band(rng.standard_normal(n), 300, 1500) * _env(n, 0.0005, self.mid_decay) * self.mid * 1.5
        x += _band(rng.standard_normal(n), self.ring_lo, self.ring_hi) * _env(n, 0.004, self.ring_decay) * self.ring
        x += _band(rng.standard_normal(n), 25, self.rumble_hi) * _env(n, 0.01, self.rumble_decay) * self.rumble * 2
        x = np.tanh(x * self.drive) / np.tanh(self.drive)
        x = _louden(x, cal.loud_db)
        fade = int(0.05 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
        return x / (np.max(np.abs(x)) + 1e-9) * 0.8


# "Match, short ring" (2026-11-19, the user's pick): the fit with a shorter ring, cleaner when fired fast
REVOLVER_STYLE = MagnumStyle("match_short_ring", ring_decay=0.14, rumble_decay=0.2, length=0.9)

# ---- the mod-wide pick (2026-11-19: "either c or d or in between") ----------------------------
SHOT_STYLE = STYLES["CD_mix"]
# calibres with a style of their own (everything else uses SHOT_STYLE)
CALIBRE_STYLES = {"44mag": REVOLVER_STYLE}


def shot(calibre: str, seed: int) -> np.ndarray:
    """An unsuppressed shot of this calibre in the mod's shot style (or the calibre's own, CALIBRE_STYLES)."""
    return CALIBRE_STYLES.get(calibre, SHOT_STYLE).shot(CALIBRES[calibre], seed)


@dataclass(frozen=True)
class SuppressedStyle:
    """How suppressed shots sound, mod-wide. Layers: crack = what is left of a supersonic bullet's
    snap (none for subsonic rounds); puff = the gas leaving the can, band-passed noise; knock = a
    short low pop from the calibre's body pitch; tail = a little room. action_gain scales the
    action's clack that follows (the loudest part of a suppressed shot in real life)."""
    name: str
    crack: float = 0.6
    crack_lo: float = 2000
    crack_hi: float = 6000
    crack_decay: float = 0.003
    puff: float = 0.8
    puff_lo: float = 500
    puff_hi: float = 3000
    puff_attack: float = 0.0015
    puff_decay: float = 0.025
    knock: float = 0.5
    knock_mult: float = 1.0
    knock_drop: float = 3.0
    knock_decay: float = 0.03
    tail: float = 0.15
    tail_decay: float = 0.12
    drive: float = 1.3
    top: float = 7000
    action_gain: float = 1.0
    zip: float = 0.0          # a quick tonal "pew": a sine dropping from zip_f0 to zip_f1 Hz
    zip_f0: float = 2200
    zip_f1: float = 700
    zip_time: float = 0.012   # how fast the pitch drops
    zip_decay: float = 0.018

    def shot(self, cal: Calibre, seed: int, supersonic: bool = True) -> np.ndarray:
        rng = np.random.default_rng(seed)
        n = int(SR * min(cal.length, 0.7))
        tt = np.arange(n) / SR
        w = cal.weight ** 0.5  # a can evens calibres out: weight matters less than unsuppressed
        x = np.zeros(n)
        if supersonic and self.crack:
            x += _band(rng.standard_normal(n), self.crack_lo, self.crack_hi) * _env(n, 0.0002, self.crack_decay)                 * self.crack * cal.snap
        x += _band(rng.standard_normal(n), self.puff_lo, self.puff_hi) * _env(n, self.puff_attack, self.puff_decay * w)             * self.puff * 1.5
        f1 = cal.body_hz * self.knock_mult * rng.uniform(0.97, 1.03)
        freq = f1 + (f1 * self.knock_drop - f1) * np.exp(-tt / 0.008)
        x += np.sin(2 * np.pi * np.cumsum(freq) / SR) * _env(n, 0.0008, self.knock_decay * w) * self.knock
        x += _band(rng.standard_normal(n), hi=900) * _env(n, 0.01, self.tail_decay * w) * self.tail
        if self.zip:
            j = rng.uniform(0.97, 1.03) * (200 / max(cal.body_hz, 80)) ** 0.15  # bigger calibre, a touch lower
            zf = self.zip_f1 * j + (self.zip_f0 - self.zip_f1) * j * np.exp(-tt / self.zip_time)
            x += np.sin(2 * np.pi * np.cumsum(zf) / SR) * _env(n, 0.0006, self.zip_decay) * self.zip
        x = np.tanh(x * self.drive) / np.tanh(self.drive)
        x = _band(_slap(x, decay=0.2)[:n], hi=self.top, order=4)
        x = _compress(x)
        x = _louden(x, cal.loud_db)
        fade = int(0.03 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
        return x / (np.max(np.abs(x)) + 1e-9) * 0.8


SUPPRESSED_STYLES = {
    # S1 "tsk": short mid puff, a small snap and a firm knock: the game-style suppressed rifle
    "S1_tsk": SuppressedStyle("S1_tsk", crack=0.35, crack_lo=2500, crack_hi=6000, crack_decay=0.002, puff=1.0,
                              puff_lo=900, puff_hi=4000, puff_attack=0.001, puff_decay=0.02, knock=0.45,
                              knock_mult=1.6, knock_drop=2.5, knock_decay=0.025, tail=0.12, tail_decay=0.1, top=8000),
    # S2 "pfft": airy and breathy, softest attack, almost no snap
    "S2_pfft": SuppressedStyle("S2_pfft", crack=0.15, puff=1.1, puff_lo=1200, puff_hi=5000, puff_attack=0.003,
                               puff_decay=0.035, knock=0.25, knock_mult=2.0, tail=0.1, top=7500, drive=1.1),
    # S3 "thud": dark low pop, the can swallowing the blast
    "S3_thud": SuppressedStyle("S3_thud", crack=0.3, crack_lo=1500, crack_hi=4000, puff=0.7, puff_lo=250,
                               puff_hi=1200, puff_decay=0.03, knock=0.9, knock_mult=1.0, knock_drop=3,
                               knock_decay=0.04, tail=0.18, tail_decay=0.14, top=5000),
    # S4 "clack": S1 with a quieter puff and the action's clack twice as loud
    "S4_clack": SuppressedStyle("S4_clack", crack=0.3, crack_lo=2500, crack_hi=6000, crack_decay=0.002, puff=0.6,
                                puff_lo=900, puff_hi=4000, puff_attack=0.001, puff_decay=0.02, knock=0.4,
                                knock_mult=1.6, knock_drop=2.5, knock_decay=0.025, tail=0.1, tail_decay=0.1,
                                top=8000, action_gain=2.0),
}
# round 2 (2026-11-19): after the CS2 M4A1-S as the reference. Punchy "pew": a quick tonal zip that
# drops in pitch, a short bright puff, a compact thump and a prominent bolt clack, very short tail.
_PEW = SuppressedStyle("P1_pew", crack=0.3, crack_lo=2500, crack_hi=6500, crack_decay=0.0018, puff=0.8,
                       puff_lo=1000, puff_hi=5000, puff_attack=0.0008, puff_decay=0.016, knock=0.55, knock_mult=1.4,
                       knock_drop=2.5, knock_decay=0.022, tail=0.08, tail_decay=0.08, drive=1.5, top=9000,
                       action_gain=1.5, zip=0.7)
SUPPRESSED_STYLES.update({
    "P1_pew": _PEW,
    # more thump under the pew
    "P2_pew_punch": replace(_PEW, name="P2_pew_punch", zip=0.55, knock=0.85, knock_mult=1.0, knock_decay=0.03),
    # tighter: faster zip, crisper puff, shortest tail
    "P3_pew_tight": replace(_PEW, name="P3_pew_tight", zip_time=0.007, zip_decay=0.012, puff_lo=2000, puff_hi=6000,
                            puff_decay=0.012, tail=0.05, action_gain=1.2),
    # softer: lower zip, darker puff
    "P4_pew_soft": replace(_PEW, name="P4_pew_soft", zip_f0=1600, zip_f1=500, zip=0.6, puff_hi=3500, top=6500,
                           action_gain=1.3),
})


def _hold_env(n, attack, hold, decay):
    """Rise over `attack`, stay flat for `hold`, then decay exponentially."""
    tt = np.arange(n) / SR
    e = np.where(tt < attack + hold, 1.0, np.exp(-(tt - attack - hold) / max(decay, 1e-5)))
    a = max(1, int(attack * SR))
    e[:a] *= np.linspace(0, 1, a) ** 1.5
    return e


@dataclass(frozen=True)
class LayeredSuppressed:
    """Suppressed shot built from layers measured off a reference recording (the CS2 M4A1-S:
    averaged band envelopes of 121 shots). Levels are dB relative to the sub layer.
      sub    held low boom (the loudest part): rises, holds, fades
      punch  short low-mid knock at the start
      hiss   broadband "tsss" of gas, strongest in the air band, held like the sub
      mid    the scooped middle, kept low
      clack  the action cycling, a bright click ~50 ms in
      tail   a little room"""
    name: str
    sub_db: float = 0
    sub_hz: float = 65          # for a 5.56 (body_hz 170); scales with the calibre
    sub_attack: float = 0.012
    sub_hold: float = 0.03
    sub_decay: float = 0.03
    punch_db: float = 2
    punch_decay: float = 0.02
    hiss_db: float = -13
    hiss_lo: float = 4500
    hiss_hi: float = 12000
    hiss_hold: float = 0.03
    hiss_decay: float = 0.035
    hiss_len_exp: float = -1.0  # >= 0: hiss hold AND decay scale with calibre weight ** this (bigger gun, longer
                                # hiss); < 0: the original rule (hold scales with weight ** 0.5, decay fixed)
    himid_db: float = -16
    mid_db: float = -13
    snap_db: float = -13        # supersonic crack (supersonic rounds only)
    clack_db: float = -6
    clack_at: float = 0.048
    clack_lo: float = 3000
    clack_decay: float = 0.004
    tail_db: float = -18
    tail_decay: float = 0.05
    top: float = 12500
    drive: float = 1.2

    def shot(self, cal: Calibre, seed: int, supersonic: bool = True, action: bool = True) -> np.ndarray:
        """action=False for manual actions (bolt guns): no cycling clack."""
        rng = np.random.default_rng(seed)
        n = int(SR * 0.5)
        tt = np.arange(n) / SR
        k = (cal.weight ** 0.5)
        g = lambda d: 10 ** (d / 20)
        f_sub = self.sub_hz * (cal.body_hz / 170) ** 0.6 * rng.uniform(0.97, 1.03)
        fsweep = f_sub * (1 + 0.4 * np.exp(-tt / 0.01))
        hold = self.sub_hold * k
        x = np.sin(2 * np.pi * np.cumsum(fsweep) / SR) * _hold_env(n, self.sub_attack, hold, self.sub_decay * k) * g(self.sub_db)
        x += _band(rng.standard_normal(n), hi=120) * _hold_env(n, self.sub_attack, hold, self.sub_decay * k) * 2.0 * g(self.sub_db)
        x += _band(rng.standard_normal(n), 120, 400) * _env(n, 0.001, self.punch_decay * k) * g(self.punch_db) * 2.5
        if self.hiss_len_exp >= 0:
            hs = cal.weight ** self.hiss_len_exp
            he = _hold_env(n, 0.002, self.hiss_hold * hs, self.hiss_decay * hs)
        else:
            he = _hold_env(n, 0.002, self.hiss_hold * k, self.hiss_decay)
        x += _band(rng.standard_normal(n), self.hiss_lo, self.hiss_hi) * he * g(self.hiss_db) * 2.5
        x += _band(rng.standard_normal(n), min(2000, 0.7 * self.hiss_lo), self.hiss_lo) * he * g(self.himid_db) * 2.5
        x += _band(rng.standard_normal(n), 400, 2000) * he * g(self.mid_db) * 2.5
        if supersonic:
            x += _band(rng.standard_normal(n), 3000, 8000) * _env(n, 0.0002, 0.0015) * g(self.snap_db) * 2.0 * cal.snap
        c = _band(rng.standard_normal(int(SR * 0.03)), self.clack_lo, 11000) * _env(int(SR * 0.03), 0.0002, self.clack_decay)
        c += _band(rng.standard_normal(int(SR * 0.03)), 600, 2500) * _env(int(SR * 0.03), 0.0005, self.clack_decay * 1.5) * 0.6
        i = int(self.clack_at * SR)
        if action:
            x[i:i + len(c)] += c * g(self.clack_db) * 2.5
        x += _band(rng.standard_normal(n), 150, 3000) * _env(n, 0.01, self.tail_decay * k) * g(self.tail_db) * 2.0
        x = np.tanh(x * self.drive) / np.tanh(self.drive)
        x = _band(_slap(x, decay=0.15)[:n], hi=self.top, order=4)
        x = _compress(x, threshold=0.3, ratio=2.0)
        x = _louden(x, cal.loud_db)
        fade = int(0.05 * SR)
        x[-fade:] *= np.linspace(1, 0, fade)
        return x / (np.max(np.abs(x)) + 1e-9) * 0.8


_R1 = LayeredSuppressed("R1_match")
LAYERED = {
    "R1_match": _R1,
    # less boom (the sub-bass 5 dB down): reads better on small speakers
    "R2_less_sub": replace(_R1, name="R2_less_sub", sub_db=-5, punch_db=4),
    # shorter: the boom and hiss held 20 ms instead of 30
    "R3_short": replace(_R1, name="R3_short", sub_hold=0.018, hiss_hold=0.018, sub_decay=0.022),
    # crisper action: the clack 4 dB louder and brighter
    "R4_clack": replace(_R1, name="R4_clack", clack_db=-2, clack_lo=3500, clack_decay=0.005),
}
# round 3 ("they are all too crispy"): R1's boom with progressively less top end
_R5 = replace(_R1, name="R5_smooth", hiss_db=-20, hiss_hi=8000, himid_db=-18, top=8000, snap_db=-24, clack_db=-8,
              clack_lo=1800)
LAYERED.update({
    "R5_smooth": _R5,
    "R6_warm": replace(_R1, name="R6_warm", hiss_db=-24, hiss_lo=3000, hiss_hi=6500, himid_db=-20, mid_db=-11, top=6000,
                       snap_db=-28, clack_db=-8, clack_lo=1200, clack_decay=0.006),
    "R7_dark": replace(_R1, name="R7_dark", hiss_db=-28, hiss_lo=2500, hiss_hi=5000, himid_db=-24, mid_db=-11, punch_db=4,
                       top=4500, snap_db=-32, clack_db=-10, clack_lo=900, clack_decay=0.007),
    "R8_smooth_less_boom": replace(_R5, name="R8_smooth_less_boom", sub_db=-4, punch_db=4),
})
# round 4 ("r5 is decent but i want more hiss"): R5 with the hiss back up, as a smooth "shhh" (lower band,
# held longer) rather than the bright sizzle, crack and clack kept as soft as R5's
LAYERED.update({
    "R9_hiss": replace(_R5, name="R9_hiss", hiss_db=-16, hiss_lo=3000, hiss_hi=8000),
    "R10_hiss_more": replace(_R5, name="R10_hiss_more", hiss_db=-13, hiss_lo=2500, hiss_hi=7000, hiss_decay=0.05),
    "R11_hiss_long": replace(_R5, name="R11_hiss_long", hiss_db=-13, hiss_lo=2500, hiss_hi=7000, hiss_hold=0.045,
                             hiss_decay=0.07),
    "R12_hiss_most": replace(_R5, name="R12_hiss_most", hiss_db=-9, hiss_lo=2000, hiss_hi=6500, himid_db=-15,
                             hiss_hold=0.04, hiss_decay=0.06),
})
# round 5 ("the hiss is too airy"): a firmer, shorter, lower "psh" instead of a breathy wide hiss
LAYERED.update({
    "R13_tight_psh": replace(_R5, name="R13_tight_psh", hiss_db=-13, hiss_lo=1800, hiss_hi=4500, hiss_hold=0.015,
                             hiss_decay=0.03, himid_db=-22),
    "R14_low_psh": replace(_R5, name="R14_low_psh", hiss_db=-12, hiss_lo=1000, hiss_hi=3000, hiss_hold=0.02,
                           hiss_decay=0.035, himid_db=-22),
    "R15_tsh": replace(_R5, name="R15_tsh", hiss_db=-11, hiss_lo=2000, hiss_hi=2900, hiss_hold=0.02, hiss_decay=0.03,
                       himid_db=-24),
    "R16_short": replace(_R5, name="R16_short", hiss_db=-12, hiss_lo=2500, hiss_hi=6000, hiss_hold=0.008,
                         hiss_decay=0.02),
})


# ---- the mod-wide suppressed pick (2026-11-19: "R13 Tight psh"; "short for smaller guns and a bit longer
# for longer guns") ----------------------------------------------------------------------------------
# Built from the CS2 M4A1-S reference: held sub-bass boom, a short firm "psh" of gas in the mids, a soft
# action clack ~50 ms in; supersonic rounds keep a faint crack. The psh is R13's length on a 5.56 and
# scales with calibre weight: 9 mm shorter, 7.62 / .300 / .338 / .50 progressively longer.
LAYERED["R13_long"] = replace(LAYERED["R13_tight_psh"], name="R13_long", hiss_hold=0.022, hiss_decay=0.04)
SUPPRESSED_STYLE = replace(LAYERED["R13_tight_psh"], name="R13_scaled", hiss_len_exp=1.3)
LAYERED["R13_scaled"] = SUPPRESSED_STYLE


def suppressed(calibre: str, seed: int, supersonic: bool = True, action: bool = True) -> np.ndarray:
    """A suppressed shot in the mod's suppressed style. action=False for manual actions (bolt guns):
    no cycling clack."""
    return SUPPRESSED_STYLE.shot(CALIBRES[calibre], seed, supersonic, action)


# ---- comparison samples ---------------------------------------------------------------------
def _sequence(render, gap=0.9):
    """M4 single, M4 6-round burst at 800 rpm, pistol x2, .300 WM, 12 ga (the same order for every style)."""
    parts = []

    def add(x, at, buf):
        i = int(at * SR)
        buf[i:i + len(x)] += x[: len(buf) - i]

    total = 9.0
    buf = np.zeros(int(SR * total))
    tpos = 0.1
    add(render("556", 1), tpos, buf)
    tpos += gap + 0.3
    for k in range(6):
        add(render("556", 10 + k) * 0.92, tpos + k * 0.075, buf)
    tpos += 6 * 0.075 + gap + 0.3
    add(render("9mm_pistol", 20), tpos, buf)
    add(render("9mm_pistol", 21), tpos + 0.32, buf)
    tpos += 0.32 + gap + 0.2
    add(render("300wm", 30), tpos, buf)
    tpos += gap + 0.9
    add(render("12ga", 40), tpos, buf)
    tpos += 1.2
    return buf[: int(SR * tpos)]


def _loudness_match(x, target_rms=0.12):
    loud = x[np.abs(x) > 0.01]
    rms = np.sqrt(np.mean(loud ** 2)) if len(loud) else 1
    y = x * (target_rms / rms)
    return np.clip(y, -0.98, 0.98)


def _current(name, seed):
    """Today's in-game sound, read from the mod's assets (for the reference track)."""
    files = {"556": "m4_fire", "9mm_pistol": "fire_9mm_pistol", "762": "fire_762", "300wm": "fire_300wm", "12ga": "fire_12ga"}
    base = ROOT / "src/main/resources/assets/modernarmory/sounds"
    cands = sorted(base.glob(files[name] + "_[0-9].ogg"))
    data, sr = sf.read(cands[seed % len(cands)])
    if data.ndim > 1:
        data = data.mean(axis=1)
    return data


def main():
    out = ROOT / "runs" / "sound_samples"
    out.mkdir(parents=True, exist_ok=True)
    seq = _loudness_match(_sequence(_current))
    sf.write(out / "0_current.wav", seq, SR)
    print("wrote 0_current")
    for key, style in STYLES.items():
        seq = _loudness_match(_sequence(lambda c, s: style.shot(CALIBRES[c], s)))
        sf.write(out / f"{key}.wav", seq, SR)
        print("wrote", key)


if __name__ == "__main__":
    main()
