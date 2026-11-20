"""Glock 17 Gen5 - full-size, all-black 9x19 striker pistol.

Real gun: 202 mm long (6 units), 114 mm barrel, 139 mm tall, 17 rounds. The whole shape is the
shared `striker_pistols.Glock` builder; this file only holds the Glock 17's numbers. A change
to every Glock goes in `Glock`, to every pistol of the family in `StrikerPistol`.
"""
from striker_pistols import GLOCK_PALETTE, Glock, Handling, PistolSpec

SPEC = PistolSpec(
    id="glock17",
    palette=GLOCK_PALETTE,
    slide_front_z=-3.5,        # slide z -3.5 .. 2.5 = 6 units (202 mm)
    grip_bottom_y=2.5,         # full-length grip; base plate to y 2.25 -> 4.25 units tall with sights
    grip_rake=20.0,            # the steep Glock grip angle
    handling=Handling(reload=1.7, reload_empty=2.2, fire=0.1, recoil=0.5, flip=5.0),
    attachments=("suppressor_9mm", "laser"),
    previews={"loadout": ("suppressor_9mm", "laser")},
)
PISTOL = Glock(SPEC)
SIGHT_Y = PISTOL.sight_y           # 6.5: front post tip = top of the rear notch
SIGHT_LINE = PISTOL.sight_line     # (0, 6.5, 2.25)


def model():
    return PISTOL.build()
