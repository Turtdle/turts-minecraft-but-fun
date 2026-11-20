"""Mossberg M590A1 (US military issue) - 12 ga pump shotgun, 8-round tube.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1041 mm = 31 units, 20 in barrel = 15 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.
No rails on the issued gun -> no attachment mounts.
"""
import attachments as att
import shotguns as sg
import templates as T
from armorygen import Model
from shotguns import ShotgunPalette as P

BORE_Y = T.BORE_Y
SIGHT_Y = 7.5            # ghost-ring centre and front blade tip
TUBE_Y = 4.0             # magazine tube axis
MUZZLE_Z = -20.5
BUTT_Z = 10.5

RECEIVER = sg.PortedReceiver(z0=-5.5, z1=1.5, y0=3.5, y1=6.5, port_z=(-4.5, -2.0), mat=P.PARKERIZED)
REAR_SIGHT_Z0 = 0.0                                   # front of the ghost-ring base
LOAD_PORT = (0, RECEIVER.y0, -3.75)                   # bottom of the receiver, in front of the guard
SHELLS = sg.ShellBones(load_port=LOAD_PORT, eject_port=RECEIVER.port_center)

# ---- pump ------------------------------------------------------------------------------
PUMP_Z0, PUMP_LEN = -14.0, 5.0
PUMP_TRAVEL = 3.0

# ---- first-person arms -------------------------------------------------------------------
R_HAND = (0, 3.0, 3.25)                               # wrist of the straight stock
L_HAND = (0, 3.25, PUMP_Z0 + PUMP_LEN / 2)            # under the middle of the pump

# ---- timings (specs: 60 rpm pump, 0.65 s per shell, 1.7 s empty) -----------------------------
FIRE_S, RELOAD_S, RELOAD_EMPTY_S = 1.0, 0.65, 1.7
RECOIL, RECOIL_RISE = 1.8, 6.0


def model() -> Model:
    m = Model("m590", density=1)
    P.register(m)
    _receiver(m)
    _barrel(m)
    _pump(m)
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
    rc.box((-0.5, 3.4, -5), (1, 0.25, 2.5), "steel")                          # elevator in the loading port
    rc.box((-0.25, 6.5, 1), (0.5, 0.25, 0.5), "steel", pattern="knurl")       # top-tang safety
    rc.box((-0.5, 2, -2.25), (1, 0.5, 3.5), P.PARKERIZED)                     # metal trigger guard, bottom
    rc.box((-0.5, 2.5, -2.25), (1, 1, 0.5), P.PARKERIZED)                     # guard, front
    rc.box((1, 4, -1.5), (0.1, 0.5, 0.5), "steel")                            # trigger-group pin
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.75), (0.5, 1, 0.5), "steel")
    origin, size = RECEIVER.bolt_box()
    m.bone("bolt", pivot=(0.5, BORE_Y, -3.25)).box(origin, size, "bright_steel")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER.z0))
    length = RECEIVER.z0 - MUZZLE_Z
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, length), P.PARKERIZED, inflate=0.15)            # heavy-wall 20 in barrel
    ba.box((-0.5, TUBE_Y - 0.5, MUZZLE_Z + 0.75), (1, 1, length - 0.75), P.PARKERIZED, inflate=0.1)  # magazine tube
    ba.box((-0.5, TUBE_Y - 0.5, MUZZLE_Z + 0.25), (1, 1, 0.5), "black_metal", pattern="knurl", inflate=0.2)  # tube cap
    ba.box((-0.75, 3.25, -18.5), (1.5, 3, 0.5), "black_metal")                          # barrel ring
    ba.box((-0.25, 2.75, -19.5), (0.5, 0.5, 1.5), "black_metal")                        # bayonet lug
    # perforated heat shield over the top of the barrel
    ba.box((-0.75, 6.15, -17.5), (1.5, 0.35, 11.5), "black_metal", pattern="slots")
    ba.box((-1, 5.4, -17.5), (0.25, 1.1, 11.5), "black_metal", pattern="slots")
    ba.box((0.75, 5.4, -17.5), (0.25, 1.1, 11.5), "black_metal", pattern="slots")


def _pump(m: Model) -> None:
    pu = m.bone("pump", pivot=(0, TUBE_Y, PUMP_Z0 + PUMP_LEN / 2))
    pu.box((-1, 2.75, PUMP_Z0), (2, 2.25, PUMP_LEN), P.FURNITURE, pattern="lines", inflate=0.1)   # ribbed fore-end
    pu.box((-1, 2.75, PUMP_Z0 + PUMP_LEN), (2, 2, 0.5), P.FURNITURE)                            # rear collar
    bar_z = PUMP_Z0 + PUMP_LEN + 0.5
    for x in (-0.9, 0.7):                                                                     # action bars
        pu.box((x, 4.25, bar_z), (0.2, 0.5, RECEIVER.z0 - bar_z + 0.5), "steel")


def _stock(m: Model) -> None:
    st = m.bone("stock", pivot=(0, 4.5, RECEIVER.z1))
    st.wedge((0, 4.75, RECEIVER.z1 - 0.25), (0, 3.25, 5.25), 2, 2.25, P.FURNITURE, pattern="stipple")  # wrist
    st.box((-1, 2.5, 4.5), (2, 2.75, BUTT_Z - 5), P.FURNITURE)                # comb
    st.box((-1, 1.5, 6.5), (2, 1, BUTT_Z - 7), P.FURNITURE)                   # stepped belly
    st.box((-1, 0.5, 8.5), (2, 1, BUTT_Z - 9), P.FURNITURE)                   # toe
    st.box((-1, 0.5, BUTT_Z - 0.5), (2, 4.75, 0.5), "rubber", pattern="lines")  # recoil pad
    st.box((-0.25, 0, 8.75), (0.5, 0.5, 0.5), "steel")                        # sling swivel


def _sights(m: Model) -> None:
    sg.aperture_sight(m.bone("rear_sight", pivot=(0, RECEIVER.y1, sg.sight_z(REAR_SIGHT_Z0))),
                      RECEIVER.y1, SIGHT_Y, REAR_SIGHT_Z0, "black_metal")
    sg.post_sight(m.bone("front_sight", pivot=(0, 6.15, MUZZLE_Z + 0.75)), 6.15, SIGHT_Y, MUZZLE_Z + 0.25,
                  "black_metal", base_h=0.6)
