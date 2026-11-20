"""M2A1 - .50 BMG heavy machine gun on a low M3 tripod (spade grips, butterfly trigger).

Same scale as the M4 (see the IN-GAME TUNING NOTES in m4.py): 1 unit ~ 3.35 cm ->
1654 mm = ~49.5 units (spade grips at z +3, flash hider at z -46.5).
Muzzle toward -Z, right side is +X, bore axis at y=5.5. The butterfly trigger sits on the back
plate at z ~ 1; both hands hold the spade grips (arm style 'spade' from heavy_weapons).
The tripod is its own bone (`tripod`) so the game can hide it.

The build pipeline and animations come from heavy_weapons.BeltFedGun (which goes through
templates.py); this file only holds what is specific to the M2A1.
"""
import heavy_weapons as hw
from armorygen import Model

BORE_Y = 5.5
SIGHT_Y = 8.5            # front post tip and centre of the raised rear leaf aperture
REAR_SIGHT_Z = -5.0
TOP_Y = 7.0              # top plate of the receiver
COVER_HINGE = (0, TOP_Y, -12.5)   # FRONT edge of the feed cover
CAN_TOP = (-3.75, 6, -10)         # `magazine` pivot: top centre of the ammo can
GRIP_X, GRIP_Y, GRIP_Z = 1.75, 5.25, 2.5   # centre of the right spade grip (left = mirrored)


