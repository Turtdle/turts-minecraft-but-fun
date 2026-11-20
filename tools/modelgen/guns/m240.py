"""M240B - belt-fed 7.62x51 medium machine gun (railed feed cover and handguard, fixed stock).

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1245 mm = ~37 units (butt at z +10.5, muzzle at z -26.5).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

The build pipeline, arm hold and animations come from heavy_weapons.BeltFedGun (which goes
through templates.py); this file only holds what is specific to the M240.
"""
import heavy_weapons as hw
import templates as T
from armorygen import Model

BORE_Y = 5.5
SIGHT_Y = 8.0            # front blade tip and centre of the rear leaf aperture
REAR_SIGHT_Z = 0.5
RAIL_Y = 7.0             # top of the feed-cover rail
MUZZLE_Z = -25.0         # bare muzzle (the conical flash suppressor sits in front of it)
COVER_HINGE = (0, 6, -9)        # FRONT edge of the feed cover
POUCH_TOP = (-2.1, 4.5, -6.5)   # `magazine` pivot: top centre of the belt pouch


class M240(hw.BeltFedGun):
    ID = "m240"
    SIGHT_Y = SIGHT_Y
    R_HAND = (0, 2, 2)                   # pistol grip
    L_HAND = (0, 3, -13.5)               # under the handguard
    MOUNTS = dict(optic=(0, RAIL_Y, -5.5), muzzle=(0, BORE_Y, MUZZLE_Z),
                  underbarrel=(0, 2.5, -15.5), side=(1.25, 4, -15))
    ATTACHMENTS = ("red_dot", "holographic", "acog", "suppressor_762", "compensator_762",
                   "vertical_grip", "angled_grip", "laser")
    DEFAULT_ATTACHMENTS = {"OPTIC": "acog"}          # stands in for the issued M145 MGO (3.4x)
    PREVIEWS = {"loadout_issue": ("acog",),
                "loadout_quiet": ("holographic", "suppressor_762", "angled_grip", "laser")}
    MUZZLE_FLASH = (0, BORE_Y, -26.75)
    SHELL_EJECT = (1.1, 4.4, -4.75)
    SIGHT_LINE = (0, SIGHT_Y, REAR_SIGHT_Z)
    RPM, RELOAD_S, EMPTY_RELOAD_S = 700, 9.0, 7.0
    RECOIL, RISE, BOLT_TRAVEL = 0.7, 1.5, 2.5
    CHARGE_KNOB, CHARGE_PULL = (1.4, 5.25, -3), 3.0

    def materials(self, m: Model) -> None:
        m.material("receiver_steel", (44, 45, 50), 5, 0.70)  # riveted steel receiver
        m.material("furniture", (36, 36, 39), 4, 0.80)       # polymer handguard / stock / grip
        m.material("barrel_steel", (66, 68, 74), 6, 0.72)
        m.material("phosphate", (30, 30, 34), 4, 0.78)       # sights, flash suppressor, bipod
        m.material("canvas", (140, 122, 88), 8, 0.84)        # tan 100-round belt pouch

    def parts(self, m: Model) -> None:
        # ---- receiver --------------------------------------------------------------
        rc = m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 4, -11), (2, 2, 14), "receiver_steel")                # long rectangular receiver
        rc.box((-1, 3, -11), (2, 1, 9), "receiver_steel")                 # belly: op-rod channel
        rc.box((-1, 3, -11), (2, 3, 2), "barrel_steel", inflate=0.15)     # front trunnion block
        rc.box((-1, 6, -2), (2, 0.5, 5), "receiver_steel")                # top behind the feed cover
        rc.box((-1, 4.25, -1.5), (2, 1.5, 4), "barrel_steel", pattern="knurl", inflate=0.1)  # riveted rear plates
        rc.box((-0.5, 2, -2), (1, 1, 3), "receiver_steel")                # trigger guard
        rc.box((1, 5, -3.5), (0.1, 0.5, 4), "phosphate")                  # cocking-handle slot

        fc = m.bone("feed_cover", pivot=COVER_HINGE)
        fc.box((-1.25, 6, -9), (2.5, 0.5, 7), "receiver_steel")           # cover
        fc.box((-1, 6.5, -8.5), (2, 0.5, 6), "receiver_steel", pattern="rails")  # picatinny rail

        bo = m.bone("bolt", pivot=(0.5, 4.5, -5))
        bo.box((0.1, 4, -6.5), (1, 0.75, 2.5), "bright_steel")            # seen in the ejection port

        ch = m.bone("charging_handle", pivot=(1, 5.25, -3))
        ch.box((1, 5, -3.5), (0.75, 0.5, 1), "barrel_steel", pattern="knurl")

        # ---- barrel ----------------------------------------------------------------
        ba = m.bone("barrel", pivot=(0, BORE_Y, -11))
        ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 14), "barrel_steel", inflate=0.15)
        ba.box((-0.5, 3.75, -19.5), (1, 1, 2.5), "barrel_steel")              # gas cylinder
        ba.box((-0.5, 3.5, -20.5), (1, 1.75, 1), "phosphate", inflate=0.2)    # gas block / regulator
        ba.box((-1, 5.75, -17.5), (2, 0.75, 6.5), "phosphate", pattern="slots")  # ventilated heat shield
        ba.box((0.25, 5.25, -12.75), (0.75, 0.5, 0.5), "phosphate")           # carry-handle arm (right)
        ba.box((1, 5.25, -14.5), (0.75, 0.75, 3.5), "furniture", pattern="lines")  # carry handle
        md = T.default_part(m, "muzzle", self.MOUNTS["muzzle"])
        md.box((-0.5, 5, -25.5), (1, 1, 0.5), "phosphate", inflate=0.2)       # cone, narrow end
        md.box((-0.5, 5, -26.5), (1, 1, 1), "phosphate", pattern="lines", inflate=0.4)  # cone, mouth

        hw.front_post(m.bone("front_sight", pivot=(0, 6, -23.5)), -23.5, 6, SIGHT_Y, "phosphate")
        hw.rear_aperture(m.bone("rear_sight", pivot=(0, 6.5, REAR_SIGHT_Z)), REAR_SIGHT_Z, 6.5,
                         SIGHT_Y, "phosphate")

        # ---- handguard + folded bipod ----------------------------------------------
        hg = m.bone("handguard", pivot=(0, BORE_Y, -11))
        hg.box((-1, 3, -17), (2, 2, 6), "furniture", pattern="lines")
        hg.box((-1.25, 3.75, -16.5), (2.5, 0.5, 5), "receiver_steel", pattern="rails")  # side rails
        hg.box((-0.5, 2.5, -16.5), (1, 0.5, 5), "receiver_steel", pattern="rails")      # bottom rail
        hw.folded_legs(m.bone("bipod", pivot=(0, 3.25, -20)), (0, 3.25, -20), 8, 2.5, "phosphate")

        # ---- stock (fixed polymer buttstock) ------------------------------------------
        st = m.bone("stock", pivot=(0, 5, 3))
        st.box((-1, 3.5, 3), (2, 2.75, 0.75), "barrel_steel", inflate=0.1)    # buffer collar
        st.box((-1, 4, 3.75), (2, 2, 6.25), "furniture")                      # body
        st.box((-1, 3, 6.5), (2, 1, 3.5), "furniture")                        # belly
        st.box((-1, 2.5, 10), (2, 3.75, 0.5), "rubber")                       # butt pad

        hw.pistol_grip(m, "furniture")
        hw.blade_trigger(m, "barrel_steel")

        # ---- belt pouch on the left + belt (bone `magazine`) ---------------------------
        mg = m.bone("magazine", pivot=POUCH_TOP)
        mg.box((-3.25, 1.5, -8.5), (2.25, 3, 4.5), "canvas")                  # 100-round soft pouch
        mg.box((-3.25, 4, -8.5), (2.25, 0.5, 4.5), "canvas", pattern="lines", inflate=0.1)  # flap
        mg.box((-1.5, 3.25, -7), (0.5, 0.75, 1.5), "phosphate")               # hanger on the receiver
        belt = m.bone("belt", parent="magazine", pivot=(-1.25, 6, -5.5))
        hw.linked_belt(belt, (-1.75, 4.5, -6.75), (0.75, 1.5, 2.25), tip=0.75)  # up to the feed tray


def model() -> Model:
    return M240().build()
