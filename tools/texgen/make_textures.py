"""Procedural textures: scope lens/tube and eye-box shadow layers, ammo icons.  python tools/texgen/make_textures.py"""
from pathlib import Path

from PIL import Image, ImageDraw

A = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/textures"


# Scope textures. The reticle itself is drawn procedurally by ScopeOverlay (front focal plane:
# it is scaled each frame so 1 mil spans exactly 1 mrad of the world). These are the layers
# around it, filtered smoothly (.mcmeta blur) since they get stretched to the screen.
TUBE_EXTENT = 1.25    # scope_m2010.png: half the texture side = 1.25 x field radius
SHADOW_EXTENT = 2.0   # scope_shadow.png: half the texture side = 2.0 x field radius


def _smooth(e0, e1, x):
    import numpy as np
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def scope_tube(path, size=1024):
    """Lens and eyepiece around the field of view (r = 1 at the field stop): transparent glass
    with a faint dark-blue vignette toward the edge and a hint of chromatic fringe, then the
    inner tube wall as a thin dark-grey ring lit slightly from above, then black."""
    import numpy as np
    c = (size - 1) / 2
    y, x = np.mgrid[0:size, 0:size].astype(np.float64)
    dx, dy = (x - c) / (size / 2) * TUBE_EXTENT, (y - c) / (size / 2) * TUBE_EXTENT
    r = np.hypot(dx, dy)
    up = -dy / np.maximum(r, 1e-6)                                   # +1 at the top of the ring
    px = 1.0 / (size / 2) * TUBE_EXTENT                              # one texel in field radii
    rgb = np.zeros((size, size, 3))
    a = np.zeros((size, size))
    # glass: vignette (darkening + slight cool tint) over the outer ~26% of the radius
    v = _smooth(0.74, 1.0, r) ** 1.6 * 0.42
    rgb[:] = (6, 9, 16)
    a = v
    # chromatic edge: a whisper of blue just inside the field stop, warm just outside it
    fringe_in = np.exp(-((r - 0.988) / 0.006) ** 2) * 0.16
    rgb = rgb * (1 - fringe_in[..., None]) + np.array((40, 70, 150)) * fringe_in[..., None]
    a = np.maximum(a, fringe_in)
    # tube wall: field stop (anti-aliased), a narrow dark-grey band lit from above, then black
    wall = _smooth(1.0 - px, 1.0 + px, r)
    t = np.clip((r - 1.0) / 0.035, 0, 1)
    shade = 40 - 18 * t + 8 * up * (1 - t) + 2 * np.sin(r * 1400) * (1 - t)
    shade = np.clip(shade, 0, 255)
    wall_rgb = np.stack([shade * 0.98, shade * 0.98, shade * 1.03], -1)
    warm = np.exp(-((r - 1.004) / 0.003) ** 2) * 0.15
    wall_rgb = wall_rgb * (1 - warm[..., None]) + np.array((110, 80, 50)) * warm[..., None]
    rgb = rgb * (1 - wall[..., None]) + wall_rgb * wall[..., None]
    a = a * (1 - wall) + wall
    # eyepiece housing beyond the wall: black, with a soft step down
    black = _smooth(1.03, 1.05, r)
    rgb = rgb * (1 - black[..., None])
    img = np.dstack([np.clip(rgb, 0, 255), np.clip(a, 0, 1) * 255]).round().astype(np.uint8)
    Image.fromarray(img, "RGBA").save(path)
    _blur_meta(path)


def scope_shadow(path, size=512):
    """Exit-pupil shadow: black with a soft round hole of the field's radius. Drawn offset from
    the centre when the eye leaves the eye box, it darkens one side of the view (a crescent)."""
    import numpy as np
    c = (size - 1) / 2
    y, x = np.mgrid[0:size, 0:size].astype(np.float64)
    r = np.hypot(x - c, y - c) / (size / 2) * SHADOW_EXTENT
    a = _smooth(0.97, 1.32, r)
    img = np.zeros((size, size, 4), np.uint8)
    img[..., 3] = (a * 255).round().astype(np.uint8)
    Image.fromarray(img, "RGBA").save(path)
    _blur_meta(path)


