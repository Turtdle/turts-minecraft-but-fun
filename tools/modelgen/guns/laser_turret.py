"""P-HEL / HELWS-style palletized high-energy laser air-defence turret (ground emplacement).

Multiblock decoration + seat, 1 texel per unit. 16 units = 1 block, Y up, front = -Z, ground Y = 0.
Footprint X [-24, 24] x Z [-24, 40] (3 x 4 blocks: 3 x 3 under the turret + the operator row).

Layout the Java side relies on (do not move):
  * pallet Y [0, 6]; cabinets on it up to Y 22; pedestal + slewing ring top at Y_RING = 26
  * bone `turret`   pivot (0, 26, 0), yaws about Y; everything on it stays within 22 units of
    the axis, above Y 26 and below Y 48 at any yaw
  * bone `director` (child of turret) pivot (0, 39, 0), pitches about X (+ = up), -5..+85
  * locator `aperture` (child of director) = centre of the exit window's outer face
  * operator row Z [24, 40], Y <= 23; locator `seat` (0, 10, 32); the seated player's legs go
    forward into the knee hole X [-9, 9], Z [8, 24] under the console (Y 15..24, Z 21.5..28.5)
"""
import math

from armorygen import Bone, Model

DECK = 6.0             # pallet top
Y_RING = 26.0          # top of the pedestal's slewing ring = turret pivot height
Y_SEAT = 10.0
TURRET = (0.0, Y_RING, 0.0)
DIRECTOR = (0.0, 39.0, 0.0)
SEAT = (0.0, Y_SEAT, 32.0)
DRUM_D = 14.0


def new_model() -> Model:
    m = Model("laser_turret", density=1)
    for name, rgb, noise, edge in (
        ("tan", (172, 148, 108), 4, 0.80), ("tan_dark", (136, 116, 84), 4, 0.80),
        ("tan_light", (192, 170, 130), 4, 0.82), ("dark_steel", (46, 48, 50), 4, 0.75),
        ("interior", (18, 18, 17), 2, 0.90), ("laser_glass", (12, 16, 34), 2, 0.70),
        ("glint", (70, 96, 170), 0, 1.0), ("radar", (120, 124, 108), 3, 0.72),
        ("pad", (88, 96, 66), 5, 0.72), ("warn", (226, 186, 36), 3, 0.85),
        ("screen", (36, 92, 78), 4, 0.70), ("screen_hi", (120, 220, 170), 0, 1.0),
        ("hose", (58, 84, 120), 3, 0.80), ("beacon", (236, 140, 30), 3, 0.85),
    ):
        m.material(name, rgb, noise, edge)
    return m


def octagon_x(b: Bone, x0, x1, y, z, d, mat, stagger=0.1):
    """Flat-sided octagonal disc on the X axis (four slim boxes, end faces stepped by
    `stagger` so they never share a plane)."""
    side = d * math.tan(math.pi / 8)
    lo, hi = min(x0, x1), max(x0, x1)
    for i in range(4):
        a, c = lo + stagger * i, hi - stagger * i
        b.box((a, y - d / 2, z - side / 2), (c - a, d, side), mat,
              rot=(45 * i, 0, 0) if i else None, pivot=((a + c) / 2, y, z))


def octagon_y(b: Bone, y0, y1, x, z, d, mat, stagger=0.1):
    """Octagonal disc on the Y axis; its highest face is exactly y1."""
    side = d * math.tan(math.pi / 8)
    for i in range(4):
        lo = y0 + 0.01 * i
        b.box((x - d / 2, lo, z - side / 2), (d, y1 - lo - stagger * i, side), mat,
              rot=(0, 45 * i, 0) if i else None, pivot=(x, (y0 + y1) / 2, z))


