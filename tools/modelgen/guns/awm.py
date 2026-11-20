"""Accuracy International AWM (Arctic Warfare Magnum, L115A1) - .338 Lapua Magnum bolt action.

Silhouette: the olive-green AI thumbhole stock whose two side panels run from the folding butt
right up to the fore-end tip, a black rubber cheek piece on top of the comb, butt spacers and a
spike under the butt, a flat-sided AI action, a long FLUTED barrel free-floating over the
fore-end with a two-port muzzle brake, a stubby 5-round steel magazine and the bipod spigot at
the very tip of the fore-end. 1200 mm -> 36 units (butt z 11, brake tip z -25).
Scale / axes as the M4 (see IN-GAME TUNING NOTES in m4.py): 1 unit ~ 3.35 cm, muzzle -Z, +X
right, bore y 5.5. Shared bolt-rifle code (trigger, grip, arms, mounts, attachments, locators,
previews) lives in bolt_rifle.py; this file only changes the bolt bone's NAME (`bolt`) and the
animation lengths (fire 1.3 s, reload 2.6 s, reload_empty 3.2 s = the M2010 timings).
"""
import bolt_rifle as BR
import templates as T
from armorygen import Bone, Model

BORE_Y = T.BORE_Y
BARREL_END_Z = -23.5                    # threads; the factory brake (muzzle_default) is in front
BRAKE_LEN = 1.5
BOLT = "bolt"                           # bolt body + handle + knob in one bone
FIRE_S, RELOAD_S, RELOAD_EMPTY_S = 1.3, 2.6, 3.2


class AwmRifle(BR.BoltRifle):
    """BoltRifle whose bolt bone is called `bolt` and whose clips have the lengths above."""

    def add_bolt(self, m: Model, body: str = "bright_steel", handle: str = "black_metal") -> Bone:
        px, py, pz = self.bolt_pivot
        b = m.bone(BOLT, pivot=self.bolt_pivot)
        b.box((px - 0.5, py - 0.5, self.bolt_front_z), (1, 1, pz + 0.75 - self.bolt_front_z), body)
        b.box((px - 0.5, py - 0.5, pz + 0.25), (1, 1, 0.5), handle, inflate=0.05)      # bolt shroud
        b.box((px + 0.5, py - 0.25, pz - 0.25), (1.5, 0.5, 0.5), handle)               # handle arm
        b.box((px + 2, py - 0.5, pz - 0.5), (1, 1, 1), handle, pattern="knurl")        # big AI knob
        return b

    def animate(self, m: Model) -> None:
        T.idle(m)
        T.fire_bolt(m, bolt_bone=BOLT, length=FIRE_S, lift_deg=self.bolt_lift, pull=self.bolt_pull,
                    recoil=self.recoil, rise=self.rise, knob=self.knob)
        T.reload_mag(m, length=RELOAD_S)
        T.reload_mag(m, length=RELOAD_EMPTY_S, empty=True, bolt_action=True, bolt_bone=BOLT,
                     lift_deg=self.bolt_lift, pull=self.bolt_pull, knob=self.knob)


RIFLE = AwmRifle(
    muzzle_z=BARREL_END_Z, muzzle_device_len=BRAKE_LEN, suppressor="suppressor_338",
    left_hand=(0, 4, -11),              # under the fore-end, behind the bipod spigot
    underbarrel=(0, 3.5, -16.5),        # bottom face of the bipod spigot block at the fore-end tip
    underbarrel_ids=("bipod",),         # ...which only takes the bipod
    side=None,                          # no side rail on an AW stock
    recoil=1.8, rise=4.5,               # .338 Lapua
)
SIGHT_Y = RIFLE.sight_y                 # 7.5: axis of the fitted scope (no iron sights)
EYE_Z = RIFLE.eye_z
MAG_PIVOT = (0, 4, -3.5)                # top centre of the magazine well


