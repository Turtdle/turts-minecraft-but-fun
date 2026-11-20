"""
Calibre reports and per-gun mechanical sounds for the catalog guns, built from the procedural kit
in make_sounds.py (metal_click / latch / slide / thud / casing_tink, timeline()).

    python tools/soundgen/gun_sounds.py               # every sound below + sounds.json entries
    python tools/soundgen/gun_sounds.py m590 fire_12ga  # only names starting with these

Naming (must match the Java side, gun.Report and gun.SoundSets.forGun):
  fire_<calibre>[_suppressed]_<n>   shared shot sounds of a calibre (event "fire_<calibre>[_suppressed]")
  <gun>_reload                      timed to the gun's "reload" animation (tube-fed: ONE shell)
  <gun>_reload_empty                timed to "reload_empty"
  <gun>_cycle                       manual actions: the bolt / pump / straight pull, timed to "fire"

Every mechanical sound is authored on the SAME base timeline as the animation template that
animates it (tools/modelgen/templates.py: reload_mag 2.2 / 2.6 / 3.0 s, reload_belt 4.2 / 5.0 s,
reload_shell 0.8 / 1.7 s, fire_bolt 1.3 s, fire_pump 0.9 s, fire_straight_pull 0.9 s) and stretched
to the gun's real animation length from docs/firearms/models.json, so clicks land on the motions.
"""
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_sounds import (SR, OUT, casing_tink, env, highpass, latch, lowpass, metal_click, norm,  # noqa: E402
                         place, reverb, room, slide, styled_suppressed, t, thud, timeline)
import shot_styles  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "docs/firearms/models.json"
SOUNDS_JSON = ROOT / "src/main/resources/assets/modernarmory/sounds.json"


# =====================================================================================
# Calibre reports
# =====================================================================================
@dataclass(frozen=True)
class Report:
    """A shot, parameterised by calibre. crack = supersonic / muzzle crack, blast = muzzle blast
    body (low-passed noise), thump = low-end punch, tail = reverberant tail, mech = the action."""
    crack: float = 1.4
    crack_decay: float = 0.004
    blast_cut: float = 3500
    blast_decay: float = 0.045
    thump_hz: float = 70
    thump: float = 0.9
    thump_decay: float = 0.07
    tail_cut: float = 900
    tail: float = 0.35
    tail_decay: float = 0.28
    mech: float = 0.3
    mech_at: float = 0.035
    drive: float = 2.2
    length: float = 1.1
    boom: float = 0.0          # second, sub-bass concussion (.50 BMG)

    def render(self, seed):
        rng = np.random.default_rng(seed)
        n = int(SR * max(0.9, self.length - 0.2))
        noise = rng.standard_normal(n)
        tt = t(n / SR)
        x = highpass(noise, 2500) * env(n, 0.0002, self.crack_decay) * self.crack
        x += lowpass(noise, self.blast_cut) * env(n, 0.0005, self.blast_decay)
        x += np.sin(2 * np.pi * self.thump_hz * tt) * env(n, 0.001, self.thump_decay) * self.thump
        x += lowpass(rng.standard_normal(n), self.tail_cut) * env(n, 0.01, self.tail_decay) * self.tail
        if self.boom:
            x += np.sin(2 * np.pi * 34 * tt) * env(n, 0.004, 0.16) * self.boom
        mech = highpass(rng.standard_normal(n), 4000) * env(n, 0.0001, 0.003) * self.mech
        x += np.roll(mech, int(self.mech_at * SR))
        x = np.tanh(x * self.drive)
        return norm(reverb(x, decay=0.3)[: int(SR * self.length)])


