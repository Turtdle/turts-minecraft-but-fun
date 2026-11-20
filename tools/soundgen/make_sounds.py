"""
Procedural gun sounds (no samples needed). Writes mono OGG files into the mod's assets.

    python tools/soundgen/make_sounds.py

Minecraft needs MONO files for positional (3D) sounds. This file holds the procedural kit and
the M4 / M2010 sounds; the calibre reports and every other gun's reload / cycle sounds (timed to
its animations) are in gun_sounds.py, which this script also runs.
"""
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/sounds"


def t(sec):
    return np.arange(int(SR * sec)) / SR


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def env(n, attack, decay):
    tt = np.arange(n) / SR
    e = np.exp(-tt / decay)
    a = max(1, int(attack * SR))
    e[:a] *= np.linspace(0, 1, a)
    return e


def norm(x, peak=0.95):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def reverb(x, delays=(0.031, 0.047, 0.071, 0.113, 0.167), decay=0.35):
    y = np.concatenate([x, np.zeros(int(SR * 0.6))])
    for i, d in enumerate(delays):
        k = int(d * SR)
        y[k:k + len(x)] += x * decay ** (i + 1)
    return y


def gunshot(seed):
    rng = np.random.default_rng(seed)
    n = int(SR * 0.9)
    noise = rng.standard_normal(n)
    crack = highpass(noise, 2500) * env(n, 0.0002, 0.004) * 1.4          # supersonic muzzle crack
    blast = lowpass(noise, 3500) * env(n, 0.0005, 0.045)                 # muzzle blast body
    thump = np.sin(2 * np.pi * 70 * t(0.9)) * env(n, 0.001, 0.07) * 0.9  # low-end punch
    tail = lowpass(rng.standard_normal(n), 900) * env(n, 0.01, 0.28) * 0.35
    mech = highpass(rng.standard_normal(n), 4000) * env(n, 0.0001, 0.003) * 0.3
    mech = np.roll(mech, int(0.035 * SR))                                # bolt carrier cycling
    x = crack + blast + thump + tail + mech
    x = np.tanh(x * 2.2)                                                 # loud = slightly clipped
    return norm(reverb(x, decay=0.3)[: int(SR * 1.1)])


def bullet_crack(seed):
    rng = np.random.default_rng(seed)
    n = int(SR * 0.25)
    x = np.zeros(n)
    w = int(SR * 0.0004)                                                 # N-wave ~0.4 ms
    x[10:10 + w] = np.linspace(1, -1, w)
    x += highpass(rng.standard_normal(n), 3000) * env(n, 0.0001, 0.012) * 0.6
    return norm(reverb(x, decay=0.25)[: int(SR * 0.4)])


def click(freq, dur, seed, noise_amt=0.5):
    """Legacy soft click (kept for compatibility)."""
    rng = np.random.default_rng(seed)
    n = int(SR * dur)
    x = np.sin(2 * np.pi * freq * t(dur)) * env(n, 0.0002, dur / 6)
    x += highpass(rng.standard_normal(n), 2000) * env(n, 0.0001, dur / 10) * noise_amt
    return x


# ---- mechanical sound kit: crisp, metallic, "clicky" ----------------------------------
from dataclasses import dataclass, replace  # noqa: E402


@dataclass(frozen=True)
class MechStyle:
    """How every mechanical sound (reloads, bolts, pumps, clicks) is voiced, mod-wide. The defaults
    are the original voicing; a style only rescales the primitives below, so each gun keeps its
    timing and parts. Pick one by setting MECH (see MECH_STYLES)."""
    spike: float = 1.0        # the 3-sample digital impulse at the start of a click
    tick: float = 1.3         # bright noise burst of a click
    tick_hp: float = 3500
    mode_scale: float = 1.0   # pitch of the ringing modes (< 1 = lower, chunkier parts)
    mode_amp: float = 1.0
    ring: float = 1.0         # ring time of the modes
    body: float = 1.0         # low knock of a click
    body_cut: float = 1.0
    slide: float = 1.0        # sliding parts (mag in / out, bolt travel)
    slide_cut: float = 1.0
    grit: float = 1.0
    slide_len: float = 1.0    # sliding sounds shorter (< 1)
    thud: float = 1.0         # palm slaps
    tink: float = 1.0         # ejected cases
    tink_scale: float = 1.0
    pitch_mul: float = 1.0    # every click lower (< 1) or higher
    weight_mul: float = 1.0   # every part heavier
    weight_min: float = 0.0   # no part lighter than this (turns small ticks into clunks)
    room: float = 0.18
    top: float = 0.0          # final low-pass in Hz (0 = none)
    compress: bool = False
    peak: float = 0.85


