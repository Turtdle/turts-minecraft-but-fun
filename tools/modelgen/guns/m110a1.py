"""HK M110A1 CSASS (G28 / HK417) - compact 7.62 DMR in RAL 8000: 16.3 in barrel, slim
Geissele M-LOK handguard, HK collapsible stock with cheek comb, curved polymer magazine.
No iron sights: issued with a scope (and usually a suppressor).

1009 mm / 33.5 = 30.1 units: flash hider at z -18.25, butt pad at z 11.5. The geometry is the
AR-10 platform in platforms_762.py; this file only declares what makes it an M110A1.
"""
import platforms_762 as P

SPEC = P.RifleSpec(
    id="m110a1",
    front_z=-15.0,                 # front of the handguard
    barrel_end_z=-17.0,            # 16.3 in barrel
    flash_hider=1.25,
    handguard="mlok",
    stock="hk_collapsible",
    magazine="curved",
    irons=False,
    forward_assist=False,
    palette=P.Palette(receiver=P.RAL8000, rail=P.RAL8000_DARK, lower=P.RAL8000, stock=P.RAL8000,
                      grip=P.RAL8000, barrel=(48, 46, 44), mag=P.MAG_TAN),
    feel=P.Feel(reload=2.4, reload_empty=3.2, fire_length=0.1, recoil=1.2, rise=3.0),
    left_hand_z=-8.0,
    optic_z=-3.5,                  # optic mount 1.5 ahead of the platform default (-2): at the hip the
                                   # scope eyepiece sat off the bottom-right corner of the screen
    left_hand_y=5.0,               # the slim tube's bottom face is at 4.5
    underbarrel_z=-11.5,
    side_z=-12.0,
    attachments=("red_dot", "holographic", "acog", "sniper_scope",
                 "suppressor_762", "suppressor_762_short", "compensator_762",
                 "vertical_grip", "angled_grip", "bipod", "laser", "stock_ctr"),
    default_optic="sniper_scope",
    loadouts={"loadout_issued": ("sniper_scope", "suppressor_762", "bipod"),
              "loadout_short_can": ("sniper_scope", "suppressor_762_short")},
)
BUILDER = P.Ar10Builder
SIGHT_Y = 8.0                      # no irons: optical axis of the issued scope (rail 7.0 + 1.0)
assert SIGHT_Y == BUILDER(SPEC).sight_line[1]


def model():
    return BUILDER(SPEC).build()
