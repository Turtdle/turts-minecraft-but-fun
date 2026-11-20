"""Shared base classes and parts for the machine-gun / .50 family (M249, M240, M2A1, M107).

Why this module exists: the four guns share one build pipeline and a lot of small parts
(aperture sights, linked belts, folded bipods). A gun file is a small subclass that only states
what is DIFFERENT about that gun; a family-wide change (fire feel of every belt-fed, how a feed
cover opens, how a belt is drawn) is one edit here, and a mod-wide change (arms, mounts, the
animation choreography) is still one edit in templates.py, which everything here goes through.

    class M249(BeltFedGun):
        ID = "m249"
        ...constants...
        def materials(self, m): ...
        def parts(self, m): ...          # receiver, barrel, feed_cover, magazine, bolt...

    def model():
        return M249().build()

STRUCTURE
  * ARM STYLES      - two extra holds registered in templates.ARM_STYLES (the registry is the
                      extension point templates.py documents)
  * part helpers    - plain functions that add boxes to a bone (sights, belt, bipod legs)
  * GunBuilder      - template-method base: materials -> parts -> arms -> mounts -> locators ->
                      animations -> previews
  * BeltFedGun      - GunBuilder for belt-fed guns (feed cover, ammo box as `magazine`, charging
                      handle; fire / reload timings derived from RPM and the reload times)
  * MagFedGun       - GunBuilder for box-magazine guns of this family
"""
from __future__ import annotations

from dataclasses import replace

import attachments as att
import templates as T
from armorygen import Bone, Model

Vec = tuple[float, float, float]

# ======================================================================================
# ARM STYLES (added to the templates registry; never overwrite an existing entry)
# ======================================================================================
# belt-fed gun fired from the shoulder: the left hand is UNDER the handguard like a rifle (on top
# of the feed cover it would sit in the sight line and inside a fitted optic), but the gun is
# canted as gently as templates' 'mg' hold while the belt is changed.
MG_SHOULDERED = "mg_shouldered"
T.ARM_STYLES.setdefault(MG_SHOULDERED, replace(T.ARM_STYLES["mg"],
                                               left_rot=T.ARM_STYLES["rifle"].left_rot))
# spade grips (M2): both fists on the vertical grips behind the receiver, forearms running almost
# straight back; no trigger finger (thumbs on the butterfly), the gun barely cants on its tripod.
SPADE = "spade"
T.ARM_STYLES.setdefault(SPADE, T.ArmStyle(right_rot=(105, 22, 0), left_rot=(105, -22, 0),
                                          finger=False, left_dz=-T.ARM_W / 2,
                                          cant=(2.0, 4.0), cant_pos=(0.0, 0.2, 0.2),
                                          swap_arm_rx=-10.0, catch=False))


# ======================================================================================
# PART HELPERS (all coordinates are model units; every helper returns the bone)
# ======================================================================================
def rear_aperture(bone: Bone, z: float, base_y: float, sight_y: float, mat: str) -> Bone:
    """Rear aperture / leaf sight (the M4 pattern): a frame with a 1 x 1 hole centred on
    `sight_y`, standing on `base_y`, 1 long around z."""
    bone.box((-1, base_y, z - 0.5), (2, sight_y - 0.5 - base_y, 1), mat)        # base = bottom of the frame
    for x in (-1, 0.5):
        bone.box((x, sight_y - 0.5, z - 0.25), (0.5, 1, 0.5), mat)              # frame sides
    bone.box((-1, sight_y + 0.5, z - 0.25), (2, 0.5, 0.5), mat)                 # top bar
    return bone


def front_post(bone: Bone, z: float, base_y: float, sight_y: float, mat: str) -> Bone:
    """Front post between two protective ears; the post TIP is exactly at `sight_y`."""
    bone.box((-0.5, base_y, z - 0.5), (1, sight_y - 1 - base_y, 1), mat)        # stem
    bone.box((-1, sight_y - 1, z - 0.5), (2, 0.5, 1), mat)                      # sight base
    for x in (-1, 0.5):
        bone.box((x, sight_y - 0.5, z - 0.5), (0.5, 1, 1), mat)                 # ears, 0.5 above the tip
    bone.box((-0.25, sight_y - 0.5, z - 0.25), (0.5, 0.5, 0.5), mat)            # post
    return bone


def linked_belt(bone: Bone, origin: Vec, size: Vec, tip: float = 0.5,
                case: str = "brass", bullet: str = "copper") -> Bone:
    """A slab of linked cartridges lying along Z, bullets toward the muzzle: `origin` / `size`
    are the slab's min corner and size, the front `tip` units are the bullets."""
    (x, y, z), (sx, sy, sz) = origin, size
    bone.box((x, y, z + tip), (sx, sy, sz - tip), case, pattern="lines")
    bone.box((x, y, z), (sx, sy, tip), bullet, pattern="lines", inflate=-0.05)
    return bone


