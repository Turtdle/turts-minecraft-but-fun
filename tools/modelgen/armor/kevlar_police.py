"""Police / civilian tier: a concealable NIJ IIIA soft vest in a black-navy external carrier (front
and back panels, side wrap, wide shoulder straps, no placards or lettering, radio pouch
with antenna, shoulder mic, body-cam, badge, belt pouches) and a black PASGT-cut "Fritz" helmet
(rounded crown, small brow brim, flared sides / back over the ears, chin strap).

Wearer faces -Z, +X = wearer's right. Soft armour: only ~1 unit thick.
"""
import armor as A

MODEL = "kevlar_police"
PIECES = {'police_vest': 'chest', 'police_helmet': 'head'}


def _sym(b, a, c, mat, **kw):
    for s in (1, -1):
        b.span((s * a[0], a[1], a[2]), (s * c[0], c[1], c[2]), mat, **kw)


def _vest(m):
    b = m.bones[A.BODY_BONE]
    F, K = -3.0, 3.0                                             # outer face of the front / back panel
    # side wrap (cummerbund), lower profile than the panels
    b.span((-4.8, 13.4, -2.6), (4.8, 19.6, 2.6), "p_wrap")
    # front panel: belly + chest, scooped neck, wide shoulder straps
    b.span((-4.4, 13.0, F), (4.4, 21.4, -1.9), "p_carrier")
    b.span((-1.5, 21.4, F), (1.5, 22.3, -1.9), "p_carrier")
    _sym(b, (1.5, 21.4, F), (4.0, 24.0, -1.9), "p_strap")
    # back panel: rides a little higher
    b.span((-4.4, 13.0, 1.9), (4.4, 21.4, K), "p_carrier")
    b.span((-1.5, 21.4, 1.9), (1.5, 23.0, K), "p_carrier")
    _sym(b, (1.5, 21.4, 1.9), (4.0, 24.0, K), "p_strap")
    # velcro cummerbund flap across the belly
    b.span((-4.5, 13.4, F - 0.2), (4.5, 16.6, F), "p_wrap")
    # radio pouch on the wearer's left, radio top + antenna
    b.span((-4.2, 13.8, F - 1.2), (-2.5, 17.4, F), "p_pouch")
    b.span((-3.95, 17.4, F - 1.0), (-2.75, 18.2, F - 0.2), "p_gear")
    b.span((-3.95, 18.2, F - 0.9), (-3.55, 21.0, F - 0.5), "p_gear")
    # shoulder mic on the wearer's right strap
    b.span((2.1, 22.0, F - 0.5), (3.3, 23.5, F), "p_gear")
    b.span((2.4, 22.3, F - 0.56), (3.0, 22.9, F - 0.5), "p_grille")
    # body-cam in the middle of the chest, badge on the left breast
    b.span((-0.75, 21.1, F - 0.5), (0.75, 22.2, F), "p_gear")
    b.span((-0.3, 21.45, F - 0.6), (0.3, 21.95, F - 0.5), "p_lens")
    b.span((-3.3, 21.5, F - 0.15), (-2.3, 22.6, F), "p_badge")
    # cuff / magazine pouches on the right of the belly
    for x in (0.9, 2.5):
        b.span((x, 13.7, F - 0.9), (x + 1.4, 16.2, F - 0.2), "p_pouch")
        b.span((x - 0.05, 15.4, F - 1.0), (x + 1.45, 16.3, F - 0.9), "p_strap")
    # back: drag handle
    b.span((-1.1, 21.6, K), (1.1, 22.3, K + 0.25), "p_wrap")


def _helmet(m):
    h = m.bones[A.HEAD]
    # crown: two crossed boxes (chamfered corners) + stepped top = rounded Fritz dome
    h.span((-4.8, 28.6, -4.2), (4.8, 31.8, 4.2), "p_shell")
    h.span((-4.2, 28.6, -4.8), (4.2, 31.8, 4.8), "p_shell")
    h.span((-4.4, 31.8, -3.8), (4.4, 32.7, 3.8), "p_shell")
    h.span((-3.8, 31.8, -4.4), (3.8, 32.7, 4.4), "p_shell")
    h.span((-3.3, 32.7, -3.3), (3.3, 33.3, 3.3), "p_shell")
    # small brim over the brow
    h.span((-4.3, 28.6, -5.4), (4.3, 29.3, -4.8), "p_shell")
    # flared skirt over the ears and the nape, stepping down from the temples
    h.span((-4.95, 27.5, -3.0), (4.95, 28.6, -1.2), "p_shell")
    h.span((-5.2, 25.6, -1.2), (5.2, 28.6, 4.4), "p_shell")
    h.span((-4.5, 25.6, 3.0), (4.5, 28.6, 5.2), "p_shell")
    # rubber edge trim along the flare
    h.span((-5.3, 25.4, -1.3), (5.3, 25.9, 4.5), "p_trim")
    h.span((-4.6, 25.4, 3.0), (4.6, 25.9, 5.3), "p_trim")
    # chin strap: cheek straps, jaw strap, chin cup
    _sym(h, (4.5, 24.9, -2.4), (4.8, 27.5, -1.7), "p_web")
    _sym(h, (4.5, 24.3, -4.8), (4.8, 24.9, -1.7), "p_web")
    h.span((-4.8, 24.3, -4.85), (4.8, 24.9, -4.55), "p_web")
    h.span((-1.5, 24.15, -5.0), (1.5, 25.1, -4.85), "p_trim")


def model():
    m = A.new_armor(MODEL)
    m.material("p_carrier", (30, 34, 48), 4, 0.82)     # black-navy cordura
    m.material("p_strap", (24, 27, 38), 3, 0.84)
    m.material("p_wrap", (20, 22, 30), 3, 0.86)
    m.material("p_pouch", (36, 40, 54), 4, 0.78)
    m.material("p_gear", (16, 16, 18), 3, 0.85)
    m.material("p_grille", (60, 62, 66), 4, 0.9)
    m.material("p_lens", (70, 110, 150), 4, 0.9)
    m.material("p_badge", (206, 170, 70), 8, 0.85)
    m.material("p_shell", (36, 37, 42), 4, 0.78)       # black helmet
    m.material("p_trim", (14, 14, 16), 2, 0.9)
    m.material("p_web", (44, 44, 46), 3, 0.9)
    _vest(m)
    _helmet(m)
    return m