@dataclass(frozen=True)
class SuppressedReport:
    """A suppressed shot: the ballistic crack stays (0 for subsonic rounds), the blast is a muffled
    pop, and the action is clearly audible."""
    crack: float = 1.4
    pop_cut: float = 1000
    pop: float = 0.6
    thump_hz: float = 0
    thump: float = 0.0
    action: float = 0.35
    length: float = 0.6

    def render(self, seed):
        rng = np.random.default_rng(seed)
        n = int(SR * self.length)
        noise = rng.standard_normal(n)
        x = highpass(noise, 3200) * env(n, 0.0001, 0.0025) * self.crack
        x += lowpass(noise, self.pop_cut) * env(n, 0.0008, 0.025) * self.pop
        if self.thump:
            x += np.sin(2 * np.pi * self.thump_hz * t(self.length)) * env(n, 0.002, 0.05) * self.thump
        x = np.tanh(x * 1.6)
        place(x, latch(seed + 1, pitch=1.0, weight=0.9, gap=0.012) * self.action, 0.03)
        return norm(reverb(x, decay=0.22)[:n])


@dataclass(frozen=True)
class Styled:
    """A calibre report in the mod's shot style (shot_styles.SHOT_STYLE). The Report /
    SuppressedReport classes above are the original voicing, kept for reference."""
    calibre: str
    suppressed: bool = False
    supersonic: bool = True
    action: float = 0.0

    def render(self, seed):
        if not self.suppressed:
            return shot_styles.shot(self.calibre, seed)
        return styled_suppressed(self.calibre, seed, self.supersonic, self.action)


# event name -> (generator, variants, volume). The volume here is only a starting value: make_sounds.main
# rewrites every shot volume from the loudness ranking in loudness.py after the files are written.
REPORTS = {
    "fire_9mm_pistol": (Styled("9mm_pistol"), 3, 0.63),
    "fire_9mm_smg": (Styled("9mm_smg"), 3, 0.65),
    "fire_9mm_suppressed": (Styled("9mm_smg", suppressed=True, supersonic=False, action=0.6), 2, 0.16),
    "fire_68": (Styled("68"), 3, 1.0),
    "fire_68_suppressed": (Styled("68", suppressed=True, action=0.35), 2, 0.35),
    "fire_762": (Styled("762"), 3, 0.94),
    "fire_762_suppressed": (Styled("762", suppressed=True, action=0.35), 2, 0.33),
    "fire_300wm": (Styled("300wm"), 2, 0.88),
    "fire_300wm_suppressed": (Styled("300wm", suppressed=True), 2, 0.32),
    "fire_338": (Styled("338"), 2, 1.0),
    "fire_338_suppressed": (Styled("338", suppressed=True), 2, 0.31),
    "fire_50bmg": (Styled("50bmg"), 2, 1.0),
    "fire_12ga": (Styled("12ga"), 3, 1.0),
    "fire_50ae": (Styled("50ae"), 3, 1.0),
    "fire_44mag": (Styled("44mag"), 3, 1.0),
    "fire_762x39": (Styled("762x39"), 3, 0.92),
    "fire_45acp": (Styled("45acp"), 3, 0.66),
}
REPORT_SEEDS = {name: 1000 + 37 * i for i, name in enumerate(REPORTS)}


# =====================================================================================
# Mechanical kits (one per action / feed). Times are on the animation template's base timeline.
# =====================================================================================
def links(seed, dur, n=7, pitch=1.4, amp=0.35):
    """A belt of links rattling: a quick run of small bright clicks."""
    rng = np.random.default_rng(seed)
    out = np.zeros(int(SR * (dur + 0.1)))
    for k in range(n):
        place(out, metal_click(seed + 11 * k, pitch=pitch * rng.uniform(0.9, 1.1), weight=0.25, ring=0.6) * amp,
              rng.uniform(0, dur))
    return out


def hull_drop(seed):
    """A spent plastic shotgun hull hitting the ground: a dull tock and a small bounce."""
    out = np.zeros(int(SR * 0.3))
    place(out, thud(seed, 0.35), 0.0)
    place(out, metal_click(seed + 1, pitch=0.55, weight=0.4, ring=0.3) * 0.25, 0.0)
    place(out, thud(seed + 2, 0.15), 0.09)
    return out


@dataclass(frozen=True)
class Kit:
    """Sound kit of one gun class. pitch > 1 = smaller, lighter parts; weight > 1 = heavier."""
    pitch: float = 1.0
    weight: float = 1.0

    # events: list of (base time, sound); subclasses implement
    def reload(self, seed): raise NotImplementedError
    def reload_empty(self, seed): raise NotImplementedError
    RELOAD_BASE = 2.2
    EMPTY_BASE = 2.6

    def render(self, events, base, length):
        k = length / base
        return timeline(length + 0.15, [(at * k, snd) for at, snd in events])


