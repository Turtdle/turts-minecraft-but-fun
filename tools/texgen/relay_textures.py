"""Drone Relay Antenna block texture (one 16x16 atlas used by models/block/drone_relay.json).

    python tools/texgen/relay_textures.py

Atlas layout (pixels, x0 y0 x1 y1):
    0  0 10  4   radio box front  (grille, knob, LED socket)
    0  4 10  8   radio box back / sides (ribbed olive panel)
    0  8 10 16   radio box top    (panel seam, mast socket)
   10  0 12 16   mast             (grey tube with clamp bands)
   12  0 13 16   whip antenna     (black, lighter tip at the top)
   13  0 16  8   dark rubber      (end bumpers, antenna head, underside)
   13  8 16 11   status LED       (bright green)
   13 11 16 16   connector steel
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

A = Path(__file__).resolve().parents[2] / "src/main/resources/assets/modernarmory/textures"

OLIVE = (86, 94, 58)
OLIVE_DARK = (58, 64, 40)
OLIVE_LIGHT = (108, 116, 74)
RUBBER = (44, 46, 48)
RUBBER_DARK = (28, 29, 31)
RUBBER_LIGHT = (62, 64, 67)
STEEL = (128, 132, 138)
STEEL_DARK = (84, 88, 94)
WHIP = (30, 31, 34)
LED = (96, 240, 110)


def noise(img, box, base, amp, seed):
    rnd = random.Random(seed)
    px = img.load()
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = rnd.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


def c(rgb):
    return tuple(rgb) + (255,)


def relay_texture():
    out = A / "block"
    out.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (16, 16), c(RUBBER))
    d = ImageDraw.Draw(img)

    # --- box front (0,0)-(10,4): olive panel, speaker grille, knob, LED socket
    noise(img, (0, 0, 10, 4), OLIVE, 4, 11)
    d.line([(0, 0), (9, 0)], fill=c(OLIVE_LIGHT))            # top edge highlight
    d.line([(0, 3), (9, 3)], fill=c(OLIVE_DARK))             # bottom edge shadow
    for x in (2, 3):                                         # grille slots
        d.line([(x, 1), (x, 2)], fill=c(RUBBER_DARK))
    d.point((5, 2), fill=c(STEEL))                           # knob
    d.point((5, 1), fill=c(STEEL_DARK))
    d.point((7, 1), fill=c(RUBBER_DARK))                     # LED socket (the LED cube sits on it)

    # --- box back / sides (0,4)-(10,8): ribbed olive
    noise(img, (0, 4, 10, 8), OLIVE, 4, 12)
    d.line([(0, 4), (9, 4)], fill=c(OLIVE_LIGHT))
    d.line([(0, 7), (9, 7)], fill=c(OLIVE_DARK))
    for x in (2, 5, 8):                                      # cooling ribs
        d.line([(x, 5), (x, 6)], fill=c(OLIVE_DARK))

    # --- box top (0,8)-(10,16): outline, panel seam, mast socket, label plate
    noise(img, (0, 8, 10, 16), OLIVE, 4, 13)
    d.rectangle([0, 8, 9, 15], outline=c(OLIVE_DARK))
    d.line([(1, 9), (8, 9)], fill=c(OLIVE_LIGHT))
    d.rectangle([3, 10, 6, 13], outline=c(RUBBER_DARK))      # mast socket ring
    d.rectangle([7, 13, 8, 14], fill=c((178, 164, 96)))      # label plate
    d.point((1, 14), fill=c(STEEL))                          # screws
    d.point((1, 10), fill=c(STEEL))

    # --- mast (10,0)-(12,16): steel tube, lit left / shaded right, clamp bands
    noise(img, (10, 0, 11, 16), STEEL, 4, 14)
    noise(img, (11, 0, 12, 16), STEEL_DARK, 4, 15)
    for y in (0, 5, 10, 15):
        d.line([(10, y), (11, y)], fill=c(RUBBER_DARK))

    # --- whip (12,0)-(13,16): near-black rod, pale tip at the top
    noise(img, (12, 0, 13, 16), WHIP, 3, 16)
    d.point((12, 0), fill=c((196, 200, 204)))
    d.point((12, 1), fill=c(STEEL_DARK))

    # --- rubber (13,0)-(16,8): bumpers / antenna head
    noise(img, (13, 0, 16, 8), RUBBER, 3, 17)
    d.line([(13, 0), (15, 0)], fill=c(RUBBER_LIGHT))
    d.line([(13, 7), (15, 7)], fill=c(RUBBER_DARK))
    d.point((14, 3), fill=c(RUBBER_LIGHT))                   # moulded grip nub
    d.point((14, 5), fill=c(RUBBER_LIGHT))

    # --- LED (13,8)-(16,11)
    d.rectangle([13, 8, 15, 10], fill=c(LED))
    d.point((14, 9), fill=c((214, 255, 220)))

    # --- connector steel (13,11)-(16,16)
    noise(img, (13, 11, 16, 16), STEEL_DARK, 4, 18)
    d.line([(13, 11), (15, 11)], fill=c(STEEL))

    img.save(out / "drone_relay.png")


if __name__ == "__main__":
    relay_texture()
    print("relay textures ok")
