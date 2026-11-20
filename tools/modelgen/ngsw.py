"""Shared base for the SIG NGSW family in 6.8x51: M7 rifle, XM8 carbine, M250 belt-fed.

The three guns share their colours, AR-type grip, telescoping stock, rails, issued optic /
suppressor and feel, so all of that lives HERE once:

    NgswGun       abstract base: palette, grip + trigger, stock, mounts, attachments, locators,
                  previews and the build order (template method `build()`).
    SpearRifle    M7 / XM8: the whole MCX-Spear rifle generated from a `SpearRifle(...)` spec;
                  the two guns differ only in the numbers of the spec.
    BeltFed       M250: belt-fed receiver, feed cover, ammo pouch.

A gun file is then only a spec:

    import ngsw
    GUN = ngsw.SpearRifle(id="m7", handguard_front_z=-12, ...)
    SIGHT_Y = GUN.sight_y
    def model(): return GUN.build()

Where to change what:
  * every gun of the mod (arm size, recoil feel, reload choreography)  -> templates.py
  * every NGSW gun (colours, issued optic, rail height, stock shape)    -> the constants and
    NgswGun methods below
  * one gun                                                              -> its spec in guns/<id>.py

Conventions are those of templates.py: 1 unit ~ 3.35 cm, muzzle -Z, +X right, bore y 5.5,
trigger near z 0.
"""
from __future__ import annotations

from dataclasses import dataclass

import attachments as att
import templates as T
from armorygen import Bone, Model

Vec = tuple[float, float, float]

# ---- family-wide constants ---------------------------------------------------------------
BORE_Y = T.BORE_Y
RAIL_Y = 7.0                      # top face of the continuous top Picatinny rail (as the M4)
RIGHT_HAND: Vec = (0, 2, 2)       # centre of the pistol grip (same as the M4)
GRIP_TOP: Vec = (0, 4, 1)
TRIGGER_PIN: Vec = (0, 4, -0.5)
EYE_RELIEF = 3.0                  # units from the eyepiece to the eye (~10 cm)
# The issued M157 fire-control optic and SLX suppressor do not exist as attachments; these are
# the closest existing ids (boxy magnified optic, .30-cal can).
DEFAULT_OPTIC = "acog"
OPTIC_REAR_DZ = 2.05              # `sight_acog` locator: eyepiece behind the mount point
DEFAULT_MUZZLE = "suppressor_762"
MUZZLE = ("suppressor_762", "suppressor_68_short", "compensator_762")
UNDERBARREL = ("vertical_grip", "angled_grip", "bipod")
SIDE = ("laser",)

# name -> (rgb, noise, edge darkening): the coyote anodising of the whole family
PALETTE = {
    "coyote": ((158, 133, 97), 6, 0.80),         # receivers, handguards
    "coyote_dark": ((132, 110, 80), 6, 0.78),    # rails, accents
    "coyote_poly": ((170, 146, 110), 5, 0.82),   # grip / stock polymer
    "barrel_black": ((40, 40, 45), 5, 0.74),
    "controls": ((46, 46, 51), 5, 0.74),         # levers, handles, flash hider
    "mag_black": ((54, 54, 58), 5, 0.76),        # steel / polymer SR-25 pattern magazine
    "mag_tan": ((146, 124, 92), 5, 0.80),        # the XM8's new polymer magazine
    "pouch": ((104, 96, 70), 9, 0.84),           # fabric belt pouch (ranger green-brown)
}


