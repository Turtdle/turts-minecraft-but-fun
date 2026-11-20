"""Light military tier: Crye JPC 2.0 plate carrier in coyote brown (front and back SAPI-cut plate
bags only, thin shoulder straps, skeletal three-band cummerbund, MOLLE rows on the back bag, kangaroo
flap with three M4 magazines, admin pouch with a velcro patch) and an Ops-Core FAST high-cut helmet
(ears exposed, ARC rails, NVG shroud, velcro panels, bungees, H-harness chin strap).

Wearer faces -Z, +X = wearer's right. Minimal: the shirt shows around the carrier.
"""
import armor as A

MODEL = "kevlar_light"
PIECES = {'plate_carrier': 'chest', 'fast_helmet': 'head'}


def _sym(b, a, c, mat, **kw):
    for s in (1, -1):
        b.span((s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, **kw)


def _carrier(m):
    b = m.bones[A.BODY_BONE]
    F, K = -3.4, 3.4                                             # outer faces of the plate bags
    for z0, z1 in ((F, -1.9), (1.9, K)):                         # SAPI cut: top corners clipped
        b.span((-3.5, 14.4, z0), (3.5, 20.4, z1), "l_bag")
        b.span((-3.0, 20.4, z0), (3.0, 21.5, z1), "l_bag")
        b.span((-2.4, 21.5, z0), (2.4, 22.5, z1), "l_bag")
    # thin padded shoulder straps
    _sym(b, (1.5, 21.5, -3.0), (3.1, 24.0, -1.9), "l_strap")
    _sym(b, (1.5, 21.5, 1.9), (3.1, 24.0, 3.0), "l_strap")
    _sym(b, (1.4, 22.9, -3.15), (3.2, 24.0, -3.0), "l_pad")
    _sym(b, (1.4, 22.9, 3.0), (3.2, 24.0, 3.15), "l_pad")
    # skeletal cummerbund: three elastic bands round the sides
    for y in (14.9, 16.2, 17.5):
        b.span((-4.6, y, -2.6), (4.6, y + 0.8, 2.6), "l_elastic")
    _sym(b, (4.5, 14.9, 1.2), (4.7, 18.3, 1.8), "l_dark")        # band spacers
    _sym(b, (4.5, 14.9, -1.8), (4.7, 18.3, -1.2), "l_dark")
    # front: kangaroo flap, three magazines, dividers
    b.span((-3.3, 14.7, F - 1.1), (3.3, 18.3, F), "l_flap")
    for c in (-2.15, 0.0, 2.15):
        b.span((c - 0.75, 18.3, F - 0.95), (c + 0.75, 19.4, F - 0.2), "l_mag")
        b.span((c - 0.2, 17.2, F - 1.2), (c + 0.2, 18.5, F - 1.1), "l_dark")   # bungee pull tab
    for x in (-1.08, 1.08):
        b.span((x - 0.12, 14.7, F - 1.16), (x + 0.12, 18.3, F - 1.1), "l_molle")
    # admin pouch + velcro patch
    b.span((-2.3, 19.8, F - 0.5), (2.3, 21.5, F), "l_flap")
    b.span((-1.7, 20.2, F - 0.58), (0.4, 21.1, F - 0.5), "l_velcro")
    b.span((0.8, 20.2, F - 0.58), (1.7, 21.1, F - 0.5), "l_patch")
    # back: MOLLE rows, drag handle
    for y in (15.1, 16.5, 17.9, 19.3):
        b.span((-3.1, y, K), (3.1, y + 0.6, K + 0.1), "l_molle", pattern="lines")
    b.span((-2.4, 20.7, K), (2.4, 21.2, K + 0.1), "l_molle", pattern="lines")
    b.span((-1.0, 21.9, K), (1.0, 22.9, K + 0.3), "l_dark")


def _helmet(m):
    h = m.bones[A.HEAD]
    # crown, cut high: nothing below y 29 except at the back
    h.span((-4.7, 29.0, -4.2), (4.7, 32.7, 4.2), "l_shell")
    h.span((-4.1, 29.0, -4.7), (4.1, 32.7, 4.7), "l_shell")
    h.span((-3.7, 32.7, -3.8), (3.7, 33.3, 3.8), "l_shell")
    h.span((-4.7, 26.8, 2.5), (4.7, 29.0, 4.2), "l_shell")
    h.span((-4.1, 26.8, 2.5), (4.1, 29.0, 4.7), "l_shell")
    # ARC rails along the ear cut
    _sym(h, (4.7, 28.5, -3.3), (5.15, 29.7, 2.5), "l_rail", pattern="rails")
    _sym(h, (4.7, 27.4, 2.0), (5.1, 28.5, 2.9), "l_rail")
    # NVG shroud
    h.span((-1.1, 29.1, -5.15), (1.1, 31.3, -4.7), "l_rail")
    h.span((-0.5, 29.6, -5.25), (0.5, 30.6, -5.15), "l_metal")
    # shock-cord bungees from the shroud to the rails
    _sym(h, (1.1, 30.2, -4.85), (4.1, 30.45, -4.7), "l_rail")
    _sym(h, (4.1, 30.2, -4.85), (4.85, 30.45, -4.2), "l_rail")
    # velcro panels: top, sides, back, front strip
    h.span((-2.3, 33.3, -2.6), (2.3, 33.4, 2.6), "l_velcro")
    _sym(h, (4.7, 30.6, -1.8), (4.8, 32.2, 2.4), "l_velcro")
    h.span((-2.6, 29.6, 4.7), (2.6, 31.8, 4.8), "l_velcro")
    h.span((-0.6, 30.2, 4.8), (0.6, 31.2, 4.86), "l_patch")
    h.span((-2.4, 31.7, -4.8), (2.4, 32.4, -4.7), "l_velcro")
    # H-harness chin strap, nape pad
    _sym(h, (4.5, 24.9, -2.5), (4.75, 29.0, -1.9), "l_web")
    _sym(h, (4.5, 24.9, 1.6), (4.75, 28.2, 2.2), "l_web")
    _sym(h, (4.5, 24.3, -4.8), (4.75, 24.9, 2.2), "l_web")
    h.span((-4.75, 24.3, -4.85), (4.75, 24.9, -4.55), "l_web")
    h.span((-1.5, 24.15, -5.0), (1.5, 25.1, -4.85), "l_rail")
    h.span((-2.6, 25.2, 4.5), (2.6, 26.8, 4.8), "l_rail")


def model():
    m = A.new_armor(MODEL)
    m.material("l_bag", (136, 110, 76), 6, 0.82)       # coyote brown cordura
    m.material("l_flap", (126, 101, 69), 6, 0.80)
    m.material("l_strap", (120, 96, 66), 5, 0.84)
    m.material("l_pad", (108, 86, 60), 5, 0.86)
    m.material("l_molle", (98, 78, 54), 5, 0.9)
    m.material("l_elastic", (86, 72, 54), 4, 0.86)
    m.material("l_dark", (60, 50, 38), 3, 0.9)
    m.material("l_mag", (40, 40, 42), 4, 0.8)
    m.material("l_velcro", (112, 100, 72), 9, 0.92)
    m.material("l_patch", (62, 70, 52), 5, 0.9)
    m.material("l_shell", (160, 136, 100), 5, 0.80)    # tan helmet
    m.material("l_rail", (30, 30, 32), 4, 0.82)
    m.material("l_metal", (90, 92, 96), 5, 0.8)
    m.material("l_web", (40, 38, 34), 3, 0.9)
    _carrier(m)
    _helmet(m)
    return m