class M2A1(hw.BeltFedGun):
    ID = "m2a1"
    SIGHT_Y = SIGHT_Y
    ARM_STYLE = hw.SPADE
    R_HAND = (GRIP_X, GRIP_Y, GRIP_Z)
    L_HAND = (-GRIP_X, GRIP_Y, GRIP_Z)
    MOUNTS = {}                          # no rails on the M2A1
    OPTIC_ON_COVER = False
    MUZZLE_FLASH = (0, BORE_Y, -46.75)
    SHELL_EJECT = (0.5, 3.4, -9)         # the M2 ejects straight down
    SIGHT_LINE = (0, SIGHT_Y, REAR_SIGHT_Z)
    RPM, RELOAD_S, EMPTY_RELOAD_S = 550, 8.0, 10.0
    RECOIL, RISE, BOLT_TRAVEL = 0.6, 0.8, 2.5
    CHARGE_KNOB, CHARGE_PULL = (2.5, 4.5, -9.5), 4.0

    def materials(self, m: Model) -> None:
        m.material("parkerized", (58, 60, 64), 6, 0.70)      # dark grey receiver
        m.material("barrel_steel", (48, 49, 54), 6, 0.72)
        m.material("phosphate", (34, 34, 38), 4, 0.78)       # sights, sleeve, flash hider
        m.material("can", (82, 90, 58), 6, 0.80)             # OD ammo can
        m.material("tripod_od", (70, 76, 54), 6, 0.78)

    def parts(self, m: Model) -> None:
        # ---- receiver --------------------------------------------------------------
        rc = m.bone("receiver", pivot=(0, BORE_Y, 0))
        rc.box((-1.5, 3.5, -15), (3, 3.5, 15), "parkerized")              # long riveted box
        rc.box((-1.5, 3.5, -15), (3, 3.5, 2.5), "barrel_steel", pattern="knurl", inflate=0.1)  # trunnion block
        rc.box((-1.5, 3.5, 0), (3, 3.5, 0.5), "barrel_steel")             # back plate
        rc.box((-0.5, 5, 0.5), (1, 1, 0.5), "barrel_steel", inflate=-0.1)  # buffer tube stub
        rc.box((1.5, 4.25, -10), (0.1, 0.5, 6), "phosphate")              # retracting-slide slot
        rc.box((-1.5, 4, -6.5), (3, 2, 5), "barrel_steel", pattern="knurl", inflate=0.05)  # riveted side plates

        fc = m.bone("feed_cover", pivot=COVER_HINGE)
        fc.box((-1.5, TOP_Y, -12.5), (3, 0.75, 5.5), "parkerized")        # cover
        fc.box((-0.5, TOP_Y + 0.75, -8), (1, 0.25, 1), "barrel_steel")    # latch

        bo = m.bone("bolt", pivot=(1.5, 4.5, -7))
        bo.box((1.5, 4.25, -7.5), (0.25, 0.5, 1), "bright_steel")         # bolt stud in the slot

        ch = m.bone("charging_handle", pivot=(1.5, 4.5, -9.5))
        ch.box((1.5, 4.25, -10), (0.5, 0.5, 2.5), "barrel_steel")         # retracting slide
        ch.box((2, 4, -10), (1, 1, 1), "barrel_steel", pattern="knurl")   # handle

        # ---- spade grips + butterfly trigger ------------------------------------------
        gr = m.bone("grip", pivot=(0, BORE_Y, 0.5))
        for x in (-GRIP_X - 0.5, GRIP_X - 0.5):
            gr.box((x, 6.75, 0.5), (1, 0.5, 2.5), "barrel_steel")         # upper arm
            gr.box((x, 3.5, 0.5), (1, 0.5, 2.5), "barrel_steel")          # lower arm
            gr.box((x, 4, 2), (1, 2.75, 1), "wood_dark", pattern="lines")  # grip
        tr = m.bone("trigger", pivot=(0, BORE_Y, 1))
        tr.box((-0.75, 5, 1), (1.5, 1, 0.5), "bright_steel")              # butterfly paddles

        # ---- barrel ----------------------------------------------------------------
        ba = m.bone("barrel", pivot=(0, BORE_Y, -15))
        ba.box((-1, 4.5, -19.5), (2, 2, 4.5), "phosphate", pattern="slots")   # perforated support sleeve
        ba.box((-0.5, 5, -44.5), (1, 1, 25), "barrel_steel", inflate=0.25)    # heavy barrel
        ba.box((-0.5, 5, -46.5), (1, 1, 2), "phosphate", pattern="lines", inflate=0.45)  # slotted flash hider
        ba.box((-0.5, 5, -23.5), (1, 1, 1), "phosphate", inflate=0.45)        # carry-handle collar
        ba.box((0.9, 5.25, -23.25), (1, 0.5, 0.5), "phosphate")               # handle arm (right)
        ba.box((1.75, 5, -24.5), (0.75, 1, 3), "wood_dark", pattern="lines")  # carry handle

        hw.front_post(m.bone("front_sight", pivot=(0, TOP_Y, -14)), -14, TOP_Y, SIGHT_Y, "phosphate")
        hw.rear_aperture(m.bone("rear_sight", pivot=(0, TOP_Y, REAR_SIGHT_Z)), REAR_SIGHT_Z, TOP_Y,
                         SIGHT_Y, "phosphate")

        # ---- ammo can on its tray + belt (bone `magazine`) -----------------------------
        mg = m.bone("magazine", pivot=CAN_TOP)
        mg.box((-5.5, 1.5, -13), (3.5, 4.5, 6), "can")                        # 100-round M2A1 can
        mg.box((-5.5, 4.75, -13), (3.5, 0.5, 6), "can", pattern="lines", inflate=0.1)  # rim under the lid
        mg.box((-5.6, 2.75, -11.5), (0.1, 1.5, 3), "orange")                  # stencil
        belt = m.bone("belt", parent="magazine", pivot=(-1.5, 6.5, -10))
        hw.linked_belt(belt, (-4.75, 6, -12), (1.25, 0.75, 4), tip=1.25)      # out of the can
        hw.linked_belt(belt, (-3.5, 6.25, -12), (2, 0.5, 4), tip=1.25)        # across into the feedway

        # ---- M3 tripod (own bone so the game can hide it) -------------------------------
        tp = m.bone("tripod", pivot=(0, 3.5, -9))
        tp.box((-0.5, 1.5, -9.5), (1, 2, 1), "barrel_steel")                  # pintle
        tp.box((-1, 0.5, -10.5), (2, 1, 3), "tripod_od")                      # tripod head
        tp.box((-5.75, 1, -13.5), (4, 0.5, 7), "tripod_od")                   # ammo-can tray
        tp.box((-2, 1, -10.25), (1, 0.5, 1.5), "tripod_od")                   # tray bracket
        tp.wedge((0, 0.75, -10.5), (0, -3, -17.5), 1, 1, "tripod_od")         # front leg
        for sx in (-1, 1):                                                    # splayed rear legs
            tp.box((sx * 0.75 - 0.5, 0.25, -8), (1, 1, 12), "tripod_od", rot=(17, sx * 28, 0),
                   pivot=(sx * 0.75, 0.75, -8))
        tp.box((-3.5, -1.25, -3.25), (7, 0.5, 0.5), "barrel_steel")           # traversing bar
        tp.box((-0.25, -0.75, -3.25), (0.5, 4.25, 0.5), "barrel_steel")       # T&E screw
        tp.box((-0.75, 1.25, -3.5), (1.5, 0.5, 1), "bright_steel", pattern="knurl")  # elevating handwheel


def model() -> Model:
    return M2A1().build()