MECH_STYLES = {
    "current": MechStyle(),
    # clean: CS-style close-mic'd parts: lower, rounder clicks with body, no digital spike, short room
    "clean": MechStyle(spike=0.12, tick=0.5, tick_hp=2500, mode_scale=0.55, mode_amp=0.8, ring=0.8, body=1.6,
                       body_cut=1.4, slide=0.7, slide_cut=0.8, grit=0.5, thud=1.2, tink=0.6, tink_scale=0.7,
                       room=0.14, top=8000, compress=True, peak=0.7),
    # chunky: heavier, darker parts; more knock and palm, least treble
    "chunky": MechStyle(spike=0.08, tick=0.35, tick_hp=2200, mode_scale=0.42, mode_amp=0.85, ring=0.7, body=2.2,
                        body_cut=1.2, slide=0.6, slide_cut=0.65, grit=0.4, thud=1.6, tink=0.45, tink_scale=0.6,
                        room=0.16, top=6000, compress=True, peak=0.7),
    # crisp: still clicky but short and tidy; brightest of the three, nothing rings
    "crisp": MechStyle(spike=0.25, tick=0.9, tick_hp=3000, mode_scale=0.75, mode_amp=0.85, ring=0.55, body=1.2,
                       body_cut=1.2, slide=0.6, slide_cut=0.9, grit=0.6, thud=1.0, tink=0.7, tink_scale=0.85,
                       room=0.1, top=10000, compress=True, peak=0.7),
}
_CHUNKY = MECH_STYLES["chunky"]
MECH_STYLES.update({
    # heavy: every part as heavy as the pump's latches; few case pings
    "heavy": replace(_CHUNKY, pitch_mul=0.85, weight_min=1.2, tink=0.25, slide=0.75),
    # heavy + rack: heavy with more of the sliding "shk-shk"
    "heavy_rack": replace(_CHUNKY, pitch_mul=0.85, weight_min=1.2, tink=0.25, slide=1.0, slide_cut=0.7, grit=0.6),
    # heavy, dry: heavy with a short ring and almost no room: a tight thunk
    "heavy_dry": replace(_CHUNKY, pitch_mul=0.85, weight_min=1.2, tink=0.25, slide=0.75, ring=0.5, room=0.08, body=2.6),
    # heavy, defined: heavy with a little more click on top
    "heavy_defined": replace(_CHUNKY, pitch_mul=0.85, weight_min=1.2, tink=0.25, slide=0.75, tick=0.55,
                             mode_scale=0.5, top=7500),
})
_DRY = MECH_STYLES["heavy_dry"]
MECH_STYLES.update({
    # round 3, all from heavy_dry ("2C"), taming the sliding noise ("weird shifting sound") different ways
    "dry_quiet_slides": replace(_DRY, slide=0.35, grit=0.0),
    "dry_no_slides": replace(_DRY, slide=0.0),
    "dry_short_slides": replace(_DRY, slide=0.45, grit=0.0, slide_len=0.45, slide_cut=0.5),
    "dry_no_comp": replace(_DRY, slide=0.35, grit=0.0, compress=False),
})
# the mod-wide pick (2026-11-19, round 3 "3C"): heavy, dry parts; short dark swipes instead of slide hiss
MECH = replace(MECH_STYLES["dry_short_slides"], peak=0.56)  # peak tuned: ~0.85x the old voicing's loudness


