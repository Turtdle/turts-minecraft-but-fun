"""Mk 13 Mod 7 - .300 Win Mag bolt action in an Accuracy International AICS 2.0 chassis.

Silhouette: thumbhole folding stock with a tall cheekpiece, short fore-end with the long plain
heavy barrel lying in an open channel, stubby 5-round AICS magazine. 1207 mm -> 36 units
(butt z 11, muzzle z -25). Scale / axes as the M4 (see IN-GAME TUNING NOTES in m4.py); shared
bolt-rifle code (bolt, arms, mounts, attachments, animations) lives in bolt_rifle.py.
"""
import bolt_rifle as BR
import templates as T
from armorygen import Model

BORE_Y = T.BORE_Y
BARREL_END_Z = -24.0                    # threads; the factory brake sits in front of it
RIFLE = BR.BoltRifle(
    muzzle_z=BARREL_END_Z, muzzle_device_len=1.0, suppressor="suppressor_300",
    left_hand=(0, 4, -10),              # under the fore-end, behind the bipod stud
    underbarrel=(0, 3.5, -13.5),        # short accessory rail under the fore-end tip
    underbarrel_ids=("bipod",),         # ...which only takes the bipod (no grips on an AICS)
    side=(1.25, 4.75, -13),             # short side rail on the fore-end
)
SIGHT_Y = RIFLE.sight_y                 # 7.5: axis of the issued scope (no iron sights)
EYE_Z = RIFLE.eye_z
MAG_PIVOT = (0, 4, -3.5)                # top centre of the magazine well


def model() -> Model:
    m = Model("mk13", density=1)
    m.material("panel", (156, 134, 98), 6, 0.80)        # AICS polymer side panels, dark earth
    m.material("panel_dark", (132, 112, 80), 6, 0.78)   # cheekpiece / panel accents
    m.material("skeleton", (40, 40, 44), 5, 0.74)       # black aluminium chassis skeleton
    m.material("action", (46, 46, 51), 5, 0.74)         # Stiller / Rem 700 long action
    m.material("barrel_black", (38, 38, 43), 5, 0.74)
    m.material("mag_steel", (54, 55, 60), 6, 0.72)      # AICS magazines are black steel

    # ---- receiver: round action bedded in the chassis --------------------------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4, -6), (2, 1, 9.5), "panel")                         # chassis under the action
    rc.box((-1, 5, -6), (1, 1, 9), "action")                          # action, left half
    rc.box((0, 5, -6), (1, 1, 2), "action")                           # right: front ring
    rc.box((0, 5, -1.5), (1, 1, 4.5), "action")                       # right: rear bridge
    rc.box((-1, 6, -6.5), (2, 0.5, 8), "action", pattern="rails")     # 20 MOA scope rail
    rc.box((-1, 3, -5.5), (2, 1, 4), "skeleton")                      # magazine well
    rc.box((-0.5, 2, -1.5), (1, 0.5, 3), "skeleton")                  # trigger guard bow
    rc.box((-0.5, 2.5, -1.5), (1, 0.5, 0.5), "skeleton")              # guard front / mag catch
    rc.box((0.75, 4.25, -1), (0.5, 0.5, 1), "skeleton")               # safety lever
    RIFLE.add_bolt(m, handle="action")
    RIFLE.add_trigger(m)

    # ---- barrel: long, plain, heavy; brake = muzzle_default ---------------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -6))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, 18), "barrel_black", inflate=0.15)
    ba.box((-0.5, 5, -7), (1, 1, 1), "action", inflate=0.3)           # barrel shank / recoil lug
    md = T.default_part(m, "muzzle", RIFLE.muzzle_mount)
    md.box((-0.5, 5, -25), (1, 1, 1), "action", inflate=0.3)          # brake / suppressor mount
    md.box((-0.9, 5.25, -24.75), (1.8, 0.5, 0.5), "black_metal")      # brake port

    # ---- fore-end: short, the barrel lies in an open channel --------------------------
    hg = m.bone("handguard", pivot=(0, BORE_Y, -6))
    hg.box((-1, 4, -15), (2, 1.5, 9), "panel")                        # side panels
    hg.box((-1, 3.75, -15), (2, 0.25, 9), "skeleton")                 # skeleton showing below
    hg.box((-1, 4, -15.5), (2, 1.5, 0.5), "skeleton")                 # fore-end tip
    hg.box((-1.25, 4.5, -14.5), (2.5, 0.5, 3), "skeleton", pattern="rails")   # short side rails
    hg.box((-0.5, 3.5, -14.75), (1, 0.25, 2.5), "skeleton", pattern="rails")  # bipod rail
    hg.box((-1.1, 4.5, -9.5), (2.2, 0.5, 2), "panel_dark", pattern="lines")   # grip grooves

    # ---- grip: part of the thumbhole stock (does not fold) -----------------------------
    RIFLE.add_ar_grip(m, mat="panel")

    # ---- stock: side-folding AICS thumbhole ---------------------------------------------
    st = m.bone("stock", pivot=(1, 4, 3.5))
    st.box((-1, 3, 3.5), (2, 2, 0.5), "skeleton")                     # folding hinge
    st.box((-1, 3.5, 4), (2, 1.5, 6), "panel")                        # comb beam (bolt runs over it)
    st.box((-1, 5, 6.5), (2, 2, 3.5), "panel_dark", pattern="lines")  # tall adjustable cheekpiece
    st.box((1, 5.25, 7.5), (0.25, 0.75, 0.75), "skeleton", pattern="knurl")   # cheek lock knob
    st.box((-1, 0.5, 4.5), (2, 1, 5.5), "panel")                      # lower rail: closes the thumbhole
    st.box((-1, 1.5, 8), (2, 2, 2), "panel")                          # rear web of the thumbhole
    st.box((-0.5, 0, 7.5), (1, 0.5, 2), "skeleton", pattern="rails")  # monopod rail
    st.box((-1, 0.5, 10), (2, 5, 0.5), "skeleton", pattern="lines")   # length-of-pull spacers
    st.box((-1, 0.5, 10.5), (2, 5, 0.5), "rubber")                    # thick butt pad

    # ---- magazine: 5-round AICS double stack --------------------------------------------
    mg = m.bone("magazine", pivot=MAG_PIVOT)
    mg.box((-0.75, 2, -5), (1.5, 2, 3), "mag_steel", pattern="lines")
    mg.box((-1, 1.5, -5.25), (2, 0.5, 3.5), "skeleton")               # floor plate

    return RIFLE.finish(m)
