"""M249 SAW - belt-fed 5.56 light machine gun (PIP stock, railed feed cover and handguard).

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1035 mm = ~31 units (butt at z +10, muzzle at z -21).
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

The build pipeline, arm hold and animations come from heavy_weapons.BeltFedGun (which goes
through templates.py); this file only holds what is specific to the M249.
"""
import heavy_weapons as hw
import templates as T
from armorygen import Model

BORE_Y = 5.5
SIGHT_Y = 8.0            # front post tip and centre of the rear aperture
REAR_SIGHT_Z = 1.5
RAIL_Y = 7.0             # top of the feed-cover rail
MUZZLE_Z = -19.5         # bare muzzle (the birdcage sits in front of it)
COVER_HINGE = (0, 6, -6)        # FRONT edge of the feed cover
BOX_TOP = (-0.75, 3, -5.25)     # `magazine` pivot: top centre of the 200-round box


class M249(hw.BeltFedGun):
    ID = "m249"
    SIGHT_Y = SIGHT_Y
    R_HAND = (0, 2, 2)                   # pistol grip
    L_HAND = (0, 3, -10.5)               # under the handguard
    MOUNTS = dict(optic=(0, RAIL_Y, -3), muzzle=(0, BORE_Y, MUZZLE_Z),
                  underbarrel=(0, 2.5, -12), side=(1.25, 4, -11.5))
    ATTACHMENTS = ("red_dot", "holographic", "acog", "suppressor_556", "compensator_556",
                   "vertical_grip", "angled_grip", "laser")
    DEFAULT_ATTACHMENTS = {"OPTIC": "acog"}          # stands in for the issued M145 MGO (3.4x)
    PREVIEWS = {"loadout_issue": ("acog",),
                "loadout_cqb": ("red_dot", "suppressor_556", "vertical_grip", "laser")}
    MUZZLE_FLASH = (0, BORE_Y, -21.25)
    SHELL_EJECT = (1.1, 4.4, -4)
    SIGHT_LINE = (0, SIGHT_Y, REAR_SIGHT_Z)
    RPM, RELOAD_S, EMPTY_RELOAD_S = 850, 9.0, 7.0
    RECOIL, RISE, BOLT_TRAVEL = 0.5, 1.2, 2.0
    CHARGE_KNOB, CHARGE_PULL = (1.4, 5.25, -7), 3.0

    def materials(self, m: Model) -> None:
        m.material("parkerized", (50, 52, 56), 5, 0.72)      # stamped-steel receiver
        m.material("furniture", (40, 40, 43), 4, 0.80)       # polymer handguard / stock / grip
        m.material("barrel_steel", (72, 74, 80), 6, 0.72)
        m.material("phosphate", (34, 34, 38), 4, 0.78)       # sights, flash hider, bipod
        m.material("pouch", (92, 98, 66), 7, 0.82)           # OD 200-round box

    def parts(self, m: Model) -> None:
        # ---- receiver --------------------------------------------------------------
        rc = m.bone("receiver", pivot=(0, 5, 0))
        rc.box((-1, 4, -8), (2, 2, 11), "parkerized")                     # stamped receiver
        rc.box((-1, 3, -8), (2, 1, 6), "parkerized")                      # belly: piston / box hanger
        rc.box((-1, 6, 0), (2, 0.5, 3), "parkerized")                     # top behind the feed cover
        rc.box((-0.5, 2, -2), (1, 1, 3), "parkerized")                    # trigger guard
        rc.box((-1.75, 3, -6.5), (0.75, 1.25, 2), "parkerized",           # angled STANAG well (left)
               rot=(0, 0, 25), pivot=(-1, 4.25, -5.5))
        rc.box((1, 5, -7.5), (0.1, 0.5, 4.5), "phosphate")                # cocking-handle slot

        fc = m.bone("feed_cover", pivot=COVER_HINGE)
        fc.box((-1.25, 6, -6), (2.5, 0.5, 6), "parkerized")               # cover
        fc.box((-1, 6.5, -5.5), (2, 0.5, 5), "parkerized", pattern="rails")  # picatinny rail

        bo = m.bone("bolt", pivot=(0.5, 4.5, -4))
        bo.box((0.1, 4, -5), (1, 0.75, 2), "bright_steel")                # seen in the ejection port

        ch = m.bone("charging_handle", pivot=(1, 5.25, -7))
        ch.box((1, 5, -7.5), (0.75, 0.5, 1), "barrel_steel", pattern="knurl")

        # ---- barrel ----------------------------------------------------------------
        ba = m.bone("barrel", pivot=(0, BORE_Y, -8))
        ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 11.5), "barrel_steel", inflate=0.1)
        ba.box((-0.5, 3.75, -16.5), (1, 1.25, 1), "phosphate", inflate=0.2)   # gas block / regulator
        ba.box((-0.25, 4, -15.5), (0.5, 0.75, 1.5), "barrel_steel")           # gas tube
        ba.box((-0.75, 6, -15), (1.5, 0.5, 3.5), "phosphate", pattern="slots")  # heat shield
        ba.box((-0.25, 6, -9.75), (0.5, 0.75, 0.5), "phosphate")              # carry-handle stem
        ba.box((-0.25, 6.75, -11), (0.5, 0.5, 3), "furniture")                # carry handle
        md = T.default_part(m, "muzzle", self.MOUNTS["muzzle"])
        md.box((-0.5, 5, -21), (1, 1, 1.5), "phosphate", pattern="lines", inflate=0.25)  # birdcage

        hw.front_post(m.bone("front_sight", pivot=(0, 6, -16)), -16, 6, SIGHT_Y, "phosphate")
        hw.rear_aperture(m.bone("rear_sight", pivot=(0, 6.5, REAR_SIGHT_Z)), REAR_SIGHT_Z, 6.5,
                         SIGHT_Y, "phosphate")

        # ---- handguard + folded bipod ----------------------------------------------
        hg = m.bone("handguard", pivot=(0, BORE_Y, -8))
        hg.box((-1, 3, -14), (2, 2, 6), "furniture", pattern="lines")         # ribbed polymer
        hg.box((-1.25, 3.75, -13.5), (2.5, 0.5, 5), "parkerized", pattern="rails")  # side rails
        hg.box((-0.5, 2.5, -13.5), (1, 0.5, 5), "parkerized", pattern="rails")      # bottom rail
        hw.folded_legs(m.bone("bipod", pivot=(0, 3.25, -15)), (0, 3.25, -15), 6.5, 2.5, "phosphate")

        # ---- stock (fixed PIP buttstock with a hollow outline) ------------------------
        st = m.bone("stock", pivot=(0, 5, 3))
        st.box((-1, 3.5, 3), (2, 3, 1), "parkerized")                         # buffer housing
        st.box((-1, 5, 4), (2, 1, 5.25), "furniture")                         # upper beam
        st.wedge((0, 3.75, 4), (0, 2.5, 9.25), 1.5, 0.75, "furniture")        # lower strut
        st.box((-1, 1.75, 9.25), (2, 4.25, 0.75), "furniture", pattern="lines")  # butt

        hw.pistol_grip(m, "furniture")
        hw.blade_trigger(m, "barrel_steel")

        # ---- 200-round box + belt (bone `magazine`; the belt travels with the box) ----
        mg = m.bone("magazine", pivot=BOX_TOP)
        mg.box((-2.5, 0, -7), (3.5, 3, 3.5), "pouch")                         # box under the receiver
        mg.box((-2.5, 2.5, -7), (3.5, 0.5, 3.5), "pouch", pattern="lines", inflate=0.1)  # lid
        belt = m.bone("belt", parent="magazine", pivot=(-1.25, 6, -4.75))
        hw.linked_belt(belt, (-1.5, 3, -5.5), (0.5, 3, 1.5))                  # up the left side to the tray


def model() -> Model:
    return M249().build()