def _finish(x, peak=None):
    """The end of every mechanical sound: room, optional low-pass + compression, normalise."""
    x = room(x, MECH.room)
    if MECH.top:
        from scipy.signal import butter, sosfilt
        x = sosfilt(butter(4, MECH.top, btype="low", fs=SR, output="sos"), x)
    if MECH.compress:
        from shot_styles import _compress
        x = _compress(x / (np.max(np.abs(x)) + 1e-9), threshold=0.3, ratio=2.5, attack=0.001, release=0.05)
    return norm(x, MECH.peak if peak is None else peak * MECH.peak / 0.85)


def metal_click(seed, pitch=1.0, weight=1.0, ring=1.0):
    """A sharp steel-on-steel click: instant transient, bright noise burst, ringing resonant
    modes of the part (decaying in 5-25 ms) and a small body knock. weight > 1 = heavier part."""
    rng = np.random.default_rng(seed)
    n = int(SR * 0.09)
    tt = t(0.09)
    x = np.zeros(n)
    M = MECH
    pitch *= M.pitch_mul
    weight = max(weight * M.weight_mul, M.weight_min)
    x[0], x[1], x[2] = 1.0 * M.spike, -0.8 * M.spike, 0.4 * M.spike
    x += highpass(rng.standard_normal(n), M.tick_hp) * env(n, 0.00002, 0.0007) * M.tick
    for f, dec, amp in ((2900, 0.012, 0.5), (4650, 0.009, 0.45), (6900, 0.006, 0.35), (8800, 0.004, 0.25)):
        f *= pitch * rng.uniform(0.96, 1.04) * M.mode_scale
        x += np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.28)) * np.exp(-tt / (dec * ring * M.ring)) * amp * M.mode_amp
    body = lowpass(rng.standard_normal(n), 900 * pitch * M.body_cut) * env(n, 0.0002, 0.006 * weight) * 0.9 * weight * M.body
    return x + body


def latch(seed, pitch=1.0, weight=1.0, gap=0.011):
    """Click-clack: a part snapping in, then the catch engaging a few ms later."""
    a = metal_click(seed, pitch * 1.1, weight * 0.7)
    b = metal_click(seed + 50, pitch * 0.9, weight)
    out = np.zeros(len(a) + int(gap * SR))
    out[:len(a)] += a * 0.8
    out[int(gap * SR):int(gap * SR) + len(b)] += b
    return out


def slide(seed, dur, cutoff=2500, amp=0.35):
    """Metal/polymer parts sliding against each other: gritty band-limited noise."""
    rng = np.random.default_rng(seed)
    n = max(1, int(SR * dur * MECH.slide_len))
    x = highpass(lowpass(rng.standard_normal(n), cutoff * MECH.slide_cut), 400)
    grit = (rng.random(n) < 0.004) * rng.uniform(0.5, 1.0, n)
    x = x + highpass(grit, 2000) * 2.0 * MECH.grit
    shape = np.sin(np.linspace(0, np.pi, n)) ** 0.7
    return x * shape * amp * MECH.slide


def thud(seed, amp=0.7):
    """Palm slap / body knock: short low thump."""
    rng = np.random.default_rng(seed)
    n = int(SR * 0.08)
    x = lowpass(rng.standard_normal(n), 380) * env(n, 0.001, 0.018) * 2.5
    x += np.sin(2 * np.pi * 140 * t(0.08)) * env(n, 0.001, 0.02) * 0.6
    return x * amp * MECH.thud


def casing_tink(seed):
    """Ejected brass case hitting the ground: two bright pings."""
    rng = np.random.default_rng(seed)
    out = np.zeros(int(SR * 0.35))
    for k, at in enumerate((0.0, 0.11 + rng.uniform(0, 0.05))):
        tt = t(0.15)
        ping = sum(np.sin(2 * np.pi * f * tt) * np.exp(-tt / d) for f, d in
                   ((5600 * rng.uniform(0.97, 1.03) * MECH.tink_scale, 0.05), (8300 * MECH.tink_scale, 0.03),
                    (11200 * MECH.tink_scale, 0.02)))
        place(out, ping * (0.35 if k == 0 else 0.2) * MECH.tink, at)
    return out


def room(x, amount=0.18):
    """Very short small-room reverb so clicks don't sound dead-dry."""
    return reverb(x, delays=(0.009, 0.017, 0.026, 0.041), decay=amount)[: len(x) + int(SR * 0.1)]