@dataclass(frozen=True)
class MagKit(Kit):
    """Box-magazine swap (templates.reload_mag): release button at 0.38, the mag slides out, a
    fresh one slides in at 1.3, seats with a click-clack at 1.45, palm slap at 1.6. From empty:
    'release' = bolt catch / slide stop pressed at 2.05 and the bolt slams home at 2.1;
    'charge' = a charging handle pulled straight back (HandCycle lift 0 from `t0`); 'bolt' = a
    turn-bolt worked (HandCycle lift 70 from 1.72)."""
    empty: str = "release"
    t0: float = 1.72

    @property
    def EMPTY_BASE(self):
        return 2.6 if self.empty == "release" else 3.0

    def _swap(self, s):
        p, w = self.pitch, self.weight
        return [
            (0.38, metal_click(s + 1, pitch=1.15 * p, weight=0.6 * w)),
            (0.40, slide(s + 2, 0.12, 1800 * p, 0.3)),
            (1.30, slide(s + 3, 0.14, 2200 * p, 0.3)),
            (1.43, latch(s + 4, pitch=p, weight=1.2 * w)),
            (1.58, thud(s + 5, 0.8)), (1.59, metal_click(s + 6, 0.9 * p, 0.8 * w)),
        ]

    def reload(self, seed):
        return self._swap(seed)

    def reload_empty(self, seed):
        p, w = self.pitch, self.weight
        ev = self._swap(seed + 20)
        if self.empty == "release":
            ev += [(2.04, metal_click(seed + 31, pitch=1.4 * p, weight=0.4 * w)),
                   (2.09, latch(seed + 32, pitch=0.75 * p, weight=2.2 * w, gap=0.006) * 1.3),
                   (2.11, thud(seed + 33, 0.5))]
        elif self.empty == "charge":
            ev += charging_handle(seed + 40, self.t0, 1.0, p, w)
        else:
            ev += turn_bolt(seed + 40, self.t0, 1.0, p, w, casing=False)
        return ev


def charging_handle(seed, t0, dur, p=1.0, w=1.0):
    """HandCycle with lift 0 (templates.HandCycle.STRAIGHT): hand on the handle at 0.26, pulled
    back to 0.48 (bolt to the rear: clack), let go and slams home at 0.72."""
    at = lambda f: t0 + f * dur
    return [(at(0.25), metal_click(seed, pitch=1.2 * p, weight=0.5 * w)),
            (at(0.27), slide(seed + 1, 0.2 * dur, 2400 * p, 0.3)),
            (at(0.48), latch(seed + 2, pitch=0.95 * p, weight=1.2 * w, gap=0.008)),
            (at(0.66), slide(seed + 3, 0.05 * dur + 0.02, 2600 * p, 0.25)),
            (at(0.71), latch(seed + 4, pitch=0.75 * p, weight=2.0 * w, gap=0.007) * 1.2),
            (at(0.72), thud(seed + 5, 0.4))]


def turn_bolt(seed, t0, dur, p=1.0, w=1.0, casing=True):
    """HandCycle with lift 70 (templates.HandCycle.TURN, fractions of 1.02): handle lifted
    (unlock) by 0.32, back to 0.48 (casing ejected), forward to 0.66, turned down (locked) at 0.78."""
    at = lambda f: t0 + f / 1.02 * dur
    ev = [(at(0.24), metal_click(seed, pitch=0.95 * p, weight=1.0 * w)),
          (at(0.33), slide(seed + 1, 0.15 * dur, 3000 * p, 0.25)),
          (at(0.48), latch(seed + 2, pitch=1.1 * p, weight=1.1 * w, gap=0.008)),
          (at(0.53), slide(seed + 3, 0.13 * dur, 2600 * p, 0.25)),
          (at(0.66), metal_click(seed + 4, pitch=0.85 * p, weight=1.3 * w)),
          (at(0.77), latch(seed + 5, pitch=0.8 * p, weight=1.5 * w, gap=0.009))]
    if casing:
        ev.append((at(0.52), casing_tink(seed + 6)))
    return ev


