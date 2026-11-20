"""FN SCAR-H Mk 17 Mod 0 (STD, 16 in) - 7.62x51 battle rifle, all flat dark earth.

965 mm / 33.5 = 28.8 units: flash hider at z -17.5, butt pad at z 11. The geometry is the
SCAR-H platform in platforms_762.py; this file only declares what makes it a Mk 17.
Conventions: see guns/m4.py (IN-GAME TUNING NOTES) and templates.py.
"""
import platforms_762 as P

SPEC = P.RifleSpec(
    id="mk17",
    front_z=-13.0,                 # front of the monolithic upper
    barrel_end_z=-16.0,            # 16 in barrel; FN flash hider in front of it
    stock="scar_folding",
    palette=P.Palette(mag=(92, 88, 80)),
    feel=P.Feel(reload=2.5, reload_empty=3.0, fire_length=0.07, recoil=1.0, rise=2.5),
    left_hand_z=-11.0,
    underbarrel_z=-7.5,
    side_rail=(-12.0, 5.0),
    side_z=-8.0,
    attachments=("red_dot", "holographic", "acog", "sniper_scope",
                 "suppressor_762", "suppressor_762_short", "compensator_762",
                 "vertical_grip", "angled_grip", "bipod", "laser"),
    loadouts={"loadout_sof": ("holographic", "suppressor_762", "vertical_grip", "laser"),
              "loadout_short_can": ("holographic", "suppressor_762_short")},
)
BUILDER = P.ScarBuilder
SIGHT_Y = 8.5                      # front post tip / rear aperture centre
assert SIGHT_Y == BUILDER(SPEC).sight_y


def model():
    return BUILDER(SPEC).build()