def folded_legs(bone: Bone, hinge: Vec, length: float, spread: float, mat: str,
                toward_muzzle: bool = False, foot: str | None = "rubber") -> Bone:
    """Integral bipod folded along the gun: a yoke at `hinge` (centre, units) and two 0.5-thick
    legs `spread` apart (centre to centre) running `length` units back (or forward)."""
    hx, hy, hz = hinge
    z0 = hz - length if toward_muzzle else hz
    bone.box((hx - spread / 2 - 0.25, hy - 0.25, hz - 0.5), (spread + 0.5, 0.75, 1), mat)   # yoke
    for sx in (-1, 1):
        x = hx + sx * spread / 2 - 0.25
        bone.box((x, hy - 0.75, z0), (0.5, 0.5, length), mat)                               # leg
        if foot:
            zf = z0 - 0.5 if toward_muzzle else z0 + length
            bone.box((x, hy - 0.75, zf), (0.5, 0.5, 0.5), foot, inflate=0.1)                # foot
    return bone


def pistol_grip(m: Model, mat: str = "polymer", top: Vec = (0, 4, 1)) -> Bone:
    """The M4's raked pistol grip (bone `grip`), so the right hand sits where the shared
    first-person transforms expect it: T.arms(right_hand=(0, 2, 2))."""
    x, y, z = top
    gr = m.bone("grip", pivot=top)
    gr.box((x - 1, y - 4, z - 1), (2, 4, 2), mat, pattern="stipple", rot=(-22.5, 0, 0), pivot=top,
           inflate=-0.05)
    return gr


def blade_trigger(m: Model, mat: str = "steel", pin: Vec = (0, 4, -0.5)) -> Bone:
    """Rifle trigger blade (bone `trigger`, pivot at the trigger pin)."""
    x, y, z = pin
    tr = m.bone(T.TRIGGER, pivot=pin)
    tr.box((x - 0.5, y - 1, z - 0.5), (1, 1, 1), mat)
    return tr


# ======================================================================================
# BUILDERS
# ======================================================================================
class GunBuilder:
    """Template-method base. Subclasses set the constants and implement `materials`, `parts`
    and `animations`; `build()` runs the fixed pipeline every gun of the family shares."""

    ID: str = ""
    BORE_Y: float = T.BORE_Y
    SIGHT_Y: float = 8.0                 # iron-sight line (post tip / aperture centre)
    # arms (templates.arms)
    ARM_STYLE: str = "rifle"
    R_HAND: Vec = (0, 2, 2)
    L_HAND: Vec | None = None
    # attachments: slot -> mount point (templates.mounts), ids (attachments.SLOTS), loadout previews
    MOUNTS: dict[str, Vec] = {}
    ATTACHMENTS: tuple[str, ...] = ()
    DEFAULT_ATTACHMENTS: dict[str, str] = {}      # slot -> id the issued gun ships with (for Java)
    PREVIEWS: dict[str, tuple[str, ...]] = {}
    # locators (templates.locators)
    MUZZLE_FLASH: Vec = (0, T.BORE_Y, 0)
    SHELL_EJECT: Vec = (1.1, T.BORE_Y, 0)
    SIGHT_LINE: Vec = (0, 8.0, 0)
    # timings from the researched specs
    RPM: float = 600
    RELOAD_S: float | None = None
    EMPTY_RELOAD_S: float | None = None
    # fire feel
    RECOIL: float = 0.8
    RISE: float = 2.0
    BOLT_TRAVEL: float = 2.0

    # ---- pipeline ---------------------------------------------------------------------
    def build(self) -> Model:
        if not self.ID:
            raise ValueError(f"{type(self).__name__}: set ID")
        m = Model(self.ID, density=1)
        self.materials(m)
        self.parts(m)
        T.arms(m, self.R_HAND, self.L_HAND, self.ARM_STYLE)
        self.mount(m)
        T.locators(m, muzzle_flash=self.MUZZLE_FLASH, shell_eject=self.SHELL_EJECT,
                   sight_line=self.SIGHT_LINE)
        T.idle(m)
        self.animations(m)
        m.preview_configs = {"bare": att.loadout(m),
                             **{name: att.loadout(m, *ids) for name, ids in self.PREVIEWS.items()}}
        return m

    # ---- hooks ------------------------------------------------------------------------
    def materials(self, m: Model) -> None:
        """Register the gun's own materials (m.material)."""

    def parts(self, m: Model) -> None:
        """Build every bone except arms, mounts, attachments and locators."""
        raise NotImplementedError

    def mount(self, m: Model) -> None:
        """Mount bones + the shared attachments that fit this gun."""
        T.mounts(m, **self.MOUNTS)
        T.attach(m, *self.ATTACHMENTS)

    def animations(self, m: Model) -> None:
        """`fire`, `reload`, `reload_empty` (idle is already added)."""
        raise NotImplementedError

    # ---- shared rules -----------------------------------------------------------------
    def shot_interval(self) -> float:
        return 60.0 / self.RPM

    def fire_length(self, cap: float = 0.07) -> float:
        """`fire` must end before the next shot: 85 % of the shot interval, at most `cap`."""
        return round(min(cap, 0.85 * self.shot_interval()), 3)


