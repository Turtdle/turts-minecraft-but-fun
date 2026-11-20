"""Beretta M9 (92FS) - 9x19 mm hammer-fired service pistol, all black, 15 rounds.

Real gun: 217 mm long (6.5 units), 125 mm barrel, 15-round magazine. Built on the shared
`striker_pistols.StrikerPistol` builder (layout, arms, mounts, animations); everything Beretta
is overridden HERE, the shared module is untouched:
  * the OPEN-TOP slide: a front bridge that carries the front sight, two low side rails, and the
    breech block behind - the barrel lies bare in between and stays put when the slide cycles
  * the barrel itself: thick, lighter than the slide, standing 0.25 proud of the muzzle end
  * the ambidextrous safety / decocker levers ON THE SLIDE (they ride with it)
  * the exposed ring HAMMER (bone `hammer`, child of `receiver`): cocked by the slide in `fire`,
    held cocked while the slide is locked back in `reload_empty`
  * the long alloy frame with a smooth dust cover (the M9 has no accessory rail; the side mount
    sits under the dust cover all the same, so the shared light / laser can be fitted), the
    trigger guard with its squared hook, black checkered grip panels with screws, lanyard loop
Bones: receiver, handguard (dust cover, carries side_mount), grip, barrel, slide, trigger,
magazine + magazine_default, controls, hammer.
"""
from armorygen import Bone
from striker_pistols import Handling, Palette, PistolSpec, StrikerPistol

M9_PALETTE = Palette(slide=(44, 45, 49), frame=(36, 36, 39), controls=(22, 22, 24),
                     barrel=(86, 88, 95), magazine=(50, 50, 54), baseplate=(26, 26, 28),
                     accent=(20, 20, 22))

HAMMER_COCK = 50.0         # degrees the hammer swings back when the slide rides over it


