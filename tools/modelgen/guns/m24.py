"""M24 Sniper Weapon System (Remington 700 long action) - 7.62x51 mm bolt action.

Silhouette: a CONVENTIONAL one-piece H-S Precision synthetic stock (dark green, no pistol grip:
the hand wraps the wrist) with a straight comb, the adjustable butt plate on its knurled wheel,
a round Remington 700 action on two-piece scope bases, a long plain HEAVY barrel with no muzzle
device, and nothing hanging under the action but the flush hinged floor plate and the trigger
guard: the 5-round magazine is INTERNAL and is loaded through the open action from the top.
1092 mm -> 32.5 units (butt z 10.5, muzzle z -22). Scale / axes as the M4 (see IN-GAME TUNING
NOTES in m4.py): 1 unit ~ 3.35 cm, muzzle -Z, +X right, bore y 5.5.

Shared bolt-rifle code (trigger, arms, mounts, attachments, locators, previews) is
bolt_rifle.py. What is specific to this rifle and lives HERE:
  * the bolt bone is called `bolt` (body + handle + knob, pivot on the bolt axis)
  * `round`: one cartridge, at rest hidden in the internal magazine under the bolt
  * `reload` / `reload_empty` (TopLoad below): the right hand opens the bolt and leaves it
    open, thumbs N cartridges one at a time down through the ejection port, then closes the
    bolt. One clip per reload (the game treats it like a magazine swap): 3 rounds for a
    top-up (`reload`), 5 from empty (`reload_empty`).
"""
import math
from dataclasses import dataclass

import bolt_rifle as BR
import templates as T
from armorygen import Bone, Model

BORE_Y = T.BORE_Y
MUZZLE_Z = -22.0                        # plain crowned muzzle, no brake
BUTT_Z = 10.5
BOLT = "bolt"
ROUND = "round"
PORT_Z = (-4.5, -2.5)                   # ejection / loading port in the right wall of the action
ROUND_REST = (0.35, 4.4, -3.5)          # centre of `round` at rest: in the magazine, under the bolt
FIRE_S = 1.3


@dataclass(frozen=True)
class TopLoad:
    """`reload` / `reload_empty` for an internal magazine loaded through the open action.

    Timeline (seconds; `open_s` and `close_s` are fixed, each round takes `per_round`):
      0 .. open_s                 right hand leaves the wrist, lifts the bolt handle, pulls it back
      open_s + i * per_round      round i: hand drops out of view, comes back with a cartridge
                                  over the port (+0.30), thumbs it down into the magazine (+0.38)
      end of the last round       hand back on the knob, bolt forward, handle down, hand home
    The gun is rolled so the port faces the shooter for the whole clip."""
    rounds: int
    per_round: float = 0.42
    open_s: float = 0.50
    close_s: float = 0.74
    lift: float = 70.0
    pull: float = 3.0

    @property
    def length(self) -> float:
        return round(self.open_s + self.rounds * self.per_round + self.close_s, 4)

    def times(self) -> dict:
        """Key times (seconds) by name: what the sounds are timed from."""
        e = self.open_s + self.rounds * self.per_round
        t = {"bolt_grab": 0.20, "bolt_up": 0.32, "bolt_back": 0.48}
        for i in range(self.rounds):
            t[f"round{i + 1}_in"] = round(self.open_s + i * self.per_round + 0.38, 4)
        t.update(bolt_regrab=round(e + 0.14, 4), bolt_forward=round(e + 0.28, 4),
                 bolt_down=round(e + 0.40, 4), hand_home=round(e + 0.64, 4), end=self.length)
        return t

    def write(self, m: Model, name: str, knob: T.Vec):
        c, rig = T.Clip(m, name, self.length, self.length), T.Rig.of(m)
        c.need(BOLT, T.RIGHT_ARM)
        IO, OUT, IN, Z = T.IO, T.OUT, T.IN, T.ZERO
        home, piv = rig.right_hand, m.bones[BOLT].pivot
        radius = math.hypot(knob[0] - piv[0], knob[1] - piv[1])
        L, pull, e = self.lift, self.pull, self.open_s + self.rounds * self.per_round
        tm = self.times()

        def hand(angle: float, back: float = 0.0) -> T.Vec:   # fist on the knob (as HandCycle)
            r = math.radians(angle)
            kx, ky = piv[0] + radius * math.cos(r), piv[1] + radius * math.sin(r)
            ox, oy = 0.9 * math.cos(r) + 0.3 * math.sin(r), -0.3 * math.cos(r) - 0.2 * math.sin(r)
            return (kx + ox - home[0], ky + oy - home[1], knob[2] - home[2] + back)

        over = T._sub((2.9, 6.0, -2.4), home)       # fist right of / above the port, cartridge in it
        seat = T._add(over, (-0.7, -0.5, 0))        # thumb has pushed the cartridge down
        away = T._add(over, (2.5, -7.0, 3.5))       # down to the right, out of view (the pouch)
        r_over = (0.95, 1.9, 0.0)                   # where `round` is (from rest) with the fist at `over`
        r_away = T._add(r_over, T._sub(away, over))

        arm_p = {0: Z, tm["bolt_grab"]: (hand(0), IO), 0.26: hand(L / 2), tm["bolt_up"]: (hand(L), OUT),
                 tm["bolt_back"]: (hand(L, pull), OUT), self.open_s: hand(L, pull)}
        rnd = {0: Z}
        for i in range(self.rounds):
            s = self.open_s + i * self.per_round
            arm_p.update({s + 0.13: (away, IN), s + 0.17: away, s + 0.30: (over, OUT),
                          s + 0.38: (seat, IO), s + 0.42: seat})
            rnd.update({s + 0.15: (r_away, "step"), s + 0.17: r_away, s + 0.30: (r_over, OUT),
                        s + 0.38: (Z, IO)})
        arm_p.update({tm["bolt_regrab"]: (hand(L, pull), IO), tm["bolt_forward"]: (hand(L), IN),
                      e + 0.34: hand(L / 2), tm["bolt_down"]: (hand(0), OUT), tm["hand_home"]: (Z, IO)})
        c.pos(T.RIGHT_ARM, arm_p)
        c.rot(T.RIGHT_ARM, {0: Z, tm["bolt_grab"]: ((6, 0, 0), IO), tm["bolt_up"]: (10, 0, 0),
                            tm["bolt_down"]: (6, 0, 0), tm["hand_home"]: (Z, IO)})
        c.pos(ROUND, rnd)
        c.rot(BOLT, {0: Z, tm["bolt_grab"]: Z, tm["bolt_up"]: ((0, 0, L), OUT),
                     tm["bolt_forward"]: (0, 0, L), tm["bolt_down"]: (Z, OUT)})
        c.pos(BOLT, {0: Z, tm["bolt_up"]: Z, tm["bolt_back"]: ((0, 0, pull), OUT),
                     tm["bolt_regrab"]: (0, 0, pull), tm["bolt_forward"]: (Z, IN)})
        tilt, lift = T._pitch_roll(4, -12), (0, 0.4, 0.3)      # port rolled toward the shooter
        c.rot(T.ROOT, {0: Z, 0.30: (tilt, IO), tm["bolt_regrab"]: tilt,
                       tm["bolt_forward"]: (T._pitch_roll(3, -9), IN), tm["bolt_down"]: (T._pitch_roll(1, 2), OUT),
                       self.length: (Z, IO)})
        c.pos(T.ROOT, {0: Z, 0.30: (lift, IO), tm["bolt_regrab"]: lift,
                       tm["bolt_forward"]: ((0, 0.3, -0.2), IN), self.length: (Z, IO)})
        return c.done()


