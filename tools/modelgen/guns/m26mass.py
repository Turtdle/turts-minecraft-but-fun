"""M26 Modular Accessory Shotgun System, stand-alone configuration - 12 ga straight-pull,
5-round box magazine, M4-style collapsible stock.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 670 mm = 20 units, 7.75 in barrel incl. the stand-off = 6 units (most of
it inside the receiver). Muzzle toward -Z, right side is +X, trigger near z=0, bore at y=5.5.
The charging lever is on the LEFT (it is reversible on the real gun) and worked by the left hand.
"""
import attachments as att
import shotguns as sg
import templates as T
from armorygen import Model
from shotguns import ShotgunPalette as P

BORE_Y = T.BORE_Y
RAIL_Y = 7.0             # top of the Picatinny rail
SIGHT_Y = 8.0            # flip-up aperture centre and front post tip
MUZZLE_Z = -10.5         # front of the stand-off prongs

RECEIVER = sg.PortedReceiver(z0=-6.5, z1=3.0, y0=4.5, y1=6.5, port_z=(-3.5, -1.5))
REAR_SIGHT_Z0 = 1.75
FRONT_SIGHT_Z0 = -6.25
OPTIC_MOUNT = (0, RAIL_Y, -2.25)
STOCK_PIVOT = (0, BORE_Y, RECEIVER.z1)                # front of the buffer tube
MAG_PIVOT = (0, 3.5, -3.25)                           # top-centre of the magazine well

# ---- straight-pull action ------------------------------------------------------------------
BOLT_PIVOT = (0, BORE_Y, -4.0)                        # on the bolt axis, at the charging lever
KNOB = (-2.5, BORE_Y, -4.0)                           # lever knob, left side
BOLT_PULL = 2.5

# ---- first-person arms -------------------------------------------------------------------
R_HAND = (0, 1.75, 2)                                 # centre of the pistol grip
L_HAND = (0, 4.0, -6.0)                               # under the front of the receiver, ahead of the magazine

# ---- timings (specs: 40 rpm -> 1.5 s per shot, 2.8 s reload, 3.8 s empty) ---------------------
FIRE_S, RELOAD_S, RELOAD_EMPTY_S = 1.5, 2.8, 3.8
RECOIL, RECOIL_RISE = 2.0, 7.0

OPTICS = ("red_dot", "holographic", "acog")


def model() -> Model:
    m = Model("m26mass", density=1)
    P.register(m)
    _receiver(m)
    _action(m)
    _barrel(m)
    _grip(m)
    _magazine(m)
    _stock(m)
    _sights(m)

    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style="rifle")
    T.mounts(m, optic=OPTIC_MOUNT)
    T.attach(m, *OPTICS, "stock_ctr")
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=RECEIVER.port_center,
               sight_line=(0, SIGHT_Y, sg.sight_z(REAR_SIGHT_Z0)))
    T.idle(m)
    T.fire_straight_pull(m, pull=BOLT_PULL, length=FIRE_S, recoil=RECOIL, rise=RECOIL_RISE,
                         hand="left", knob=KNOB)
    T.reload_mag(m, length=RELOAD_S)
    T.reload_mag(m, length=RELOAD_EMPTY_S, empty=True, empty_action="cycle", lift_deg=0,
                 pull=BOLT_PULL, hand="left", knob=KNOB)
    m.preview_configs = {"bare": att.loadout(m),
                         "loadout_a": att.loadout(m, "red_dot", "stock_ctr")}
    return m


def _receiver(m: Model) -> None:
    rc = RECEIVER.add(m.bone("receiver", pivot=(0, 5, 0)))
    rc.box((-1, 6.5, RECEIVER.z0), (2, RAIL_Y - 6.5, 9.5), P.MATTE_BLACK, pattern="rails")   # top rail
    rc.box((-1, 3.5, RECEIVER.z0), (2, 1, 9.5), P.MATTE_BLACK)                # lower receiver
    rc.box((-1, 2.5, -4.75), (2, 1, 3), P.MATTE_BLACK)                        # magazine well
    rc.box((-0.5, 2, -1.75), (1, 0.5, 2.75), P.MATTE_BLACK)                   # trigger guard
    rc.box((-1.05, 5.25, -4.5), (0.05, 0.5, 3.5), P.PORT)                     # charging-lever slot (left)
    rc.box((-0.5, 3.75, RECEIVER.z0 - 1), (1, 1, 1), "black_metal")           # barrel yoke under the barrel
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.75), (0.5, 1, 0.5), "steel")


def _action(m: Model) -> None:
    """`bolt_handle` = bolt body (seen in the ejection port) + the left-side charging lever."""
    bh = m.bone("bolt_handle", pivot=BOLT_PIVOT)
    origin, size = RECEIVER.bolt_box()
    bh.box(origin, size, "bright_steel")
    bh.box((KNOB[0] + 0.5, 5.25, KNOB[2] - 0.25), (1, 0.5, 0.5), "steel")                 # lever arm
    bh.box((KNOB[0] - 0.5, 5, KNOB[2] - 0.5), (1, 1, 1), "black_metal", pattern="knurl")  # knob


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER.z0))
    ba.box((-0.5, 5, -9), (1, 1, 2.5), P.MATTE_BLACK, inflate=0.2)            # stubby barrel
    ba.box((-0.5, 5, -10), (1, 1, 1), "black_metal", inflate=0.45)            # stand-off breaching device
    for x in (-0.95, 0.45):                                                   # crenellated tip
        for y in (4.55, 5.95):
            ba.box((x, y, MUZZLE_Z), (0.5, 0.5, 0.5), "black_metal")


def _grip(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 3.5, 1))
    gr.box((-1, -0.5, 0), (2, 4, 2), P.FURNITURE, pattern="stipple", rot=(-22.5, 0, 0), pivot=(0, 3.5, 1),
           inflate=-0.05)


def _magazine(m: Model) -> None:
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    mg.box((-0.75, -0.25, -4.5), (1.5, 3.75, 2.5), P.MATTE_BLACK, pattern="lines")   # 5-round box
    mg.box((-0.75, -0.5, -4.5), (1.5, 0.25, 2.5), P.FURNITURE, inflate=0.1)          # floor plate


def _stock(m: Model) -> None:
    z = STOCK_PIVOT[2]
    st = T.default_part(m, "stock", STOCK_PIVOT)                              # M4 buttstock module
    st.box((-0.5, 5, z), (1, 1, 3), "gunmetal", pattern="lines")              # buffer tube
    st.box((-1, 4.5, z + 3), (2, 2, 4), P.FURNITURE)                          # stock body
    st.box((-1, 3.5, z + 5), (2, 1, 2), P.FURNITURE, pattern="lines")         # stepped underside
    st.box((-1, 2.5, z + 6), (2, 1, 1), P.FURNITURE)                          # toe


def _sights(m: Model) -> None:
    sg.aperture_sight(m.bone("rear_sight", pivot=(0, RAIL_Y, sg.sight_z(REAR_SIGHT_Z0))),
                      RAIL_Y, SIGHT_Y, REAR_SIGHT_Z0, "black_metal")
    sg.post_sight(m.bone("front_sight", pivot=(0, RAIL_Y, FRONT_SIGHT_Z0 + 0.5)), RAIL_Y, SIGHT_Y,
                  FRONT_SIGHT_Z0, "black_metal", wings=True)