def place(buf, x, at):
    i = int(at * SR)
    buf[i:i + len(x)] += x[: max(0, len(buf) - i)]


def timeline(length, events):
    buf = np.zeros(int(SR * length))
    for at, snd in events:
        place(buf, snd, at)
    return _finish(buf)


# Timings follow the animations in tools/modelgen/guns/*.py
def reload_sound():
    """M4 tactical reload, 2.2 s."""
    return timeline(2.3, [
        (0.34, metal_click(1, pitch=1.15, weight=0.6)),          # mag release button
        (0.38, slide(2, 0.16, 1800, 0.3)),                       # mag slides out of the well
        (1.30, slide(3, 0.12, 2200, 0.3)),                       # new mag into the well
        (1.43, latch(4, pitch=1.0, weight=1.2)),                 # seats: click-clack
        (1.55, thud(5, 0.8)), (1.56, metal_click(6, 0.9, 0.8)),  # palm slap
        (1.74, metal_click(7, pitch=1.3, weight=0.3, ring=0.5) * 0.4),  # hand back on the rail
    ])


def reload_empty_sound():
    """M4 empty reload, 2.6 s: same mag swap, then the bolt catch is slapped and the bolt slams home."""
    return timeline(2.7, [
        (0.34, metal_click(11, pitch=1.15, weight=0.6)),
        (0.38, slide(12, 0.16, 1800, 0.3)),
        (1.30, slide(13, 0.12, 2200, 0.3)),
        (1.43, latch(14, pitch=1.0, weight=1.2)),
        (1.55, thud(15, 0.8)), (1.56, metal_click(16, 0.9, 0.8)),
        (2.04, metal_click(17, pitch=1.4, weight=0.4)),              # bolt catch pressed
        (2.08, latch(18, pitch=0.75, weight=2.2, gap=0.006) * 1.3),  # bolt carrier slams forward
        (2.10, thud(19, 0.5)),
    ])


def dry_fire():
    """Hammer falls on an empty chamber: one crisp tick."""
    return _finish(metal_click(31, pitch=1.25, weight=0.5), 0.6)


def fire_select():
    """Selector lever detent: a tiny double click."""
    return _finish(latch(41, pitch=1.6, weight=0.25, gap=0.007), 0.45)


def ricochet(seed):
    rng = np.random.default_rng(seed)
    n = int(SR * 0.5)
    f = np.linspace(3800, 1600, n)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, 0.15) * 0.6
    x += highpass(rng.standard_normal(n), 3000) * env(n, 0.0001, 0.01)
    return norm(x, 0.7)


def suppressed_shot_with_bolt(seed):
    """Suppressed .300 Win Mag: the suppressor kills most of the blast, but the supersonic bullet
    still cracks loudly; then the bolt is worked (lift, pull back, push, lock) ~0.45-0.9 s later."""
    rng = np.random.default_rng(seed)
    n = int(SR * 1.3)
    noise = rng.standard_normal(n)
    crack = highpass(noise, 3000) * env(n, 0.0001, 0.003) * 1.6             # ballistic crack
    pop = lowpass(noise, 1200) * env(n, 0.0008, 0.03) * 0.7                  # muffled muzzle pop
    thump = np.sin(2 * np.pi * 55 * t(1.3)) * env(n, 0.002, 0.06) * 0.7
    tail = lowpass(rng.standard_normal(n), 700) * env(n, 0.01, 0.22) * 0.25
    x = np.tanh((crack + pop + thump + tail) * 1.8)
    x = reverb(x, decay=0.28)[:n]
    for at, snd in bolt_cycle(seed, 0.42):
        place(x, snd, at)
    return norm(x)


def bolt_cycle(seed, t0):
    """Working a bolt action, relative to t0 (matches the 'fire' animation: handle up at +0.12,
    back at +0.28, forward +0.46, locked down +0.58)."""
    return [
        (t0 + 0.12, metal_click(seed + 1, pitch=0.95, weight=1.0)),
        (t0 + 0.15, slide(seed + 2, 0.12, 3000, 0.25)),
        (t0 + 0.28, latch(seed + 3, pitch=1.1, weight=1.1, gap=0.008)),
        (t0 + 0.32, casing_tink(seed + 4)),
        (t0 + 0.38, slide(seed + 5, 0.1, 2600, 0.25)),
        (t0 + 0.46, metal_click(seed + 6, pitch=0.85, weight=1.3)),
        (t0 + 0.58, latch(seed + 7, pitch=0.8, weight=1.5, gap=0.009)),
    ]


