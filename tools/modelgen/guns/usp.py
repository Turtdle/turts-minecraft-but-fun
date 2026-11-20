"""H&K USP45 - full-size .45 ACP hammer-fired pistol, black, 12 rounds.

Real gun: 200 mm long (6 units), 112 mm barrel, 12-round magazine. Built on the shared
`striker_pistols.StrikerPistol` builder (layout, arms, mounts, animations); everything that makes
it a USP instead of a Glock is overridden HERE, the shared module is untouched:
  * a taller, slab-sided slide (1.25 high) with rear-only serrations and 3-dot sights
  * the long trigger guard with its rounded (chamfered) front corners
  * the USP accessory GROOVE under the dust cover (a grooved block, not a Picatinny rail)
  * the control lever (safety / decocker) on the left of the frame, slide release, the
    ambidextrous magazine-release paddle at the trigger guard
  * the exposed HAMMER (bone `hammer`, child of `receiver`): it is cocked by the slide in `fire`
    and stays cocked while the slide is locked back in `reload_empty`
  * an upright grip with a flared magazine floor plate
Bones: receiver, handguard (dust cover + groove, carries side_mount), grip, barrel, slide,
trigger, magazine + magazine_default, controls, hammer.
"""
from armorygen import Bone
from striker_pistols import Handling, Palette, PistolSpec, StrikerPistol

USP_PALETTE = Palette(slide=(40, 41, 45), frame=(28, 28, 30), controls=(20, 20, 22),
                      barrel=(96, 98, 104), magazine=(44, 44, 47), baseplate=(24, 24, 26),
                      accent=(18, 18, 20))

HAMMER_COCK = 50.0         # degrees the hammer swings back when the slide rides over it


