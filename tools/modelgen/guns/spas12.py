"""Franchi SPAS-12 - 12 ga dual-mode (pump / gas semi-auto) combat shotgun, 8-round tube.

Silhouette: the folding steel stock FOLDED OVER THE TOP of the gun (two flat struts along the
receiver's top edges, the butt frame standing over the barrel with its swivelling arm hook
laid back along the top), the big ribbed / perforated heat shield round the barrel, a fat
ribbed pump fore-end, the magazine tube running almost to the muzzle, a pistol grip and an
oversized trigger guard. All black.
1041 mm with the stock extended; folded it is 930 mm -> ~27.5 units here (grip heel z 4,
muzzle z -22.5), 21.5 in barrel = 16.5 units.
Scale / axes as the M4 (see IN-GAME TUNING NOTES in m4.py): 1 unit ~ 3.35 cm, muzzle -Z, +X
right, bore y 5.5. No rails -> no attachment mounts. The Java side switches pump / semi-auto;
the model's `fire` is the pump stroke (bone `pump`), the reloads are the family's tube reloads.
The sight line (y 7.5) passes between the stock struts and THROUGH the open butt frame.
"""
import attachments as att
import shotguns as sg
import templates as T
from armorygen import Model
from shotguns import ShotgunPalette as P

BORE_Y = T.BORE_Y
SIGHT_Y = 7.5            # rear aperture centre and front blade tip
TUBE_Y = 4.0             # magazine tube axis
MUZZLE_Z = -22.5
SHIELD_Z = (-17.0, -6.0)  # heat shield: from the receiver to in front of the pump

RECEIVER = sg.PortedReceiver(z0=-6.0, z1=1.5, y0=3.5, y1=6.5, port_z=(-4.5, -2.0))
REAR_SIGHT_Z0 = 0.25                                  # front of the rear sight base
LOAD_PORT = (0, RECEIVER.y0, -3.75)                   # bottom of the receiver, in front of the guard
SHELLS = sg.ShellBones(load_port=LOAD_PORT, eject_port=RECEIVER.port_center)

# ---- pump ------------------------------------------------------------------------------
PUMP_Z0, PUMP_LEN = -15.5, 6.0
PUMP_TRAVEL = 3.0

# ---- folded stock ------------------------------------------------------------------------
HINGE_Z = 2.5                                         # hinge pin behind the receiver
STRUT_Y = (6.6, 7.1)                                  # the struts lie just above the receiver top
BUTT_Z = -8.5                                         # front face of the folded butt frame

# ---- first-person arms -------------------------------------------------------------------
R_HAND = (0, 1.75, 2.25)                              # centre of the pistol grip
L_HAND = (0, 3.25, PUMP_Z0 + PUMP_LEN / 2)            # under the middle of the pump

# ---- timings -------------------------------------------------------------------------------
FIRE_S, RELOAD_S, RELOAD_EMPTY_S = 0.9, 0.7, 1.7
RECOIL, RECOIL_RISE = 1.8, 6.0


def model() -> Model:
    m = Model("spas12", density=1)
    P.register(m)
    m.material("spas_shield", (58, 58, 63), 7, 0.66)      # stamped heat shield (a touch lighter: reads as ribbed)
    m.material("spas_stock", (52, 53, 57), 6, 0.72)       # folding stock steel
    _receiver(m)
    _barrel(m)
    _pump(m)
    _grip(m)
    _stock(m)
    _sights(m)
    SHELLS.add(m)

    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style="shotgun_pump")
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z - 0.25), shell_eject=RECEIVER.port_center,
               sight_line=(0, SIGHT_Y, sg.sight_z(REAR_SIGHT_Z0)))
    T.idle(m)
    T.fire_pump(m, travel=PUMP_TRAVEL, length=FIRE_S, recoil=RECOIL, rise=RECOIL_RISE)
    sg.tube_reloads(m, SHELLS, RELOAD_S, RELOAD_EMPTY_S, pump_bone="pump", travel=PUMP_TRAVEL)
    m.preview_configs = {"bare": att.loadout(m)}
    return m


