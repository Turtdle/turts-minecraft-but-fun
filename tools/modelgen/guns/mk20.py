"""FN Mk 20 Mod 0 SSR (SCAR-H TPR) - 7.62 sniper support rifle: longer upper, heavy 20 in
barrel, fixed precision stock, black polymer lower. Issued with a variable sniper scope.

1080 mm / 33.5 = 32.2 units: flash hider at z -21, recoil pad at z 11.25. The geometry is the
SCAR-H platform in platforms_762.py; this file only declares what makes it a Mk 20.
"""
import platforms_762 as P

SPEC = P.RifleSpec(
    id="mk20",
    front_z=-16.0,                 # extended upper
    barrel_end_z=-19.5,            # 20 in heavy barrel
    barrel_inflate=0.2,
    stock="precision",
    base_pad=0.75,                 # extended magazine base pad
    palette=P.Palette(lower=P.BLACK_POLYMER, grip=P.BLACK_POLYMER, stock=P.FDE, mag=(124, 114, 98)),
    feel=P.Feel(reload=2.5, reload_empty=3.3, fire_length=0.1, recoil=1.2, rise=3.0),
    left_hand_z=-9.5,
    optic_z=-3.5,                  # optic mount 1.5 ahead of the platform default (-2): at the hip the
                                   # scope eyepiece sat off the bottom-right corner of the screen
    underbarrel_z=-13.5,
    side_rail=(-15.0, 5.0),
    side_z=-13.0,
    attachments=("red_dot", "holographic", "acog", "sniper_scope",
                 "suppressor_762", "suppressor_762_short", "compensator_762",
                 "vertical_grip", "angled_grip", "bipod", "laser"),
    default_optic="sniper_scope",
    loadouts={"loadout_issued": ("sniper_scope", "bipod"),
              "loadout_suppressed": ("sniper_scope", "suppressor_762", "bipod", "laser")},
)
BUILDER = P.ScarBuilder
SIGHT_Y = 8.5                      # back-up irons; the sniper scope's axis is at the same height
assert SIGHT_Y == BUILDER(SPEC).sight_y


def model():
    return BUILDER(SPEC).build()