@dataclass(frozen=True)
class BeltKit(Kit):
    """Belt-fed (templates.reload_belt): cover latch at 0.35 and up by 0.7, ammo box out (1.2-1.6,
    the belt rattling), fresh box seated at 2.45, belt laid on the feed tray at 3.0, cover slammed
    at 3.55. From empty: the charging handle racked (HandCycle lift 0 from 3.63, 0.9 s)."""
    RELOAD_BASE = 4.2
    EMPTY_BASE = 5.0

    def reload(self, seed):
        p, w = self.pitch, self.weight
        return [
            (0.33, metal_click(seed + 1, pitch=1.1 * p, weight=0.7 * w)),
            (0.36, latch(seed + 2, pitch=1.0 * p, weight=0.8 * w)),
            (0.66, metal_click(seed + 3, pitch=0.8 * p, weight=0.9 * w, ring=1.5) * 0.6),   # cover hits its stop
            (1.22, latch(seed + 4, pitch=0.9 * p, weight=1.0 * w)),                          # box latch released
            (1.3, links(seed + 5, 0.3, 6, 1.4 * p)),
            (1.35, slide(seed + 6, 0.25, 1500 * p, 0.25)),
            (2.25, slide(seed + 7, 0.2, 1500 * p, 0.25)),
            (2.44, thud(seed + 8, 0.7 * w)), (2.45, latch(seed + 9, pitch=0.9 * p, weight=1.2 * w)),
            (2.9, links(seed + 10, 0.2, 7, 1.5 * p, 0.45)),
            (3.02, metal_click(seed + 11, pitch=1.0 * p, weight=0.8 * w)),                 # belt seated on the tray
            (3.54, latch(seed + 12, pitch=0.7 * p, weight=2.2 * w, gap=0.008) * 1.3),      # cover slammed shut
            (3.55, thud(seed + 13, 0.7 * w)),
        ]

    def reload_empty(self, seed):
        return self.reload(seed + 50) + charging_handle(seed + 80, 3.63, 0.9, self.pitch * 0.9, self.weight * 1.2)


@dataclass(frozen=True)
class ShellKit(Kit):
    """Tube magazine (templates.reload_shell). 'reload' = ONE shell (base 0.8): the hand fetches a
    shell (rustle), thumbs it into the loading port at 0.6 (slide + follower click). 'reload_empty'
    (base 1.7): the action opened (pump racked back by 0.24, or the bolt locked back at 0.05), a
    shell dropped into the ejection port at 1.0 (0.88 without pump), action closed (1.48 / 1.4)."""
    pump: bool = True
    RELOAD_BASE = 0.8
    EMPTY_BASE = 1.7

    def reload(self, seed):
        p = self.pitch
        return [(0.32, slide(seed + 1, 0.08, 1200 * p, 0.12)),                       # shell out of the pouch
                (0.56, slide(seed + 2, 0.06, 1800 * p, 0.3)),
                (0.61, latch(seed + 3, pitch=1.2 * p, weight=0.7, gap=0.01))]       # past the shell latch

    def reload_empty(self, seed):
        p, w = self.pitch, self.weight
        if self.pump:
            ev = [(0.08, slide(seed + 1, 0.16, 2000 * p, 0.3)), (0.24, latch(seed + 2, pitch=0.9 * p, weight=1.2 * w)),
                  (1.0, thud(seed + 3, 0.35)), (1.01, metal_click(seed + 4, pitch=0.8 * p, weight=0.6)),
                  (1.36, slide(seed + 5, 0.12, 2000 * p, 0.3)),
                  (1.47, latch(seed + 6, pitch=0.75 * p, weight=2.0 * w, gap=0.007) * 1.2)]
        else:
            ev = [(0.04, latch(seed + 1, pitch=0.9 * p, weight=1.2 * w)),             # bolt pulled and locked back
                  (0.88, thud(seed + 3, 0.35)), (0.89, metal_click(seed + 4, pitch=0.8 * p, weight=0.6)),
                  (1.32, metal_click(seed + 5, pitch=1.3 * p, weight=0.5)),           # bolt release button
                  (1.39, latch(seed + 6, pitch=0.75 * p, weight=2.0 * w, gap=0.007) * 1.2)]
        return ev


