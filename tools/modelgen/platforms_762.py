"""7.62 NATO battle-rifle / DMR platforms: FN SCAR-H (Mk 17, Mk 20 SSR) and AR-10 (M110, M110A1).

A gun file in guns/ only DECLARES a `RifleSpec` (lengths, colours, stock, attachments, timings)
and hands it to a platform builder::

    import platforms_762 as P
    SPEC = P.RifleSpec(id="mk17", ...)
    def model(): return P.ScarBuilder(SPEC).build()

STRUCTURE (where to make a change)
  * templates.py / attachments.py   mod-wide: arms, mounts, animations, shared attachments.
  * RifleBuilder (here)             the build pipeline shared by every rifle of this family: the
                                    order of the parts, grip, trigger, iron sights, arms, mounts,
                                    attachments, locators, animations, preview loadouts.
  * ScarBuilder / Ar10Builder       the geometry of one PLATFORM (receiver, action, handguard...).
                                    Variants of a part are methods named `<part>_<variant>`
                                    (stock_scar_folding, handguard_quad_rail, magazine_curved...),
                                    picked by name from the spec: a new stock = one new method.
  * RifleSpec (in the gun file)     everything that differs between two guns of one platform.

Conventions are the M4's (see guns/m4.py and templates.py): 1 unit ~ 3.35 cm, muzzle -Z, +X =
right, bore y 5.5, trigger near z 0, grip centre (0, 2, 2).
"""
from __future__ import annotations

from dataclasses import dataclass, field

import attachments as att
import templates as T
from armorygen import Bone, Model

Vec = tuple[float, float, float]
RGB = tuple[int, int, int]
BORE_Y = T.BORE_Y

# colours shared by the family (a Palette picks from these or brings its own)
FDE: RGB = (160, 134, 96)
FDE_DARK: RGB = (134, 112, 80)
FDE_POLYMER: RGB = (172, 146, 108)
RAL8000: RGB = (146, 124, 86)
RAL8000_DARK: RGB = (122, 103, 72)
BLACK_POLYMER: RGB = (44, 44, 46)
BARREL_BLACK: RGB = (40, 40, 45)
PARKERISED: RGB = (34, 34, 38)
MAG_STEEL: RGB = (104, 104, 108)
MAG_TAN: RGB = (182, 156, 108)


# ======================================================================================
# SPEC
# ======================================================================================
@dataclass(frozen=True)
class Palette:
    """Material colours of one gun (RGB). Becomes the materials `receiver`, `rail`, `lower`,
    `stock_mat`, `grip_mat`, `barrel_mat`, `mag_mat`, `detail`."""
    receiver: RGB = FDE
    rail: RGB = FDE_DARK
    lower: RGB = FDE_POLYMER
    stock: RGB = FDE_POLYMER
    grip: RGB = FDE_POLYMER
    barrel: RGB = BARREL_BLACK
    mag: RGB = MAG_STEEL
    detail: RGB = PARKERISED


@dataclass(frozen=True)
class Feel:
    """Animation timing / strength (seconds, units, degrees)."""
    reload: float = 2.5
    reload_empty: float = 3.0
    fire_length: float = 0.07      # keep below the shot interval
    recoil: float = 1.0
    rise: float = 2.5
    bolt_travel: float = 2.0


@dataclass(frozen=True)
class RifleSpec:
    """What differs between two rifles of one platform. z values are model units (muzzle -Z)."""
    id: str
    front_z: float                 # front end of the fore-end (SCAR: of the monolithic upper)
    barrel_end_z: float            # end of the barrel = muzzle mount; the flash hider sits in front
    stock: str                     # builder method `stock_<name>`
    handguard: str = "default"     # builder method `handguard_<name>`
    magazine: str = "straight"     # builder method `magazine_<name>`
    flash_hider: float = 1.5       # length of the factory flash hider
    barrel_inflate: float = 0.1    # barrel profile: 0.1 standard, 0.2 heavy
    irons: bool = True             # folding front + rear back-up sights
    forward_assist: bool = True    # AR-10 only
    base_pad: float = 0.25         # magazine floor plate height (0.75 = extended base pad)
    palette: Palette = Palette()
    feel: Feel = Feel()
    left_hand_z: float = -10.0     # where the left hand holds the fore-end
    left_hand_y: float | None = None   # None = 0.5 above the bottom face of the bottom rail
    optic_z: float = -2.0          # middle of the optic base on the top rail
    underbarrel_z: float = -8.0    # mount point on the bottom rail
    side_z: float = -8.0           # mount point on the right rail
    side_rail: tuple[float, float] | None = None   # (front z, length) of the short side rails
    attachments: tuple[str, ...] = ()
    default_optic: str | None = None   # optic the rifle is issued with (None = iron sights)
    loadouts: dict = field(default_factory=dict)   # preview name -> attachment ids


