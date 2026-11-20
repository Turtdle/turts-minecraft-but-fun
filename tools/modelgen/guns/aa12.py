"""MPS AA-12 (Atchisson Assault Shotgun) - full-auto 12 ga, open bolt, 300 rpm, 20-round drum.

Same scale and conventions as the M4 (see the IN-GAME TUNING NOTES in m4.py):
1 unit ~ 3.35 cm -> 991 mm = 29.5 units (z -18.0 .. 11.5), 18 in barrel = 13.6 units.
Muzzle toward -Z, right side is +X, trigger near z=0, bore axis at y=5.5.

Silhouette: one long slab-sided black body from the butt to the front of the fore-end; the
carry-handle-like sight bridge on top (a Picatinny deck on three pillars, the charging handle
runs in the gap under it); the ventilated fore-end round the barrel with the short vertical
grip moulded under it; the bare barrel sticking out in front; the straight-line stock with a
thick rubber recoil pad; and the big round 20-round DRUM hanging under the receiver just ahead
of the trigger guard.

Slot: OPTIC only (the deck of the sight bridge). The fold-away aperture rear sight and the
winged front post stand on the deck (bones `rear_sight` / `front_sight`, hidden under an optic).

Open bolt: at rest the bolt (bone `bolt`) is BACK, only its front edge shows at the rear of the
ejection port; `fire` throws it forward over the port and back (negative travel). The charging
handle (bone `bolt_handle`, on top, under the deck) does not reciprocate; the left hand pulls it
after an empty reload.
"""
import math

import attachments as att
import shotguns as sg
import templates as T
from armorygen import Model
from shotguns import ShotgunPalette as P

GUN_ID = "aa12"

# ---- datum lines ---------------------------------------------------------------------
BORE_Y = T.BORE_Y
RECV_TOP_Y = 6.5           # top of the body (the charging handle slides on it)
DECK_Y = 7.25              # underside of the sight bridge's deck
RAIL_Y = 7.75              # top of the Picatinny deck = optic mount height
SIGHT_Y = 8.75             # aperture centre and front post tip
MUZZLE_Z = -18.0
GUARD_FRONT_Z = -13.5      # front of the fore-end
BUTT_Z = 11.5              # rear face of the recoil pad

RECEIVER = sg.PortedReceiver(z0=-7.5, z1=4.0, y0=3.5, y1=RECV_TOP_Y, port_z=(-5.0, -2.0))
DECK_Z = (-13.25, 3.25)                               # the sight bridge's deck (z from .. to)
REAR_SIGHT_Z0 = 2.0                                   # front of the aperture's base
FRONT_SIGHT_Z0 = -13.25                               # front of the front post's base
OPTIC_MOUNT = (0, RAIL_Y, -2.5)                       # on the deck, over the receiver

# ---- open bolt -------------------------------------------------------------------------
BOLT_BACK = 2.25                                      # how far back the bolt rests (open bolt)
# ---- charging handle (top, under the deck) -------------------------------------------------
HANDLE_Z = -5.5
HANDLE_PIVOT = (0, 6.8, HANDLE_Z)
KNOB = (-0.6, 6.8, HANDLE_Z)                          # its left end: the left hand takes it
HANDLE_PULL = 3.0

# ---- drum ------------------------------------------------------------------------------
MAG_PIVOT = (0, RECEIVER.y0, -4.75)                   # top-centre of the feed tower
DRUM_D, DRUM_LEN = 5.5, 3.0                          # ~18 cm across, one shell deep
DRUM_C = (0, 0.25, -4.75)                             # drum axis (it lies along the bore)

# ---- first-person arms -------------------------------------------------------------------
R_HAND = (0, 1.75, 2)                                 # centre of the pistol grip
L_HAND = (0, 3.0, -10.5)                              # round the moulded front grip

# ---- timings (300 rpm -> 0.2 s per shot; 3.0 s reload, 3.8 s empty) ----------------------------
FIRE_S, RELOAD_S, RELOAD_EMPTY_S = 0.16, 3.0, 3.8
RECOIL, RECOIL_RISE = 0.45, 1.0                       # famously soft shooting

OPTICS = ("red_dot", "holographic", "micro_red_dot", "acog")


