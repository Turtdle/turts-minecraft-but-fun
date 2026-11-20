"""Shared base for the magazine-fed, turn-bolt sniper rifles (Mk 13, Mk 21, Mk 22, ...).

A gun file models only what makes the rifle look like itself (chassis, stock, fore-end,
magazine, muzzle device) and describes everything else in ONE `BoltRifle` spec:

    import bolt_rifle as BR

    RIFLE = BR.BoltRifle(muzzle_z=-24, optic_z=-4, left_hand=(0, 4, -10), ...)
    SIGHT_Y = RIFLE.sight_y

    def model():
        m = Model("mygun")
        ...receiver / barrel / handguard / stock / grip / magazine boxes...
        RIFLE.add_bolt(m)            # bolt_handle bone (body + handle + knob)
        RIFLE.add_trigger(m)
        return RIFLE.finish(m)       # arms, mounts, attachments, locators, animations, previews

So a change for every bolt-action sniper (bolt throw, recoil feel, eye relief, which optics
fit, preview loadouts...) is an edit of the defaults HERE, and a change for every gun of the
mod (arm size, reload choreography...) stays in templates.py, which this module only calls.

Conventions are those of templates.py: 1 unit ~ 3.35 cm, muzzle -Z, +X right, bore y 5.5.
"""
from __future__ import annotations

from dataclasses import dataclass

import attachments as att
import templates as T
from armorygen import Bone, Model

Vec = tuple[float, float, float]

# ---- family-wide defaults ---------------------------------------------------------------
RAIL_Y = 6.5                    # top face of the scope rail
EYE_RELIEF = 2.75               # units from the eyepiece to the eye (~9 cm)
OPTICS = ("red_dot", "holographic", "acog", "sniper_scope")   # anything fits a Picatinny top rail
DEFAULT_OPTIC = "sniper_scope"  # these rifles have no irons: they ship with a scope
SCOPE_EYEPIECE_DZ = 6.0         # sniper_scope: eyepiece end behind the mount point
UNDERBARREL = ("vertical_grip", "angled_grip", "bipod")
SIDE = ("laser",)


