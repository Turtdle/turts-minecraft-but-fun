"""Benelli M4 Super 90 / M1014 - 12 ga semi-auto (ARGO gas), 7-round tube.

Same scale and conventions as the M4 carbine (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 1010 mm = 30 units, 18.5 in barrel = 14 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.
Top Picatinny rail -> optic mount; nothing else is railed.
"""
import attachments as att
import shotguns as sg
import templates as T
from armorygen import Model
from shotguns import ShotgunPalette as P

BORE_Y = T.BORE_Y
RAIL_Y = 7.0             # top of the receiver rail
SIGHT_Y = 8.0            # ghost-ring centre and front post tip
TUBE_Y = 4.0             # magazine tube axis
MUZZLE_Z = -20.0
BUTT_Z = 10.0

RECEIVER = sg.PortedReceiver(z0=-6.0, z1=1.5, y0=3.5, y1=6.5, port_z=(-4.5, -2.0))
REAR_SIGHT_Z0 = 0.25                                  # front of the ghost-ring base (behind the rail)
LOAD_PORT = (0, RECEIVER.y0, -3.75)
SHELLS = sg.ShellBones(load_port=LOAD_PORT, eject_port=RECEIVER.port_center)
OPTIC_MOUNT = (0, RAIL_Y, -2.25)                      # middle of the rail

BOLT_TRAVEL = 2.5

# ---- first-person arms -------------------------------------------------------------------
R_HAND = (0, 1.75, 2)                                 # centre of the pistol grip
L_HAND = (0, 3.5, -10)                                # under the fore-end

# ---- timings (specs: 180 rpm -> 0.33 s interval, 0.6 s per shell, 1.5 s empty) ---------------
FIRE_S, RELOAD_S, RELOAD_EMPTY_S = 0.25, 0.6, 1.5
RECOIL, RECOIL_RISE = 1.5, 5.0

OPTICS = ("red_dot", "holographic", "acog")


def model() -> Model:
    m = Model("benelli_m4", density=1)
    P.register(m)
    _receiver(m)
    _barrel(m)
    _handguard(m)
    _grip_and_stock(m)
    _sights(m)
    SHELLS.add(m)

    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style="rifle")
    T.mounts(m, optic=OPTIC_MOUNT)
    T.attach(m, *OPTICS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z - 0.25), shell_eject=RECEIVER.port_center,
               sight_line=(0, SIGHT_Y, sg.sight_z(REAR_SIGHT_Z0)))
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_travel=BOLT_TRAVEL, length=FIRE_S, rise=RECOIL_RISE)
    sg.tube_reloads(m, SHELLS, RELOAD_S, RELOAD_EMPTY_S, pump_bone=None, travel=BOLT_TRAVEL)
    m.preview_configs = {"bare": att.loadout(m), "loadout_red_dot": att.loadout(m, "red_dot")}
    return m


def _receiver(m: Model) -> None:
    rc = RECEIVER.add(m.bone("receiver", pivot=(0, 5, 0)))
    rc.box((-1, 6.5, -4.5), (2, RAIL_Y - 6.5, 4.5), P.MATTE_BLACK, pattern="rails")   # Picatinny rail
    rc.box((-0.5, 3.4, -5), (1, 0.25, 2.5), "steel")                          # carrier in the loading port
    rc.box((1, 4, -3.75), (0.25, 0.75, 1), "steel", pattern="knurl")          # oversized bolt release
    rc.box((-0.5, 2, -2.25), (1, 0.5, 3), P.FURNITURE)                        # trigger guard, bottom
    rc.box((-0.5, 2.5, -2.25), (1, 1, 0.5), P.FURNITURE)                      # guard, front
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.75), (0.5, 1, 0.5), "steel")
    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -3.25))
    origin, size = RECEIVER.bolt_box()
    bo.box(origin, size, "bright_steel")
    bo.box((0.9, 5.25, -2.75), (1.35, 0.5, 0.5), "steel")                     # large bolt handle
    bo.box((1.75, 5.1, -2.9), (0.5, 0.8, 0.8), "steel", pattern="knurl")      # its knob


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER.z0))
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, RECEIVER.z0 - MUZZLE_Z), P.MATTE_BLACK, inflate=0.15)      # 18.5 in barrel
    ba.box((-0.5, TUBE_Y - 0.5, -18.5), (1, 1, RECEIVER.z0 + 18.5), P.MATTE_BLACK, inflate=0.1)   # magazine tube
    ba.box((-0.5, TUBE_Y - 0.5, -19), (1, 1, 0.5), "black_metal", pattern="knurl", inflate=0.2)   # tube cap
    ba.box((-0.75, 3.25, -16.5), (1.5, 3, 0.75), "black_metal")                                   # barrel clamp


def _handguard(m: Model) -> None:
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECEIVER.z0))
    hg.box((-1.25, 2.75, -13), (2.5, 2.25, 7), P.FURNITURE, pattern="lines")   # bulged lower fore-end
    hg.box((-1, 5, -13), (2, 1, 7), P.FURNITURE)                              # upper halves beside the barrel
    for x in (-1.1, 0.6):                                                     # twin ARGO gas pistons
        hg.box((x, 4.6, -13.75), (0.5, 0.5, 0.75), "bright_steel")


def _grip_and_stock(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 3.5, 1))
    gr.box((-1, -0.5, 0), (2, 4, 2), P.FURNITURE, pattern="stipple", rot=(-22.5, 0, 0), pivot=(0, 3.5, 1),
           inflate=-0.05)
    st = m.bone("stock", pivot=(0, 4.5, RECEIVER.z1))
    st.box((-1, 3.75, RECEIVER.z1), (2, 2, 1), P.MATTE_BLACK)                 # receiver end cap
    st.box((-0.5, 4, 2.5), (1, 1, BUTT_Z - 3.5), "black_metal", pattern="lines")   # telescoping tube
    st.box((-1, 5, 4.5), (2, 1, 3.5), P.FURNITURE)                            # cheek rest
    st.wedge((0, 3.75, 3), (0, 1.75, BUTT_Z - 1), 1, 0.5, P.FURNITURE)        # lower strut (skeletal)
    st.box((-1, 1, BUTT_Z - 1), (2, 5, 0.5), P.FURNITURE)                     # butt plate
    st.box((-1, 1, BUTT_Z - 0.5), (2, 5, 0.5), "rubber", pattern="lines")     # recoil pad


def _sights(m: Model) -> None:
    sg.aperture_sight(m.bone("rear_sight", pivot=(0, RECEIVER.y1, sg.sight_z(REAR_SIGHT_Z0))),
                      RECEIVER.y1, SIGHT_Y, REAR_SIGHT_Z0, "black_metal")
    sg.post_sight(m.bone("front_sight", pivot=(0, 6.15, MUZZLE_Z + 0.75)), 6.15, SIGHT_Y, MUZZLE_Z + 0.25,
                  "black_metal", base_h=0.85)
