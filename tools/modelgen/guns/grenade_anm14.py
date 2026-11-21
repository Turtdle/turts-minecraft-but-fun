"""AN-M14 TH3 incendiary (thermate) hand grenade. Sheet-metal can 146 mm long, 64 mm across;
light grey with a purple band and heavy dark block lettering; plain lid (no emission holes),
M201A1 fuze, spoon and pull ring.

1 unit ~ 3.35 cm: 4.4 long, 2 across. Centre of mass = middle of the can = model origin = pivot
of `grenade`. Bones / animations / locators: see grenades.py.
"""
import grenades as G
from armorygen import Model

GUN_ID = "grenade_anm14"
HALF = 2.2
A = 0.95
RIM = 0.14
FUZE = G.FuzeSpec(top_y=HALF, side_x=A + 0.04, spoon_end_y=0.3)
BLOCKS = ((0.2, 0.2, 0.2),)    # heavy block letters

# ---- handling: 900 g, the heaviest: a slow heave with a big wind-up, short reach ----------------
# right-arm poses = (position offset, rotation offset) from the rest pose; see grenades.Handling
HOLD = ((-2.3, 2.3, 0.9), (30.0, -8.0, 0.0))                 # carried lower: it is heavy
HANDLING = G.Handling(
    grip_h=1.0,                                             # can held lengthwise, a full-handed grip
    idle_len=3.4, idle_sway=0.15,
    pull_len=0.44, tug=0.26, twist=30.0, squeeze=5.5,       # deliberate pull, hard clamp on the spoon
    hold_pose=HOLD, hold_len=2.6, bob=0.22,                 # slow, heavy bob
    flip=70.0,
    release_t=0.19,
    throw={
        0.0: (*HOLD, "linear"),
        0.075: ((-2.6, 2.5, 1.5), (41.0, -9.0, 0.0), G.OUT),     # long wind-up
        0.155: ((-0.4, 4.4, -0.6), (4.0, -6.0, 0.0), G.IN),      # heave
        0.19: ((1.3, 5.4, -3.6), (-26.0, -4.0, 0.0), "linear"), # RELEASE
        0.23: ((0.8, 3.8, -5.2), (-46.0, -2.0, 0.0), G.OUT),    # little wrist: the weight carries the arm
        0.31: ((0.2, 0.2, -5.8), (-56.0, 0.0, 0.0), G.OUT),
        0.42: ((-1.0, -9.5, -3.6), (-52.0, 0.0, 0.0), G.IO),
    },
    under_release_t=0.20,
    under={
        0.0: (*HOLD, "linear"),
        0.11: ((1.9, -3.0, 2.8), (-48.0, -6.0, 0.0), G.IO),     # deep back-swing
        0.17: ((1.7, -4.0, -0.4), (-62.0, -5.0, 0.0), G.IN),
        0.20: ((1.4, -2.2, -3.8), (-52.0, -4.0, 0.0), "linear"),  # RELEASE
        0.245: ((1.0, 0.0, -5.2), (-40.0, -2.0, 0.0), G.OUT),
        0.32: ((0.7, 1.8, -5.8), (-34.0, 0.0, 0.0), G.OUT),
        0.42: ((0.0, -8.5, -3.6), (-44.0, 0.0, 0.0), G.IO),
    },
)


def model() -> Model:
    m = G.new_model(GUN_ID)
    m.material("g_body", (150, 152, 150), 7, 0.80)   # light grey can
    m.material("g_rim", (124, 126, 124), 6, 0.80)
    m.material("g_fuze", (112, 114, 110), 6, 0.76)
    m.material("g_spoon", (128, 130, 126), 6, 0.76)
    m.material("g_band", (186, 40, 36), 6, 0.85)     # red band: incendiary (swapped with the M7A3's purple, to read as fire)
    m.material("g_mark", (176, 46, 42), 5, 0.9)      # red lettering
    g = G.body(m)
    G.octagon(g, A, -HALF + RIM, HALF - RIM, "g_body")
    G.octagon(g, A + 0.04, -HALF, -HALF + RIM, "g_rim")
    G.octagon(g, A + 0.04, HALF - RIM, HALF, "g_rim")
    G.band(g, A, 0.25, 0.85, "g_band")                           # wide purple band round the middle
    for th in (-45, 0, 45, 135, 180, 225):
        G.text_row(g, A, 1.45, (th,), "g_mark", h=0.3, words=BLOCKS)
        G.text_row(g, A, -0.25, (th,), "g_mark", h=0.22, words=BLOCKS)
    G.text_row(g, A, -0.6, (0, 180), "g_mark", h=0.16)
    G.fuze(m, FUZE)
    return G.finish(m, FUZE, HANDLING)