class Usp(StrikerPistol):
    SLIDE_H = 1.25                     # tall slab-sided slide -> sight line y 6.75
    GUARD_Z = (-2.25, 0.0)             # long trigger guard (made for gloves)
    GUARD_Y = 2.75
    HAMMER_PIVOT = (0.0, 4.9, 2.55)

    # ---- frame --------------------------------------------------------------------------
    def frame(self, b: Bone) -> None:
        """Frame strip + the long USP trigger guard, its lower front corner cut off at 45 degrees
        (the blocky stand-in for the rounded guard)."""
        w, f, (gf, gr) = self.HALF_W, self.mat("frame"), self.GUARD_Z
        gy, c = self.GUARD_Y, 0.7
        b.box((-w, self.FRAME_Y, gf), (2 * w, 0.5, self.SLIDE_REAR_Z - gf), f)            # frame strip
        b.box((-0.5, gy, gf + c), (1, 0.5, gr - gf - c + 1), f)                            # guard: bottom bar (runs into the grip)
        b.box((-0.5, gy + c, gf), (1, self.FRAME_Y - gy - c, 0.5), f)                      # front bar
        b.wedge((0, gy + 0.3, gf + c + 0.2), (0, gy + c + 0.2, gf + 0.3), 1, 0.45, f)     # the cut corner
        b.box((-0.5, self.FRAME_Y - 0.3, self.SLIDE_REAR_Z), (1, 0.6, 0.35), f)            # short beavertail

    def rail(self, b: Bone, z0: float, length: float) -> None:
        """USP universal mounting groove: a plain block with one long groove per side."""
        w = self.HALF_W
        b.box((-w, self.RAIL_Y, z0 + 0.25), (2 * w, 0.5, length - 0.25), self.mat("frame"))
        b.box((-w - 0.03, self.RAIL_Y + 0.15, z0 + 0.4), (2 * w + 0.06, 0.2, length - 0.6), self.mat("accent"))

    def grip(self, b: Bone) -> None:
        super().grip(b)
        w, (px, py, pz) = self.HALF_W, self.GRIP_PIVOT
        L, d = self.grip_len, self.GRIP_DEPTH
        kw = dict(rot=self.grip_rot, pivot=self.GRIP_PIVOT)
        b.box((-w - 0.04, py - L + 0.3, pz - d / 2 + 0.3), (2 * w + 0.08, L - 1.3, d - 0.6),
              self.mat("frame"), pattern="knurl", **kw)                               # stippled side panels
        b.box((-0.3, py - L + 0.2, pz + d / 2 - 0.2), (0.6, L - 1.2, 0.25), self.mat("frame"),
              pattern="lines", **kw)                                                  # checkered backstrap

    # ---- slide ---------------------------------------------------------------------------
    def serrations(self, b: Bone) -> None:
        """Rear cocking serrations only (the HK pattern: a block of fine vertical cuts)."""
        w, y = self.HALF_W + 0.05, self.SLIDE_Y + 0.4
        b.box((-w, y, self.SLIDE_REAR_Z - 1.6), (2 * w, self.SLIDE_H - 0.5, 1.3), self.mat("slide"), pattern="lines")

    def slide(self, b: Bone) -> None:
        super().slide(b)
        w = self.HALF_W
        # the long flat step along the bottom of the USP slide
        b.box((-w - 0.04, self.SLIDE_Y, self.front_z + 0.2), (2 * w + 0.08, 0.3, self.SLIDE_REAR_Z - self.front_z - 0.4),
              self.mat("slide"))

    def sight_marks(self, front, rear, front_face_z, rear_face_z) -> None:
        y = self.sight_deck_y
        front.box((-0.125, y + 0.2, front_face_z), (0.25, 0.25, 0.03), "white")       # 3-dot sights
        for x in (-0.625, 0.375):
            rear.box((x, y + 0.2, rear_face_z), (0.25, 0.25, 0.03), "white")

    # ---- controls + hammer -----------------------------------------------------------------
    def controls(self, b: Bone) -> None:
        w, c = self.HALF_W, self.mat("controls")
        b.box((-w - 0.15, 4.55, 1.2), (0.15, 0.35, 1.1), c)                           # control lever: safety / decocker (left)
        b.box((-w - 0.1, 4.65, -0.6), (0.1, 0.25, 1.4), c)                            # long slide release (left)
        b.box((-w - 0.08, 3.3, -0.3), (2 * w + 0.16, 0.3, 0.6), c)                  # ambidextrous magazine-release paddle
        hm = self.m.bone("hammer", parent="receiver", pivot=self.HAMMER_PIVOT)
        hm.box((-0.2, 4.9, self.SLIDE_REAR_Z), (0.4, 0.9, 0.3), c)                    # hammer, down
        hm.box((-0.25, 5.55, self.SLIDE_REAR_Z + 0.3), (0.5, 0.3, 0.2), c, pattern="lines")  # spur

    def magazine(self, b: Bone) -> None:
        super().magazine(b)
        w, (px, py, pz) = self.HALF_W, self.GRIP_PIVOT
        L, d, bp = self.grip_len, self.GRIP_DEPTH, self.BASEPLATE_H
        b.box((-w - 0.05, py - L - bp, pz - d / 2 - 0.4), (2 * w + 0.1, bp, 0.3), self.mat("baseplate"),
              rot=self.grip_rot, pivot=self.GRIP_PIVOT)                               # floor-plate toe

    # ---- animations ------------------------------------------------------------------------
    def animations(self) -> None:
        super().animations()
        rest, cock = (0.0, 0.0, 0.0), (HAMMER_COCK, 0.0, 0.0)
        fire, empty = self.m.animations["fire"], self.m.animations["reload_empty"]
        k = fire.length / 0.1                       # templates.fire_pistol base timeline
        fire.keys("hammer", "rotation", {0: rest, round(0.02 * k, 4): (cock, "easeOutQuad"),
                                         round(0.07 * k, 4): cock, round(0.085 * k, 4): (rest, "easeInQuad")})
        k = empty.length / 2.6                      # templates.reload_mag base timeline (bolt_release)
        empty.keys("hammer", "rotation", {0: rest, round(0.05 * k, 4): (cock, "easeOutQuad"),
                                          round(2.1 * k, 4): cock, round(2.2 * k, 4): (rest, "easeInQuad")})


SPEC = PistolSpec(
    id="usp",
    palette=USP_PALETTE,
    slide_front_z=-3.5,        # slide z -3.5 .. 2.5 = 6 units (200 mm)
    grip_bottom_y=2.25,        # big .45 grip; floor plate to y 2.0
    grip_rake=14.0,            # more upright than a Glock
    handling=Handling(reload=1.8, reload_empty=2.3, fire=0.1, recoil=0.6, flip=6.5),
    # attachments.py has one pistol can (suppressor_9mm, "9 mm pistol / SMG can"); no .45 can exists
    attachments=("suppressor_9mm", "laser"),
    previews={"loadout": ("suppressor_9mm", "laser")},
)
PISTOL = Usp(SPEC)
SIGHT_Y = PISTOL.sight_y           # 6.75
SIGHT_LINE = PISTOL.sight_line     # (0, 6.75, 2.25)


def model():
    return PISTOL.build()
