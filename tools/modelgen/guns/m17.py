"""SIG Sauer M17 (MHS, full-size P320) - coyote-tan 9x19 striker pistol with black controls.

Real gun: 203 mm long (6 units), 4.7 in barrel, 140 mm tall, 17 rounds, manual thumb safety,
slide cut for a reflex sight (issued with a cover plate that carries the rear sight). The whole
shape is the shared `striker_pistols.SigP320` builder; this file only holds the M17's numbers.
"""
from striker_pistols import M17_PALETTE, Handling, PistolSpec, SigP320

SPEC = PistolSpec(
    id="m17",
    palette=M17_PALETTE,
    slide_front_z=-3.5,        # slide z -3.5 .. 2.5 = 6 units (203 mm)
    grip_bottom_y=2.5,         # base plate to y 2.25 -> 4.5 units tall with the bevelled top and sights
    grip_rake=15.0,            # the P320 grip is more upright than a Glock's
    handling=Handling(reload=1.8, reload_empty=2.3, fire=0.1, recoil=0.5, flip=5.0),
    attachments=("red_dot", "holographic", "suppressor_9mm", "laser"),
    previews={"loadout": ("red_dot", "suppressor_9mm", "laser"), "holo": ("holographic",)},
)
PISTOL = SigP320(SPEC)
SIGHT_Y = PISTOL.sight_y           # 6.75: front post tip = top of the rear notch
SIGHT_LINE = PISTOL.sight_line     # (0, 6.75, 2.25)


def model():
    return PISTOL.build()