def pallet(m: Model):
    b = m.bone("pallet")
    b.span((-23.9, 4.5, -23.9), (23.9, DECK, 24), "tan_dark")                  # deck
    b.span((-5, 0, -23.8), (5, 4.5, 23.9), "tan_dark")                       # centre runner
    for sx in (-1, 1):
        for z0, z1 in ((-23.8, -15), (-5, 5), (15, 23.9)):                  # side runner blocks
            b.span((sx * 17, 0, z0), (sx * 23.8, 4.5, z1), "tan_dark")
    # dark core: the forklift pockets (front / back / both sides) open onto it
    b.span((-20, 0.3, -20), (20, 4.5, 20), "interior")
    for sx in (-1, 1):                                                   # lifting / tie-down lugs
        b.span((sx * 6, DECK, -23.5), (sx * 8.5, 7.5, -21.5), "steel")
    b.span((-8, 4.7, -24), (8, 5.7, -23.9), "warn")                      # front edge marking


def cabinets(m: Model):
    b = m.bone("cabinets")
    # --- left rear: power cabinet -------------------------------------------------
    b.span((-23, DECK, -6), (-9, 22, 23), "tan")
    for z0, z1 in ((-4.5, 8), (9, 21.5)):                                # doors, outer face
        b.span((-23.5, 8, z0), (-23, 20.5, z1), "tan_light")
        b.span((-23.9, 13, z1 - 2.5), (-23.5, 15.5, z1 - 1.5), "dark_steel")   # handle
    b.span((-21, 22, -3), (-11, 22.5, 9), "dark_steel", pattern="slots")  # top vent
    b.span((-21.5, 9, -6.5), (-10.5, 19, -6), "dark_steel", pattern="slots")  # front louvre
    b.span((-22.4, 16, 23), (-10, 20, 23.4), "dark_steel", pattern="slots")   # rear vent
    b.span((-23.8, 16.5, 2), (-23.5, 19.5, 6), "warn")                   # high-voltage label
    b.span((-23.9, 17.3, 3.2), (-23.8, 18.7, 4.8), "black_metal")
    # --- left front: battery / generator box ---------------------------------------
    b.span((-23, DECK, -22), (-9, 14, -8), "tan")
    b.span((-23.4, 12, -22.4), (-8.6, 13, -7.6), "tan_dark")             # lid band
    b.span((-21, 7.5, -22.5), (-14, 11, -22), "dark_steel", pattern="slots")
    b.span((-13, 7.5, -22.6), (-10, 10.5, -22), "warn")
    b.span((-12.3, 8.2, -22.8), (-10.7, 9.8, -22.6), "black_metal")
    b.span((-20, 14, -19), (-12, 14.5, -11), "tan_dark")                 # lid plate
    b.span((-17.5, 14.5, -16.5), (-14.5, 15.3, -13.5), "red")            # battery cut-off
    # --- right front: chiller with two top fans -------------------------------------
    b.span((9, DECK, -22), (23, 18, 10), "tan")
    for z in (-14.0, 2.0):
        octagon_y(b, 18, 18.8, 16, z, 11.0, "dark_steel")
        b.cbox((16, 19.05, z), (3, 0.7, 3), "steel")
        b.cbox((16, 18.95, z), (9.5, 0.3, 1.2), "black_metal")           # fan guard bars
        b.cbox((16, 18.95, z), (1.2, 0.3, 9.5), "black_metal")
    b.span((23, 8, -20), (23.5, 16, 8), "dark_steel", pattern="slots")   # side radiator
    b.span((11, 8, -22.5), (21, 16, -22), "dark_steel", pattern="slots")  # front radiator
    b.span((9.6, 14, -21), (9, 17, -17), "warn")                          # hot-surface label
    # --- right rear: fire-control electronics cabinet ------------------------------
    b.span((9, DECK, 12), (23, 22, 23), "tan")
    b.span((23, 8, 13.5), (23.5, 20.5, 21.5), "tan_light")               # door
    b.span((23.5, 13, 14.5), (23.9, 15.5, 15.5), "dark_steel")
    b.span((23.5, 16.5, 17), (23.8, 19.5, 20.5), "warn")                 # laser hazard label
    b.span((23.8, 17.3, 18), (23.9, 18.7, 19.5), "black_metal")
    b.span((11, 22, 14), (21, 22.5, 21), "dark_steel", pattern="slots")
    b.span((10, 16, 23), (22.4, 20, 23.4), "dark_steel", pattern="slots")
    # --- whip antenna on the power cabinet's rear outer corner ---------------------
    b.cbox((-21, 22.75, 21), (2.4, 1.5, 2.4), "dark_steel")
    b.cbox((-21, 24.5, 21), (1.2, 2, 1.2), "steel")
    b.cbox((-21, 36.25, 21), (0.6, 21.5, 0.6), "black_metal")