def model() -> Model:
    m = Model("awm", density=1)
    m.material("aw_green", (98, 110, 66), 7, 0.80)        # olive-green polymer stock sides
    m.material("aw_green_dark", (78, 88, 54), 6, 0.78)   # panel accents / grooves
    m.material("aw_black", (38, 38, 42), 5, 0.74)        # aluminium chassis, action, rail
    m.material("barrel_black", (44, 44, 49), 5, 0.74)
    m.material("flute", (24, 24, 27), 3, 0.85)           # the flutes cut into the barrel
    m.material("mag_steel", (56, 57, 62), 6, 0.72)

    # ---- receiver: flat-sided AI action bolted into the chassis -------------------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 4, -6.5), (2, 1, 10), "aw_green")                      # stock sides under the action
    rc.box((-1, 5, -6.5), (1, 1, 10), "aw_black")                      # action, left half
    rc.box((0, 5, -6.5), (1, 1, 2.5), "aw_black")                      # right: front ring
    rc.box((0, 5, -1.5), (1, 1, 5), "aw_black")                        # right: rear bridge
    rc.box((-1, 6, -6.5), (2, 0.5, 8.5), "aw_black", pattern="rails")  # scope rail
    rc.box((-1, 3, -5.75), (2, 1, 4.5), "aw_green")                    # magazine well
    rc.box((-0.5, 2, -1.25), (1, 0.5, 2.75), "aw_black")               # trigger guard bow
    rc.box((-0.5, 2.5, -1.25), (1, 0.5, 0.5), "aw_black")              # guard front / mag catch
    rc.box((-1.1, 4.25, -6), (2.2, 0.5, 0.5), "aw_black")              # stock bolt heads
    rc.box((-1.1, 4.25, 1.5), (2.2, 0.5, 0.5), "aw_black")
    rc.box((0.9, 5.25, 2), (0.35, 0.5, 1), "aw_black", pattern="knurl")   # 3-position safety
    RIFLE.add_bolt(m, handle="aw_black")
    RIFLE.add_trigger(m)

    # ---- barrel: long, fluted, free-floating; brake = muzzle_default --------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -6.5))
    ba.box((-0.5, 5, BARREL_END_Z), (1, 1, 17), "barrel_black", inflate=0.15)
    ba.box((-0.5, 5, -7.5), (1, 1, 1), "aw_black", inflate=0.3)        # barrel shank
    ba.box((-0.7, 5.3, -22), (1.4, 0.4, 5.25), "flute")                # side flutes (in front of the fore-end)
    ba.box((-0.2, 5, -22), (0.4, 1, 5.25), "flute", inflate=0.2)       # top / bottom flutes
    md = T.default_part(m, "muzzle", RIFLE.muzzle_mount)
    md.box((-0.5, 5, -25), (1, 1, BRAKE_LEN), "aw_black", inflate=0.3)  # brake body
    md.box((-0.9, 5.2, -24.75), (1.8, 0.6, 0.4), "flute")              # two side ports
    md.box((-0.9, 5.2, -24.1), (1.8, 0.6, 0.4), "flute")

    # ---- fore-end: the green side panels go on to the tip, barrel in an open channel ----
    hg = m.bone("handguard", pivot=(0, BORE_Y, -6.5))
    hg.box((-1, 4, -16.5), (2, 1.5, 10), "aw_green")                   # side panels
    hg.box((-1, 3.75, -16.5), (2, 0.25, 10), "aw_green_dark")          # flat belly
    hg.box((-1.1, 4.4, -12.5), (2.2, 0.6, 4), "aw_green_dark", pattern="lines")   # grip grooves
    hg.box((-1, 4, -17), (2, 1.5, 0.5), "aw_black")                    # fore-end tip
    hg.box((-0.5, 3.5, -17.5), (1, 0.75, 1.75), "aw_black")            # bipod spigot block
    hg.box((-1.25, 4.5, -15.75), (2.5, 0.5, 0.5), "aw_black")          # sling loops

    # ---- grip: part of the thumbhole stock -------------------------------------------------
    RIFLE.add_ar_grip(m, mat="aw_green")

    # ---- stock: side-folding thumbhole butt -------------------------------------------------
    st = m.bone("stock", pivot=(1, 4, 3.5))
    st.box((-1, 3, 3.5), (2, 2, 0.5), "aw_black")                      # folding hinge
    st.box((-1, 3.5, 4), (2, 1.5, 6), "aw_green")                      # comb beam (the bolt runs over it)
    st.box((-1, 5, 6.5), (2, 1.5, 3.5), "rubber", pattern="lines")     # adjustable cheek piece
    st.box((1, 4.25, 7.25), (0.25, 0.75, 0.75), "aw_black", pattern="knurl")      # cheek piece lock
    st.box((1, 4.25, 8.75), (0.25, 0.75, 0.75), "aw_black", pattern="knurl")
    st.box((-1, 0.5, 4.5), (2, 1, 5.5), "aw_green")                    # lower rail: closes the thumbhole
    st.box((-1, 1.5, 7.5), (2, 2, 2.5), "aw_green")                    # rear web of the thumbhole
    st.box((-0.5, -0.5, 8), (1, 1, 1), "aw_black", pattern="knurl")    # butt spike (monopod)
    st.box((-1, 0.5, 10), (2, 6, 0.5), "aw_black", pattern="lines")    # length-of-pull spacers
    st.box((-1, 0.5, 10.5), (2, 6, 0.5), "rubber")                     # butt pad

    # ---- magazine: 5-round single-stack steel box ---------------------------------------
    mg = m.bone("magazine", pivot=MAG_PIVOT)
    mg.box((-0.75, 1.75, -5.25), (1.5, 2.25, 3.5), "mag_steel", pattern="lines")
    mg.box((-1, 1.25, -5.5), (2, 0.5, 4), "aw_black")                  # floor plate

    return RIFLE.finish(m)