class BeltFedGun(GunBuilder):
    """Belt-fed, automatic. `parts` must build: receiver, barrel, trigger, `feed_cover`
    (pivot = its hinge), `magazine` (the ammo box; the visible belt is a child of it),
    `bolt` and `charging_handle`."""

    ARM_STYLE = MG_SHOULDERED
    COVER_BONE, BOX_BONE, CHARGE_BONE, BOLT_BONE = "feed_cover", "magazine", "charging_handle", "bolt"
    # the real covers hinge at the FRONT, so the rear end lifts: a negative templates open_deg
    COVER_OPEN_DEG: float = -65.0
    OPTIC_ON_COVER: bool = True          # the top rail is the feed cover: the optic opens with it
    CHARGE_KNOB: Vec | None = None       # centre of the charging-handle knob at rest
    CHARGE_PULL: float = 3.0
    CHARGE_HAND: str = "right"

    def mount(self, m: Model) -> None:
        optic = self.MOUNTS.get("optic")
        if optic is not None and self.OPTIC_ON_COVER:
            # same bone name / pivot templates.mounts would create, but riding on the cover
            m.bone(T.MOUNTS["optic"][0], parent=self.COVER_BONE, pivot=optic)
        super().mount(m)

    def animations(self, m: Model) -> None:
        T.fire_auto(m, recoil=self.RECOIL, bolt_bone=self.BOLT_BONE, bolt_travel=self.BOLT_TRAVEL,
                    length=self.fire_length(), rise=self.RISE)
        for empty, length in ((False, self.RELOAD_S), (True, self.EMPTY_RELOAD_S)):
            T.reload_belt(m, cover_bone=self.COVER_BONE, box_bone=self.BOX_BONE, length=length,
                          empty=empty, open_deg=self.COVER_OPEN_DEG, charge_bone=self.CHARGE_BONE,
                          charge_pull=self.CHARGE_PULL, charge_hand=self.CHARGE_HAND,
                          charge_also=(self.BOLT_BONE,), knob=self.CHARGE_KNOB)


class MagFedGun(GunBuilder):
    """Semi-automatic box-magazine gun whose bolt does not lock open: after an empty reload a
    hand racks the charging handle, which is part of the `bolt` bone. `parts` must build:
    receiver, barrel, trigger, magazine, `bolt`."""

    BOLT_BONE, MAG_BONE = "bolt", "magazine"
    FIRE_LENGTH: float = 0.25
    CHARGE_KNOB: Vec | None = None
    CHARGE_PULL: float = 3.0
    CHARGE_HAND: str = "right"
    BARREL_RECOIL: float = 0.0           # short-recoil guns: units the `barrel` bone slides back

    def animations(self, m: Model) -> None:
        length = min(self.FIRE_LENGTH, self.fire_length(cap=self.FIRE_LENGTH))
        fire = T.fire_auto(m, recoil=self.RECOIL, bolt_bone=self.BOLT_BONE,
                           bolt_travel=self.BOLT_TRAVEL, length=length, rise=self.RISE)
        if self.BARREL_RECOIL and "barrel" in m.bones:
            fire.keys("barrel", "position", {0: (0, 0, 0),
                                             round(length * 0.3, 4): ((0, 0, self.BARREL_RECOIL), T.OUT),
                                             length: ((0, 0, 0), T.IO)})
        T.reload_mag(m, self.MAG_BONE, length=self.RELOAD_S)
        T.reload_mag(m, self.MAG_BONE, length=self.EMPTY_RELOAD_S, empty=True, empty_action="cycle",
                     bolt_bone=self.BOLT_BONE, lift_deg=0, pull=self.CHARGE_PULL,
                     hand=self.CHARGE_HAND, knob=self.CHARGE_KNOB)