# ---- action cycles (manual actions; start with the shot, timed to "fire") ------------------
@dataclass(frozen=True)
class BoltCycle:
    """templates.fire_bolt (base 1.3): HandCycle TURN from 0.22 for 1.02 s, casing ejected."""
    BASE = 1.3
    pitch: float = 1.0
    weight: float = 1.0

    def events(self, seed):
        return turn_bolt(seed, 0.22, 1.02, self.pitch, self.weight)


@dataclass(frozen=True)
class PumpCycle:
    """templates.fire_pump (base 0.9): pump racked back 0.26-0.44 (hull ejected), forward 0.50-0.64."""
    BASE = 0.9

    def events(self, seed):
        return [(0.27, slide(seed + 1, 0.16, 2200, 0.35)),
                (0.43, latch(seed + 2, pitch=0.9, weight=1.3, gap=0.008)),
                (0.5, slide(seed + 3, 0.13, 2200, 0.35)),
                (0.63, latch(seed + 4, pitch=0.75, weight=2.0, gap=0.007) * 1.2),
                (0.6, hull_drop(seed + 5))]


@dataclass(frozen=True)
class StraightPullCycle:
    """templates.fire_straight_pull (base 0.9): HandCycle STRAIGHT from 0.18 for 0.68 s; hull ejected."""
    BASE = 0.9

    def events(self, seed):
        return charging_handle(seed, 0.18, 0.68, 1.0, 1.2) + [(0.56, hull_drop(seed + 9))]


@dataclass(frozen=True)
class GunSounds:
    """One gun's mechanical sounds: its reload kit and, for manual actions, its action cycle."""
    kit: Kit
    cycle: object = None


@dataclass(frozen=True)
class RevolverKit(Kit):
    """Swing-out revolver with a speedloader (templates.reload_revolver, base 2.6 s, the same with or
    without empties): latch, crane swings out, ejector rod pushed (empties fall), speedloader seated and
    twisted, loader pulled off, cylinder snapped shut."""
    RELOAD_BASE = 2.6
    EMPTY_BASE = 2.6

    def reload(self, s):
        p, w = self.pitch, self.weight
        return [
            (0.12, metal_click(s + 1, pitch=1.15 * p, weight=0.6 * w)),          # latch
            (0.22, slide(s + 2, 0.15, 2000 * p, 0.3)),                           # crane swings out
            (0.38, metal_click(s + 3, pitch=0.95 * p, weight=1.0 * w)),          # cylinder stops open
            (0.62, slide(s + 4, 0.08, 2600 * p, 0.3)),                           # ejector rod
            (0.68, metal_click(s + 5, pitch=1.0 * p, weight=0.8 * w)),
            (0.78, casing_tink(s + 6)), (0.84, casing_tink(s + 7)), (0.9, casing_tink(s + 8)),  # empties
            (1.5, latch(s + 9, pitch=0.95 * p, weight=1.1 * w)),                 # speedloader seated
            (1.72, metal_click(s + 10, pitch=1.2 * p, weight=0.5 * w)),          # knob twisted
            (1.95, slide(s + 11, 0.1, 1800 * p, 0.25)),                          # loader pulled off
            (2.18, latch(s + 12, pitch=0.85 * p, weight=1.5 * w)),               # cylinder shut
        ]

    def reload_empty(self, s):
        return self.reload(s)