# ======================================================================================
# SHARED PIPELINE
# ======================================================================================
class RifleBuilder:
    """Template-method builder: `build()` runs the steps in order; platforms override the
    geometry steps and the class constants."""

    RAIL_TOP = 7.0                 # y of the top of the receiver rail
    IRON_HEIGHT = 1.0              # sight line above the rail (irons AND the sniper scope axis)
    RIGHT_HAND: Vec = (0, 2, 2)    # centre of the pistol grip (shared first-person transforms)
    ARM_STYLE = "rifle"
    MAG_PIVOT: Vec = (0, 4, -3.5)  # top centre of the magazine well
    REAR_SIGHT_Z0 = 1.0            # front face of the rear sight base
    EJECT: Vec = (1.1, BORE_Y, -2.0)
    UNDER_Y = 4.0                  # bottom face of the bottom rail
    SIDE_X = 1.25                  # outer face of the right rail

    def __init__(self, spec: RifleSpec):
        self.spec = spec
        self.m = Model(spec.id, density=1)

    # ---- derived numbers (also used by the gun files / the report) ----------------------
    @property
    def sight_y(self) -> float:
        return self.RAIL_TOP + self.IRON_HEIGHT

    @property
    def rear_sight_z(self) -> float:
        return self.REAR_SIGHT_Z0 + 0.75

    @property
    def optic_mount(self) -> Vec:
        return (0, self.RAIL_TOP, self.spec.optic_z)

    @property
    def muzzle_mount(self) -> Vec:
        return (0, BORE_Y, self.spec.barrel_end_z)

    @property
    def muzzle_z(self) -> float:
        """Front of the factory flash hider (the bare muzzle)."""
        return self.spec.barrel_end_z - self.spec.flash_hider

    @property
    def sight_line(self) -> Vec:
        """Irons: centre of the rear aperture. Optic-only rifle: the issued optic's eyepiece."""
        s = self.spec
        if s.irons:
            return (0, self.sight_y, self.rear_sight_z)
        if s.default_optic != "sniper_scope":
            raise ValueError(f"{s.id}: a rifle without irons needs default_optic='sniper_scope'")
        return (0, self.RAIL_TOP + att.OPTIC_AXIS_Y[s.default_optic], s.optic_z + 6.0)

    @property
    def left_hand(self) -> Vec:
        s = self.spec
        return (0, self.UNDER_Y + 0.5 if s.left_hand_y is None else s.left_hand_y, s.left_hand_z)

    # ---- pipeline -------------------------------------------------------------------------
    def build(self) -> Model:
        self.materials()
        self.receiver()
        self.action()
        self.barrel()
        self._variant("handguard", self.spec.handguard)
        self.sights()
        self._variant("stock", self.spec.stock)
        self.grip_and_trigger()
        self._variant("magazine", self.spec.magazine)
        self.arms()
        self.mounts_and_attachments()
        self.locators()
        self.animations()
        self.previews()
        return self.m

    def _variant(self, part: str, name: str) -> None:
        fn = getattr(self, f"{part}_{name}", None)
        if fn is None:
            known = sorted(n[len(part) + 1:] for n in dir(self) if n.startswith(part + "_"))
            raise ValueError(f"{self.spec.id}: no {part} variant {name!r}; known: {known}")
        fn()

    # ---- geometry steps the platforms implement ---------------------------------------------
    def receiver(self) -> None:
        raise NotImplementedError

    def action(self) -> None:
        raise NotImplementedError

    def barrel(self) -> None:
        raise NotImplementedError

    def front_sight_base(self) -> tuple[float, float]:
        """(z of the front face, y of the surface) the folding front sight stands on."""
        raise NotImplementedError

    # ---- shared steps ---------------------------------------------------------------------
    def materials(self) -> None:
        p, m = self.spec.palette, self.m
        m.material("receiver", p.receiver, 6, 0.78)
        m.material("rail", p.rail, 6, 0.76)
        m.material("lower", p.lower, 5, 0.80)
        m.material("stock_mat", p.stock, 5, 0.80)
        m.material("grip_mat", p.grip, 4, 0.80)
        m.material("barrel_mat", p.barrel, 5, 0.74)
        m.material("mag_mat", p.mag, 6, 0.74)
        m.material("detail", p.detail, 4, 0.78)

    def flash_hider(self) -> Bone:
        """Factory flash hider in `muzzle_default` (hidden while a muzzle device is fitted)."""
        s = self.spec
        md = T.default_part(self.m, "muzzle", self.muzzle_mount)
        md.box((-0.5, 5, self.muzzle_z), (1, 1, s.flash_hider), "detail", pattern="lines", inflate=0.25)
        return md

    def sights(self) -> None:
        """Folding back-up irons: post tip and aperture centre exactly at `sight_y`."""
        if not self.spec.irons:
            return
        sy = self.sight_y
        z0, base = self.front_sight_base()
        fs = self.m.bone("front_sight", pivot=(0, base, z0 + 0.5))
        fs.box((-1, base, z0), (2, 0.5, 1), "detail")                              # base
        fs.box((-1, base + 0.5, z0), (0.5, sy - base, 1), "detail")                # wings, 0.5 above the tip
        fs.box((0.5, base + 0.5, z0), (0.5, sy - base, 1), "detail")
        fs.box((-0.25, base + 0.5, z0 + 0.25), (0.5, sy - base - 0.5, 0.5), "detail")   # post
        z0, zc, rail = self.REAR_SIGHT_Z0, self.rear_sight_z, self.RAIL_TOP
        rs = self.m.bone("rear_sight", pivot=(0, rail, z0 + 0.5))                  # hidden under an optic
        rs.box((-1, rail, z0), (2, sy - 0.5 - rail, 1), "detail")                  # base = bottom of the frame
        rs.box((-1, sy - 0.5, zc - 0.25), (0.5, 1, 0.5), "detail")                 # 1x1 aperture frame
        rs.box((0.5, sy - 0.5, zc - 0.25), (0.5, 1, 0.5), "detail")
        rs.box((-1, sy + 0.5, zc - 0.25), (2, 0.5, 0.5), "detail")

    def grip_and_trigger(self) -> None:
        gr = self.m.bone("grip", pivot=(0, 4, 1))
        gr.box((-1, 0, 0), (2, 4, 2), "grip_mat", pattern="stipple",
               rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)
        tr = self.m.bone("trigger", pivot=(0, 4, -0.5))
        tr.box((-0.5, 3, -1), (1, 1, 1), "barrel_mat")

    # ---- magazines (20-round 7.62 box; 2 units sit inside the well) --------------------------
    def magazine_straight(self) -> None:
        """Straight steel SR-25 / SCAR-H magazine."""
        pad = self.spec.base_pad
        mg = T.default_part(self.m, "magazine", self.MAG_PIVOT)
        mg.box((-0.75, -0.5, -4.75), (1.5, 4.5, 2.5), "mag_mat", pattern="lines")
        mg.box((-0.75, -0.5 - pad, -4.75), (1.5, pad, 2.5), "detail", inflate=0.1)     # floor plate

    def magazine_curved(self) -> None:
        """Slightly curved polymer HK417 magazine."""
        mg = T.default_part(self.m, "magazine", self.MAG_PIVOT)
        mg.box((-0.75, 1.5, -4.75), (1.5, 2.5, 2.5), "mag_mat", pattern="lines")
        mg.box((-0.75, -0.75, -4.75), (1.5, 2.25, 2.5), "mag_mat", pattern="lines",
               rot=(9, 0, 0), pivot=(0, 1.5, -2.25), inflate=-0.05)
        mg.box((-0.75, -1.0, -4.75), (1.5, 0.25, 2.5), "detail",
               rot=(9, 0, 0), pivot=(0, 1.5, -2.25), inflate=0.1)                      # floor plate

    def arms(self) -> None:
        T.arms(self.m, self.RIGHT_HAND, self.left_hand, style=self.ARM_STYLE)

    def mounts_and_attachments(self) -> None:
        s = self.spec
        T.mounts(self.m, optic=self.optic_mount, muzzle=self.muzzle_mount,
                 underbarrel=(0, self.UNDER_Y, s.underbarrel_z),
                 side=(self.SIDE_X, BORE_Y, s.side_z))
        T.attach(self.m, *s.attachments)

    def locators(self) -> None:
        T.locators(self.m, muzzle_flash=(0, BORE_Y, self.muzzle_z - 0.25), shell_eject=self.EJECT,
                   sight_line=self.sight_line)

    def animations(self) -> None:
        f, m = self.spec.feel, self.m
        T.idle(m)
        T.fire_auto(m, recoil=f.recoil, bolt_bone="bolt", bolt_travel=f.bolt_travel,
                    length=f.fire_length, rise=f.rise)
        T.reload_mag(m, length=f.reload)
        T.reload_mag(m, length=f.reload_empty, empty=True, pull=f.bolt_travel)

    def previews(self) -> None:
        self.m.preview_configs = {"bare": att.loadout(self.m)}
        for name, ids in self.spec.loadouts.items():
            self.m.preview_configs[name] = att.loadout(self.m, *ids)


