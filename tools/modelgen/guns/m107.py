"""Barrett M107 (M82A1) - semi-automatic .50 BMG anti-materiel rifle, 10-round box magazine.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1448 mm = ~43 units (recoil pad at z +10, brake front at z -33).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

The arrowhead muzzle brake is the shared attachment `muzzle_brake_50` and the Leupold is
`sniper_scope`: the issued rifle ships with both (DEFAULT_ATTACHMENTS), the bare model has a
thread protector and the folding iron sights.

The build pipeline and animations come from heavy_weapons.MagFedGun (which goes through
templates.py); this file only holds what is specific to the M107.
"""
import attachments as att
import heavy_weapons as hw
import templates as T
from armorygen import Model

BORE_Y = 5.5
SIGHT_Y = 8.0            # folding irons: front post tip / rear aperture centre
REAR_SIGHT_Z = 4.5
RAIL_Y = 7.0             # top of the long receiver rail
MUZZLE_Z = -29.0         # bare muzzle (the brake adds 4)
OPTIC_MOUNT = (0, RAIL_Y, -2)
SCOPE_Y = RAIL_Y + att.OPTIC_AXIS_Y["sniper_scope"]      # 8.0: optical axis of the issued scope
SCOPE_EYEPIECE_Z = OPTIC_MOUNT[2] + 6                    # rear end of its eyepiece
MAG_PIVOT = (0, 4, -3.75)


class M107(hw.MagFedGun):
    ID = "m107"
    SIGHT_Y = SIGHT_Y
    ARM_STYLE = "rifle"
    R_HAND = (0, 2, 2)                   # pistol grip
    L_HAND = (0, 4.5, -11)               # under the barrel shroud, between the bipod legs
    MOUNTS = dict(optic=OPTIC_MOUNT, muzzle=(0, BORE_Y, MUZZLE_Z))    # top rail only
    ATTACHMENTS = ("red_dot", "holographic", "acog", "sniper_scope", "muzzle_brake_50")
    DEFAULT_ATTACHMENTS = {"OPTIC": "sniper_scope", "MUZZLE": "muzzle_brake_50"}
    PREVIEWS = {"loadout_issue": ("sniper_scope", "muzzle_brake_50")}
    MUZZLE_FLASH = (0, BORE_Y, MUZZLE_Z - 0.75)
    SHELL_EJECT = (1.1, BORE_Y, -3.5)
    SIGHT_LINE = (0, SIGHT_Y, REAR_SIGHT_Z)
    RPM, RELOAD_S, EMPTY_RELOAD_S = 60, 4.0, 5.5
    RECOIL, RISE, BOLT_TRAVEL = 1.8, 5.0, 3.0
    FIRE_LENGTH, BARREL_RECOIL = 0.3, 1.0
    CHARGE_KNOB, CHARGE_PULL = (1.6, BORE_Y, -2.5), 3.0

    def materials(self, m: Model) -> None:
        m.material("sheet_steel", (42, 43, 47), 5, 0.72)     # stamped receiver halves
        m.material("shroud", (50, 51, 56), 5, 0.72)          # perforated barrel shroud
        m.material("barrel_steel", (70, 72, 78), 6, 0.72)
        m.material("phosphate", (32, 32, 36), 4, 0.78)       # sights, bipod, pins
        m.material("mag_steel", (58, 60, 66), 6, 0.72)

    def parts(self, m: Model) -> None:
        # ---- receiver (upper + lower held by cross-pins) -------------------------------
        rc = m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 4.5, -5), (2, 2, 14), "sheet_steel")                  # upper receiver
        rc.box((-0.75, 6.5, -13), (1.5, 0.5, 19), "sheet_steel", pattern="rails")  # long top rail
        rc.box((-1, 4, -6.5), (2, 0.5, 15.5), "sheet_steel")              # lower receiver
        rc.box((-1, 2.5, -6), (2, 1.5, 4.5), "sheet_steel")               # magazine well
        rc.box((-0.5, 2, -1.5), (1, 1, 2.5), "sheet_steel")               # trigger guard
        for z in (-6, 7.5):
            rc.box((-1.1, 4.25, z), (2.2, 0.5, 0.5), "bright_steel")      # cross-pins
        rc.box((-1.75, 5.75, -9.5), (0.75, 0.75, 4.5), "polymer", pattern="lines")  # folded carry handle

        bo = m.bone("bolt", pivot=(0, BORE_Y, -2.5))
        bo.box((0.1, 5, -5), (1, 1, 3), "bright_steel")                   # carrier in the ejection port
        bo.box((1.1, 5.25, -2.75), (0.9, 0.5, 0.5), "phosphate", pattern="knurl")  # charging handle

        # ---- barrel (recoils inside the shroud) -----------------------------------------
        ba = m.bone("barrel", pivot=(0, BORE_Y, -17))
        ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 12.5), "barrel_steel", pattern="lines", inflate=0.2)  # fluted
        md = T.default_part(m, "muzzle", self.MOUNTS["muzzle"])
        md.box((-0.5, 5, MUZZLE_Z - 0.5), (1, 1, 0.5), "phosphate", pattern="knurl", inflate=0.3)  # thread protector

        hg = m.bone("handguard", pivot=(0, BORE_Y, -5))
        hg.box((-1, 4.5, -17), (2, 2, 12), "shroud", pattern="slots")     # perforated barrel shroud
        hg.box((-1, 4.5, -17.25), (2, 2, 0.25), "phosphate", inflate=0.1)  # shroud front collar

        hw.front_post(m.bone("front_sight", pivot=(0, RAIL_Y, -12.25)), -12.25, RAIL_Y, SIGHT_Y, "phosphate")
        hw.rear_aperture(m.bone("rear_sight", pivot=(0, RAIL_Y, REAR_SIGHT_Z)), REAR_SIGHT_Z, RAIL_Y,
                         SIGHT_Y, "phosphate")

        # bipod on the front of the lower receiver, legs folded forward along the shroud
        hw.folded_legs(m.bone("bipod", pivot=(0, 4, -7.5)), (0, 4.25, -7.5), 8.5, 2.5, "phosphate",
                       toward_muzzle=True, foot="bright_steel")

        # ---- rear: fixed stock with recoil pad, rear grip and monopod socket ---------------
        st = m.bone("stock", pivot=(0, 5, 3))
        st.box((-1, 3, 3), (2, 1, 6), "sheet_steel")                      # lower rear housing
        st.box((-1, 2, 7), (2, 1, 2), "sheet_steel", pattern="lines")     # rear hand grip
        st.box((-0.5, 1.5, 7.5), (1, 0.5, 1), "phosphate")                # monopod socket
        st.box((-1, 2, 9), (2, 4.5, 1), "rubber", pattern="lines")        # thick recoil pad

        hw.pistol_grip(m, "polymer")
        hw.blade_trigger(m, "barrel_steel")

        # ---- 10-round box magazine ----------------------------------------------------
        mg = m.bone("magazine", pivot=MAG_PIVOT)
        mg.box((-0.75, 0, -5.75), (1.5, 3.5, 4), "mag_steel", pattern="lines")
        mg.box((-1, -0.25, -6), (2, 0.25, 4.5), "phosphate")              # floor plate


def model() -> Model:
    return M107().build()
