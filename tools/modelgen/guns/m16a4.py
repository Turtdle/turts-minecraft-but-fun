"""M16A4 - 20 in service rifle: fixed A2 stock, KAC M5 RAS quad rail, A2 front sight tower and
the detachable carry handle (bone `rear_sight`, hidden when an optic is fitted).

Built on the shared AR-15 platform (platform_ar15.py); only what differs from it is here.
Scale as the M4: 1 unit ~ 3.35 cm -> 1000 mm = 30 units (z -20 .. 10).
"""
from armorygen import Model
from platform_ar15 import Ar15Platform

SIGHT_Y = 8.0            # A2 front post tip = centre of the carry handle's rear aperture
REAR_SIGHT_Z = 1.25      # z centre of the carry handle's aperture housing
FSB_Z = -16.0            # front face of the front sight base / gas block


class M16A4(Ar15Platform):
    ID = "m16a4"
    RPM = 800
    RELOAD_S, RELOAD_EMPTY_S = 2.4, 2.9
    RECOIL, RISE = 0.7, 1.8              # long, heavy rifle: a little softer than the M4

    SIGHT_Y, REAR_SIGHT_Z = SIGHT_Y, REAR_SIGHT_Z
    HANDGUARD_Z = (-14.0, -5.0)          # rifle-length RAS (12 in)
    BARREL_END_Z = -18.0
    L_HAND = (0, 4.5, -10.5)
    UNDERBARREL_MOUNT = (0, 4, -9)       # bottom rail, just behind the left hand
    SIDE_MOUNT = (1.25, 5.5, -12.5)      # right rail, front end
    STOCK_SLOT = False                   # fixed A2 stock on a rifle buffer tube: no CTR
    LOADOUTS = {
        "loadout_cco": ("red_dot", "vertical_grip", "laser"),
        "loadout_acog": ("acog", "suppressor_556", "bipod", "pmag_40"),
        "loadout_micro": ("micro_red_dot",),
    }

    def barrel(self):
        ba = self.m.bone("barrel", pivot=(0, self.BORE_Y, -4))
        ba.box((-0.5, 5, self.BARREL_END_Z), (1, 1, 4), "barrel_steel")              # slim 20 in barrel
        ba.box((-0.25, 4.25, FSB_Z - 1), (0.5, 0.75, 1), "phosphate")                # bayonet lug

    def front_sight(self):
        self.a2_front_sight(FSB_Z, leg_to=self.HANDGUARD_Z[0] + 0.5)

    def handguard(self):
        z0, z1 = self.HANDGUARD_Z
        n = z1 - z0
        hg = self.m.bone("handguard", pivot=(0, self.BORE_Y, -4))
        hg.box((-1, 4.5, z0), (2, 2, n), "anodised")                                 # KAC M5 RAS body
        hg.box((-1, 6.5, z0 + 1), (2, 0.5, n - 1), "anodised", pattern="rails")      # top rail (short of the sight leg)
        hg.box((-1.25, 5.25, z0), (2.5, 0.5, n), "anodised", pattern="rails")        # side rails
        hg.box((-0.5, 4, z0), (1, 0.5, n), "anodised", pattern="rails")              # bottom rail
        hg.box((-0.75, 4.75, z0 - 0.5), (1.5, 1.5, 0.5), "barrel_steel")             # handguard cap
        hg.box((-1, 4.5, z1), (2, 2, 1), "barrel_steel", inflate=0.25)               # delta ring

    def rear_sight(self):
        """Detachable carry handle: two feet on the rail, a U-channel bar, A2 aperture housing."""
        ry, sy, z = self.RAIL_Y, self.SIGHT_Y, REAR_SIGHT_Z
        rs = self.m.bone("rear_sight", pivot=(0, ry, -1))
        rs.box((-1, ry, -3.5), (2, 0.5, 1), "anodised")                              # front foot
        rs.box((-1, ry, 0), (2, 0.5, 1.5), "anodised")                               # rear foot
        rs.box((1, ry, -3.25), (0.5, 0.5, 0.5), "barrel_steel", pattern="knurl")     # thumb nuts
        rs.box((1, ry, 0.25), (0.5, 0.5, 0.5), "barrel_steel", pattern="knurl")
        rs.box((-1, ry + 0.5, -3.5), (2, 0.25, 4), "anodised")                       # floor of the channel
        rs.box((-1, ry + 0.75, -3.5), (0.5, 0.5, 4), "anodised")                     # channel walls
        rs.box((0.5, ry + 0.75, -3.5), (0.5, 0.5, 4), "anodised")
        rs.box((-1, sy - 0.5, z - 0.75), (0.5, 1, 1), "anodised")                    # aperture housing, left
        rs.box((0.5, sy - 0.5, z - 0.75), (0.5, 1, 1), "anodised")                   # right
        rs.box((-1, sy + 0.5, z - 0.75), (2, 0.25, 1), "anodised")                   # top
        rs.box((1, sy - 0.5, z - 0.5), (0.5, 0.75, 0.5), "barrel_steel", pattern="knurl")  # windage knob

    def stock(self):
        """Fixed A2 buttstock: straight comb in line with the bore, underside stepping down."""
        z = self.RECEIVER_Z[1]
        st = self.slot_bone("stock", self.STOCK_PIVOT, self.STOCK_SLOT)
        st.box((-1, 4.5, z), (2, 2, 7.5), "furniture")                               # comb
        st.box((-1, 3.5, z + 2.5), (2, 1, 5), "furniture")                           # underside, step 1
        st.box((-1, 2.5, z + 5), (2, 1, 2.5), "furniture")                           # step 2
        st.box((-1, 2, z + 7.5), (2, 4.5, 0.5), "phosphate", pattern="stipple")      # butt plate with trapdoor


def model() -> Model:
    return M16A4().build()