def _blur_meta(path):
    Path(str(path) + ".mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n')


def cartridges(path, case, case_dark, case_light, bullet, tip, rounds, long=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (x0, y0) in rounds:
        top = 0
        px[x0 + 1, y0] = tip
        for y in (1, 2) if not long else (1, 2, 3):
            for x in range(3):
                px[x0 + x, y0 + y] = bullet if x else (bullet[0] - 34, bullet[1] - 24, bullet[2] - 16, 255)
        start = 3 if not long else 4
        end = 10 if not long else 13
        for y in range(start, end):
            for x in range(3):
                px[x0 + x, y0 + y] = case_light if x == 2 else case_dark if x == 0 else case
        for x in range(3):
            px[x0 + x, y0 + end] = case_dark
        px[x0 + 1, y0 + end] = (120, 90, 40, 255)
    img.save(path)


def _shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def round_(px, x0, y0, w, bullet_len, case_len, case, bullet, tip=None, head=None, rim=(120, 90, 40, 255),
           ogive=True):
    """One cartridge standing upright, top-left at (x0, y0), w px wide: bullet (rounded or pointed
    ogive, optional coloured / steel tip), case (optional differently coloured head, e.g. the 6.8's
    steel head), dark extractor rim. Left column shaded dark, right column lit."""
    def col(c, x):
        return _shade(c, 0.78) if x == 0 else _shade(c, 1.15) if x == w - 1 else c
    y = y0
    for i in range(bullet_len):
        inset = 1 if (ogive and i == 0 and w >= 3) else 0
        for x in range(inset, w - inset):
            c = tip if (tip is not None and i == 0) else bullet
            px[x0 + x, y] = col(c, x)
        y += 1
    for i in range(case_len):
        c = head if (head is not None and i >= case_len - 2) else case
        for x in range(w):
            px[x0 + x, y] = col(c, x)
        y += 1
    for x in range(w):
        px[x0 + x, y] = _shade(rim, 0.8 if x in (0, w - 1) else 1.0)


def shell(px, x0, y0, w, hull_len, hull, brass, crimp=True):
    """A shotgun shell standing upright: plastic hull with a folded (crimped) top, brass head."""
    def col(c, x):
        return _shade(c, 0.75) if x == 0 else _shade(c, 1.2) if x == w - 1 else c
    for i in range(hull_len):
        for x in range(w):
            c = _shade(hull, 0.7) if (crimp and i == 0 and x % 2 == 1) else hull
            px[x0 + x, y0 + i] = col(c, x)
    for i in range(3):
        for x in range(w):
            px[x0 + x, y0 + hull_len + i] = col(brass if i < 2 else _shade(brass, 0.7), x)


def ammo_icons():
    """Ammo item icons (16x16) for the cartridges added with the 25-gun update. Pistol rounds are
    short, magnum rifle rounds tall, the .50 BMG one fat round across the whole icon, shotgun
    shells plastic hulls with brass heads."""
    brass = (196, 154, 70, 255)
    copper = (184, 110, 66, 255)
    gilding = (190, 128, 78, 255)
    steel = (150, 152, 158, 255)
    nickel = (205, 208, 212, 255)

    def icon(name, draw):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        draw(img.load())
        img.save(A / "item" / f"{name}.png")

    # 9x19 M882: three stubby round-nose rounds
    icon("ammo_9x19", lambda px: [round_(px, x, y, 3, 2, 4, brass, gilding) for x, y in ((2, 6), (7, 4), (12, 7))][0])
    # 6.8x51 XM1186: brass body with the hybrid case's steel head, exposed steel penetrator tip
    icon("ammo_68x51", lambda px: [round_(px, x, y, 3, 3, 8, brass, copper, tip=steel, head=steel) for x, y in ((3, 1), (9, 3))][0])
    # 7.62x51 M80A1 EPR: brass case, copper jacket with the exposed steel penetrator tip
    icon("ammo_762x51", lambda px: [round_(px, x, y, 3, 3, 8, brass, copper, tip=steel) for x, y in ((3, 1), (9, 3))][0])
    # 7.62x51 M118LR: long match OTM bullet, no tip colour, slightly darker match brass
    icon("ammo_762_m118lr", lambda px: [round_(px, x, y, 3, 4, 8, _shade(brass, 0.92), copper) for x, y in ((3, 1), (9, 2))][0])
    # .338 Lapua Magnum: tall fat magnum case, long copper Scenar
    icon("ammo_338lm", lambda px: [round_(px, x, y, 3, 4, 10, brass, copper) for x, y in ((3, 0), (9, 1))][0])
    # .338 Norma Magnum: shorter case, longer 300 gr bullet, nickel-plated case to tell it apart
    icon("ammo_338nm", lambda px: [round_(px, x, y, 3, 5, 9, nickel, copper) for x, y in ((3, 0), (9, 1))][0])
    # .50 BMG M33: one big round filling the icon
    icon("ammo_50bmg", lambda px: round_(px, 5, 0, 5, 5, 10, brass, copper))
    # 12 gauge 00 buck: two shells, red hull and brass head
    icon("ammo_12ga_00", lambda px: [shell(px, x, y, 5, 9, (170, 40, 36, 255), brass) for x, y in ((1, 2), (9, 4))][0])
    # 7.62x39 M43: lacquered steel case (the Soviet green-grey), copper-washed bullet, a bit shorter than 7.62 NATO
    lacquer = (112, 118, 84, 255)
    icon("ammo_762x39", lambda px: [round_(px, x, y, 3, 3, 7, lacquer, copper) for x, y in ((3, 2), (9, 4))][0])
    # .45 ACP: three fat, short round-nose rounds
    icon("ammo_45acp", lambda px: [round_(px, x, y, 4, 2, 4, brass, gilding) for x, y in ((1, 6), (6, 4), (11, 7))][0])


if __name__ == "__main__":
    (A / "misc").mkdir(parents=True, exist_ok=True)
    scope_tube(A / "misc/scope_m2010.png")
    scope_shadow(A / "misc/scope_shadow.png")
    brass = ((196, 154, 70, 255), (150, 112, 44, 255), (226, 190, 110, 255))
    # .300 Win Mag Mk 248: long magnum case, copper OTM bullet, no colour tip
    cartridges(A / "item/ammo_300wm.png", *brass, (184, 110, 66, 255), (170, 100, 60, 255),
               [(3, 1), (9, 2)], long=True)
    ammo_icons()
    print("textures ok")