@dataclass(frozen=True)
class NgswGun:
    """What every NGSW gun has. Subclasses add their own spec fields and implement `body()`
    (receiver, action, barrel, handguard, feed) and `animate()`."""
    id: str
    left_hand: Vec                    # see templates.arms
    muzzle_z: float                   # muzzle mount: where a suppressor starts
    underbarrel: Vec                  # bottom face of the bottom rail
    side: Vec                         # outer face of the right rail
    eject: Vec                        # ejection port, right side
    optic_z: float = -1.0             # z of the optic mount on the top rail
    optics: tuple = ("red_dot", "holographic", "acog", "sniper_scope", "lpvo_m157")
    muzzle_tip_len: float = 2.0       # barrel in front of the mount (hidden under a suppressor)
    stock_z: float = 2.0              # rear face of the receiver = front of the stock
    stock_len: float = 7.0
    stock_folding: bool = False       # side-folding hinge block (M7 only)
    arm_style: str = "rifle"
    # timing / feel
    rpm: float = 750.0
    reload_s: float = 2.6
    empty_reload_s: float = 3.2
    recoil: float = 1.0               # 6.8x51 kicks harder than the M4's 0.8
    rise: float = 2.5

    # ---- derived values (the Java side needs these) --------------------------------------
    @property
    def optic_mount(self) -> Vec:
        return (0.0, RAIL_Y, self.optic_z)

    @property
    def muzzle_mount(self) -> Vec:
        return (0.0, BORE_Y, self.muzzle_z)

    @property
    def sight_y(self) -> float:
        """Optical axis of the issued optic (the family has no iron sights)."""
        return RAIL_Y + att.OPTIC_AXIS_Y[DEFAULT_OPTIC]

    @property
    def sight_line(self) -> Vec:
        return (0.0, self.sight_y, self.optic_z + OPTIC_REAR_DZ)

    @property
    def eye_z(self) -> float:
        """Suggested model z of the eye for ADS: eyepiece + eye relief."""
        return self.sight_line[2] + EYE_RELIEF

    @property
    def bare_muzzle_z(self) -> float:
        return self.muzzle_z - self.muzzle_tip_len - 0.25

    @property
    def muzzle_flash(self) -> Vec:
        return (0.0, BORE_Y, self.bare_muzzle_z)

    @property
    def fire_length(self) -> float:
        """`fire` must end before the next shot."""
        return round(min(0.07, 0.9 * 60.0 / self.rpm), 3)

    def attachment_ids(self) -> tuple:
        return tuple(self.optics) + MUZZLE + UNDERBARREL + SIDE

    def default_attachments(self) -> dict:
        return {"OPTIC": DEFAULT_OPTIC, "MUZZLE": DEFAULT_MUZZLE}

    # ---- build order ----------------------------------------------------------------------
    def build(self) -> Model:
        m = Model(self.id, density=1)
        for name, (rgb, noise, edge) in PALETTE.items():
            m.material(name, rgb, noise, edge)
        self.body(m)
        self.add_muzzle_tip(m)
        self.add_stock(m)
        self.add_grip(m)
        self.add_trigger(m)
        T.arms(m, RIGHT_HAND, self.left_hand, style=self.arm_style)
        T.mounts(m, optic=self.optic_mount, muzzle=self.muzzle_mount,
                 underbarrel=self.underbarrel, side=self.side)
        T.attach(m, *self.attachment_ids())
        T.locators(m, muzzle_flash=self.muzzle_flash, shell_eject=self.eject,
                   sight_line=self.sight_line)
        T.idle(m)
        T.fire_auto(m, recoil=self.recoil, rise=self.rise, length=self.fire_length)
        self.animate(m)
        issued = tuple(self.default_attachments().values())
        m.preview_configs = {
            "bare": att.loadout(m),
            "issued": att.loadout(m, *issued),
            "loadout_cqb": att.loadout(m, "holographic", "compensator_762", "vertical_grip", "laser"),
            "loadout_m157": att.loadout(m, "lpvo_m157", "suppressor_68_short"),
        }
        return m

    def body(self, m: Model) -> None:
        raise NotImplementedError

    def animate(self, m: Model) -> None:
        raise NotImplementedError

    # ---- parts shared by the whole family ---------------------------------------------------
    def add_muzzle_tip(self, m: Model) -> Bone:
        """`muzzle_default`: the barrel in front of the mount + the taper-mount flash hider.
        The game hides it under a muzzle device, so a suppressor starts at the handguard."""
        z, n = self.muzzle_z, self.muzzle_tip_len
        md = T.default_part(m, "muzzle", self.muzzle_mount)
        md.box((-0.5, 5, z - n), (1, 1, n), "barrel_black", inflate=0.1)
        md.box((-0.5, 5, z - n), (1, 1, 1), "controls", pattern="lines", inflate=0.25)  # flash hider
        return md

    def add_stock(self, m: Model) -> Bone:
        """`stock_default` (under `stock`): skeletal telescoping stock - a tube on the bore
        line, a cheek rest, a thin lower strut and a butt; `stock_folding` adds the M7's hinge."""
        z0, z1 = self.stock_z, self.stock_z + self.stock_len
        st = T.default_part(m, "stock", (0, BORE_Y, z0))
        if self.stock_folding:
            st.box((-1, 4.5, z0), (2, 2, 1), "controls")                               # hinge block
            st.box((-1.25, 4.75, z0 + 0.25), (0.25, 1.5, 0.5), "steel")                # hinge pin (left)
        else:
            st.box((-1, 4.5, z0), (2, 2, 0.5), "coyote_dark")                          # rail adapter
        st.box((-0.5, 5, z0), (1, 1, self.stock_len - 1.5), "controls", pattern="lines")  # tube
        st.box((-1, 6, z1 - 4.5), (2, 0.5, 3), "coyote_poly")                          # cheek rest
        st.wedge((0, 4.75, z0 + 1.5), (0, 3.25, z1 - 1.5), 1, 0.5, "coyote_poly")      # lower strut
        st.box((-1, 2.5, z1 - 1.5), (2, 4, 1), "coyote_poly")                          # butt
        st.box((-1, 2.5, z1 - 0.5), (2, 4, 0.5), "rubber")                             # butt pad
        return st

    def add_grip(self, m: Model) -> Bone:
        """`grip`: raked AR-type pistol grip (same shape and place as the M4's)."""
        x, y, z = GRIP_TOP
        g = m.bone("grip", pivot=GRIP_TOP)
        g.box((x - 1, y - 4, z - 1), (2, 4, 2), "coyote_poly", pattern="stipple",
              rot=(-22.5, 0, 0), pivot=GRIP_TOP, inflate=-0.05)
        return g

    def add_trigger(self, m: Model) -> Bone:
        x, y, z = TRIGGER_PIN
        t = m.bone(T.TRIGGER, pivot=TRIGGER_PIN)
        t.box((x - 0.5, y - 1, z - 0.5), (1, 1, 1), "controls")
        return t

    @staticmethod
    def add_handguard(m: Model, rear_z: float, front_z: float, bottom_rail: float = 4.0,
                      side_rail: float = 3.0) -> Bone:
        """`handguard`: free-float M-LOK tube from rear_z to front_z with the top rail carried on
        from the receiver and short Picatinny sections at the front (bottom, left + right)."""
        n = rear_z - front_z
        hg = m.bone("handguard", pivot=(0, BORE_Y, rear_z))
        hg.box((-1, 4, front_z), (2, 2.5, n), "coyote", pattern="slots")
        hg.box((-1, RAIL_Y - 0.5, front_z), (2, 0.5, n), "coyote_dark", pattern="rails")
        hg.box((-0.5, 3.5, front_z), (1, 0.5, bottom_rail), "coyote_dark", pattern="rails")
        hg.box((-1.25, 5.25, front_z + 0.5), (2.5, 0.5, side_rail), "coyote_dark", pattern="rails")
        return hg