def model() -> Model:
    m = Model(GUN_ID, density=1)
    P.register(m)
    m.material("aa_drum", (34, 34, 38), 4, 0.78)      # polymer drum body
    m.material("aa_drum_face", (46, 46, 51), 5, 0.74)  # its front / rear covers
    _receiver(m)
    _action(m)
    _barrel(m)
    _handguard(m)
    _grip(m)
    _magazine(m)
    _stock(m)
    _sights(m)

    T.arms(m, right_hand=R_HAND, left_hand=L_HAND, style="rifle")
    T.mounts(m, optic=OPTIC_MOUNT)
    T.attach(m, *OPTICS)
    T.locators(m, muzzle_flash=(0, BORE_Y, MUZZLE_Z), shell_eject=RECEIVER.port_center,
               sight_line=(0, SIGHT_Y, sg.sight_z(REAR_SIGHT_Z0)))
    T.idle(m)
    T.fire_auto(m, recoil=RECOIL, bolt_bone="bolt", bolt_travel=-BOLT_BACK, length=FIRE_S,
                rise=RECOIL_RISE)
    T.reload_mag(m, length=RELOAD_S, grab=m.mag_grab)
    T.reload_mag(m, length=RELOAD_EMPTY_S, empty=True, empty_action="cycle", lift_deg=0,
                 pull=HANDLE_PULL, hand="left", knob=KNOB, grab=m.mag_grab)
    arms = {T.RIGHT_ARM, T.LEFT_ARM}
    m.preview_configs = {
        "bare": att.loadout(m),
        "noarms": att.loadout(m) | arms,
        "red_dot": att.loadout(m, "red_dot") | arms,
        "holographic": att.loadout(m, "holographic"),
        "micro_red_dot": att.loadout(m, "micro_red_dot") | arms,
        "acog": att.loadout(m, "acog") | arms,
    }
    return m


def _receiver(m: Model) -> None:
    rc = RECEIVER.add(m.bone("receiver", pivot=(0, 5, 0)))
    z0, z1 = RECEIVER.z0, RECEIVER.z1
    rc.box((-1.1, 3.75, z0 + 0.5), (0.1, 1.0, z1 - z0 - 1.0), P.FURNITURE, pattern="lines")   # ribbed side panel (left)
    rc.box((1.0, 3.75, -1.5), (0.1, 1.0, z1 + 1.0), P.FURNITURE, pattern="lines")            # (right, behind the port)
    rc.box((-1, 2.75, -6.25), (2, 0.75, 3.0), P.MATTE_BLACK)                  # magazine well lips
    rc.box((-0.5, 2.0, -3.0), (1, 0.4, 3.75), P.FURNITURE)                    # trigger guard, bottom
    rc.box((-0.5, 2.4, -3.0), (1, 0.35, 0.4), P.FURNITURE)                    # guard, front (meets the well)
    rc.box((-1.15, 4.9, 0.75), (0.15, 0.5, 0.9), "steel", pattern="knurl")    # safety (left)
    # carry-handle-like sight bridge: three pillars and the Picatinny deck
    d0, d1 = DECK_Z
    rc.box((-0.6, DECK_Y, d0), (1.2, RAIL_Y - DECK_Y, d1 - d0), P.PARKERIZED, pattern="rails")
    for z, length in ((1.75, 1.5), (-8.25, 1.25), (d0, 1.0)):
        rc.box((-0.6, RECV_TOP_Y, z), (1.2, DECK_Y - RECV_TOP_Y, length), P.MATTE_BLACK)
    rc.box((-0.25, RECV_TOP_Y, -7.0), (0.5, 0.1, 8.75), P.PORT)               # charging-handle track
    tr = m.bone("trigger", pivot=(0, 3.5, -0.5))
    tr.box((-0.25, 2.5, -0.75), (0.5, 1, 0.5), "steel")


def _action(m: Model) -> None:
    """`bolt` rests BACK (open bolt); `bolt_handle` is the non-reciprocating charging handle."""
    bo = m.bone("bolt", pivot=(0.5, BORE_Y, -3.5 + BOLT_BACK))
    origin, size = RECEIVER.bolt_box()
    bo.box((origin[0], origin[1], origin[2] + BOLT_BACK), size, "bright_steel")
    bh = m.bone("bolt_handle", pivot=HANDLE_PIVOT)
    bh.box((-0.6, RECV_TOP_Y + 0.1, HANDLE_Z - 0.4), (1.2, 0.5, 0.8), "steel", pattern="knurl")


def _barrel(m: Model) -> None:
    ba = m.bone("barrel", pivot=(0, BORE_Y, RECEIVER.z0))
    ba.box((-0.5, 5, MUZZLE_Z + 0.5), (1, 1, RECEIVER.z0 - MUZZLE_Z - 0.5), P.PARKERIZED, inflate=0.05)
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 0.75), P.MATTE_BLACK, pattern="knurl", inflate=0.2)   # muzzle nut