def pedestal(m: Model):
    b = m.bone("pedestal")
    octagon_y(b, DECK, 8.0, 0, 0, 18.0, "dark_steel")                    # base flange
    octagon_y(b, 8.0, 23.5, 0, 0, 13.0, "tan")                           # column
    octagon_y(b, 23.5, Y_RING, 0, 0, 20.0, "dark_steel")                 # slewing ring
    b.span((-3, 10, -7), (3, 15, -6.5), "tan_dark")                      # access hatch
    b.span((-1.5, 16.5, -7), (1.5, 19, -6.6), "warn")
    # --- cable runs ---------------------------------------------------------------
    # battery box -> along the deck -> up the pedestal front
    b.span((-9, DECK, -18), (-3.5, 8, -16), "rubber")
    b.span((-5.5, DECK, -16), (-3.5, 8, -9), "rubber")
    b.span((-5.4, 8, -8.6), (-3.6, 23.5, -6.6), "rubber")
    # power cabinet -> pedestal (two bus cables)
    for y in (11.0, 16.0):
        b.span((-9, y, -3), (-6, y + 2, -1), "rubber")
    # chiller -> pedestal (coolant supply / return hoses)
    for y, z in ((10.0, -2.5), (14.5, 1.5)):
        b.span((6, y, z - 1), (9, y + 2, z + 1), "hose")
        b.span((5.5, y - 0.4, z - 1.4), (6.4, y + 2.4, z + 1.4), "steel")  # hose clamp
    # electronics cabinet -> pedestal (signal conduit along the deck)
    b.span((6, DECK, 4.5), (9.5, 7.5, 6), "rubber")
    b.span((9.5, DECK, 4.5), (11, 7.5, 12), "rubber")


def operator(m: Model):
    b = m.bone("operator")
    sz = SEAT[2]
    # skid extension under the seat
    for sx in (-1, 1):
        b.span((sx * 7, 0, 24), (sx * 11, 2.5, 40), "tan_dark")
    b.span((-12, 2.5, 24), (12, 4, 40), "tan_dark")
    b.span((-9, 4, 24.5), (9, 4.5, 28), "steel", pattern="lines")        # tread plate
    # seat: post, pan frame, cushions, backrest frame, armrests
    b.span((-2.5, 4, sz - 2.5), (2.5, 6.5, sz + 2.5), "steel")
    b.span((-6.5, 6.5, 26.5), (6.5, 7.5, 38.5), "dark_steel")
    b.span((-6, 7.5, 26.2), (6, 9.5, 37), "pad")                         # seat cushion
    b.span((-6.5, 4, 38.5), (6.5, 23, 40), "dark_steel")                 # backrest frame
    b.span((-6, 9.5, 37), (6, 22.5, 38.5), "pad")                        # backrest cushion
    b.span((-5, 18.5, 36.4), (5, 22, 37), "pad")                         # head pad
    for sx in (-1, 1):
        b.span((sx * 6.5, 7.5, 36.5), (sx * 8, 14.5, 38.5), "dark_steel")
        b.span((sx * 6.5, 13, 30), (sx * 8.5, 14.5, 36.5), "pad")        # armrest
    # footrest bar in the knee hole (angled plate on the pallet deck)
    b.wedge((0, 6.3, 20), (0, 9, 16.5), 13, 1.0, "steel", pattern="lines")
    for sx in (-1, 1):
        b.span((sx * 6.5, DECK, 16), (sx * 7.5, 9, 17.5), "dark_steel")
    # console: screen housing bridged between the two rear cabinets
    b.span((-7, 16, 21.5), (7, 24, 24), "dark_steel")
    for sx in (-1, 1):
        b.span((sx * 7, 17.5, 22), (sx * 9, 21.5, 23.5), "steel")        # mounting brackets
    b.span((-5.5, 17.5, 24), (5.5, 23, 24.3), "screen")
    b.span((-1.5, 19.5, 24.3), (1.5, 21, 24.4), "screen_hi")             # track box
    b.span((-4.5, 22, 24.3), (-2.5, 22.4, 24.4), "screen_hi")
    # control shelves reaching toward the operator: joystick (right), keypad + e-stop (left)
    b.span((2, 15, 24), (7.5, 16, 28.5), "dark_steel")
    b.span((3.5, 16, 25.5), (6.5, 16.8, 28.2), "black_metal")
    b.span((4.4, 16.8, 26.2), (5.6, 20, 27.4), "polymer")                # stick
    b.span((4, 20, 25.8), (6, 21.5, 27.8), "black_metal")                # grip head
    b.span((4.6, 21.5, 26.2), (5.4, 21.9, 27), "red")                    # fire button
    b.span((-7.5, 15, 24), (-2, 16, 28.5), "dark_steel")
    b.span((-6.8, 16, 24.6), (-4, 16.5, 27.8), "steel", pattern="knurl")  # keypad
    b.span((-3.6, 16, 26), (-2.4, 17.2, 27.2), "red")                    # emergency stop


