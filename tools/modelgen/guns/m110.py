"""KAC M110 SASS (SR-25) - 7.62 semi-automatic sniper system: AR-10 receiver, 20 in heavy
barrel in a long URX quad rail, fixed stock with LOP knob. Issued with a Leupold scope and
the KAC QD suppressor.

1029 mm / 33.5 = 30.7 units: flash hider at z -20.75, butt plate at z 10. The geometry is the
AR-10 platform in platforms_762.py; this file only declares what makes it an M110.
"""
import platforms_762 as P

SPEC = P.RifleSpec(
    id="m110",
    front_z=-15.0,                 # front of the URX rail
    barrel_end_z=-19.5,            # 20 in barrel
    flash_hider=1.25,
    barrel_inflate=0.15,
    handguard="quad_rail",
    stock="fixed_lop",
    palette=P.Palette(lower=P.FDE, stock=P.FDE_POLYMER, grip=P.BLACK_POLYMER, mag=P.MAG_STEEL),
    feel=P.Feel(reload=2.5, reload_empty=3.3, fire_length=0.1, recoil=1.1, rise=2.8),
    left_hand_z=-9.5,
    optic_z=-3.5,                  # optic mount 1.5 ahead of the platform default (-2): at the hip the
                                   # scope eyepiece sat off the bottom-right corner of the screen
    underbarrel_z=-13.0,
    side_z=-13.0,
    attachments=("red_dot", "holographic", "acog", "sniper_scope",
                 "suppressor_762", "suppressor_762_short", "compensator_762",
                 "vertical_grip", "angled_grip", "bipod", "laser"),
    default_optic="sniper_scope",
    loadouts={"loadout_issued": ("sniper_scope", "suppressor_762", "bipod"),
              "loadout_short_can": ("sniper_scope", "suppressor_762_short")},
)
BUILDER = P.Ar10Builder
SIGHT_Y = 8.0                      # back-up irons; the sniper scope's axis is at the same height
assert SIGHT_Y == BUILDER(SPEC).sight_y


def model():
    return BUILDER(SPEC).build()