def sniper_reload():
    """M2010 reload, 2.6 s."""
    return timeline(2.7, [
        (0.36, metal_click(51, pitch=1.1, weight=0.7)),
        (0.45, slide(52, 0.14, 1700, 0.3)),
        (1.55, slide(53, 0.12, 2000, 0.3)),
        (1.67, latch(54, pitch=0.95, weight=1.3)),
        (1.85, thud(55, 0.8)), (1.86, metal_click(56, 0.9, 0.8)),
    ])


def sniper_reload_empty():
    """M2010 empty reload, 3.2 s: mag swap, then the bolt is cycled to chamber a round (no casing)."""
    events = [
        (0.36, metal_click(61, pitch=1.1, weight=0.7)),
        (0.45, slide(62, 0.14, 1700, 0.3)),
        (1.55, slide(63, 0.12, 2000, 0.3)),
        (1.67, latch(64, pitch=0.95, weight=1.3)),
        (1.85, thud(65, 0.8)), (1.86, metal_click(66, 0.9, 0.8)),
    ] + [e for i, e in enumerate(bolt_cycle(70, 1.95)) if i != 3]
    return timeline(3.3, events)


def _pad(x, sec):
    out = np.zeros(int(SR * sec))
    out[:min(len(x), len(out))] += x[:len(out)]
    return out


def styled_suppressed(calibre, seed, supersonic=True, action=0.35):
    """Suppressed shot in the mod's suppressed style (shot_styles.SUPPRESSED_STYLE). Any action > 0
    means a self-loading gun: the style's own clack plays; 0 = manual action, no clack."""
    import shot_styles
    return shot_styles.suppressed(calibre, seed, supersonic, action=bool(action))


def styled_shot_with_bolt(seed, suppressed_shot):
    """.300 Win Mag shot (suppressed or not) in the shot style, then the bolt worked (mech style)."""
    import shot_styles
    shot = shot_styles.suppressed("300wm", seed, action=False) if suppressed_shot else shot_styles.shot("300wm", seed)
    x = _pad(shot, 1.3) + _pad(timeline(1.3, bolt_cycle(seed, 0.42)), 1.3) * 0.45
    return norm(x, 0.8)


def suppressed_556(seed):
    """Suppressed 5.56: the supersonic crack stays, the blast is a muffled pop, the action clacks."""
    rng = np.random.default_rng(seed)
    n = int(SR * 0.6)
    noise = rng.standard_normal(n)
    crack = highpass(noise, 3200) * env(n, 0.0001, 0.0025) * 1.4
    pop = lowpass(noise, 1000) * env(n, 0.0008, 0.025) * 0.6
    x = np.tanh((crack + pop) * 1.6)
    place(x, latch(seed + 1, pitch=1.0, weight=0.9, gap=0.012) * 0.35, 0.03)
    return norm(reverb(x, decay=0.22)[:n])


def loud_shot_with_bolt(seed):
    """Unsuppressed .300 Win Mag: a big blast, then the bolt is worked."""
    x = gunshot(seed)
    m = int(SR * 1.3)
    x = np.concatenate([x, np.zeros(m - len(x))]) if len(x) < m else x[:m]
    x = x * 0.9
    for at, snd in bolt_cycle(seed, 0.42):
        place(x, snd * 0.6, at)
    return norm(x)


def _ping(seed, freq, decay, dur=0.4):
    """A small steel part ringing (the spoon, a pin ring): a few inharmonic modes."""
    rng = np.random.default_rng(seed)
    tt = t(dur)
    x = sum(np.sin(2 * np.pi * freq * k * rng.uniform(0.98, 1.02) * tt + rng.uniform(0, 6.28)) * np.exp(-tt / (decay / k)) / k
            for k in (1.0, 2.76, 5.4))
    return x * 0.5


