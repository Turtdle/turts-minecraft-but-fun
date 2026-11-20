"""SIG MCX Virtus 5.56, 11.5 in SBR - short-stroke piston carbine: slab-sided upper with a
continuous top rail, slim M-LOK handguard, NO buffer tube (flat receiver rear with a side-folding
skeletal stock), 3-prong flash hider. No iron sights: it ships with a red dot.

Built on the shared AR-15 platform (platform_ar15.py); only what differs from it is here.
Scale as the M4: 1 unit ~ 3.35 cm -> 787 mm = 23.5 units (z -14 .. 9.5).
"""
import attachments as att
from armorygen import Model
from platform_ar15 import Ar15Platform

DEFAULT_OPTIC = "red_dot"
OPTIC_MOUNT = (0, 7, -1)
SIGHT_Y = OPTIC_MOUNT[1] + att.OPTIC_AXIS_Y[DEFAULT_OPTIC]   # 8.25: axis of the issued red dot
SIGHT_Z = OPTIC_MOUNT[2] + 1.5                               # rear face of the red dot's tube


class Mcx(Ar15Platform):
    ID = "mcx"
    RPM = 850
    RELOAD_S, RELOAD_EMPTY_S = 2.3, 2.8
    RECOIL, RISE = 0.85, 2.3             # short and light

    SIGHT_Y, REAR_SIGHT_Z = SIGHT_Y, SIGHT_Z
    OPTIC_MOUNT = OPTIC_MOUNT
    HANDGUARD_Z = (-11.0, -4.0)
    BARREL_END_Z = -12.0
    STOCK_PIVOT = (-1, 5.5, 2.5)         # folding hinge, left side
    L_HAND = (0, 4.75, -8.5)
    UNDERBARREL_MOUNT = (0, 4.5, -7)     # M-LOK, 6 o'clock
    SIDE_MOUNT = (1, 5.5, -6)            # M-LOK, 3 o'clock
    STOCK_SLOT = False                   # no buffer tube: AR stocks do not fit
    DEFAULT_ATTACHMENTS = {"OPTIC": DEFAULT_OPTIC}
    PALETTE = {**Ar15Platform.PALETTE,
               "anodised": ((142, 121, 90), 6, 0.78),    # SIG coyote / FDE anodising
               "furniture": ((132, 112, 84), 6, 0.80),   # stock in matching FDE
               "grip_black": ((40, 40, 43), 4, 0.80),
               "barrel_steel": ((48, 49, 54), 5, 0.74),
               "mag": ((44, 44, 48), 5, 0.76)}           # black magazine
    LOADOUTS = {
        "loadout_sof": ("red_dot", "suppressor_556", "angled_grip", "laser"),
        "loadout_b": ("holographic", "compensator_556", "vertical_grip", "pmag_40"),
        "loadout_micro": ("micro_red_dot", "suppressor_556"),
    }

    def receiver_details(self, rc):
        rc.box((0.5, 5, 0), (1, 1, 1), "anodised")                                   # brass deflector (no forward assist)
        rc.box((-1, 4, 2), (2, 3, 0.5), "phosphate")                                 # flat rear plate, no buffer tube

    def grip(self):
        gr = self.m.bone("grip", pivot=(0, 4, 1))
        gr.box((-1, 0, 0), (2, 4, 2), "grip_black", pattern="stipple",
               rot=(-22.5, 0, 0), pivot=(0, 4, 1), inflate=-0.05)

    def barrel(self):
        ba = self.m.bone("barrel", pivot=(0, self.BORE_Y, -4))
        ba.box((-0.5, 5, self.BARREL_END_Z), (1, 1, 1.5), "barrel_steel", inflate=0.1)  # 11.5 in barrel, barely out
        ba.box((-0.25, 6, -11.5), (0.5, 0.5, 0.75), "phosphate")                     # gas valve above the barrel

    def muzzle_device(self):
        """SIG 3-prong flash hider."""
        md = self.slot_bone("muzzle", self.muzzle_mount, self.MUZZLE_SLOT)
        z, n = self.BARREL_END_Z, self.MUZZLE_DEVICE_LEN
        md.box((-0.5, 5, z - 0.5), (1, 1, 0.5), "phosphate", inflate=0.2)            # collar
        md.box((-0.2, 5.8, z - n), (0.4, 0.4, n - 0.5), "phosphate")                 # prongs: top
        md.box((-0.7, 4.85, z - n), (0.4, 0.4, n - 0.5), "phosphate")                # bottom left
        md.box((0.3, 4.85, z - n), (0.4, 0.4, n - 0.5), "phosphate")                 # bottom right

    def handguard(self):
        z0, z1 = self.HANDGUARD_Z
        n = z1 - z0
        hg = self.m.bone("handguard", pivot=(0, self.BORE_Y, -4))
        hg.box((-1, 4.5, z0), (2, 2, n), "anodised", pattern="slots")                # slim M-LOK tube
        hg.box((-1, 6.5, z0), (2, 0.5, n), "anodised", pattern="rails")              # continuous top rail
        hg.box((-1, 4.5, z1 - 0.5), (2, 2, 0.5), "anodised", inflate=0.1)            # rear collar at the receiver
        for i in range(3):                                                           # M-LOK slots, 3 and 9 o'clock
            hg.box((-1.05, 5.25, z0 + 0.75 + 2 * i), (2.1, 0.5, 1.25), "phosphate")
        hg.box((-0.25, 4.45, z0 + 0.75), (0.5, 0.25, 5.25), "phosphate")             # M-LOK slots, 6 o'clock

    def stock(self):
        """Side-folding telescoping skeleton stock: hinge, twin rods, lock block, butt."""
        st = self.slot_bone("stock", self.STOCK_PIVOT, self.STOCK_SLOT)
        st.box((-1, 4.5, 2.5), (2, 2, 1), "phosphate")                               # folding hinge / knuckle
        st.box((-1, 5.5, 3.5), (0.5, 0.5, 5), "barrel_steel")                        # twin rods
        st.box((0.5, 5.5, 3.5), (0.5, 0.5, 5), "barrel_steel")
        st.box((-1, 5, 7), (2, 1.5, 1.5), "furniture")                               # lock block / cheek rest
        st.box((-1, 2.5, 8.5), (2, 4, 0.5), "furniture")                             # butt plate
        st.box((-1, 2.5, 9), (2, 4, 0.5), "rubber")                                  # butt pad


def model() -> Model:
    return Mcx().build()