# ======================================================================================
# FN SCAR-H
# ======================================================================================
class ScarBuilder(RifleBuilder):
    """FN SCAR-H: tall monolithic aluminium upper with an uninterrupted top rail, polymer
    lower, reciprocating charging handle (part of the `bolt` bone), gas block with the folding
    front sight in front of the upper. `spec.front_z` = front of the upper."""

    RAIL_TOP = 7.5
    REAR_Z = 3.0                   # rear face of the receiver / stock hinge
    REAR_SIGHT_Z0 = 1.5
    EJECT = (1.1, BORE_Y, -3.25)
    UNDER_Y = 3.5

    def receiver(self) -> None:
        s, F, R = self.spec, self.spec.front_z, self.REAR_Z
        rc = self.m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 5, -6), (2, 2, R + 6), "receiver")                             # upper, rear half
        rc.box((-1, 5, F), (2, 2, -6 - F), "receiver", pattern="slots")            # vented front half
        rc.box((-1, 7, F), (2, 0.5, R - F), "rail", pattern="rails")               # uninterrupted top rail
        rc.box((-1, 4, -5), (2, 1, R + 5), "lower")                                # polymer trigger module
        rc.box((-1, 2, -5), (2, 2, 3), "lower", inflate=0.1)                       # flared magazine well
        rc.box((-0.5, 2, -2), (1, 1, 3), "lower")                                  # trigger guard
        rc.box((-1.05, 6, -9.5), (2.1, 0.5, 6), "detail")                          # charging-handle slots
        rc.box((0.5, 5.25, -1.75), (0.75, 0.75, 0.75), "receiver")                 # case deflector
        rc.box((-1.2, 4.25, -2.5), (0.2, 0.5, 1), "detail")                        # bolt catch (left)
        rc.box((-1.2, 4.25, 0.25), (2.4, 0.5, 0.5), "detail")                      # ambi selector

    def action(self) -> None:
        bo = self.m.bone("bolt", pivot=(0.5, BORE_Y, -3))
        bo.box((0.1, 5, -4.5), (1, 1, 2.5), "bright_steel")                        # carrier in the port
        bo.box((-1.75, 5.9, -9), (0.75, 0.7, 1), "detail", pattern="knurl")        # reciprocating handle (left)

    def barrel(self) -> None:
        s, F, E = self.spec, self.spec.front_z, self.spec.barrel_end_z
        ba = self.m.bone("barrel", pivot=(0, BORE_Y, F))
        ba.box((-0.5, 5, E), (1, 1, F - E), "barrel_mat", inflate=s.barrel_inflate)
        ba.box((-0.5, 5, F - 1.5), (1, 1, 1.5), "detail", inflate=0.25)            # gas block sleeve
        ba.box((-0.5, 6, F - 1.5), (1, 1, 1.5), "detail")                          # gas block
        ba.box((-0.25, 6.25, F - 2), (0.5, 0.5, 0.5), "steel", pattern="knurl")    # gas regulator
        self.flash_hider()

    def front_sight_base(self) -> tuple[float, float]:
        return self.spec.front_z - 1.25, 7.0                                       # on the gas block

    def handguard_default(self) -> None:
        """Lower fore-end with the 6 o'clock rail and the short removable side rails."""
        s, F = self.spec, self.spec.front_z
        hg = self.m.bone("handguard", pivot=(0, BORE_Y, -5))
        hg.box((-1, 4, F), (2, 1, -5 - F), "receiver")                             # fore-end under the upper
        hg.box((-0.5, 3.5, F + 0.5), (1, 0.5, -5.5 - F), "rail", pattern="rails")  # bottom rail
        z, n = s.side_rail or (F + 1, 5)
        hg.box((-1.25, 5.25, z), (2.5, 0.5, n), "rail", pattern="rails")           # side rails

    def stock_scar_folding(self) -> None:
        """Mk 17: side-folding, telescoping polymer stock with cheek riser (the 'boot')."""
        R = self.REAR_Z
        st = self.m.bone("stock", pivot=(1, BORE_Y, R + 0.5))                      # hinge on the right
        st.box((-1, 4.5, R), (2, 2.5, 1), "detail")                                # folding hinge
        st.box((-1, 5, R + 1), (2, 1.5, 5.5), "stock_mat")                         # telescoping body
        st.box((-1, 6.5, R + 2), (2, 0.75, 4), "stock_mat", pattern="lines")       # cheek riser
        st.wedge((0, 4.75, R + 1.5), (0, 2.75, R + 6.5), 1, 1, "stock_mat")        # lower strut
        st.box((-1, 2, R + 6.5), (2, 5, 1), "stock_mat")                           # butt
        st.box((-1, 2, R + 7.5), (2, 5, 0.5), "rubber")                            # pad

    def stock_precision(self) -> None:
        """Mk 20 SSR: fixed precision stock, adjustable cheek piece and length of pull."""
        R = self.REAR_Z
        st = self.m.bone("stock", pivot=(0, BORE_Y, R))
        st.box((-1, 4.5, R), (2, 2.5, 1), "detail")                                # fixed adapter
        st.box((-1, 5, R + 1), (2, 1.5, 6), "stock_mat")                           # main beam
        st.box((-0.25, 6.5, R + 2.5), (0.5, 0.5, 0.5), "steel")                    # cheek-piece posts
        st.box((-0.25, 6.5, R + 4.5), (0.5, 0.5, 0.5), "steel")
        st.box((-1, 7, R + 1.75), (2, 0.75, 4), "stock_mat", pattern="lines")      # raised cheek piece
        st.wedge((0, 4.75, R + 1.5), (0, 3, R + 4), 1, 1, "stock_mat")             # strut down to the hook
        st.box((-0.5, 2.5, R + 4), (1, 1, 3), "stock_mat")                         # bag-rider hook
        st.box((1, 5.25, R + 5), (0.5, 1, 1), "detail", pattern="knurl")           # LOP knob
        st.box((-1, 2, R + 7), (2, 5.5, 0.5), "detail")                            # butt plate
        st.box((-1, 2, R + 7.5), (2, 5.5, 0.75), "rubber")                         # recoil pad


