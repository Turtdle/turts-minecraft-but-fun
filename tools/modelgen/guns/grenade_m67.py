"""M67 fragmentation hand grenade (US Army). Steel ball 64 mm (2.5 in) across, 400 g, olive drab
with yellow stencil markings; M213 fuze on top with the spoon down the +X side, pull ring, and
the wire safety clip.

Scale as the guns (1 unit ~ 3.35 cm): the ball is 2 units across (a touch chunky), ~2.9 tall with
the fuze. Low-poly ball: seven octagonal layers. Centre of mass = centre of the ball = the model
origin = pivot of `grenade`. Bones / animations / locators: see grenades.py.
"""
import grenades as G
from armorygen import Model

GUN_ID = "grenade_m67"
R = 1.0                        # ball radius
# (y0, y1, half-width) of the octagonal layers, bottom half; mirrored for the top
LAYERS = ((-1.0, -0.8, 0.5), (-0.8, -0.55, 0.76), (-0.55, -0.3, 0.92))
EQUATOR = (-0.3, 0.3)
FUZE = G.FuzeSpec(top_y=R, side_x=R, spoon_end_y=-0.55, shoulder=(R + 0.07, 0.3), clip=True)

# ---- handling: a 400 g ball, thrown like a baseball -------------------------------------------
# right-arm poses = (position offset, rotation offset) from the rest pose; see grenades.Handling
HOLD = ((-0.6, 3.6, 0.9), (32.0, -8.0, 0.0))                 # cocked beside the head
HANDLING = G.Handling(
    grip_h=0.35,                                            # the ball sits deep in the fist
    idle_len=3.0, idle_sway=0.12,
    pull_len=0.4, tug=0.22, twist=35.0, squeeze=4.0,
    hold_pose=HOLD, hold_len=2.0, bob=0.15,
    release_t=0.15,
    throw={
        0.0: (*HOLD, "linear"),
        0.05: ((-0.7, 4.0, 1.6), (42.0, -9.0, 0.0), G.OUT),      # wind-up: a little further back
        0.12: ((0.4, 5.8, -1.6), (0.0, -6.0, 0.0), G.IN),       # whip: the elbow leads, hand comes over
        0.15: ((1.0, 6.2, -4.5), (-30.0, -4.0, 0.0), "linear"), # RELEASE
        0.18: ((0.5, 4.6, -6.0), (-52.0, -2.0, 0.0), G.OUT),    # wrist snap
        0.25: ((0.0, 1.0, -6.6), (-60.0, 0.0, 0.0), G.OUT),     # follow-through
        0.35: ((-1.0, -9.0, -4.0), (-56.0, 0.0, 0.0), G.IO),    # arm drops out of view
    },
    under_release_t=0.17,
    under={
        0.0: (*HOLD, "linear"),
        0.09: ((1.8, -2.6, 2.2), (-52.0, -6.0, 0.0), G.IO),     # drop to the back of the pendulum
        0.14: ((1.6, -3.4, -1.0), (-62.0, -5.0, 0.0), G.IN),    # bottom of the swing
        0.17: ((1.3, -1.6, -4.6), (-52.0, -4.0, 0.0), "linear"),  # RELEASE, hand rising
        0.21: ((1.0, 0.8, -6.2), (-38.0, -2.0, 0.0), G.OUT),   # wrist flicks up
        0.28: ((0.6, 3.0, -6.8), (-30.0, 0.0, 0.0), G.OUT),
        0.37: ((0.0, -8.0, -4.0), (-42.0, 0.0, 0.0), G.IO),
    },
)


def model() -> Model:
    m = G.new_model(GUN_ID)
    m.material("g_body", (80, 88, 56), 7, 0.80)      # olive drab
    m.material("g_fuze", (98, 104, 76), 6, 0.78)
    m.material("g_spoon", (108, 114, 84), 6, 0.78)
    m.material("g_mark", (216, 184, 44), 5, 0.9)     # yellow stencil
    g = G.body(m)
    for y0, y1, a in LAYERS:
        G.octagon(g, a, y0, y1, "g_body")
        G.octagon(g, a, -y1, -y0, "g_body")
    G.octagon(g, R, *EQUATOR, "g_body")
    for th in (-45, 0, 45, 180, 225):                # two lines of lettering round the equator
        G.text_row(g, R, 0.11, (th,), "g_mark", h=0.13)
    G.text_row(g, R, -0.12, (0, 180), "g_mark", h=0.13, words=((0.22, 0.1, 0.22),))
    G.fuze(m, FUZE)
    return G.finish(m, FUZE, HANDLING)
