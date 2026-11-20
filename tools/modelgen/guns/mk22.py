"""Mk 22 (Barrett MRAD) - .338 Norma Magnum bolt action.

Silhouette: tall, angular, slab-sided upper receiver flowing into a long slim handguard under
one continuous top rail, black polymer lower with a black AR grip, fluted barrel with a big
multi-port brake, boxy folding stock, straight 10-round magazine.
1255 mm -> 37.5 units (butt z 11, muzzle z -26.5). Scale / axes as the M4 (see IN-GAME TUNING
NOTES in m4.py); shared bolt-rifle code lives in bolt_rifle.py.
"""
import bolt_rifle as BR
import templates as T
from armorygen import Model

BORE_Y = T.BORE_Y
BARREL_END_Z = -24.5                    # the Barrett brake (muzzle_default) sits in front of it
HG_FRONT_Z, HG_REAR_Z = -20.0, -6.0
RIFLE = BR.BoltRifle(
    muzzle_z=BARREL_END_Z, muzzle_device_len=2.0, suppressor="suppressor_338",
    left_hand=(0, 4.5, -12),            # under the handguard, behind the 6 o'clock rail block
    underbarrel=(0, 4, -17.5),          # bottom face of the 6 o'clock Picatinny block
    side=(1.25, BORE_Y, -18),           # outer face of the 3 o'clock Picatinny block
    eject=(1.1, BORE_Y, -2.25),
    trigger_pin=(0, 3.5, -0.5),
)
SIGHT_Y = RIFLE.sight_y                 # 7.5: axis of the issued scope (no iron sights)
EYE_Z = RIFLE.eye_z
MAG_PIVOT = (0, 3.5, -3.75)             # top centre of the magazine well


def model() -> Model:
    m = Model("mk22", density=1)
    m.material("coyote", (138, 112, 80), 6, 0.80)        # coyote-brown Cerakote upper / handguard
    m.material("coyote_dark", (112, 90, 64), 6, 0.78)    # rails and accents
    m.material("lower_black", (42, 42, 45), 4, 0.80)     # polymer lower, grip
    m.material("barrel_coyote", (120, 98, 70), 6, 0.74)  # fluted barrel, also Cerakoted

    # ---- receiver: slab-sided upper on a polymer lower --------------------------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4.5, -6), (1, 1.5, 9.5), "coyote")                     # upper, left half
    rc.box((0, 4.5, -6), (1, 1.5, 2.5), "coyote")                      # right: in front of the port
    rc.box((0, 4.5, -1), (1, 1.5, 4.5), "coyote")                      # right: behind the port
    rc.box((0, 4.5, -3.5), (1, 0.5, 2.5), "coyote")                    # sill under the ejection port
    rc.box((-1, 6, -6), (2, 0.5, 9.5), "coyote_dark", pattern="rails")  # top rail (receiver part)
    rc.box((-1, 3.5, -6), (2, 1, 9.5), "lower_black")                  # polymer lower
    rc.box((-1, 2.5, -6), (2, 1, 4.5), "lower_black")                  # magazine well
    rc.box((-0.5, 2, -1.5), (1, 0.5, 3), "lower_black")                # trigger guard bow
    rc.box((-0.5, 2.5, 1), (1, 1, 0.5), "lower_black")                 # guard rear
    rc.box((-1.1, 3.75, 0.25), (2.2, 0.5, 0.75), "coyote_dark")        # ambidextrous safety
    rc.box((-1.1, 4.75, -5.5), (2.2, 0.5, 1.5), "coyote_dark")         # barrel clamp bolts
    RIFLE.add_bolt(m)
    RIFLE.add_trigger(m, mat="black_metal")

    # ---- barrel: fluted, big multi-port brake (muzzle_default) -------------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -6))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, 18.5), "barrel_coyote", pattern="lines", inflate=0.15)
    md = T.default_part(m, "muzzle", RIFLE.muzzle_mount)
    md.box((-0.75, 4.75, -26.5), (1.5, 1.5, 2), "black_metal")         # brake body
    md.box((-1.1, 5, -26.25), (2.2, 1, 0.5), "gunmetal")               # baffle wings
    md.box((-1.1, 5, -25.25), (2.2, 1, 0.5), "gunmetal")

    # ---- handguard: long, slim, M-LOK, Picatinny blocks at 3 / 6 / 9 --------------------
    hg = m.bone("handguard", pivot=(0, BORE_Y, HG_REAR_Z))
    length = HG_REAR_Z - HG_FRONT_Z
    hg.box((-1, 4.5, HG_FRONT_Z), (2, 1.5, length), "coyote", pattern="slots", inflate=-0.05)
    hg.box((-1, 6, HG_FRONT_Z), (2, 0.5, length), "coyote_dark", pattern="rails")  # 10 MIL top rail
    hg.box((-1.25, 5.25, -19.5), (2.5, 0.5, 3), "coyote_dark", pattern="rails")    # 3 / 9 o'clock
    hg.box((-0.5, 4, -19.5), (1, 0.5, 4), "coyote_dark", pattern="rails")          # 6 o'clock

    # ---- grip: black AR pattern -----------------------------------------------------------
    RIFLE.add_ar_grip(m, mat="lower_black")

    # ---- stock: right-folding, boxy ------------------------------------------------------
    st = m.bone("stock", pivot=(1, 4, 3.5))
    st.box((-1, 3, 3.5), (2, 2, 0.5), "coyote_dark")                   # folding hinge
    st.box((-1, 3.5, 4), (2, 1.5, 5.5), "coyote")                      # upper beam (bolt runs over it)
    st.box((-1, 5, 6.5), (2, 2, 3), "lower_black", pattern="lines")    # adjustable cheek rest
    st.box((-1, 2, 4), (2, 1.5, 1), "coyote")                          # front web
    st.box((-1, 2, 5), (2, 0.5, 4.5), "coyote")                        # lower beam -> window in the stock
    st.box((-1, 1.5, 9.5), (2, 5, 1), "coyote")                        # butt frame
    st.box((-0.5, 1.5, 7), (1, 0.5, 2.5), "coyote_dark", pattern="rails")  # monopod rail
    st.box((1, 4, 8.5), (0.25, 0.75, 0.75), "lower_black")             # length-of-pull button
    st.box((-1, 1.5, 10.5), (2, 5, 0.5), "rubber")                     # butt pad

    # ---- magazine: straight 10-round polymer double stack ---------------------------------
    mg = m.bone("magazine", pivot=MAG_PIVOT)
    mg.box((-0.75, 0.5, -5.5), (1.5, 3, 3.5), "lower_black", pattern="lines")
    mg.box((-1, 0, -5.75), (2, 0.5, 4), "lower_black")                 # floor plate

    return RIFLE.finish(m)