class BerettaM9(StrikerPistol):
    BRIDGE_LEN = 0.75                  # front bridge of the slide (carries the front sight)
    BREECH_Z = -0.25                   # front face of the breech block = rear end of the open top
    RAIL_H = 0.45                      # height of the slide's side rails beside the bare barrel
    PORT_Z = (-1.5, BREECH_Z)          # cases leave through the open top right ahead of the breech
    HAMMER_PIVOT = (0.0, 4.9, 2.55)
    PROUD = 0.25                       # the barrel stands this far out of the slide

    @property
    def muzzle(self):
        return (0.0, self.BORE_Y, self.front_z - self.PROUD)

    # ---- frame --------------------------------------------------------------------------
    def frame(self, b: Bone) -> None:
        super().frame(b)
        f, (gf, _) = self.mat("frame"), self.GUARD_Z
        b.box((-0.5, self.GUARD_Y, gf - 0.25), (1, 0.75, 0.25), f)                    # squared hook of the guard
        b.box((-0.5, self.FRAME_Y - 0.4, self.SLIDE_REAR_Z), (1, 0.7, 0.5), f)        # frame tang under the hammer

    def rail(self, b: Bone, z0: float, length: float) -> None:
        """No rail on an M9: the deep, smooth dust cover that tapers toward the muzzle."""
        b.box((-0.6, self.RAIL_Y, z0 + 1.0), (1.2, 0.5, length - 1.0), self.mat("frame"))
        b.box((-0.6, self.RAIL_Y + 0.25, z0 + 0.4), (1.2, 0.25, 0.6), self.mat("frame"))

    def grip(self, b: Bone) -> None:
        super().grip(b)
        m, w, (px, py, pz) = self.m, self.HALF_W, self.GRIP_PIVOT
        L, d = self.grip_len, self.GRIP_DEPTH
        kw = dict(rot=self.grip_rot, pivot=self.GRIP_PIVOT)
        m.material("m9_panel", (23, 23, 25), 5, 0.82)                                 # black plastic grip panels
        m.material("m9_screw", (120, 122, 128), 4, 0.8)
        b.box((-w - 0.1, py - L + 0.2, pz - d / 2 + 0.25), (2 * w + 0.2, L - 0.9, d - 0.45), "m9_panel",
              pattern="knurl", **kw)                                                  # checkered panels (both sides)
        for along in (L - 1.0, 0.45):                                                 # panel screws
            b.box((-w - 0.13, py - L + along, pz - 0.1), (2 * w + 0.26, 0.2, 0.2), "m9_screw", **kw)
        b.box((-0.15, py - L - 0.05, pz + d / 2 - 0.1), (0.3, 0.3, 0.3), self.mat("controls"), **kw)   # lanyard loop

    # ---- barrel + open-top slide --------------------------------------------------------------
    def barrel(self, b: Bone) -> None:
        z0 = self.front_z - self.PROUD
        b.box((-0.45, 5.05, z0), (0.9, 0.9, self.BREECH_Z + 0.5 - z0), self.mat("barrel"))        # bare barrel
        b.box((-0.25, 5.25, z0 - 0.03), (0.5, 0.5, 0.03), self.mat("accent"))                     # bore
        b.box((-0.55, 4.98, self.BREECH_Z - 1.0), (1.1, 0.5, 1.0), self.mat("barrel"), pattern="lines")  # locking-block lugs

    def slide(self, b: Bone) -> None:
        w, y, h, z0, mat = self.HALF_W, self.SLIDE_Y, self.SLIDE_H, self.front_z, self.mat("slide")
        o0, o1 = z0 + self.BRIDGE_LEN, self.BREECH_Z
        b.box((-w, y, z0), (2 * w, h, self.BRIDGE_LEN), mat)                          # front bridge round the barrel
        for x in (-w, w - 0.3):                                                       # low side rails: the open top
            b.box((x, y, o0), (0.3, self.RAIL_H, o1 - o0), mat)
        b.box((-w, y, o1), (2 * w, h, self.SLIDE_REAR_Z - o1), mat)                   # breech block
        b.box((w, y + 0.55, o1), (0.06, 0.2, 0.7), self.mat("barrel"))                # extractor
        b.box((-w - 0.05, y + 0.1, self.SLIDE_REAR_Z - 1.4), (2 * w + 0.1, h - 0.2, 1.1), mat, pattern="lines")  # serrations
        c = self.mat("controls")
        b.box((-w - 0.2, y + 0.4, 1.45), (2 * w + 0.4, 0.3, 0.8), c)                  # slide-mounted safety / decocker levers
        b.box((-w - 0.08, y + 0.75, 1.95), (0.08, 0.15, 0.15), "red")                 # red "fire" dot (left)

    def sight_marks(self, front, rear, front_face_z, rear_face_z) -> None:
        y = self.sight_deck_y
        front.box((-0.125, y + 0.2, front_face_z), (0.25, 0.25, 0.03), "white")       # dot ...
        rear.box((-0.125, y + 0.02, rear_face_z), (0.25, 0.2, 0.03), "white")         # ... and post ("dot the i")

    # ---- controls + hammer -----------------------------------------------------------------
    def controls(self, b: Bone) -> None:
        w, c = self.HALF_W, self.mat("controls")
        b.box((-w - 0.1, 4.65, -0.9), (0.1, 0.25, 1.6), c)                            # slide stop (left)
        b.box((-w - 0.1, 4.55, -2.3), (0.1, 0.35, 0.6), c)                            # takedown lever (left)
        b.box((w, 4.6, -2.2), (0.08, 0.25, 0.3), c)                                   # takedown button (right)
        b.box((-w - 0.1, 3.9, 0.05), (2 * w + 0.2, 0.35, 0.35), c)                    # magazine release
        hm = self.m.bone("hammer", parent="receiver", pivot=self.HAMMER_PIVOT)
        z = self.SLIDE_REAR_Z
        hm.box((-0.2, 4.9, z), (0.4, 0.5, 0.3), c)                                    # hammer body, down
        hm.box((-0.25, 5.4, z), (0.5, 0.2, 0.55), c)                                  # ring: bottom
        hm.box((-0.25, 5.95, z), (0.5, 0.15, 0.55), c)                                # ring: top
        hm.box((-0.25, 5.6, z + 0.4), (0.5, 0.35, 0.15), c)                           # ring: rear (open in the middle)

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
    id="m9",
    palette=M9_PALETTE,
    slide_front_z=-4.0,        # slide z -4.0 .. 2.5 = 6.5 units (217 mm)
    grip_bottom_y=2.4,         # full-size grip; base plate to y 2.15
    grip_rake=16.0,
    handling=Handling(reload=1.8, reload_empty=2.3, fire=0.1, recoil=0.5, flip=5.0),
    attachments=("suppressor_9mm", "laser"),
    previews={"loadout": ("suppressor_9mm", "laser")},
)
PISTOL = BerettaM9(SPEC)
SIGHT_Y = PISTOL.sight_y           # 6.5: front blade tip = top of the rear notch
SIGHT_LINE = PISTOL.sight_line     # (0, 6.5, 2.25)


def model():
    return PISTOL.build()
