"""M18 coloured smoke grenade, GREEN. Sheet-steel can 146 mm long, 64 mm across; olive drab with
the top painted in the smoke colour, a colour band and light-green lettering; four emission holes
in the top round the M201A1 fuze; spoon and pull ring.

1 unit ~ 3.35 cm: 4.4 long, 2 across. Centre of mass = middle of the can = model origin = pivot
of `grenade`. Bones / animations / locators: see grenades.py.
"""
import grenades as G
from armorygen import Model

GUN_ID = "grenade_m18"
HALF = 2.2                     # half the length
A = 0.95                       # can half-width
RIM = 0.14                     # rolled rims, top and bottom
FUZE = G.FuzeSpec(top_y=HALF, side_x=A + 0.04, spoon_end_y=0.3)

# ---- handling: 540 g can, lobbed in a high arc ---------------------------------------------------
# right-arm poses = (position offset, rotation offset) from the rest pose; see grenades.Handling
HOLD = ((-2.4, 2.8, 0.8), (36.0, -6.0, 0.0))                 # cocked high
HANDLING = G.Handling(
    grip_h=1.0,                                             # can held lengthwise by its lower half
    idle_len=3.2, idle_sway=0.13,
    pull_len=0.4, tug=0.22, twist=30.0, squeeze=4.0,
    hold_pose=HOLD, hold_len=2.2, bob=0.17,
    release_t=0.16,
    throw={
        0.0: (*HOLD, "linear"),
        0.055: ((-2.7, 3.1, 1.4), (46.0, -7.0, 0.0), G.OUT),     # wind-up
        0.13: ((-0.6, 5.8, -1.0), (8.0, -5.0, 0.0), G.IN),       # up and over
        0.16: ((1.1, 7.4, -3.8), (-20.0, -4.0, 0.0), "linear"), # RELEASE high and early: a lob
        0.195: ((0.6, 6.2, -5.6), (-44.0, -2.0, 0.0), G.OUT),   # wrist rolls over the top
        0.27: ((0.0, 2.0, -6.6), (-58.0, 0.0, 0.0), G.OUT),
        0.37: ((-1.0, -9.0, -4.0), (-56.0, 0.0, 0.0), G.IO),
    },
    under_release_t=0.18,
    under={
        0.0: (*HOLD, "linear"),
        0.095: ((1.8, -2.8, 2.4), (-50.0, -6.0, 0.0), G.IO),
        0.15: ((1.6, -3.6, -0.8), (-62.0, -5.0, 0.0), G.IN),
        0.18: ((1.3, -1.4, -4.4), (-48.0, -4.0, 0.0), "linear"),  # RELEASE
        0.22: ((1.0, 1.4, -6.0), (-32.0, -2.0, 0.0), G.OUT),   # long upward follow-through
        0.29: ((0.6, 3.6, -6.6), (-24.0, 0.0, 0.0), G.OUT),
        0.38: ((0.0, -8.0, -4.0), (-42.0, 0.0, 0.0), G.IO),
    },
)


def model() -> Model:
    m = G.new_model(GUN_ID)
    m.material("g_body", (74, 90, 60), 7, 0.80)      # olive drab can
    m.material("g_rim", (60, 74, 50), 6, 0.80)
    m.material("g_fuze", (104, 108, 92), 6, 0.78)
    m.material("g_spoon", (116, 120, 100), 6, 0.78)
    m.material("g_smoke", (58, 176, 74), 6, 0.85)    # the smoke colour: top + band
    m.material("g_mark", (156, 204, 146), 5, 0.9)    # light green lettering
    g = G.body(m)
    G.octagon(g, A, -HALF + RIM, HALF - RIM, "g_body")
    G.octagon(g, A + 0.04, -HALF, -HALF + RIM, "g_rim")
    G.octagon(g, A + 0.04, HALF - RIM, HALF, "g_smoke")           # painted top (rim and lid)
    G.band(g, A, HALF - 0.75, HALF - 0.4, "g_smoke")
    for dx, dz in ((0.52, 0.52), (-0.52, 0.52), (0.52, -0.52), (-0.52, -0.52)):   # emission holes
        g.cbox((dx, HALF, dz), (0.26, 0.12, 0.26), "g_hole")
    g.cbox((0, -HALF, 0), (0.3, 0.12, 0.3), "g_hole")             # and the one in the bottom
    for th in (-45, 0, 45, 135, 180, 225):
        G.text_row(g, A, 0.95, (th,), "g_mark")
        G.text_row(g, A, 0.68, (th + 45,), "g_mark")
    G.text_row(g, A, 0.4, (0, 180), "g_mark", words=((0.24, 0.1, 0.2),))
    G.fuze(m, FUZE)
    return G.finish(m, FUZE, HANDLING)