RELOAD = TopLoad(rounds=3)              # 2.50 s
RELOAD_EMPTY = TopLoad(rounds=5)        # 3.34 s


class M24Rifle(BR.BoltRifle):
    """BoltRifle with a bolt bone called `bolt` and the top-loading reloads above."""

    def add_bolt(self, m: Model, body: str = "bright_steel", handle: str = "black_metal") -> Bone:
        px, py, pz = self.bolt_pivot
        b = m.bone(BOLT, pivot=self.bolt_pivot)
        b.box((px - 0.5, py - 0.5, self.bolt_front_z), (1, 1, pz + 0.75 - self.bolt_front_z), body)
        b.box((px - 0.5, py - 0.5, pz + 0.25), (1, 1, 0.5), handle, inflate=0.05)      # bolt shroud
        b.box((px + 0.5, py - 0.25, pz - 0.25), (1.5, 0.5, 0.5), handle)               # handle arm
        b.box((px + 2, py - 0.4, pz - 0.4), (0.8, 0.8, 0.8), handle, pattern="knurl")  # round knob
        return b

    def animate(self, m: Model) -> None:
        T.idle(m)
        T.fire_bolt(m, bolt_bone=BOLT, length=FIRE_S, lift_deg=self.bolt_lift, pull=self.bolt_pull,
                    recoil=self.recoil, rise=self.rise, knob=self.knob)
        RELOAD.write(m, "reload", self.knob)
        RELOAD_EMPTY.write(m, "reload_empty", self.knob)


RIFLE = M24Rifle(
    muzzle_z=MUZZLE_Z, muzzle_device_len=0.0, suppressor="suppressor_762",
    right_hand=(0, 3.0, 3.25),          # wrist of the stock (no pistol grip)
    left_hand=(0, 3.75, -10.5),         # under the fore-end, behind the bipod stud
    underbarrel=(0, 3.5, -14),          # bottom face of the bipod stud plate under the fore-end tip
    underbarrel_ids=("bipod",),
    side=None,
    bolt_front_z=-4.5,
    eject=(1.1, BORE_Y, sum(PORT_Z) / 2),
    trigger_pin=(0, 3.75, -0.5),
    recoil=1.4, rise=3.5,
)
SIGHT_Y = RIFLE.sight_y                 # 7.5: axis of the fitted 10x scope (no iron sights)
EYE_Z = RIFLE.eye_z


