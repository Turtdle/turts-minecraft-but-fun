"""AR-15 family platform: everything the AR-pattern rifles share, written ONCE.

A gun of this family is a small subclass of `Ar15Platform` in guns/<id>.py that sets the layout
constants and overrides only the parts that make it that gun (handguard, sights, stock, muzzle
device...). Family-wide changes (receiver shape, STANAG magazine, grip, how the arms / mounts /
animations are wired to templates.py) are ONE edit here; mod-wide changes (arm size, recoil
feel, reload choreography) stay in templates.py.

    class Hk416(Ar15Platform):
        ID = "hk416"
        HANDGUARD_Z = (-11, -4)
        def handguard(self): ...
        def stock(self): ...

    def model() -> Model:
        return Hk416().build()

`build()` is a template method: materials -> PARTS (in order) -> arms -> mounts + attachments
-> locators -> animations -> preview configs. Every step is a method, so a subclass can
replace or extend any of them (call super() to keep the shared part).

Conventions as in templates.py / the IN-GAME TUNING NOTES in guns/m4.py: 1 unit ~ 3.35 cm,
muzzle -Z, +X right, bore y 5.5, trigger near z 0, receiver z -4..2, rail top y 7.
"""
from __future__ import annotations

import attachments as att
import templates as T
from armorygen import Bone, Model

Vec = tuple[float, float, float]


