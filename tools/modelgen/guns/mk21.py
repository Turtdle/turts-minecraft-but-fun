"""Mk 21 Precision Sniper Rifle (Remington MSR) - .338 Lapua Magnum bolt action.

Silhouette: long ROUND tubular free-float handguard under a monolithic top rail, squat AAC
brake, AR grip, skeletal folding stock on a thin tube, long straight 10-round magazine.
1295 mm -> 38.5 units (butt z 11.5, muzzle z -27). Scale / axes as the M4 (see IN-GAME TUNING
NOTES in m4.py); shared bolt-rifle code lives in bolt_rifle.py.
"""
import bolt_rifle as BR
import templates as T
from armorygen import Model

BORE_Y = T.BORE_Y
BARREL_END_Z = -25.0                    # the AAC brake (muzzle_default) sits in front of it
HG_FRONT_Z, HG_REAR_Z = -19.0, -6.0
RIFLE = BR.BoltRifle(
    muzzle_z=BARREL_END_Z, muzzle_device_len=2.0, suppressor="suppressor_338",
    left_hand=(0, 4.5, -11),            # under the tube, behind the 6 o'clock rail
    underbarrel=(0, 4, -16),            # bottom face of the 6 o'clock rail section
    side=(1.4, BORE_Y, -16.5),          # outer face of the 3 o'clock rail section
)
SIGHT_Y = RIFLE.sight_y                 # 7.5: axis of the issued scope (no iron sights)
EYE_Z = RIFLE.eye_z
MAG_PIVOT = (0, 4, -3.75)               # top centre of the magazine well


def model() -> Model:
    m = Model("mk21", density=1)
    m.material("msr_fde", (170, 144, 106), 7, 0.80)      # coyote / FDE chassis and handguard
    m.material("msr_dark", (140, 117, 84), 6, 0.78)      # rails and accents
    m.material("barrel_black", (40, 40, 45), 5, 0.74)
    m.material("mag_fde", (150, 128, 96), 6, 0.76)

    # ---- receiver: titanium action in the one-piece chassis --------------------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4, -6), (2, 1, 9.5), "msr_fde")                        # chassis under the action
    rc.box((-1, 5, -6), (1, 1, 9), "msr_dark")                         # action, left half
    rc.box((0, 5, -6), (1, 1, 2), "msr_dark")                          # right: front ring
    rc.box((0, 5, -1.5), (1, 1, 4.5), "msr_dark")                      # right: rear bridge
    rc.box((-1, 6, -6), (2, 0.5, 8), "msr_dark", pattern="rails")      # receiver part of the top rail
    rc.box((-1, 3, -6), (2, 1, 4.5), "msr_fde")                        # magazine well
    rc.box((-1, 2.5, -6), (2, 0.5, 1), "msr_fde")                      # well front lip
    rc.box((-0.5, 2, -1.5), (1, 0.5, 3), "msr_dark")                   # trigger guard bow
    rc.box((-0.5, 2.5, -1.5), (1, 0.5, 0.5), "black_metal")            # paddle mag release
    rc.box((0.75, 4.25, -0.75), (0.5, 0.5, 1), "black_metal")          # safety
    RIFLE.add_bolt(m)
    RIFLE.add_trigger(m)

    # ---- barrel + squat AAC brake (muzzle_default) ------------------------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -6))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, 19), "barrel_black", inflate=0.1)
    md = T.default_part(m, "muzzle", RIFLE.muzzle_mount)
    md.box((-0.75, 4.75, -27), (1.5, 1.5, 2), "barrel_black")          # brake body
    md.box((-0.95, 5.25, -26.6), (1.9, 0.5, 0.5), "black_metal")       # ports
    md.box((-0.95, 5.25, -25.8), (1.9, 0.5, 0.5), "black_metal")
    md.box((-0.5, 5, -25), (1, 1, 0.25), "barrel_black", inflate=0.3)  # QD ratchet collar

    # ---- handguard: round tube, monolithic top rail, rail sections at 3 / 6 / 9 --------
    hg = m.bone("handguard", pivot=(0, BORE_Y, HG_REAR_Z))
    length = HG_REAR_Z - HG_FRONT_Z
    hg.cyl((0, BORE_Y, (HG_FRONT_Z + HG_REAR_Z) / 2), 2.25, length, "msr_fde", sides=8,
           pattern="slots")
    hg.box((-1, 6, HG_FRONT_Z), (2, 0.5, length), "msr_dark", pattern="rails")   # top rail
    hg.box((-1.4, 5.25, -18.5), (2.8, 0.5, 4), "msr_dark", pattern="rails")      # 3 / 9 o'clock
    hg.box((-0.5, 4, -18.5), (1, 0.5, 5), "msr_dark", pattern="rails")           # 6 o'clock
    hg.box((-1, 4.5, HG_REAR_Z - 0.5), (2, 1.5, 0.5), "msr_dark", inflate=0.15)  # barrel nut collar

    # ---- grip: AR pattern ---------------------------------------------------------------
    RIFLE.add_ar_grip(m, mat="polymer")

    # ---- stock: skeletal right-folding buttstock ----------------------------------------
    st = m.bone("stock", pivot=(1, 4, 3.5))
    st.box((-1, 3, 3.5), (2, 2, 0.75), "msr_dark")                     # folding hinge
    st.box((-0.5, 4, 4.25), (1, 1, 5.25), "barrel_black")              # thin stock tube
    st.box((-1, 5, 6.5), (2, 1.75, 3), "msr_fde", pattern="lines")     # cheek riser
    st.box((1, 4.25, 6), (0.25, 0.75, 0.75), "black_metal", pattern="knurl")     # throw lever
    st.wedge((0, 3.5, 4.5), (0, 1.75, 9.5), 1, 0.75, "msr_fde")        # lower strut
    st.box((-1, 1, 9.5), (2, 5.5, 1), "msr_fde")                       # butt frame
    st.box((-0.5, 0.25, 8.5), (1, 0.75, 2), "msr_dark")                # rear hook / monopod mount
    st.box((-1, 1, 10.5), (2, 5.5, 1), "rubber")                       # butt pad

    # ---- magazine: straight 10-round double stack ---------------------------------------
    mg = m.bone("magazine", pivot=MAG_PIVOT)
    mg.box((-0.75, 0.5, -5.5), (1.5, 3.5, 3.5), "mag_fde", pattern="lines")
    mg.box((-1, 0, -5.75), (2, 0.5, 4), "msr_dark")                    # floor plate

    return RIFLE.finish(m)