# ======================================================================================
# AR-10 (KAC SR-25 / M110, HK417 / G28 / M110A1)
# ======================================================================================
class Ar10Builder(RifleBuilder):
    """AR-10 pattern: the M4's receiver with a longer magazine well and ejection port.
    `spec.front_z` = front of the free-float handguard."""

    FRONT_Z, REAR_Z = -5.0, 2.0    # receiver
    EJECT = (1.1, BORE_Y, -1.5)

    def receiver(self) -> None:
        s, F, R = self.spec, self.FRONT_Z, self.REAR_Z
        rc = self.m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 5, F), (2, 1, R - F), "receiver")                              # upper
        rc.box((-1, 6, F), (2, 1, R - F), "receiver", pattern="rails")             # flat-top rail
        rc.box((-1, 4, F), (2, 1, R - F), "lower")                                 # lower
        rc.box((-1, 2, F), (2, 2, 3), "lower")                                     # magazine well
        rc.box((-0.5, 2, -2), (1, 1, 3), "lower")                                  # trigger guard
        rc.box((0.5, 5, 0), (1, 1, 1), "receiver")                                 # brass deflector
        if s.forward_assist:
            rc.box((0.5, 5, 1), (1, 1, 1), "detail", pattern="knurl")
        rc.box((-1.2, 4.5, -2.5), (0.2, 0.75, 1), "detail")                        # bolt catch paddle (left)
        rc.box((-1.2, 4.25, 0.25), (2.4, 0.5, 0.5), "detail")                      # ambi selector

    def action(self) -> None:
        bo = self.m.bone("bolt", pivot=(0.5, BORE_Y, -1.5))
        bo.box((0.1, 5, -3), (1, 1, 3), "bright_steel")                            # carrier in the port
        ch = self.m.bone("charging_handle", pivot=(0, 6.5, self.REAR_Z))
        ch.box((-1, 6, self.REAR_Z), (2, 1, 1), "detail")

    def barrel(self) -> None:
        s, F, E = self.spec, self.spec.front_z, self.spec.barrel_end_z
        ba = self.m.bone("barrel", pivot=(0, BORE_Y, self.FRONT_Z))
        ba.box((-0.5, 5, E), (1, 1, F - E), "barrel_mat", inflate=s.barrel_inflate)
        ba.box((-0.5, 5, F - 1.25), (1, 1, 1), "detail", inflate=0.3)              # gas block / QD plate
        self.flash_hider()

    def front_sight_base(self) -> tuple[float, float]:
        return self.spec.front_z, self.RAIL_TOP                                    # front end of the top rail

    def handguard_quad_rail(self) -> None:
        """KAC URX: free-float tube with full-length rails at 12, 3, 6 and 9 o'clock."""
        F, n = self.spec.front_z, self.FRONT_Z - self.spec.front_z
        hg = self.m.bone("handguard", pivot=(0, BORE_Y, self.FRONT_Z))
        hg.box((-1, 4.5, F), (2, 2, n), "receiver")
        hg.box((-1, 6.5, F), (2, 0.5, n), "rail", pattern="rails")                 # top, flush with the receiver
        hg.box((-1.25, 5.25, F), (2.5, 0.5, n), "rail", pattern="rails")           # sides
        hg.box((-0.5, 4, F), (1, 0.5, n), "rail", pattern="rails")                 # bottom
        hg.box((-1, 4.5, self.FRONT_Z - 0.5), (2, 2, 0.5), "detail", inflate=0.15)  # barrel nut

    def handguard_mlok(self) -> None:
        """Geissele free-float: slim slotted tube, rail only on top, short accessory rails at
        6 and 3 o'clock where the mounts are."""
        s, F, n = self.spec, self.spec.front_z, self.FRONT_Z - self.spec.front_z
        hg = self.m.bone("handguard", pivot=(0, BORE_Y, self.FRONT_Z))
        hg.box((-1, 4.75, F), (2, 1.75, n), "receiver", pattern="slots")
        hg.box((-0.75, 6.5, F), (1.5, 0.5, n), "rail", pattern="rails")            # top rail
        hg.box((-0.5, 4, s.underbarrel_z - 2), (1, 0.75, 4), "rail", pattern="rails")   # bottom M-LOK rail
        z, k = s.side_rail or (s.side_z - 1.5, 3)
        hg.box((1, 5.25, z), (0.25, 0.5, k), "rail", pattern="rails")              # right M-LOK rail
        hg.box((-1, 4.75, self.FRONT_Z - 0.5), (2, 1.75, 0.5), "detail", inflate=0.15)  # barrel nut

    def stock_fixed_lop(self) -> None:
        """M110: fixed A2-length buttstock with the length-of-pull knob."""
        R = self.REAR_Z
        st = self.m.bone("stock", pivot=(0, BORE_Y, R))
        st.box((-0.75, 4.75, R), (1.5, 1.5, 1), "detail")                          # receiver extension collar
        st.box((-1, 4.5, R + 1), (2, 2, 6.5), "stock_mat")                         # body
        st.box((-1, 3.5, R + 3), (2, 1, 4.5), "stock_mat")                         # stepped underside
        st.box((-1, 2.5, R + 5), (2, 1, 2.5), "stock_mat")                         # toe
        st.box((1, 4, R + 5.5), (0.5, 1, 1), "detail", pattern="knurl")            # LOP knob
        st.box((-1, 2.5, R + 7.5), (2, 4, 0.5), "detail")                          # butt plate

    def stock_hk_collapsible(self) -> None:
        """M110A1: HK collapsible stock with the raised adjustable cheek comb, on a buffer tube
        (in `stock_default`, so an AR stock attachment can replace it)."""
        R = self.REAR_Z
        st = T.default_part(self.m, "stock", (0, BORE_Y, R))
        st.box((-0.5, 5, R), (1, 1, 3), "detail", pattern="lines")                 # buffer tube
        st.box((-1, 4.5, R + 3), (2, 2, 5.5), "stock_mat")                         # body
        st.box((-1, 6.5, R + 3.5), (2, 1, 4), "stock_mat", pattern="lines")        # cheek comb
        st.box((-1, 3.5, R + 5.5), (2, 1, 3), "stock_mat")                         # stepped underside
        st.box((-1, 2.5, R + 7), (2, 1, 1.5), "stock_mat")                         # toe
        st.box((-1, 2.5, R + 8.5), (2, 5, 1), "rubber")                            # butt pad