@dataclass(frozen=True)
class BoltRifle:
    """Everything the shared code needs to know about one bolt-action rifle (model units)."""
    muzzle_z: float                       # bare end of the barrel = the muzzle mount
    left_hand: Vec                        # under the fore-end (see templates.arms)
    suppressor: str                       # muzzle attachment id of the right calibre
    muzzle_device_len: float = 0.0        # length of the factory brake in `muzzle_default`
    optic_z: float = -4.0                 # z of the optic mount on the rail
    rail_y: float = RAIL_Y
    right_hand: Vec = (0, 2, 2)           # centre of the pistol grip (same as the M4 / M2010)
    bolt_pivot: Vec = (0, T.BORE_Y, 2.75)  # on the bolt axis, at the handle
    bolt_front_z: float = -4.0            # front end of the bolt body (inside the receiver)
    bolt_lift: float = 70.0               # degrees
    bolt_pull: float = 3.0                # units (magnum-length action)
    eject: Vec = (1.1, T.BORE_Y, -2.75)   # ejection port, right side
    underbarrel: Vec | None = None        # bottom face of the bottom rail, or None = no rail
    underbarrel_ids: tuple = UNDERBARREL
    side: Vec | None = None               # outer face of the right rail, or None = no rail
    trigger_pin: Vec = (0, 4, -0.5)
    # timing / feel
    rpm: float = 40.0
    reload_s: float = 3.5
    empty_reload_s: float = 4.5
    recoil: float = 1.5
    rise: float = 4.0

    # ---- derived values (the Java side needs these) --------------------------------------
    @property
    def optic_mount(self) -> Vec:
        return (0.0, self.rail_y, self.optic_z)

    @property
    def muzzle_mount(self) -> Vec:
        return (0.0, T.BORE_Y, self.muzzle_z)

    @property
    def sight_y(self) -> float:
        """Optical axis of the default scope (there are no iron sights)."""
        return self.rail_y + att.OPTIC_AXIS_Y[DEFAULT_OPTIC]

    @property
    def eyepiece_z(self) -> float:
        return self.optic_z + SCOPE_EYEPIECE_DZ

    @property
    def sight_line(self) -> Vec:
        return (0.0, self.sight_y, self.eyepiece_z)

    @property
    def eye_z(self) -> float:
        """Suggested model z of the eye for ADS: eyepiece + eye relief."""
        return self.eyepiece_z + EYE_RELIEF

    @property
    def muzzle_flash(self) -> Vec:
        return (0.0, T.BORE_Y, self.muzzle_z - self.muzzle_device_len - 0.3)

    @property
    def shot_interval(self) -> float:
        return 60.0 / self.rpm

    @property
    def knob(self) -> Vec:
        p = self.bolt_pivot
        return (p[0] + 2.5, p[1], p[2])

    def attachment_ids(self) -> tuple:
        ids = OPTICS + (self.suppressor,)
        if self.underbarrel is not None:
            ids += tuple(self.underbarrel_ids)
        if self.side is not None:
            ids += SIDE
        return ids

    def default_attachments(self) -> dict:
        return {"OPTIC": DEFAULT_OPTIC}

    # ---- shared parts ---------------------------------------------------------------------
    def add_bolt(self, m: Model, body: str = "bright_steel", handle: str = "black_metal") -> Bone:
        """`bolt_handle`: bolt body + shroud on the bore axis, handle arm out to +X, big knob.
        The pivot is on the bolt axis so the handle lifts about it (templates.HandCycle)."""
        px, py, pz = self.bolt_pivot
        b = m.bone("bolt_handle", pivot=self.bolt_pivot)
        b.box((px - 0.5, py - 0.5, self.bolt_front_z), (1, 1, pz + 0.75 - self.bolt_front_z), body)
        b.box((px - 0.5, py - 0.5, pz + 0.25), (1, 1, 0.5), handle, inflate=0.05)      # bolt shroud
        b.box((px + 0.5, py - 0.25, pz - 0.25), (1.5, 0.5, 0.5), handle)               # handle arm
        b.box((px + 2, py - 0.5, pz - 0.5), (1, 1, 1), handle, pattern="knurl")        # knob
        return b

    def add_trigger(self, m: Model, mat: str = "bright_steel") -> Bone:
        """`trigger`, pivot at the trigger pin, blade hanging 1 below it."""
        x, y, z = self.trigger_pin
        t = m.bone(T.TRIGGER, pivot=self.trigger_pin)
        t.box((x - 0.25, y - 1, z - 0.5), (0.5, 1, 0.75), mat)
        return t

    @staticmethod
    def add_ar_grip(m: Model, mat: str = "polymer", top: Vec = (0, 4, 1)) -> Bone:
        """`grip`: raked AR-pattern pistol grip hanging from `top` (same shape as the M2010's)."""
        x, y, z = top
        g = m.bone("grip", pivot=top)
        g.box((x - 1, y - 4, z - 1), (2, 4, 2), mat, pattern="stipple",
              rot=(-22.5, 0, 0), pivot=top, inflate=-0.05)
        return g

    # ---- everything that is the same for every rifle ---------------------------------------
    def finish(self, m: Model) -> Model:
        """Arms, mounts, attachments, locators, the four animations and the preview loadouts.
        Call it last: receiver, barrel, handguard, magazine, bolt_handle must exist."""
        T.arms(m, self.right_hand, self.left_hand, style="rifle")
        T.mounts(m, optic=self.optic_mount, muzzle=self.muzzle_mount,
                 underbarrel=self.underbarrel, side=self.side)
        ids = self.attachment_ids()
        T.attach(m, *ids)
        T.locators(m, muzzle_flash=self.muzzle_flash, shell_eject=self.eject,
                   sight_line=self.sight_line)
        self.animate(m)
        full = [DEFAULT_OPTIC, self.suppressor] + [i for i in ("bipod", "laser") if i in ids]
        m.preview_configs = {
            "bare": att.loadout(m),
            "issued": att.loadout(m, *self.default_attachments().values()),
            "loadout_sniper": att.loadout(m, *full),
        }
        return m

    def animate(self, m: Model) -> None:
        T.idle(m)
        T.fire_bolt(m, length=self.shot_interval, lift_deg=self.bolt_lift, pull=self.bolt_pull,
                    recoil=self.recoil, rise=self.rise, knob=self.knob)
        T.reload_mag(m, length=self.reload_s)
        T.reload_mag(m, length=self.empty_reload_s, empty=True, bolt_action=True,
                     lift_deg=self.bolt_lift, pull=self.bolt_pull, knob=self.knob)