def model() -> Model:
    m = Model("m24", density=1)
    m.material("hs_green", (62, 74, 58), 9, 0.80)        # H-S Precision stock: dark green...
    m.material("hs_web", (40, 46, 40), 8, 0.78)          # ...with the black web / grip panels
    m.material("action", (44, 44, 49), 5, 0.74)          # parkerised Remington 700 action
    m.material("barrel_black", (48, 48, 53), 5, 0.74)

    # ---- receiver: round action bedded in the stock, two-piece scope bases --------------
    rc = m.bone("receiver", pivot=(0, 5, 0))
    rc.box((-1, 3.5, -6.5), (2, 1.5, 8.5), "hs_green")                 # stock round the action
    rc.box((-1, 5, -6.5), (1, 1, 10), "action")                        # action, left half
    rc.box((0, 5, -6.5), (1, 1, PORT_Z[0] + 6.5), "action")            # right: front ring
    rc.box((0, 5, PORT_Z[1]), (1, 1, 3.5 - PORT_Z[1]), "action")       # right: rear bridge
    rc.box((-1, 6, -6.5), (2, 0.5, 2), "action", pattern="rails")      # front scope base
    rc.box((-1, 6, -2.5), (2, 0.5, 3), "action", pattern="rails")      # rear scope base
    rc.box((-0.6, 3.3, -5.25), (1.2, 0.2, 4), "action")                # hinged floor plate (flush)
    rc.box((-0.5, 2.25, -1.5), (1, 0.4, 3), "action")                  # trigger guard bow
    rc.box((-0.5, 2.65, -1.5), (1, 0.65, 0.4), "action")               # guard front / floor-plate latch
    rc.box((-0.5, 2.65, 1.1), (1, 0.65, 0.4), "action")                # guard rear
    rc.box((0.9, 5.6, 2.4), (0.3, 0.6, 0.8), "action", pattern="knurl")   # safety lever
    RIFLE.add_bolt(m, handle="action")
    RIFLE.add_trigger(m)
    rd = m.bone(ROUND, pivot=ROUND_REST)                               # 7.62x51 cartridge, bullet forward
    x, y, z = ROUND_REST
    rd.box((x - 0.2, y - 0.2, z - 0.4), (0.4, 0.4, 1.4), "brass")
    rd.box((x - 0.15, y - 0.15, z - 1.0), (0.3, 0.3, 0.6), "copper")

    # ---- barrel: long plain heavy barrel, crowned, no muzzle device ---------------------
    ba = m.bone("barrel", pivot=(0, BORE_Y, -6.5))
    ba.box((-0.5, 5, MUZZLE_Z), (1, 1, 15.5), "barrel_black", inflate=0.2)
    ba.box((-0.5, 5, -7.25), (1, 1, 0.75), "action", inflate=0.3)      # recoil lug / barrel shank
    ba.box((-0.3, 5.2, MUZZLE_Z - 0.25), (0.6, 0.6, 0.1), "black_metal")   # recessed crown

    # ---- fore-end of the one-piece stock --------------------------------------------------
    hg = m.bone("handguard", pivot=(0, BORE_Y, -6.5))
    hg.box((-1, 3.75, -15), (2, 1.5, 8.5), "hs_green")                 # fore-end, barrel channel on top
    hg.box((-1.15, 4, -14), (2.3, 0.9, 6), "hs_web", pattern="stipple")   # textured grip panels
    hg.box((-0.75, 4, -15.5), (1.5, 1.25, 0.5), "hs_green")            # rounded tip
    hg.box((-0.5, 3.5, -14.75), (1, 0.25, 2), "action")                # bipod / sling stud plate
    hg.box((-0.25, 3.25, -11.5), (0.5, 0.5, 0.5), "action")            # second sling stud

    # ---- butt: wrist, straight comb, adjustable butt plate -----------------------------------
    st = m.bone("stock", pivot=(0, 4.5, 2))
    st.wedge((0, 4.25, 1.75), (0, 3.1, 5.25), 2, 2.2, "hs_web", pattern="stipple")   # wrist
    st.box((-1, 2.75, 4.5), (2, 2.25, 4.75), "hs_green")               # comb (the bolt runs over it)
    st.box((-1, 1.5, 6), (2, 1.25, 3.25), "hs_green")                  # stepped belly
    st.box((-1, 0.5, 7.75), (2, 1, 1.5), "hs_green")                   # toe
    st.box((-0.5, 2, 9.25), (1, 2, 0.5), "bright_steel", pattern="knurl", inflate=0.15)   # LOP wheel
    st.box((-0.25, 0.9, 9.25), (0.5, 0.5, 0.5), "bright_steel")        # guide rod
    st.box((-1, 0.5, 9.75), (2, 4.5, 0.25), "action")                  # butt plate
    st.box((-1, 0.5, 10), (2, 4.5, 0.5), "rubber", pattern="lines")    # recoil pad
    st.box((-0.25, 0, 8), (0.5, 0.5, 0.5), "action")                   # rear sling stud

    return RIFLE.finish(m)
