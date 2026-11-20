"""Gunsmith Table textures: block faces (16x16 pixel art) and the GUI background.

    python tools/texgen/gunsmith_textures.py
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

A = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/textures"


def noise(img, box, base, amp, seed):
    rnd = random.Random(seed)
    px = img.load()
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = rnd.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


def planks(img, seed, base=(136, 98, 58)):
    noise(img, (0, 0, 16, 16), base, 7, seed)
    px = img.load()
    for y in (3, 7, 11, 15):  # plank seams
        for x in range(16):
            px[x, y] = tuple(int(c * 0.62) for c in base) + (255,)
    rnd = random.Random(seed + 1)
    for row in range(4):
        x = rnd.randint(2, 13)
        px[x, row * 4 + 1] = tuple(int(c * 0.7) for c in base) + (255,)


def block_textures():
    out = A / "block"
    out.mkdir(parents=True, exist_ok=True)
    # top: dark green cleaning mat on a wooden frame, a rifle outline + a few tools
    top = Image.new("RGBA", (16, 16))
    noise(top, (0, 0, 16, 16), (126, 90, 52), 6, 1)
    noise(top, (1, 1, 15, 15), (46, 74, 52), 4, 2)
    d = ImageDraw.Draw(top)
    steel, dark = (150, 152, 158, 255), (40, 40, 44, 255)
    d.line([(3, 6), (12, 6)], fill=dark)          # rifle outline: barrel+receiver
    d.line([(9, 7), (9, 9)], fill=dark)           # grip
    d.line([(7, 7), (7, 9)], fill=dark)           # mag
    d.line([(2, 6), (2, 8)], fill=dark)           # stock
    d.line([(3, 12), (6, 12)], fill=steel)        # screwdriver
    d.point((7, 12), fill=(180, 40, 40, 255))
    d.rectangle([10, 11, 12, 13], fill=(160, 130, 60, 255))  # box of rounds
    d.point((11, 11), fill=(210, 170, 80, 255))
    top.save(out / "gunsmith_table_top.png")
    # sides: wooden table with a drawer
    side = Image.new("RGBA", (16, 16))
    planks(side, 3)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 2], fill=(98, 70, 40, 255))
    d.rectangle([3, 6, 12, 10], outline=(74, 52, 30, 255))
    d.line([(7, 8), (8, 8)], fill=(170, 170, 176, 255))     # drawer handle
    d.rectangle([0, 13, 1, 15], fill=(74, 52, 30, 255))
    d.rectangle([14, 13, 15, 15], fill=(74, 52, 30, 255))
    side.save(out / "gunsmith_table_side.png")
    # front: pegboard with tools hanging
    front = Image.new("RGBA", (16, 16))
    planks(front, 5)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 2], fill=(98, 70, 40, 255))
    noise(front, (1, 4, 15, 12), (176, 150, 110), 4, 6)
    px = front.load()
    for y in range(5, 12, 2):
        for x in range(2, 15, 2):
            px[x, y] = (120, 100, 70, 255)
    d.line([(3, 5), (3, 10)], fill=(150, 152, 158, 255))     # wrench
    d.point((3, 5), fill=(90, 90, 96, 255))
    d.line([(7, 6), (7, 10)], fill=(70, 70, 74, 255))         # hammer handle
    d.line([(6, 6), (8, 6)], fill=(150, 152, 158, 255))
    d.rectangle([10, 6, 13, 8], fill=(46, 46, 50, 255))       # scope
    d.point((13, 7), fill=(120, 170, 200, 255))
    front.save(out / "gunsmith_table_front.png")


def bevel_panel(d, box, fill=(198, 198, 198, 255)):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=fill)
    d.line([(x0, y0), (x1 - 1, y0)], fill=(255, 255, 255, 255))
    d.line([(x0, y0), (x0, y1 - 1)], fill=(255, 255, 255, 255))
    d.line([(x0 + 1, y1), (x1, y1)], fill=(85, 85, 85, 255))
    d.line([(x1, y0 + 1), (x1, y1)], fill=(85, 85, 85, 255))


def slot(d, x, y, big=False):
    s = 26 if big else 18
    o = 5 if big else 1
    x, y = x - o, y - o
    d.rectangle([x, y, x + s - 1, y + s - 1], fill=(139, 139, 139, 255))
    d.line([(x, y), (x + s - 2, y)], fill=(55, 55, 55, 255))
    d.line([(x, y), (x, y + s - 2)], fill=(55, 55, 55, 255))
    d.line([(x + 1, y + s - 1), (x + s - 1, y + s - 1)], fill=(255, 255, 255, 255))
    d.line([(x + s - 1, y + 1), (x + s - 1, y + s - 1)], fill=(255, 255, 255, 255))


def gui():
    """GUI background. Coordinates match GunsmithMenu (slots, WIDTH/HEIGHT) and GunsmithScreen (result panel)."""
    out = A / "gui"
    out.mkdir(parents=True, exist_ok=True)
    W, H = 252, 196
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W - 1, H - 1], fill=(0, 0, 0, 255))
    bevel_panel(d, (1, 1, W - 2, H - 2))
    # the bench: a dark green felt mat the gun goes on
    bx0, by0, bx1, by1 = 7, 15, 145, 101
    d.rectangle([bx0, by0, bx1, by1], fill=(56, 84, 62, 255), outline=(36, 56, 40, 255))
    rnd = random.Random(9)
    px = img.load()
    for y in range(by0 + 1, by1):
        for x in range(bx0 + 1, bx1):
            if rnd.random() < 0.15:
                c = px[x, y]
                px[x, y] = (c[0] - 6, c[1] - 6, c[2] - 6, 255)
    # the gun slot in the middle (big) and the part slots where the parts mount
    slot(d, 68, 50, big=True)
    for x, y in ((68, 22), (124, 50), (100, 78), (100, 28), (14, 50), (62, 78)):
        slot(d, x, y)
    # arrow -> result
    ay = 58
    d.rectangle([149, ay - 2, 160, ay + 2], fill=(139, 139, 139, 255))
    d.polygon([(159, ay - 7), (168, ay), (159, ay + 7)], fill=(139, 139, 139, 255))
    d.line([(149, ay + 2), (159, ay + 2)], fill=(85, 85, 85, 255))
    d.line([(159, ay + 7), (168, ay)], fill=(85, 85, 85, 255))
    # the result panel: dark, inset, preview on top and stats below
    px0, py0, px1, py1 = 172, 15, 246, 101
    d.rectangle([px0, py0, px1 - 1, py1 - 1], fill=(36, 40, 46, 255))
    d.line([(px0, py0), (px1 - 1, py0)], fill=(55, 55, 55, 255))
    d.line([(px0, py0), (px0, py1 - 1)], fill=(55, 55, 55, 255))
    d.line([(px0 + 1, py1 - 1), (px1 - 1, py1 - 1)], fill=(255, 255, 255, 255))
    d.line([(px1 - 1, py0 + 1), (px1 - 1, py1 - 1)], fill=(255, 255, 255, 255))
    d.line([(px0 + 4, 58), (px1 - 5, 58)], fill=(58, 64, 72, 255))
    # player inventory, centred
    for r in range(3):
        for c in range(9):
            slot(d, 45 + c * 18, 114 + r * 18)
    for c in range(9):
        slot(d, 45 + c * 18, 172)
    img.save(out / "gunsmith.png")


if __name__ == "__main__":
    block_textures()
    gui()
    print("gunsmith textures ok")
