"""M84 stun grenade (flashbang). Perforated steel tube 133 mm long, 44 mm across, with hexagonal
end caps; rows of vent holes down the body; M201A1 fuze with the spoon, the round pull ring and
the secondary pin with its TRIANGULAR ring.

1 unit ~ 3.35 cm: 4 units long, tube 1.3 across, hex caps 1.6. Dark parkerised grey with a pale
green band and markings. Centre of mass = middle of the tube = model origin = pivot of `grenade`.
Bones / animations / locators: see grenades.py.
"""
import grenades as G
from armorygen import Model

GUN_ID = "grenade_m84"
HALF = 2.0                     # half the length
TUBE_A = 0.65                  # tube half-width
CAP_A, CAP_H = 0.8, 0.45       # hex caps
HOLES = 5                      # vent holes per flat
FUZE = G.FuzeSpec(top_y=HALF, side_x=CAP_A, spoon_end_y=0.35, fuze_mat="g_fuze", spoon_mat="g_spoon",
                  second_ring=True)

# ---- handling: 236 g, the lightest: a quick sidearm flick, mostly wrist -------------------------
# right-arm poses = (position offset, rotation offset) from the rest pose; see grenades.Handling
HOLD = ((-1.2, 2.8, 0.8), (26.0, -16.0, 0.0))                # cocked lower and wider than the frag
HANDLING = G.Handling(
    grip_h=0.9,                                             # slim tube held lengthwise
    idle_len=2.6, idle_sway=0.10,
    pull_len=0.36, tug=0.18, twist=45.0, squeeze=3.0,       # snappy pull
    hold_pose=HOLD, hold_len=1.6, bob=0.12,                 # light: a quick, small bob
    flip=95.0,
    release_t=0.12,
    throw={
        0.0: (*HOLD, "linear"),
        0.035: ((-1.2, 3.0, 1.3), (33.0, -19.0, 0.0), G.OUT),    # barely any wind-up
        0.095: ((0.2, 4.2, -1.8), (-4.0, -8.0, 0.0), G.IN),     # sidearm whip
        0.12: ((1.2, 4.9, -4.6), (-34.0, 2.0, 0.0), "linear"),  # RELEASE
        0.145: ((0.4, 3.6, -5.8), (-58.0, 8.0, 0.0), G.OUT),    # hard wrist snap, across the body
        0.21: ((-0.6, 0.6, -6.2), (-64.0, 10.0, 0.0), G.OUT),
        0.30: ((-1.5, -9.0, -4.0), (-58.0, 6.0, 0.0), G.IO),
    },
    under_release_t=0.14,
    under={
        0.0: (*HOLD, "linear"),
        0.07: ((2.0, -2.4, 2.0), (-50.0, -10.0, 0.0), G.IO),
        0.115: ((1.7, -3.2, -1.0), (-60.0, -6.0, 0.0), G.IN),
        0.14: ((1.4, -1.8, -4.4), (-50.0, -2.0, 0.0), "linear"),  # RELEASE
        0.175: ((1.0, 0.4, -6.0), (-34.0, 2.0, 0.0), G.OUT),
        0.24: ((0.6, 2.4, -6.4), (-28.0, 2.0, 0.0), G.OUT),
        0.32: ((0.0, -8.0, -4.0), (-42.0, 0.0, 0.0), G.IO),
    },
)


def model() -> Model:
    m = G.new_model(GUN_ID)
    m.material("g_body", (60, 62, 60), 6, 0.78)      # parkerised steel tube
    m.material("g_cap", (46, 48, 47), 5, 0.78)       # hex end caps
    m.material("g_fuze", (120, 122, 118), 6, 0.76)
    m.material("g_spoon", (134, 136, 132), 6, 0.76)
    m.material("g_mark", (150, 196, 150), 5, 0.9)    # pale green band / lettering
    g = G.body(m)
    y0, y1 = -HALF + CAP_H, HALF - CAP_H
    G.octagon(g, TUBE_A, y0, y1, "g_body")
    G.hexagon(g, CAP_A, -HALF, y0, "g_cap")
    G.hexagon(g, CAP_A, y1, HALF, "g_cap")
    G.band(g, TUBE_A, y1 - 0.3, y1 - 0.12, "g_mark")
    pitch = (y1 - 0.45 - y0 - 0.2) / HOLES
    for th in range(0, 360, 45):                      # rows of vent holes on every flat
        for i in range(HOLES):
            G.plate(g, TUBE_A, th, 0, y0 + 0.2 + pitch * (i + 0.5), 0.26, 0.3, "g_hole", t=0.02)
    for sgn in (-1, 1):                               # the big vent in each end cap
        g.cbox((0.42, sgn * HALF, 0.42), (0.3, 0.1, 0.3), "g_hole")
        g.cbox((-0.42, sgn * HALF, -0.42), (0.3, 0.1, 0.3), "g_hole")
    G.fuze(m, FUZE)
    return G.finish(m, FUZE, HANDLING)