# ==========================================================================================
# M7 / XM8
# ==========================================================================================
@dataclass(frozen=True)
class SpearRifle(NgswGun):
    """MCX-Spear pattern rifle. Receiver z -5..2; everything in front of it comes from
    `handguard_front_z` and `muzzle_z`, the magazine from the mag_* fields."""
    handguard_front_z: float = -12.0
    barrel_inflate: float = 0.25      # 0.25 = the M7's thick barrel, 0.1 = the XM8's slim one
    mag_len: float = 3.0              # magazine showing below the well (20 rd: 3, 25 rd: 4)
    mag_curve: float = 8.0            # degrees the body sweeps forward
    mag_material: str = "mag_black"
    eject: Vec = (1.1, BORE_Y + 0.25, -2.5)

    RECEIVER_FRONT_Z = -5.0
    MAG_PIVOT = (0.0, 4.0, -3.5)      # top centre of the magazine well
    BOLT_CATCH = (-1.5, 4.5, -2.5)    # left side of the lower, above the magazine

    def body(self, m: Model) -> None:
        self._receiver(m)
        self._barrel(m)
        self.add_handguard(m, self.RECEIVER_FRONT_Z, self.handguard_front_z)
        self._magazine(m)

    def _receiver(self, m: Model) -> None:
        rc = m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 4, -5), (2, 1, 7), "coyote")                              # lower receiver
        rc.box((-1, 5, -5), (2, 1.5, 7), "coyote")                            # slab-sided upper
        rc.box((-1, RAIL_Y - 0.5, -5), (2, 0.5, 7), "coyote_dark", pattern="rails")  # top rail
        rc.box((-1, 2.5, -5), (2, 1.5, 3), "coyote")                          # deep SR-25 magazine well
        rc.box((-0.5, 2, -2), (1, 0.5, 3), "coyote")                          # trigger guard
        rc.box((0.05, 5.25, -3.5), (1, 1, 2), "controls")                     # ejection port (dark)
        rc.box((1, 5.25, -1.25), (0.25, 1, 0.75), "coyote_dark")              # brass deflector
        rc.box((-1.25, 6, 1.5), (2.5, 0.5, 0.5), "controls")                  # rear charging handle latches
        for x in (-1.25, 1):                                                  # ambidextrous selector
            rc.box((x, 4.25, 0), (0.25, 0.5, 1), "controls")
        rc.box((1, 4.25, -2.75), (0.25, 0.5, 0.5), "controls")                # magazine release
        bo = m.bone("bolt", pivot=(0.5, BORE_Y + 0.25, -2.5))
        bo.box((0.1, 5.25, -3.5), (1, 1, 2), "bright_steel")                  # carrier seen in the port
        ch = m.bone("charging_handle", pivot=(-1.25, 5.75, -4))
        ch.box((-1.5, 5.5, -4.5), (0.5, 0.5, 1), "controls")                  # left side charging handle

    def _barrel(self, m: Model) -> None:
        z0 = self.RECEIVER_FRONT_Z
        ba = m.bone("barrel", pivot=(0, BORE_Y, z0))
        ba.box((-0.5, 5, self.muzzle_z), (1, 1, z0 - self.muzzle_z), "barrel_black",
               inflate=self.barrel_inflate)

    def _magazine(self, m: Model) -> None:
        px, py, pz = self.MAG_PIVOT
        well = 2.5                                                            # bottom of the well
        mg = T.default_part(m, "magazine", self.MAG_PIVOT)
        hinge = (0, well + 0.5, pz + 1.25)                                    # rear top edge of the visible body
        kw = dict(rot=(self.mag_curve, 0, 0), pivot=hinge)
        mg.box((-0.75, well, pz - 1.25), (1.5, py - well, 2.5), self.mag_material)   # part in the well
        mg.box((-0.75, well + 0.5 - self.mag_len, pz - 1.25), (1.5, self.mag_len, 2.5),
               self.mag_material, pattern="lines", inflate=-0.05, **kw)
        mg.box((-0.75, well + 0.25 - self.mag_len, pz - 1.25), (1.5, 0.25, 2.5), "controls",
               inflate=0.1, **kw)                                             # floor plate

    def animate(self, m: Model) -> None:
        T.reload_mag(m, length=self.reload_s)
        T.reload_mag(m, length=self.empty_reload_s, empty=True, catch=self.BOLT_CATCH)