class Ar15Platform:
    # ---- identity / real-world numbers (override per gun) ---------------------------------
    ID: str = ""
    RPM: float = 800            # cyclic rate -> `fire` length stays below the shot interval
    RELOAD_S: float = 2.2       # `reload` length (s)
    RELOAD_EMPTY_S: float = 2.6  # `reload_empty` length (s)
    RECOIL: float = 0.8         # units the gun kicks back per shot
    RISE: float = 2.0           # degrees of muzzle rise per shot
    BOLT_TRAVEL: float = 2.0    # units the bolt carrier slides back

    # ---- layout (model units) ---------------------------------------------------------------
    BORE_Y: float = T.BORE_Y
    RAIL_Y: float = 7.0                 # top of the receiver's Picatinny rail
    SIGHT_Y: float = 8.0                # iron-sight line (or the default optic's axis)
    REAR_SIGHT_Z: float = 1.75          # z of the rear aperture (sight_line locator)
    RECEIVER_Z: tuple = (-4.0, 2.0)     # (front, rear) of the receiver
    HANDGUARD_Z: tuple = (-9.0, -5.0)   # (front, rear) of the handguard
    BARREL_END_Z: float = -14.0         # bare barrel end = muzzle mount
    MUZZLE_DEVICE_LEN: float = 2.0      # length of the factory flash hider
    STOCK_PIVOT: Vec = (0, T.BORE_Y, 2)
    MAG_PIVOT: Vec = (0, 4, -3)         # top centre of the magazine well
    R_HAND: Vec = (0, 2, 2)             # centre of the pistol grip
    L_HAND: Vec = (0, 4.5, -8.5)        # left hand under the handguard
    ARM_STYLE: str = "rifle"

    # ---- attachments -----------------------------------------------------------------------
    OPTIC_MOUNT: Vec | None = (0, 7, -1)
    UNDERBARREL_MOUNT: Vec | None = (0, 4, -7)
    SIDE_MOUNT: Vec | None = (1.25, T.BORE_Y, -6.5)
    MUZZLE_SLOT: bool = True            # threaded muzzle: flash hider goes in `muzzle_default`
    STOCK_SLOT: bool = True             # carbine buffer tube: stock goes in `stock_default`
    MAGAZINE_SLOT: bool = True          # STANAG well: magazine goes in `magazine_default`
    OPTICS: tuple = ("red_dot", "holographic", "acog", "sniper_scope", "micro_red_dot")
    MUZZLES: tuple = ("suppressor_556", "compensator_556")
    UNDERBARRELS: tuple = ("vertical_grip", "angled_grip", "bipod")
    SIDES: tuple = ("laser",)
    STOCKS: tuple = ("stock_ctr",)
    MAGAZINES: tuple = ("pmag_40",)
    DEFAULT_ATTACHMENTS: dict = {}      # {"OPTIC": id, ...} the issued gun ships with
    LOADOUTS: dict = {}                 # extra previews: {name: (attachment ids...)}

    # ---- materials: name -> (rgb, noise, edge) -------------------------------------------------
    PALETTE: dict = {
        "anodised": ((52, 54, 61), 5, 0.72),       # receivers, rails
        "furniture": ((68, 68, 69), 5, 0.76),      # stock / grip polymer
        "barrel_steel": ((98, 101, 110), 6, 0.72),
        "mag": ((124, 126, 130), 6, 0.72),         # aluminium STANAG magazine
        "phosphate": ((36, 36, 41), 4, 0.78),      # flash hider, sights, gas block
    }

    # build order of the gun's own parts (each is a method)
    PARTS: tuple = ("receiver", "bolt", "charging_handle", "barrel", "muzzle_device", "front_sight",
                    "handguard", "rear_sight", "stock", "grip", "trigger", "magazine")

    def __init__(self):
        if not self.ID:
            raise ValueError(f"{type(self).__name__}: set ID")
        self.m: Model = None

    # ======================================================================================
    # template method
    # ======================================================================================
    def build(self) -> Model:
        self.m = Model(self.ID, density=1)
        self.materials()
        for part in self.PARTS:
            getattr(self, part)()
        self.arms()
        self.mounts()
        self.locators()
        self.animations()
        self.previews()
        return self.m

    # ---- derived points ----------------------------------------------------------------------
    @property
    def muzzle_mount(self) -> Vec:
        return (0, self.BORE_Y, self.BARREL_END_Z)

    @property
    def muzzle_flash(self) -> Vec:
        return (0, self.BORE_Y, self.BARREL_END_Z - self.MUZZLE_DEVICE_LEN - 0.25)

    @property
    def shell_eject(self) -> Vec:
        return (1.1, self.BORE_Y, -1)

    @property
    def sight_line(self) -> Vec:
        return (0, self.SIGHT_Y, self.REAR_SIGHT_Z)

    @property
    def fire_length(self) -> float:
        """Just under the shot interval, capped at the M4's 0.07 s."""
        return round(min(0.07, 60.0 / self.RPM - 0.005), 3)

    def slot_bone(self, slot: str, pivot: Vec, has_slot: bool) -> Bone:
        """Bone for a factory part: `<slot>_default` (hidden while an attachment is fitted) when
        the gun supports that slot, else the plain parent bone."""
        if has_slot:
            return T.default_part(self.m, slot, pivot)
        _, parent, _ = T.DEFAULT_PARTS[slot]
        return self.m.bone(parent, pivot=pivot)

    def attachment_ids(self) -> tuple:
        ids = ()
        for wanted, group in ((self.OPTIC_MOUNT, self.OPTICS), (self.MUZZLE_SLOT, self.MUZZLES),
                              (self.UNDERBARREL_MOUNT, self.UNDERBARRELS), (self.SIDE_MOUNT, self.SIDES),
                              (self.STOCK_SLOT, self.STOCKS), (self.MAGAZINE_SLOT, self.MAGAZINES)):
            if wanted:
                ids += tuple(group)
        return ids

    # ======================================================================================
    # shared parts (AR lower + flat-top upper, bolt, charging handle, grip, trigger, magazine)
    # ======================================================================================
    def materials(self) -> None:
        for name, (rgb, noise, edge) in self.PALETTE.items():
            self.m.material(name, rgb, noise, edge)

    def receiver(self) -> None:
        z0, z1 = self.RECEIVER_Z
        rc = self.m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 5, z0), (2, 1, z1 - z0), "anodised")                    # upper receiver
        rc.box((-1, 6, z0), (2, 1, z1 - z0), "anodised", pattern="rails")   # flat-top Picatinny rail
        rc.box((-1, 4, z0), (2, 1, z1 - z0), "anodised")                    # lower receiver
        rc.box((-1, 2, z0), (2, 2, 2), "anodised")                          # magazine well
        rc.box((-0.5, 2, z0 + 2), (1, 1, 3), "anodised")                    # trigger guard
        self.receiver_details(rc)

    def receiver_details(self, rc: Bone) -> None:
        """Right-side furniture of the upper: brass deflector and forward assist."""
        rc.box((0.5, 5, 0), (1, 1, 1), "anodised")                          # brass deflector
        rc.box((0.5, 5, 1), (1, 1, 1), "barrel_steel", pattern="knurl")     # forward assist

    def bolt(self) -> None:
        bo = self.m.bone("bolt", pivot=(0.5, self.BORE_Y, -1))
        bo.box((0.1, 5, -2), (1, 1, 2), "bright_steel")                     # carrier in the ejection port

    def charging_handle(self) -> None:
        ch = self.m.bone("charging_handle", pivot=(0, 6.5, 2))
        ch.box((-1, 6, 2), (2, 1, 1), "barrel_steel")

    def grip(self) -> None:
        gr = self.m.bone("grip", pivot=(0, 4, 1))
        gr.box((-1, 0, 0), (2, 4, 2), "furniture", pattern="stipple",
               rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)

    def trigger(self) -> None:
        tr = self.m.bone("trigger", pivot=(0, 4, -0.5))
        tr.box((-0.5, 3, -1), (1, 1, 1), "barrel_steel")

    def magazine(self) -> None:
        """Curved 30-round STANAG magazine."""
        mg = self.slot_bone("magazine", self.MAG_PIVOT, self.MAGAZINE_SLOT)
        mg.box((-0.5, 1, -4), (1, 1, 2), "mag", pattern="lines")
        mg.box((-0.5, -2, -4), (1, 3, 2), "mag", pattern="lines",
               rot=(22.5, 0, 0), pivot=(0, 1, -2), inflate=-0.05)

    def muzzle_device(self) -> None:
        """A2-style birdcage flash hider in front of the barrel end."""
        md = self.slot_bone("muzzle", self.muzzle_mount, self.MUZZLE_SLOT)
        z = self.BARREL_END_Z - self.MUZZLE_DEVICE_LEN
        md.box((-0.5, 5, z), (1, 1, self.MUZZLE_DEVICE_LEN), "phosphate", pattern="lines", inflate=0.25)

    # ---- parts every gun defines itself -----------------------------------------------------
    def barrel(self) -> None:
        raise NotImplementedError

    def handguard(self) -> None:
        raise NotImplementedError

    def stock(self) -> None:
        raise NotImplementedError

    def front_sight(self) -> None:
        """No front sight by default (optics-only guns)."""

    def rear_sight(self) -> None:
        """No rear sight by default. Irons go in a bone named `rear_sight` (hidden by optics)."""

    # ---- reusable sub-assemblies -------------------------------------------------------------
    def a2_front_sight(self, z: float, leg_to: float | None = None) -> Bone:
        """A2 front sight on the gas block: post in a U between two wings, post tip at SIGHT_Y.
        `z` = front face of the 1-long block; `leg_to` = z where the slanted rear leg lands."""
        sy = self.SIGHT_Y
        # the base stays on the gas block; only the sight itself (`front_sight`: wings + post) folds under an optic
        base = self.m.bone("front_sight_base", pivot=(0, 6, z + 0.5))
        base.box((-0.5, 5, z), (1, 2, 1), "phosphate", inflate=0.25)                 # gas block / sight base
        base.box((-1, 7, z), (2, 0.5, 1), "phosphate")                               # deck under the post
        if leg_to is not None:
            base.wedge((0, 5.75, leg_to), (0, 7.0, z + 1.25), 1, 1, "phosphate")     # slanted rear leg
        fs = self.m.bone("front_sight", pivot=(0, 7, z + 0.5))
        fs.box((-1, 7.5, z), (0.5, sy + 0.5 - 7.5, 1), "phosphate")                  # left wing
        fs.box((0.5, 7.5, z), (0.5, sy + 0.5 - 7.5, 1), "phosphate")                 # right wing
        fs.box((-0.25, 7.5, z + 0.25), (0.5, sy - 7.5, 0.5), "phosphate")            # post: TOP at SIGHT_Y
        return fs

    def aperture_rear_sight(self, z: float | None = None) -> Bone:
        """Flip-up rear aperture on the receiver rail: 1x1 hole centred on SIGHT_Y."""
        z = self.REAR_SIGHT_Z if z is None else z
        sy, ry = self.SIGHT_Y, self.RAIL_Y
        rs = self.m.bone("rear_sight", pivot=(0, ry, z - 0.25))
        rs.box((-1, ry, z - 0.75), (2, sy - 0.5 - ry, 1), "phosphate")               # base on the rail
        rs.box((-1, sy - 0.5, z - 0.25), (0.5, 1, 0.5), "phosphate")                 # frame, left
        rs.box((0.5, sy - 0.5, z - 0.25), (0.5, 1, 0.5), "phosphate")                # frame, right
        rs.box((-1, sy + 0.5, z - 0.25), (2, 0.5, 0.5), "phosphate")                 # top bar
        return rs

    def buffer_tube(self, st: Bone, length: float = 3.0) -> None:
        z = self.RECEIVER_Z[1]
        st.box((-0.5, 5, z), (1, 1, length), "barrel_steel", pattern="lines")

    # ======================================================================================
    # shared wiring to templates.py
    # ======================================================================================
    def arms(self) -> None:
        T.arms(self.m, self.R_HAND, self.L_HAND, style=self.ARM_STYLE)

    def mounts(self) -> None:
        T.mounts(self.m, optic=self.OPTIC_MOUNT, muzzle=self.muzzle_mount if self.MUZZLE_SLOT else None,
                 underbarrel=self.UNDERBARREL_MOUNT, side=self.SIDE_MOUNT)
        T.attach(self.m, *self.attachment_ids())

    def locators(self) -> None:
        T.locators(self.m, muzzle_flash=self.muzzle_flash, shell_eject=self.shell_eject,
                   sight_line=self.sight_line)

    def animations(self) -> None:
        m = self.m
        T.idle(m)
        T.fire_auto(m, recoil=self.RECOIL, bolt_travel=self.BOLT_TRAVEL, length=self.fire_length,
                    rise=self.RISE)
        T.reload_mag(m, length=self.RELOAD_S)
        T.reload_mag(m, length=self.RELOAD_EMPTY_S, empty=True, pull=self.BOLT_TRAVEL)

    def previews(self) -> None:
        m = self.m
        bare = att.loadout(m)
        cfg = {"bare": bare, "noarms": bare | {T.RIGHT_ARM, T.LEFT_ARM}}
        if self.DEFAULT_ATTACHMENTS:
            cfg["issued"] = att.loadout(m, *self.DEFAULT_ATTACHMENTS.values()) | {T.RIGHT_ARM, T.LEFT_ARM}
        for name, ids in self.LOADOUTS.items():
            cfg[name] = att.loadout(m, *ids)
        m.preview_configs = cfg