@dataclass(frozen=True)
class HKSlapKit(MagKit):
    """Heckler & Koch roller-delayed guns (MP5): the magazine swap of an SMG, and from empty the "HK slap"
    (templates.reload_hk_slap, base 2.8 s, key times from tools/modelgen/guns/mp5.py): the charging handle
    is pulled back and locked up in its notch first, then the magazine is swapped, then the hand slaps the
    handle down and the bolt slams home."""
    pitch: float = 1.15
    weight: float = 0.8

    @property
    def EMPTY_BASE(self):
        return 2.8

    def reload_empty(self, s):
        p, w = self.pitch, self.weight
        return [
            (0.28, metal_click(s + 1, pitch=1.2 * p, weight=0.4 * w)),            # hand on the handle
            (0.30, slide(s + 2, 0.13, 2600 * p, 0.28)),                           # handle (and bolt) pulled back
            (0.448, latch(s + 3, pitch=0.95 * p, weight=1.0 * w, gap=0.008)),     # bolt at the rear
            (0.541, metal_click(s + 4, pitch=1.05 * p, weight=0.9 * w)),          # locked up in the notch
            (0.95, metal_click(s + 5, pitch=1.15 * p, weight=0.6 * w)),           # paddle release
            (1.008, slide(s + 6, 0.12, 1800 * p, 0.3)),                           # old magazine out
            (1.76, slide(s + 7, 0.13, 2200 * p, 0.3)),                            # fresh one in
            (1.895, latch(s + 8, pitch=p, weight=1.2 * w)),                       # seated
            (2.035, thud(s + 9, 0.8)), (2.04, metal_click(s + 10, 0.9 * p, 0.8 * w)),  # slap on the base
            (2.371, thud(s + 11, 0.9)),                                           # the HK slap: palm on the handle
            (2.38, latch(s + 12, pitch=0.78 * p, weight=2.1 * w, gap=0.006) * 1.3),  # bolt slams home
            (2.427, metal_click(s + 13, pitch=0.85 * p, weight=1.2 * w)),         # handle home
        ]


# Gun -> its kit. Animation lengths come from docs/firearms/models.json.
PISTOL = MagKit(pitch=1.25, weight=0.6)
SMG = MagKit(pitch=1.15, weight=0.8)
RIFLE = MagKit()
RIFLE_762 = MagKit(pitch=0.92, weight=1.15)
GUNS = {
    "m17": GunSounds(PISTOL), "glock17": GunSounds(PISTOL), "glock19": GunSounds(PISTOL),
    "apc9": GunSounds(SMG), "mpx": GunSounds(SMG),
    "m16a4": GunSounds(RIFLE), "hk416": GunSounds(RIFLE), "mcx": GunSounds(RIFLE),
    "m7": GunSounds(RIFLE_762), "xm8": GunSounds(RIFLE_762), "mk17": GunSounds(RIFLE_762),
    "mk20": GunSounds(RIFLE_762), "m110": GunSounds(RIFLE_762), "m110a1": GunSounds(RIFLE_762),
    "mk13": GunSounds(MagKit(pitch=0.95, weight=1.2, empty="bolt"), BoltCycle()),
    "mk21": GunSounds(MagKit(pitch=0.9, weight=1.3, empty="bolt"), BoltCycle(0.92, 1.2)),
    "mk22": GunSounds(MagKit(pitch=0.9, weight=1.3, empty="bolt"), BoltCycle(0.9, 1.25)),
    "m590": GunSounds(ShellKit(pump=True), PumpCycle()),
    "benelli_m4": GunSounds(ShellKit(pump=False)),
    "m26mass": GunSounds(MagKit(pitch=0.95, weight=1.1, empty="charge", t0=1.68), StraightPullCycle()),
    "m249": GunSounds(BeltKit()), "m250": GunSounds(BeltKit(pitch=0.95, weight=1.1)),
    "m240": GunSounds(BeltKit(pitch=0.88, weight=1.3)),
    "m2a1": GunSounds(BeltKit(pitch=0.75, weight=1.7)),
    "m107": GunSounds(MagKit(pitch=0.8, weight=1.6, empty="charge")),
    "scorpion_evo3": GunSounds(MagKit(pitch=1.1, weight=0.85, empty="charge", t0=1.72)),
    "deagle": GunSounds(MagKit(pitch=1.0, weight=1.05)),
    "anaconda": GunSounds(RevolverKit()),
    "mp5": GunSounds(HKSlapKit()),
    "famas": GunSounds(RIFLE),
    "ak47": GunSounds(MagKit(pitch=0.95, weight=1.1)), "aug": GunSounds(RIFLE), "m14": GunSounds(RIFLE_762),
    "awm": GunSounds(MagKit(pitch=0.9, weight=1.3, empty="bolt"), BoltCycle(0.9, 1.25)),
    "m24": GunSounds(MagKit(pitch=0.92, weight=1.15, empty="bolt"), BoltCycle()),
    "spas12": GunSounds(ShellKit(pump=True), PumpCycle()),
    # AA-12: heavy drum, rocked off and on; open bolt, so from empty the charging handle is pulled back
    "aa12": GunSounds(MagKit(pitch=0.85, weight=1.3, empty="charge", t0=1.68)),  # left hand on the charging handle
    # Saiga-12: AK-pattern box rocked in, AK charging handle from empty
    "saiga12": GunSounds(MagKit(pitch=0.95, weight=1.1, empty="charge")),
    "vector": GunSounds(SMG), "usp": GunSounds(PISTOL), "m9": GunSounds(PISTOL),
}