def grenade_sounds():
    """Hand grenades: pin, spoon, bounce, the M84's bang, M18 hiss, AN-M14 thermite, and the ringing ears."""
    import shot_styles as ss
    rng = np.random.default_rng(940)
    out = {}
    out["grenade_pin"] = timeline(0.5, [(0.0, slide(901, 0.18, 3000, 0.3)), (0.16, metal_click(902, pitch=1.5, weight=0.35)),
                                        (0.2, _ping(903, 3100, 0.08) * 0.35)])
    out["grenade_spoon"] = timeline(0.55, [(0.0, metal_click(911, pitch=1.3, weight=0.5)), (0.02, _ping(912, 2300, 0.14) * 0.6),
                                           (0.06, metal_click(913, pitch=0.9, weight=0.8) * 0.6)])
    out["grenade_bounce"] = timeline(0.35, [(0.0, thud(921, 0.8)), (0.002, metal_click(922, pitch=0.8, weight=1.4))])
    bang_style = ss.replace(ss.SHOT_STYLE, snap=1.4, thwack=1.0, body=1.0, sub=0.7, tail=0.5, tail_decay=0.45, top=9000)
    out["flashbang"] = bang_style.shot(ss.Calibre(body_hz=105, weight=1.3, snap=1.5, sub_hz=45, length=1.6, mech=0.0, loud_db=8), 931)
    n = int(SR * 2.2)
    hiss = highpass(lowpass(rng.standard_normal(n), 6500), 1500)
    flutter = 0.75 + 0.25 * np.sin(2 * np.pi * 3.1 * t(2.2)) * np.sin(2 * np.pi * 0.7 * t(2.2))
    shape = np.minimum(1, t(2.2) / 0.08) * np.minimum(1, (2.2 - t(2.2)) / 0.3)
    out["smoke_hiss"] = norm(hiss * flutter * shape, 0.6)
    roar = lowpass(rng.standard_normal(n), 900) * 0.8 + highpass(lowpass(rng.standard_normal(n), 5000), 1500) * 0.35
    pops = np.zeros(n)
    for _ in range(60):
        place(pops, metal_click(int(rng.integers(1000, 9999)), pitch=rng.uniform(0.7, 1.3), weight=0.4) * rng.uniform(0.2, 0.6),
              rng.uniform(0, 2.1))
    out["thermite_burn"] = norm((roar + pops[:n]) * shape, 0.7)
    tr = t(4.5)
    ring = np.sin(2 * np.pi * 4300 * tr) * 0.7 + np.sin(2 * np.pi * 4350 * tr) * 0.3
    out["flash_ring"] = norm(ring * np.exp(-tr / 1.6) * np.minimum(1, tr / 0.05), 0.5)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = {}
    for i in range(3):
        files[f"m4_fire_{i + 1}"] = __import__("shot_styles").shot("556", 100 + i)
        files[f"bullet_crack_{i + 1}"] = bullet_crack(200 + i)
        files[f"ricochet_{i + 1}"] = ricochet(300 + i)
    files["dry_fire"] = dry_fire()
    files["fire_select"] = fire_select()
    files["m4_reload"] = reload_sound()
    files["m4_reload_empty"] = reload_empty_sound()
    for i in range(2):
        files[f"m2010_fire_{i + 1}"] = styled_shot_with_bolt(400 + i, True)
    files["m2010_reload"] = sniper_reload()
    files["m2010_reload_empty"] = sniper_reload_empty()
    for i in range(2):
        files[f"m2010_fire_loud_{i + 1}"] = styled_shot_with_bolt(500 + i, False)
        files[f"m4_fire_suppressed_{i + 1}"] = styled_suppressed("556", 600 + i)
    files.update(grenade_sounds())
    for name, data in files.items():
        sf.write(OUT / f"{name}.ogg", data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", name)
    import drone_sounds  # drone rotors, chirps, crash, detonation (+ their sounds.json entries)
    drone_sounds.main()
    import gun_sounds  # calibre reports + per-gun mechanical sounds (imports this module's kit)
    gun_sounds.main([])
    import loudness  # last: in-game volumes from the loudness ranking, measured off the files just written
    print("volumes (loudness ranking):")
    loudness.apply()


if __name__ == "__main__":
    main()
