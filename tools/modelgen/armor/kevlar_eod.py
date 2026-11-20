"""Juggernaut tier: Med-Eng EOD 10 bomb disposal suit in olive green, massively bulky.
  * jacket (body + arm bones): thick padded torso, big hard chest / abdomen blast plate, chin guard
    and a high blast collar round the neck and the back of the head, huge padded shoulders and thick
    sleeves to the wrists, the back cooling / power unit, groin flap;
  * trousers (leg bones): thick padded legs with hard thigh / knee / shin plates and chunky boot covers;
  * helmet: big rounded-box helmet with a large flat tinted visor in a black frame, fan / vent unit
    on the top-back, a lamp on the wearer's right.

Wearer faces -Z, +X = wearer's right.
"""
import armor as A

MODEL = "kevlar_eod"
PIECES = {'eod_jacket': 'chest', 'eod_trousers': 'legs', 'eod_helmet': 'head'}


def _sym(b, a, c, mat, **kw):
    for s in (1, -1):
        b.span((s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, **kw)


def _jacket(m):
    b = m.bones[A.BODY_BONE]
    F, K = -4.3, 4.3                                             # outer face of the padding
    b.span((-5.0, 11.4, F), (5.0, 24.0, K), "e_pad")
    # hard blast plate: chest + abdomen, raised centre with a groove between the two
    b.span((-4.3, 12.8, -5.3), (4.3, 22.6, F), "e_plate")
    b.span((-3.6, 18.0, -5.7), (3.6, 21.9, -5.3), "e_plate2")
    b.span((-3.6, 13.5, -5.7), (3.6, 17.4, -5.3), "e_plate2")
    b.span((-3.0, 18.8, -6.0), (-1.4, 20.8, -5.7), "e_black")    # remote / control box
    b.span((-2.7, 20.0, -6.08), (-2.2, 20.5, -6.0), "e_red")
    b.span((-2.7, 19.1, -6.08), (-1.7, 19.6, -6.0), "e_grey")
    _sym(b, (2.2, 15.0, -5.78), (3.2, 16.0, -5.7), "e_black")    # plate retention buckles
    # chin guard: the front of the collar, below the visor
    b.span((-4.6, 22.2, -6.7), (4.6, 25.1, F), "e_pad2")
    # blast collar: yoke, high back wall, side walls stepping down to the front
    b.span((-5.0, 21.4, K), (5.0, 24.6, 5.7), "e_pad")
    b.span((-6.6, 23.0, 5.7), (6.6, 29.2, 7.0), "e_pad2")
    _sym(b, (5.7, 24.6, 0.6), (7.0, 28.0, 7.0), "e_pad2")
    _sym(b, (5.7, 24.6, -3.6), (7.0, 26.2, 0.6), "e_pad2")
    b.span((-6.7, 28.4, 5.6), (6.7, 29.3, 7.1), "e_dark")        # collar rim
    # back cooling / power unit
    b.span((-3.3, 13.4, K), (3.3, 21.0, 6.3), "e_unit")
    b.span((-2.4, 17.8, 6.3), (2.4, 20.2, 6.4), "e_black", pattern="slots")
    b.span((-2.7, 14.1, 6.3), (-0.3, 16.8, 6.8), "e_black")
    b.span((0.6, 14.4, 6.3), (2.6, 16.4, 6.5), "e_grey")
    # groin flap with its plate
    b.span((-3.3, 8.2, -5.2), (3.3, 11.9, -4.4), "e_pad")
    b.span((-2.5, 8.8, -5.5), (2.5, 11.2, -5.2), "e_plate")
    # arms: huge shoulders, thick sleeves, cuffs, forearm and elbow plates
    for s, bone in ((1, A.R_ARM), (-1, A.L_ARM)):
        a = m.bones[bone]
        a.span((s * 3.9, 12.9, -3.3), (s * 9.5, 21.0, 3.3), "e_pad")
        a.span((s * 3.9, 20.4, -4.0), (s * 10.2, 25.2, 4.0), "e_pad2")
        a.span((s * 4.7, 25.2, -3.2), (s * 9.5, 25.8, 3.2), "e_pad2")
        a.span((s * 3.8, 12.7, -3.45), (s * 9.65, 13.6, 3.45), "e_dark")
        a.span((s * 3.85, 17.7, -3.38), (s * 9.58, 18.1, 3.38), "e_seam")
        a.span((s * 4.9, 14.1, -3.75), (s * 8.6, 17.2, -3.3), "e_plate")
        a.span((s * 9.5, 15.2, -2.2), (s * 9.95, 19.6, 2.2), "e_plate")


def _trousers(m):
    for s, bone in ((1, A.R_LEG), (-1, A.L_LEG)):
        g = m.bones[bone]
        g.span((s * 0.05, 2.4, -3.3), (s * 5.6, 12.4, 3.3), "e_pad")
        g.span((s * 0.8, 8.8, -3.9), (s * 4.9, 11.6, -3.3), "e_plate")       # thigh
        g.span((s * 0.5, 5.6, -4.5), (s * 5.2, 8.3, -3.3), "e_plate2")       # knee
        g.span((s * 0.9, 2.4, -4.0), (s * 4.8, 5.6, -3.3), "e_plate")        # shin
        g.span((s * 0.0, 8.35, -3.36), (s * 5.66, 8.7, 3.36), "e_seam")
        g.span((s * 5.6, 8.9, -2.0), (s * 6.1, 11.4, 2.0), "e_pad2")         # cargo pocket
        # boot cover + sole
        g.span((s * 0.05, 0.6, -4.7), (s * 5.8, 2.6, 3.6), "e_boot")
        g.span((s * 0.05, 0.0, -4.85), (s * 5.95, 0.6, 3.75), "e_sole")
        g.span((s * 0.5, 1.3, -4.82), (s * 5.4, 2.0, -4.7), "e_dark")


def _helmet(m):
    h = m.bones[A.HEAD]
    h.span((-5.2, 24.9, -4.2), (5.2, 33.4, 4.2), "e_pad")
    h.span((-4.4, 24.9, -5.2), (4.4, 33.4, 5.2), "e_pad")
    h.span((-4.3, 33.4, -4.3), (4.3, 34.2, 4.3), "e_pad")
    # visor: tinted glass in a black frame, across the whole face
    h.span((-3.9, 26.0, -5.55), (3.9, 31.5, -5.2), "e_glass")
    h.span((-4.4, 31.5, -5.75), (4.4, 32.2, -5.2), "e_black")
    h.span((-4.4, 25.3, -5.75), (4.4, 26.0, -5.2), "e_black")
    _sym(h, (3.9, 26.0, -5.75), (4.4, 31.5, -5.2), "e_black")
    h.span((1.4, 29.6, -5.6), (3.2, 30.6, -5.55), "e_glint")
    h.span((0.4, 28.4, -5.6), (1.6, 29.2, -5.55), "e_glint")
    # fan / vent unit over the top-back
    h.span((-2.5, 31.0, 5.2), (2.5, 34.2, 6.6), "e_unit")
    h.span((-2.5, 34.2, 1.4), (2.5, 35.0, 6.6), "e_unit")
    h.span((-1.8, 31.7, 6.6), (1.8, 33.7, 6.7), "e_black", pattern="slots")
    h.span((-1.6, 35.0, 2.2), (1.6, 35.1, 5.6), "e_black", pattern="slots")
    # ear bulges, lamp on the wearer's right
    _sym(h, (5.2, 26.6, -1.6), (5.6, 30.0, 1.8), "e_pad2")
    h.span((5.2, 31.0, -3.8), (6.3, 32.5, -1.0), "e_black")
    h.span((5.4, 31.2, -4.0), (6.1, 32.3, -3.8), "e_lamp")


def model():
    m = A.new_armor(MODEL)
    m.material("e_pad", (90, 98, 64), 6, 0.84)         # olive padding
    m.material("e_pad2", (80, 88, 58), 6, 0.82)
    m.material("e_seam", (62, 68, 46), 3, 1.0)
    m.material("e_plate", (68, 76, 52), 5, 0.74)       # hard blast plates
    m.material("e_plate2", (58, 66, 46), 5, 0.74)
    m.material("e_dark", (40, 44, 32), 3, 0.9)
    m.material("e_unit", (62, 66, 58), 5, 0.78)
    m.material("e_black", (24, 24, 26), 3, 0.85)
    m.material("e_grey", (110, 112, 108), 5, 0.85)
    m.material("e_glass", (50, 70, 92), 5, 0.9)        # tinted visor
    m.material("e_glint", (112, 142, 168), 4, 1.0)
    m.material("e_boot", (62, 68, 48), 5, 0.8)
    m.material("e_sole", (28, 28, 28), 3, 0.9)
    m.material("e_lamp", (238, 232, 190), 4, 1.0)
    m.material("e_red", (190, 44, 40), 4, 1.0)
    _jacket(m)
    _trousers(m)
    _helmet(m)
    return m