# ==========================================================================================
# M250
# ==========================================================================================
@dataclass(frozen=True)
class BeltFed(NgswGun):
    """M250: squared receiver z -7..3 with a feed cover in front of the fixed optic rail, a
    belt pouch under the left side (bone `magazine`) and a left-side charging handle."""
    handguard_front_z: float = -14.0
    stock_z: float = 3.0
    stock_len: float = 6.0
    optic_z: float = 0.0
    # the sniper scope's objective would sit over the feed cover and block it from opening
    # (the M157 LPVO ends at the cover hinge, so it fits)
    optics: tuple = ("red_dot", "holographic", "acog", "lpvo_m157")
    eject: Vec = (1.6, 5.0, -4.5)
    rpm: float = 800.0
    reload_s: float = 7.0
    empty_reload_s: float = 6.0
    recoil: float = 0.7               # the heavy gun soaks it up
    rise: float = 1.5
    cover_open_deg: float = 65.0
    charge_pull: float = 3.0

    RECEIVER_FRONT_Z = -7.0
    COVER_HINGE = (0.0, 6.5, -3.0)    # rear edge of the feed cover
    MAG_PIVOT = (-0.5, 4.0, -5.0)     # top centre of the pouch
    CHARGE_PIVOT = (-1.5, 5.0, -1.5)
    CHARGE_KNOB = (-2.0, 5.0, -1.5)

    def default_attachments(self) -> dict:
        return {**super().default_attachments(), "UNDERBARREL": "bipod"}   # issued with a bipod

    def body(self, m: Model) -> None:
        self._receiver(m)
        ba = m.bone("barrel", pivot=(0, BORE_Y, self.RECEIVER_FRONT_Z))
        ba.box((-0.5, 5, self.muzzle_z), (1, 1, self.RECEIVER_FRONT_Z - self.muzzle_z),
               "barrel_black", inflate=0.2)
        ba.box((-0.75, 4.5, self.handguard_front_z - 1.25), (1.5, 1.75, 1), "controls")  # gas block
        self.add_handguard(m, self.RECEIVER_FRONT_Z, self.handguard_front_z, bottom_rail=4.5,
                           side_rail=4.0)
        self._feed(m)

    def _receiver(self, m: Model) -> None:
        rc = m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1.5, 4, -7), (3, 2, 10), "coyote")                           # squared receiver
        rc.box((-1.5, 6, -3), (3, 0.5, 6), "coyote")                          # top, behind the feed cover
        rc.box((-1, RAIL_Y - 0.5, -3), (2, 0.5, 6), "coyote_dark", pattern="rails")  # fixed optic rail
        rc.box((-1, 3, -2.5), (2, 1, 4.5), "coyote")                          # trigger group housing
        rc.box((-0.5, 2, -2), (1, 0.5, 3), "coyote")                          # trigger guard
        rc.box((-1, 5.95, -6.5), (2, 0.1, 3), "steel")                        # feed tray (under the cover)
        rc.box((0.55, 4.5, -5.5), (1, 1, 2), "controls")                      # ejection port (dark)
        for x in (-1.75, 1.5):                                                # ambidextrous selector
            rc.box((x, 4.25, 0), (0.25, 0.5, 1), "controls")
        bo = m.bone("bolt", pivot=(1, 5, -4.5))
        bo.box((0.6, 4.5, -5.5), (1, 1, 2), "bright_steel")                   # bolt seen in the port
        px, py, pz = self.CHARGE_PIVOT
        ch = m.bone("charging_handle", pivot=self.CHARGE_PIVOT)
        ch.box((px - 0.25, py - 0.25, pz - 3), (0.25, 0.5, 3.5), "controls")  # slide bar
        ch.box((px - 0.75, py - 0.25, pz - 0.5), (0.5, 0.5, 1), "controls", pattern="knurl")  # handle

    def _feed(self, m: Model) -> None:
        fc = m.bone("feed_cover", pivot=self.COVER_HINGE)
        fc.box((-1.5, 6, -7), (3, 0.5, 4), "coyote_dark")
        fc.box((-1, RAIL_Y - 0.5, -7), (2, 0.5, 4), "coyote_dark", pattern="rails")
        fc.box((-0.5, 6, -7.25), (1, 0.5, 0.25), "controls")                  # cover latch
        mg = T.default_part(m, "magazine", self.MAG_PIVOT)
        mg.box((-2, 0.5, -7), (3, 3.5, 4), "pouch", pattern="stipple")        # 100-round belt pouch
        mg.box((-2, 3.5, -7), (3, 0.5, 4), "coyote_dark")                     # pouch lid / clip rail
        mg.box((-2.1, 1, -6), (0.1, 2, 2), "coyote_dark", pattern="lines")    # left panel stitching
        mg.box((-2, 4, -6), (0.5, 2.25, 1.5), "brass", pattern="lines")       # belt up into the feed tray

    def animate(self, m: Model) -> None:
        kw = dict(cover_bone="feed_cover", box_bone="magazine", open_deg=self.cover_open_deg)
        T.reload_belt(m, length=self.reload_s, **kw)
        T.reload_belt(m, length=self.empty_reload_s, empty=True, charge_bone="charging_handle",
                      charge_pull=self.charge_pull, charge_hand="left", charge_also=("bolt",),
                      knob=self.CHARGE_KNOB, **kw)