def _handguard(m: Model) -> None:
    """Ventilated fore-end round the barrel, with the short vertical grip moulded under it."""
    hg = m.bone("handguard", pivot=(0, BORE_Y, RECEIVER.z0))
    z0, length = GUARD_FRONT_Z, RECEIVER.z0 - GUARD_FRONT_Z
    hg.box((-1.1, 4.25, z0), (2.2, RECV_TOP_Y - 4.25, length), P.FURNITURE, pattern="slots")   # shroud
    hg.box((-1.15, 4.25, z0), (2.3, RECV_TOP_Y - 4.2, 0.4), P.MATTE_BLACK)    # front collar
    hg.box((-0.9, 3.75, z0 + 0.5), (1.8, 0.5, length - 0.5), P.FURNITURE)     # belly
    hg.box((-0.8, 1.75, -11.75), (1.6, 2.1, 2.0), P.FURNITURE, pattern="lines",
           rot=(8, 0, 0), pivot=(0, 3.75, -10.75))                            # moulded front grip
    hg.box((-0.85, 1.6, -11.8), (1.7, 0.3, 2.1), P.FURNITURE,
           rot=(8, 0, 0), pivot=(0, 3.75, -10.75))                            # its flared foot
    for x in (-1.15, 1.1):                                                    # cooling vents, both sides
        for i in range(4):
            hg.box((x, 5.2, z0 + 0.9 + 1.25 * i), (0.05, 0.6, 0.8), P.PORT)


def _grip(m: Model) -> None:
    gr = m.bone("grip", pivot=(0, 3.5, 1))
    gr.box((-1, -0.5, 0), (2, 4, 2), P.FURNITURE, pattern="stipple", rot=(-22.5, 0, 0), pivot=(0, 3.5, 1),
           inflate=-0.05)


def _magazine(m: Model) -> None:
    """20-round drum: a regular octagon (four slabs turned in 45 degree steps round the drum axis,
    which lies along the bore), a hub on both faces and the feed tower up into the well."""
    mg = T.default_part(m, "magazine", MAG_PIVOT)
    cx, cy, cz = DRUM_C
    side = DRUM_D * math.tan(math.radians(22.5))                               # one side of the octagon
    for i in range(4):                                                        # lengths differ: no shared end planes
        ang = 45.0 * i
        mg.cbox(DRUM_C, (DRUM_D, side, DRUM_LEN - 0.08 * i), "aa_drum", pattern="lines" if i == 0 else "none",
                rot=(0, 0, ang) if ang else None, pivot=DRUM_C if ang else None)
    for dz in (-1, 1):                                                        # cover plate + winding hub, front and rear
        zc = cz + dz * (DRUM_LEN / 2 + 0.05)
        mg.cyl((cx, cy, zc), 3.6, 0.1, "aa_drum_face")
        mg.cyl((cx, cy, zc + dz * 0.12), 1.3, 0.15, "steel", sides=4)
    tower_y0 = cy + DRUM_D / 2 - 0.5
    mg.box((-0.7, tower_y0, cz - 1.25), (1.4, RECEIVER.y0 - tower_y0, 2.5), "aa_drum")   # feed tower
    m.mag_grab = (0.0, cy - 1.0, cz)                                          # the left fist under the drum's middle


def _stock(m: Model) -> None:
    """Fixed straight-line stock (the bolt recoils into it) with a thick rubber recoil pad."""
    z0 = RECEIVER.z1
    st = m.bone("stock", pivot=(0, BORE_Y, z0))
    st.box((-0.9, 4.0, z0), (1.8, 2.4, BUTT_Z - 1.5 - z0), P.FURNITURE)                   # stock body
    st.wedge((0, 4.3, z0 + 0.2), (0, 3.4, BUTT_Z - 1.5), 1.6, 1.4, P.FURNITURE)            # belly, deeper to the rear
    st.box((-1.0, 2.25, BUTT_Z - 1.5), (2.0, 4.4, 0.5), P.MATTE_BLACK)                    # butt plate
    st.box((-1.0, 2.25, BUTT_Z - 1.0), (2.0, 4.4, 1.0), "rubber", pattern="lines")        # recoil pad
    st.box((-1.0, 5.0, z0 + 1.0), (0.15, 0.6, 0.5), "steel")                              # sling loop (left)


def _sights(m: Model) -> None:
    sg.aperture_sight(m.bone("rear_sight", pivot=(0, RAIL_Y, sg.sight_z(REAR_SIGHT_Z0))),
                      RAIL_Y, SIGHT_Y, REAR_SIGHT_Z0, "black_metal")
    sg.post_sight(m.bone("front_sight", pivot=(0, RAIL_Y, FRONT_SIGHT_Z0 + 0.5)), RAIL_Y, SIGHT_Y,
                  FRONT_SIGHT_Z0, "black_metal", wings=True)
