"""HK416 D14.5RS - piston AR: thick boxy HK quad rail with a front lip, folding front sight on
the gas block, HK diopter rear sight, HK telescoping stock with a convex butt pad.

Built on the shared AR-15 platform (platform_ar15.py); only what differs from it is here.
Scale as the M4: 1 unit ~ 3.35 cm -> 886 mm = 26.5 units (z -16 .. 10.5).
"""
from armorygen import Model
from platform_ar15 import Ar15Platform

SIGHT_Y = 8.0            # front post tip = centre of the diopter aperture
REAR_SIGHT_Z = 1.5
GAS_BLOCK_Z = -12.5      # front face of the gas block


class Hk416(Ar15Platform):
    ID = "hk416"
    RPM = 850
    RELOAD_S, RELOAD_EMPTY_S = 2.3, 2.8
    RECOIL, RISE = 0.8, 2.0

    SIGHT_Y, REAR_SIGHT_Z = SIGHT_Y, REAR_SIGHT_Z
    HANDGUARD_Z = (-11.0, -4.0)          # 9 in free-float rail, straight onto the receiver
    BARREL_END_Z = -14.0
    L_HAND = (0, 4.25, -9)
    UNDERBARREL_MOUNT = (0, 3.75, -7.5)
    SIDE_MOUNT = (1.5, 5.5, -6)
    PALETTE = {**Ar15Platform.PALETTE,
               "anodised": ((44, 45, 50), 5, 0.72),      # HK black
               "furniture": ((40, 40, 43), 4, 0.80),
               "barrel_steel": ((70, 72, 79), 6, 0.72),
               "mag": ((86, 88, 94), 6, 0.72)}           # HK steel magazine
    LOADOUTS = {
        "loadout_delta": ("holographic", "suppressor_556", "vertical_grip", "laser"),
        "loadout_b": ("acog", "compensator_556", "angled_grip", "stock_ctr", "pmag_40"),
        "loadout_micro": ("micro_red_dot", "vertical_grip"),
    }

    def barrel(self):
        ba = self.m.bone("barrel", pivot=(0, self.BORE_Y, -4))
        ba.box((-0.5, 5, self.BARREL_END_Z), (1, 1, 3), "barrel_steel", inflate=0.15)   # heavy-profile barrel
        ba.box((-0.5, 5, GAS_BLOCK_Z), (1, 1.5, 1), "phosphate", inflate=0.25)          # piston gas block

    def front_sight(self):
        """Folding front sight on the gas block: post under an HK hood."""
        sy, z = self.SIGHT_Y, GAS_BLOCK_Z + 0.25
        fs = self.m.bone("front_sight", pivot=(0, 6.75, z + 0.25))
        fs.box((-1, 6.75, z), (2, 0.75, 0.5), "phosphate")                           # hinge base
        fs.box((-1, 7.5, z), (0.5, sy + 0.5 - 7.5, 0.5), "phosphate")                # hood, left
        fs.box((0.5, 7.5, z), (0.5, sy + 0.5 - 7.5, 0.5), "phosphate")               # hood, right
        fs.box((-1, sy + 0.5, z), (2, 0.25, 0.5), "phosphate")                       # hood, top
        fs.box((-0.25, 7.5, z), (0.5, sy - 7.5, 0.5), "phosphate")                   # post: TOP at SIGHT_Y

    def handguard(self):
        z0, z1 = self.HANDGUARD_Z
        n = z1 - z0
        hg = self.m.bone("handguard", pivot=(0, self.BORE_Y, -4))
        hg.box((-1.25, 4.25, z0), (2.5, 2.5, n), "anodised")                         # thick boxy rail body
        hg.box((-1, 6.5, z0), (2, 0.5, n), "anodised", pattern="rails")              # top rail, flush with the receiver
        hg.box((-1.5, 5.25, z0), (3, 0.5, n), "anodised", pattern="rails")           # side rails
        hg.box((-0.5, 3.75, z0), (1, 0.5, n), "anodised", pattern="rails")           # bottom rail
        hg.box((-1.25, 4.25, z0 - 0.25), (2.5, 2.5, 0.5), "phosphate", inflate=0.2)  # pronounced front lip

    def rear_sight(self):
        rs = self.aperture_rear_sight()
        z = self.REAR_SIGHT_Z
        rs.box((1, self.SIGHT_Y - 0.5, z - 0.5), (0.5, 1, 1), "phosphate", pattern="knurl")  # diopter drum / windage

    def stock(self):
        """HK telescoping stock on a carbine buffer tube: sloped underside, convex butt pad."""
        st = self.slot_bone("stock", self.STOCK_PIVOT, self.STOCK_SLOT)
        self.buffer_tube(st)
        st.box((-1, 4.5, 5), (2, 2, 4.5), "furniture")                               # body round the tube
        st.box((-1, 3.5, 6.5), (2, 1, 3), "furniture", pattern="lines")              # underside, step 1
        st.box((-1, 2.5, 8), (2, 1, 1.5), "furniture")                               # toe
        st.box((-1, 2.5, 9.5), (2, 4.25, 0.5), "rubber")                             # butt pad
        st.box((-1, 3.5, 10), (2, 2.5, 0.5), "rubber")                               # its convex centre


def model() -> Model:
    return Hk416().build()