def turret(m: Model):
    t = m.bone("turret", pivot=TURRET)
    octagon_y(t, Y_RING, 28.5, 0, 0, 18.0, "tan")                        # turntable
    t.span((-6.5, 28.5, -5), (6.5, 30.5, 8.6), "tan_dark")               # saddle
    for sx in (-1, 1):                                                   # yoke arms
        t.span((sx * 6.5, 28.5, -3.5), (sx * 9, 42.5, 3.5), "tan")
        t.span((sx * 9, 37, -2), (sx * 9.8, 41, 2), "dark_steel")        # elevation drive cap
        t.span((sx * 9, 30, -2.5), (sx * 9.3, 34, 0.5), "warn")          # laser hazard label
    # laser / beam-path housing behind the director
    t.span((-8, 28.5, 8.6), (8, 40, 17), "tan")
    t.span((-6, 40, 10), (6, 40.5, 16), "dark_steel", pattern="slots")
    t.span((-6, 31, 17), (6, 38, 17.5), "dark_steel", pattern="slots")
    for sx in (-1, 1):                                                   # flat radar panels
        t.span((sx * 8, 30.5, 9.5), (sx * 8.6, 39, 16.5), "dark_steel")
        t.span((sx * 8.6, 31.2, 10.2), (sx * 9.3, 38.3, 15.8), "radar")
    # EO / IR sensor ball on top of the right arm, warning beacon on the left
    t.span((7, 42.5, -1), (9, 43.2, 1), "dark_steel")
    t.span((5.9, 43.2, -2.1), (10.1, 47.4, 2.1), "black_metal")
    t.span((6.8, 44, -2.5), (9.2, 46.6, -2.1), "glass")
    t.span((-8.8, 42.5, -1), (-6.8, 43, 1), "dark_steel")
    t.span((-8.6, 43, -0.8), (-7, 44.8, 0.8), "beacon")

    d = m.bone("director", parent="turret", pivot=DIRECTOR)
    x, y, z = DIRECTOR
    octagon_x(d, -6, 6, y, z, DRUM_D, "tan_light")                       # gimballed drum
    for sx in (-1, 1):
        d.span((sx * 6, y - 1.5, z - 1.5), (sx * 6.5, y + 1.5, z + 1.5), "steel")  # trunnions
    d.span((-5.2, y - 4, z - 7.0), (5.2, y + 4, z - 5.5), "dark_steel")  # window bezel
    d.span((-4.2, y - 3.2, z - 7.3), (4.2, y + 3.2, z - 7.0), "laser_glass")  # exit window
    d.span((-3.4, y + 1.2, z - 7.4), (-1.6, y + 2.4, z - 7.3), "glint")
    m.locator("aperture", (x, y, z - 7.3), parent="director")


def model() -> Model:
    m = new_model()
    pallet(m)
    cabinets(m)
    pedestal(m)
    operator(m)
    turret(m)
    m.locator("seat", SEAT)
    a = m.anim("aim_check", 4.0, loop=True)
    a.keys("turret", "rotation", {t: (0, 90 * t, 0) for t in range(5)})
    a.keys("director", "rotation", {0: (-5, 0, 0), 2: (85, 0, 0), 4: (-5, 0, 0)})
    return m
