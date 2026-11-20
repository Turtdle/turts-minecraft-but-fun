"""Glock 19 Gen5 - compact, all-black 9x19 striker pistol.

Real gun: 185 mm long (5.5 units), 102 mm barrel, 128 mm tall, 15 rounds: a Glock 17 with half a
unit less slide and half a unit less grip. The whole shape is the shared `striker_pistols.Glock`
builder; this file only holds the Glock 19's numbers.
"""
from striker_pistols import GLOCK_PALETTE, Glock, Handling, PistolSpec

SPEC = PistolSpec(
    id="glock19",
    palette=GLOCK_PALETTE,
    slide_front_z=-3.0,        # slide z -3.0 .. 2.5 = 5.5 units (185 mm)
    grip_bottom_y=3.0,         # short grip; base plate to y 2.75 -> 3.75 units tall with sights
    grip_rake=20.0,
    handling=Handling(reload=1.7, reload_empty=2.2, fire=0.1, recoil=0.55, flip=5.5),
    attachments=("suppressor_9mm", "laser"),
    previews={"loadout": ("suppressor_9mm", "laser")},
)
PISTOL = Glock(SPEC)
SIGHT_Y = PISTOL.sight_y           # 6.5: front post tip = top of the rear notch
SIGHT_LINE = PISTOL.sight_line     # (0, 6.5, 2.25)


def model():
    return PISTOL.build()
