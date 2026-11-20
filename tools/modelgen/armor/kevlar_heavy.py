"""Heavy military tier: US Army IOTV Gen IV in OCP / Scorpion W2 (thick full torso wrap, front and
back ESAPI plate bulges, side ESBI plate pouches, high collar and throat yoke, deltoid protectors on
the upper arms, groin flap, MOLLE rows, three magazine pouches, IFAK, quick-release handle, name
tapes) and an ECH full-cut helmet (covers the ears, OCP cover, NVG shroud, side rails, helmet band
with cat-eyes, chin strap).

Wearer faces -Z, +X = wearer's right. The camouflage is a tan base with thin green / brown / dark /
cream blotches laid on the faces (seeded, so every build is identical).
"""
import random

import armor as A

MODEL = "kevlar_heavy"
PIECES = {'iotv': 'chest', 'ech_helmet': 'head'}

CAMO = ("h_green", "h_green", "h_brown", "h_brown", "h_dark", "h_cream")


def _sym(b, a, c, mat, **kw):
    for s in (1, -1):
        b.span((s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, **kw)


def _camo(b, rng, axis, at, out, u0, u1, v0, v1, n):
    """`n` camouflage blotches on the flat face at `axis` = `at` whose outside is the `out` (+1/-1)
    direction. (u, v) = (x, y) on a z face, (z, y) on an x face, (x, z) on a y face."""
    placed, tries = [], 0
    while len(placed) < n and tries < 300:
        tries += 1
        w, h = rng.choice((1.0, 1.5, 2.0, 2.5)), rng.choice((0.75, 1.0, 1.5))
        if rng.random() < 0.3:
            w, h = h, w
        if w > u1 - u0 or h > v1 - v0:
            continue
        u = u0 + round(rng.uniform(0, u1 - u0 - w) * 4) / 4
        v = v0 + round(rng.uniform(0, v1 - v0 - h) * 4) / 4
        if any(u < pu + pw + 0.25 and pu < u + w + 0.25 and v < pv + ph + 0.25 and pv < v + h + 0.25
               for pu, pv, pw, ph in placed):
            continue
        placed.append((u, v, w, h))
        lo, hi = at - 0.1 * out, at + 0.06 * out
        mat = rng.choice(CAMO)
        if axis == "z":
            b.span((u, v, lo), (u + w, v + h, hi), mat)
        elif axis == "x":
            b.span((lo, v, u), (hi, v + h, u + w), mat)
        else:
            b.span((u, lo, v), (u + w, hi, v + h), mat)


def _vest(m, rng):
    b = m.bones[A.BODY_BONE]
    F, K = -3.2, 3.2                                             # outer face of the soft shell
    PF, PK = -4.0, 4.0                                           # outer face of the plate bulges
    b.span((-5.0, 12.6, F), (5.0, 24.0, K), "h_base")            # full torso wrap
    # ESAPI plate bulges, SAPI cut
    b.span((-3.5, 15.8, PF), (3.5, 21.2, F), "h_base")
    b.span((-2.8, 21.2, PF), (2.8, 22.7, F), "h_base")
    b.span((-3.5, 15.0, K), (3.5, 21.4, PK), "h_base")
    b.span((-2.8, 21.4, K), (2.8, 22.9, PK), "h_base")
    # ESBI side plate pouches
    _sym(b, (3.7, 13.0, -3.7), (5.25, 18.4, 3.7), "h_pouch")
    # collar: throat yoke at the front, neck protector round the sides and back
    b.span((-4.4, 23.0, -4.7), (4.4, 24.7, F), "h_base")
    _sym(b, (4.25, 23.6, -1.0), (5.4, 25.6, 5.4), "h_base")
    b.span((-4.25, 23.2, 4.3), (4.25, 26.2, 5.4), "h_base")
    # groin protector
    b.span((-2.3, 9.3, PF), (2.3, 12.6, -3.3), "h_base")
    b.span((-2.3, 12.0, PF - 0.1), (2.3, 12.5, PF), "h_molle", pattern="lines")
    # MOLLE rows on the plates
    for y in (17.0, 18.4):
        b.span((-3.2, y, PF - 0.1), (3.2, y + 0.5, PF), "h_molle", pattern="lines")
    for y in (15.6, 17.0, 18.4, 19.8):
        b.span((-3.2, y, PK), (3.2, y + 0.5, PK + 0.1), "h_molle", pattern="lines")
    # three magazine pouches across the belly
    for c in (-2.25, 0.0, 2.25):
        b.span((c - 0.95, 13.0, -4.8), (c + 0.95, 16.3, F), "h_pouch")
        b.span((c - 1.0, 15.3, -4.92), (c + 1.0, 16.4, -4.8), "h_flap")
        b.span((c - 0.25, 14.7, -4.98), (c + 0.25, 15.5, -4.92), "h_web")
    # quick-release handle, rank patch (no name tapes or lettering)
    b.span((-1.0, 22.2, PF - 0.35), (1.0, 22.9, PF), "h_web")
    b.span((-0.5, 22.0, PF - 0.4), (0.5, 22.3, PF - 0.35), "h_red")
    b.span((-0.5, 19.6, PF - 0.08), (0.5, 20.5, PF), "h_web")
    # IFAK on the back, drag handle
    b.span((-2.1, 12.8, K), (2.1, 14.8, 4.7), "h_pouch")
    b.span((-2.15, 14.2, 4.7), (2.15, 14.9, 4.8), "h_flap")
    b.span((-1.1, 22.9, K), (1.1, 23.2, PK + 0.2), "h_web")
    # camouflage
    _camo(b, rng, "z", PF, -1, -3.5, 3.5, 19.0, 21.0, 3)         # front plate
    _camo(b, rng, "z", F, -1, 3.6, 5.0, 18.6, 23.0, 2)           # shell beside the plate
    _camo(b, rng, "z", F, -1, -5.0, -3.6, 18.6, 23.0, 2)
    _camo(b, rng, "z", -3.7, -1, 3.7, 5.25, 13.0, 18.4, 2)       # side pouches
    _camo(b, rng, "z", -3.7, -1, -5.25, -3.7, 13.0, 18.4, 2)
    _camo(b, rng, "z", PF, -1, -2.3, 2.3, 9.3, 11.9, 3)          # groin flap
    _camo(b, rng, "z", -4.7, -1, -4.4, 4.4, 23.0, 24.7, 2)       # throat yoke
    _camo(b, rng, "z", PK, 1, -3.5, 3.5, 20.4, 21.4, 2)          # back plate
    _camo(b, rng, "z", K, 1, 3.6, 5.0, 18.6, 23.0, 1)
    _camo(b, rng, "z", K, 1, -5.0, -3.6, 18.6, 23.0, 1)
    _camo(b, rng, "z", 3.7, 1, 3.7, 5.25, 13.0, 18.4, 1)
    _camo(b, rng, "z", 3.7, 1, -5.25, -3.7, 13.0, 18.4, 1)
    _camo(b, rng, "z", 5.4, 1, -4.25, 4.25, 23.2, 26.2, 3)       # collar back
    # deltoid protectors on the upper arms
    for s, bone in ((1, A.R_ARM), (-1, A.L_ARM)):
        a = m.bones[bone]
        a.span((s * 4.6, 19.4, -2.9), (s * 9.0, 24.9, 2.9), "h_base")
        a.span((s * 5.4, 18.2, -2.5), (s * 9.0, 19.4, 2.5), "h_base")
        _camo(a, rng, "x", s * 9.0, s, -2.9, 2.9, 19.4, 21.4, 2)
        _camo(a, rng, "x", s * 9.0, s, -2.9, 2.9, 23.4, 24.9, 1)
        _camo(a, rng, "z", -2.9, -1, min(s * 5.2, s * 9.0), max(s * 5.2, s * 9.0), 19.4, 24.9, 1)
        _camo(a, rng, "z", 2.9, 1, min(s * 5.2, s * 9.0), max(s * 5.2, s * 9.0), 19.4, 24.9, 1)
        _camo(a, rng, "y", 24.9, 1, min(s * 5.4, s * 9.0), max(s * 5.4, s * 9.0), -2.9, 2.9, 2)


def _helmet(m, rng):
    h = m.bones[A.HEAD]
    # full-cut shell: crown + skirt down over the ears and the nape
    h.span((-4.8, 29.0, -4.3), (4.8, 32.8, 4.3), "h_base")
    h.span((-4.3, 28.6, -4.8), (4.3, 32.8, 4.8), "h_base")
    h.span((-3.8, 32.8, -3.8), (3.8, 33.4, 3.8), "h_base")
    h.span((-4.8, 27.8, -2.8), (4.8, 29.0, -0.6), "h_base")
    h.span((-4.8, 26.4, -0.6), (4.8, 29.0, 4.3), "h_base")
    h.span((-4.3, 26.4, 3.0), (4.3, 28.6, 4.8), "h_base")
    # NVG shroud, side rails
    h.span((-1.1, 29.2, -5.25), (1.1, 31.5, -4.8), "h_black")
    h.span((-0.5, 29.7, -5.35), (0.5, 30.7, -5.25), "h_metal")
    _sym(h, (4.8, 28.3, -3.5), (5.2, 29.4, 0.8), "h_black", pattern="rails")
    # helmet band with cat-eyes
    h.span((-4.9, 30.4, -4.4), (4.9, 31.0, 4.4), "h_band")
    h.span((-4.4, 30.4, -4.9), (4.4, 31.0, 4.9), "h_band")
    _sym(h, (0.5, 30.45, 4.9), (1.5, 30.95, 4.97), "h_cat")
    # chin strap
    _sym(h, (4.5, 24.9, -2.5), (4.75, 27.8, -1.9), "h_web")
    _sym(h, (4.5, 24.3, -4.8), (4.75, 24.9, -1.9), "h_web")
    h.span((-4.75, 24.75, -4.95), (4.75, 25.35, -4.75), "h_web")
    # camouflage cover
    for s in (1, -1):
        _camo(h, rng, "x", s * 4.8, s, -4.3, 4.3, 31.1, 32.8, 2)
        _camo(h, rng, "x", s * 4.8, s, -0.6, 4.3, 26.4, 30.3, 2)
    _camo(h, rng, "y", 33.4, 1, -3.8, 3.8, -3.8, 3.8, 4)
    _camo(h, rng, "z", 4.8, 1, -4.3, 4.3, 31.1, 32.8, 2)
    _camo(h, rng, "z", 4.8, 1, -4.3, 4.3, 26.4, 28.6, 2)
    _camo(h, rng, "z", -4.8, -1, -4.3, -1.3, 31.1, 32.8, 1)
    _camo(h, rng, "z", -4.8, -1, 1.3, 4.3, 31.1, 32.8, 1)


def model():
    m = A.new_armor(MODEL)
    m.material("h_base", (150, 143, 110), 6, 0.86)     # OCP: light tan base
    m.material("h_green", (100, 108, 74), 5, 1.0)
    m.material("h_brown", (112, 90, 64), 5, 1.0)
    m.material("h_dark", (70, 60, 46), 4, 1.0)
    m.material("h_cream", (184, 176, 144), 5, 1.0)
    m.material("h_pouch", (138, 131, 100), 6, 0.80)
    m.material("h_flap", (124, 118, 90), 5, 0.84)
    m.material("h_molle", (116, 110, 84), 5, 0.92)
    m.material("h_web", (78, 74, 56), 4, 0.9)
    m.material("h_band", (84, 82, 60), 4, 0.9)
    m.material("h_cat", (196, 214, 150), 4, 1.0)
    m.material("h_red", (150, 50, 40), 4, 1.0)
    m.material("h_black", (32, 32, 34), 4, 0.82)
    m.material("h_metal", (92, 94, 98), 5, 0.8)
    rng = random.Random(4)
    _vest(m, rng)
    _helmet(m, rng)
    return m