def _receiver(m: Model) -> None:
    rc = RECEIVER.add(m.bone("receiver", pivot=(0, 5, 0)))
    rc.box((-0.5, 3.4, -5), (1, 0.25, 2.5), "steel")                          # carrier in the loading port
    rc.box((-1, 3.75, 1.5), (2, 2.75, 1), P.MATTE_BLACK)                      # receiver end cap
    rc.box((1, 4, -1.25), (0.2, 0.6, 0.6), "steel", pattern="knurl")          # carrier latch button
    rc.box((-1.2, 4.1, -5.25), (0.2, 0.5, 1), "steel")                        # magazine cut-off (left)
    rc.box((-0.5, 1.75, -2.5), (1, 0.5, 4), P.MATTE_BLACK)                    # oversized trigger guard, bottom
    rc.box((-0.5, 2.25, -2.5), (1, 1.25, 0.5), P.MATTE_BLACK)                 # guard, front
    rc.box((-0.6, 2.4, -2.4), (1.2, 0.5, 0.3), "steel", pattern="knurl")      # lever safety in the guard
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.75), (0.5, 1, 0.5), "steel")
    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -3.25))
    origin, size = RECEIVER.bolt_box()
    bo.box(origin, size, "bright_steel")
    bo.box((0.9, 5.25, -2.75), (0.85, 0.5, 0.5), "steel")                     # cocking handle


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER.z0))
    length = RECEIVER.z0 - MUZZLE_Z
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, length), P.MATTE_BLACK, inflate=0.15)                  # 21.5 in barrel
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 0.75), "black_metal", pattern="knurl", inflate=0.3)    # muzzle thread protector
    ba.box((-0.5, TUBE_Y - 0.5, MUZZLE_Z + 1.5), (1, 1, length - 1.5), P.MATTE_BLACK, inflate=0.1)   # 8-round tube
    ba.box((-0.5, TUBE_Y - 0.5, MUZZLE_Z + 1), (1, 1, 0.5), "black_metal", pattern="knurl", inflate=0.2)  # tube cap
    ba.box((-0.75, 3.25, -19.5), (1.5, 3, 0.75), "black_metal")                               # barrel / tube clamp
    ba.box((-1, 4.25, -19.4), (2, 0.5, 0.5), "steel")                                         # sling swivel on the clamp
    # heat shield: a ribbed, perforated stamping wrapped over the top and both sides of the barrel
    z0, z1 = SHIELD_Z
    n = z1 - z0
    ba.box((-1, 6.2, z0), (2, 0.4, n), "spas_shield", pattern="slots")                        # top
    ba.box((-1.3, 4.9, z0), (0.3, 1.5, n), "spas_shield", pattern="slots")                    # left wall
    ba.box((1, 4.9, z0), (0.3, 1.5, n), "spas_shield", pattern="slots")                       # right wall
    for z in (z0, z0 + 3.5, z0 + 7, z1 - 0.5):                                                # raised ribs
        ba.box((-1.4, 4.8, z), (2.8, 1.8, 0.5), "black_metal")


def _pump(m: Model) -> None:
    pu = m.bone("pump", pivot=(0, TUBE_Y, PUMP_Z0 + PUMP_LEN / 2))
    pu.box((-1.25, 2.75, PUMP_Z0), (2.5, 2, PUMP_LEN), P.FURNITURE, pattern="lines", inflate=0.1)   # ribbed fore-end
    pu.box((-1.35, 3.25, PUMP_Z0 + 0.5), (2.7, 0.5, PUMP_LEN - 1), P.FURNITURE)                 # finger groove band
    pu.box((-1, 2.9, PUMP_Z0 + PUMP_LEN), (2, 1.6, 0.5), P.FURNITURE)                           # rear collar
    bar_z = PUMP_Z0 + PUMP_LEN + 0.5
    for x in (-0.9, 0.7):                                                                     # action bars
        pu.box((x, 4.25, bar_z), (0.2, 0.4, RECEIVER.z0 - bar_z + 0.5), "steel")


def _grip(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 3.75, 1.25))
    gr.box((-1, -0.25, 0.25), (2, 4, 2), P.FURNITURE, pattern="stipple", rot=(-22.5, 0, 0),
           pivot=(0, 3.75, 1.25), inflate=-0.05)


def _stock(m: Model) -> None:
    """The folding stock, folded: hinge behind the receiver, two struts forward along the top
    edges, the open butt frame over the barrel, the arm hook swung back along the right strut."""
    st = m.bone("stock", pivot=(0, 6.5, HINGE_Z))
    y0, y1 = STRUT_Y
    st.box((-1.4, 5.75, HINGE_Z - 0.5), (2.8, 1.35, 1), "spas_stock")                         # hinge block
    st.box((-1.5, 6.1, HINGE_Z - 0.25), (3, 0.5, 0.5), "steel")                               # hinge pin
    for x in (-1.4, 1.0):                                                                     # the two struts
        st.box((x, y0, BUTT_Z + 0.5), (0.4, y1 - y0, HINGE_Z - 0.5 - (BUTT_Z + 0.5)), "spas_stock",
               pattern="slots")
    st.box((-1.4, y0, -5), (2.8, 0.25, 0.5), "spas_stock")                                    # strut cross brace
    # butt frame: an open loop, so the sights look through it
    for x in (-1.4, 1.0):
        st.box((x, y0, BUTT_Z), (0.4, 2.4, 0.5), "spas_stock")                                # side posts
    st.box((-1.4, y0 + 2.4, BUTT_Z), (2.8, 0.5, 0.5), "spas_stock")                           # top bar
    st.box((-1.4, y0 + 0.4, BUTT_Z - 0.25), (0.4, 2.5, 0.25), "rubber")                       # butt pad strips
    st.box((1.0, y0 + 0.4, BUTT_Z - 0.25), (0.4, 2.5, 0.25), "rubber")
    # arm hook: pivots on the butt, swung flat along the top right
    hy = y0 + 2.4
    st.box((0.9, hy, BUTT_Z + 0.5), (0.5, 0.5, 3), "steel")                                   # hook arm
    st.box((0.9, hy - 1.1, BUTT_Z + 3), (0.5, 1.1, 0.5), "steel")                             # hook tip, turned down
    st.box((1.4, hy + 0.05, BUTT_Z + 0.1), (0.25, 0.4, 0.4), "steel", pattern="knurl")        # hook pivot screw


def _sights(m: Model) -> None:
    sg.aperture_sight(m.bone("rear_sight", pivot=(0, RECEIVER.y1, sg.sight_z(REAR_SIGHT_Z0))),
                      RECEIVER.y1, SIGHT_Y, REAR_SIGHT_Z0, "black_metal")
    sg.post_sight(m.bone("front_sight", pivot=(0, 6.15, MUZZLE_Z + 1.25)), 6.15, SIGHT_Y, MUZZLE_Z + 0.75,
                  "black_metal", base_h=0.6)