def animation_lengths():
    return {g["id"]: g["animations"] for g in json.load(open(MODELS, encoding="utf-8"))}


def builders():
    """name -> (zero-arg function returning the samples, sounds.json event, entry kwargs)."""
    out = {}
    for event, (gen, variants, vol) in REPORTS.items():
        for i in range(variants):
            out[f"{event}_{i + 1}"] = (lambda g=gen, s=REPORT_SEEDS[event] + i: g.render(s), event, vol)
    anims = animation_lengths()
    for k, (gid, gs) in enumerate(GUNS.items()):
        a, seed = anims[gid], 5000 + 100 * k
        kit = gs.kit
        out[f"{gid}_reload"] = (lambda kit=kit, s=seed, L=a["reload"]: kit.render(kit.reload(s), kit.RELOAD_BASE, L),
                                f"{gid}_reload", None)
        out[f"{gid}_reload_empty"] = (lambda kit=kit, s=seed + 50, L=a["reload_empty"]:
                                      kit.render(kit.reload_empty(s), kit.EMPTY_BASE, L), f"{gid}_reload_empty", None)
        if gs.cycle is not None:
            cyc = gs.cycle
            out[f"{gid}_cycle"] = (lambda c=cyc, s=seed + 90, L=a["fire"]:
                                   timeline(L + 0.15, [(at * L / c.BASE, snd) for at, snd in c.events(s)]),
                                   f"{gid}_cycle", None)
    return out


def update_sounds_json(names):
    """Adds / refreshes the events of these files in sounds.json (other entries untouched)."""
    data = json.load(open(SOUNDS_JSON, encoding="utf-8"))
    events = {}
    for name in names:
        _, event, vol = BUILDERS[name]
        events.setdefault(event, []).append((name, vol))
    for event, files in events.items():
        if event.startswith("fire_"):
            subtitle = "subtitles.modernarmory." + ("m2010_fire" if event.endswith("_suppressed") else "m4_fire")
        elif event.endswith("_cycle"):
            subtitle = "subtitles.modernarmory.cycle"
        else:
            subtitle = "subtitles.modernarmory.reload"
        sounds = [{"name": "modernarmory:" + n, "volume": v} if v is not None else "modernarmory:" + n
                  for n, v in sorted(files)]
        if event in data and event.startswith("fire_"):   # keep variants not regenerated in this run
            have = {s["name"] if isinstance(s, dict) else s for s in sounds}
            sounds += [s for s in data[event]["sounds"] if (s["name"] if isinstance(s, dict) else s) not in have]
            sounds.sort(key=lambda s: s["name"] if isinstance(s, dict) else s)
        data[event] = {"sounds": sounds, "subtitle": subtitle}
    with open(SOUNDS_JSON, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    __import__("sound_ranges").main()  # keep other players' hearing distances (sound_ranges.py)


BUILDERS = builders()


def main(prefixes):
    OUT.mkdir(parents=True, exist_ok=True)
    names = [n for n in BUILDERS if not prefixes or any(n.startswith(p) for p in prefixes)]
    for name in names:
        data = BUILDERS[name][0]()
        sf.write(OUT / f"{name}.ogg", data.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        print("wrote", name, f"{len(data) / SR:.2f} s")
    update_sounds_json(names)


if __name__ == "__main__":
    main(sys.argv[1:])
