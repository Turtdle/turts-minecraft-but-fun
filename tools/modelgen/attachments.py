"""Shared attachment models for every gun.

Each attachment is written ONCE here as `add_<id>(m, parent, origin)`. It builds a bone named
`att_<id>` (pivot = `origin`, the mount point in model units) under `parent` (a mount bone, or
`stock` / `magazine`), so it inherits that bone's animation. The game hides every `att_*` bone
unless that attachment is fitted; several attachments of one slot therefore overlap in the
model, which is expected.

All coordinates inside a function are RELATIVE to the mount point:
  * optics       - origin = top of the rail, X centre, Z = middle of the optic's base
  * muzzle       - origin = end of the barrel on the bore axis; the device grows toward -Z
  * underbarrel  - origin = bottom face of the bottom rail; the device hangs below (-Y)
  * side         - origin = outer face of the RIGHT rail (+X); the device grows toward +X
  * stock        - origin = the `stock` bone pivot (front of the buffer tube, bore axis)
  * magazine     - origin = the `magazine` bone pivot

Locators (empty bones inside the attachment bone, read by the game at runtime):
  * `sight_<id>`  every optic: optical axis on the REAR face (eyepiece / window rear)
  * `flash_<id>`  compensators / muzzle brakes: front end on the bore axis
  * `beam_laser`  laser: emitter front
"""
import math

from armorygen import Model

# id -> slot
SLOTS = {
    "red_dot": "optic", "holographic": "optic", "acog": "optic", "sniper_scope": "optic",
    "lpvo_m157": "optic", "micro_red_dot": "optic",
    "suppressor_556": "muzzle", "compensator_556": "muzzle", "suppressor_300": "muzzle",
    "suppressor_9mm": "muzzle", "suppressor_762": "muzzle", "suppressor_338": "muzzle",
    "compensator_762": "muzzle", "muzzle_brake_50": "muzzle",
    "suppressor_68_short": "muzzle", "suppressor_762_short": "muzzle",
    "vertical_grip": "underbarrel", "angled_grip": "underbarrel", "bipod": "underbarrel",
    "laser": "side",
    "stock_ctr": "stock",
    "pmag_40": "magazine",
}
# slot -> default-part bones the game hides while an attachment of that slot is fitted
# (only the ones that exist in a given gun apply)
REPLACES = {
    "optic": ("rear_sight",),
    "muzzle": ("muzzle_default",),
    "stock": ("stock_default",),
    "magazine": ("magazine_default",),
}
# optical axis height above the rail, per optic (handy for the Java side / docs)
OPTIC_AXIS_Y = {"red_dot": 1.25, "holographic": 1.25, "acog": 1.25, "sniper_scope": 1.0,
                "lpvo_m157": 1.25, "micro_red_dot": 1.0}


def loadout(m: Model, *ids: str) -> set:
    """Bone names to HIDE so that only the attachments `ids` show (plus the default parts they
    replace). `loadout(m)` = the bare gun. Use for `m.preview_configs`."""
    for i in ids:
        if f"att_{i}" not in m.bones:
            raise ValueError(f"{m.name} has no attachment {i!r}")
    hidden = {n for n in m.bones if n.startswith("att_") and n[4:] not in ids}
    for i in ids:
        hidden |= {b for b in REPLACES.get(SLOTS[i], ()) if b in m.bones}
    return hidden


def _materials(m: Model):
    if "att_black" in m.materials:
        return
    m.material("att_black", (28, 28, 31), 4, 0.76)      # optics bodies
    m.material("att_metal", (46, 46, 51), 5, 0.74)      # rings, mounts, clamps
    m.material("att_can", (34, 34, 38), 4, 0.78)        # suppressors
    m.material("att_polymer", (40, 40, 43), 4, 0.80)    # Magpul furniture
    m.material("att_tan", (150, 127, 92), 6, 0.80)      # PEQ-15 housing
    m.material("att_shield", (104, 91, 70), 7, 0.80)    # suppressor heat-shield sleeve (burnt coyote)


class _Att:
    """The `att_<id>` bone with every coordinate offset by the mount point."""

    def __init__(self, m: Model, att_id: str, parent: str, origin):
        if parent not in m.bones:
            raise ValueError(f"mount bone {parent!r} missing for attachment {att_id!r}")
        _materials(m)
        self.m, self.id, self.o = m, att_id, tuple(map(float, origin))
        self.name = f"att_{att_id}"
        self.bone = m.bone(self.name, parent=parent, pivot=self.o)
        if att_id in SLOTS:  # one attachment per slot is shown: the z-fighting check skips pairs
            m.exclusive[self.name] = (SLOTS[att_id], att_id)

    def _p(self, v):
        return tuple(a + b for a, b in zip(v, self.o))

    def box(self, origin, size, mat, **kw):
        if kw.get("pivot") is not None:
            kw["pivot"] = self._p(kw["pivot"])
        self.bone.box(self._p(origin), size, mat, **kw)
        return self

    def cyl(self, center, diameter, length, mat, **kw):
        self.bone.cyl(self._p(center), diameter, length, mat, **kw)
        return self

    def wedge(self, start, end, width, height, mat, **kw):
        self.bone.wedge(self._p(start), self._p(end), width, height, mat, **kw)
        return self

    def locator(self, name, pos):
        self.m.locator(name, self._p(pos), parent=self.name)
        return self


# ======================================================================================
# OPTICS (origin: rail top, X centre, Z = middle of the base)
# ======================================================================================
def add_red_dot(m: Model, parent: str, origin):
    """Aimpoint CompM4 / M68 CCO: 30 mm tube on a QRP2 riser. The tube is HOLLOW (1x1 window)
    so the shooter looks through it; axis 1.25 above the rail (lower-1/3 co-witness)."""
    a = _Att(m, "red_dot", parent, origin)
    a.box((-1, 0, -1), (2, 0.5, 2), "att_metal")                            # QRP2 mount / riser
    a.box((1, 0, -0.5), (0.5, 0.5, 1), "att_metal", pattern="knurl")        # clamp knob (right)
    a.box((-0.75, 0.5, -2), (1.5, 0.25, 3.5), "att_black")                  # tube: bottom wall
    a.box((-0.75, 1.75, -2), (1.5, 0.25, 3.5), "att_black")                 # top wall
    a.box((-0.75, 0.75, -2), (0.25, 1, 3.5), "att_black")                   # left wall
    a.box((0.5, 0.75, -2), (0.25, 1, 3.5), "att_black")                     # right wall
    a.box((0.75, 0.75, -1.5), (0.5, 0.5, 1.25), "att_black", pattern="knurl")  # battery / switch housing
    a.box((-0.25, 2, -0.25), (0.5, 0.25, 0.5), "att_black", pattern="knurl")   # elevation cap
    a.locator("sight_red_dot", (0, 1.25, 1.5))
    return a.bone


def add_holographic(m: Model, parent: str, origin):
    """EOTech EXPS3: low base with a square hooded window (open, 1.5 x 1.5); axis 1.25 above the rail."""
    a = _Att(m, "holographic", parent, origin)
    a.box((-1, 0, -1.5), (2, 0.5, 3), "att_black")                          # base / electronics
    a.box((-1, 0.5, -1.5), (2, 0.5, 0.5), "att_black", pattern="lines")     # transverse battery cap (front)
    a.box((-1, 0.5, -1), (0.25, 1.5, 1.5), "att_black")                     # hood: left wall
    a.box((0.75, 0.5, -1), (0.25, 1.5, 1.5), "att_black")                   # right wall
    a.box((-1, 2, -1), (2, 0.25, 1.5), "att_black")                         # roof
    a.box((1, 0, -1), (0.25, 0.5, 1.5), "att_metal")                        # QD lever (right)
    a.box((-1.25, 0.1, 0.25), (0.25, 0.3, 1), "att_metal", pattern="lines")  # side buttons (left)
    a.locator("sight_holographic", (0, 1.25, 0.5))
    return a.bone


def add_acog(m: Model, parent: str, origin):
    """Trijicon TA31 4x32 ACOG: short boxy body, big objective, fibre-optic strip on top.
    Solid with glass lenses (a magnified optic: the game draws the picture)."""
    a = _Att(m, "acog", parent, origin)
    a.box((-0.5, 0, -1.5), (1, 0.5, 3), "att_metal")                        # TA51 mount
    a.box((0.5, 0, -1.25), (0.5, 0.5, 0.5), "att_metal", pattern="knurl")   # thumbscrews (right)
    a.box((0.5, 0, 0.75), (0.5, 0.5, 0.5), "att_metal", pattern="knurl")
    a.box((-0.5, 0.5, -1), (1, 1.5, 2), "att_black")                        # prism body
    a.box((-0.75, 0.5, -2.5), (1.5, 1.5, 1.5), "att_black")                 # objective housing
    a.box((-0.5, 0.75, -2.6), (1, 1, 0.1), "glass")                         # objective lens
    a.box((-0.75, 0.5, 1), (1.5, 1.5, 1), "att_black")                      # eyepiece
    a.box((-0.5, 0.75, 1.95), (1, 1, 0.1), "glass")                         # ocular lens
    a.box((-0.25, 2, -2), (0.5, 0.25, 2.5), "red")                          # fibre-optic light pipe
    a.box((0.5, 1, -0.5), (0.25, 0.5, 0.5), "att_black", pattern="knurl")   # windage adjuster
    a.locator("sight_acog", (0, 1.25, 2.05))
    return a.bone


def add_sniper_scope(m: Model, parent: str, origin):
    """Leupold Mark 4 6.5-20x50 LR/T on two rings; optical axis 1.0 above the rail.
    Runs from z-6.1 (objective) to z+6.05 (ocular) around the mount point."""
    a = _Att(m, "sniper_scope", parent, origin)
    a.box((-0.5, 0.5, -3), (1, 1, 6), "att_black")                          # main tube
    a.box((-1, 0, -6), (2, 2, 2.5), "att_black")                            # objective bell
    a.box((-0.75, 0.25, -3.5), (1.5, 1.5, 0.5), "att_black")                # bell taper
    a.box((-0.75, 0.25, -6.1), (1.5, 1.5, 0.1), "glass")                    # objective lens
    a.box((-0.75, 0.25, 3), (1.5, 1.5, 3), "att_black")                     # eyepiece
    a.box((-0.75, 0.25, 3.5), (1.5, 1.5, 0.5), "att_black", pattern="knurl", inflate=0.12)  # power ring
    a.box((-0.5, 0.5, 5.9), (1, 1, 0.15), "glass")                          # ocular lens
    a.box((-0.75, 0.25, -0.5), (1.5, 1.5, 1.5), "att_black")                # turret saddle
    a.box((-0.5, 1.75, -0.25), (1, 0.75, 1), "att_black", pattern="knurl")  # elevation turret
    a.box((0.75, 0.5, -0.25), (0.75, 1, 1), "att_black", pattern="knurl")   # windage turret (right)
    a.box((-1.5, 0.5, -0.25), (0.75, 1, 1), "att_black", pattern="knurl")   # side focus (left)
    a.box((-0.75, 0, -2), (1.5, 1.75, 0.75), "att_metal")                   # front ring
    a.box((-0.75, 0, 1.75), (1.5, 1.75, 0.75), "att_metal")                 # rear ring
    a.locator("sight_sniper_scope", (0, 1.0, 6.0))
    return a.bone


def add_lpvo_m157(m: Model, parent: str, origin):
    """Vortex XM157 NGSW-FC, 1-8x30 LPVO: fat boxy electronics housing round a 30 mm tube, the
    laser rangefinder / ballistic computer box on top-right reaching out over the objective,
    integral mount. Optical axis 1.25 above the rail (as the ACOG); runs from z-3.1 (objective)
    to z+3.55 (ocular). Solid with glass lenses (a magnified optic: the game draws the picture)."""
    a = _Att(m, "lpvo_m157", parent, origin)
    a.box((-0.75, 0, -2.25), (1.5, 0.5, 4.5), "att_metal")                  # integral mount
    a.box((0.75, 0, -1.75), (0.5, 0.5, 0.5), "att_metal", pattern="knurl")  # clamp nuts (right)
    a.box((0.75, 0, 1.25), (0.5, 0.5, 0.5), "att_metal", pattern="knurl")
    a.box((-1, 0.5, -2), (2, 1.75, 3.5), "att_black")                       # electronics housing
    a.box((-0.75, 0.5, -3), (1.5, 1.5, 1), "att_black")                     # objective
    a.box((-0.5, 0.75, -3.1), (1, 1, 0.1), "glass")                         # objective lens
    a.box((-0.5, 0.75, 1.5), (1, 1, 0.75), "att_black")                     # 30 mm tube
    a.box((-0.75, 0.5, 2.25), (1.5, 1.5, 1.25), "att_black")                # eyepiece
    a.box((-0.75, 0.5, 2.25), (1.5, 1.5, 0.5), "att_black", pattern="knurl", inflate=0.12)  # power ring
    a.box((-0.5, 0.75, 3.45), (1, 1, 0.1), "glass")                         # ocular lens
    a.box((0, 2.25, -2.75), (1.25, 0.75, 3), "att_black")                   # rangefinder / computer box (top-right)
    a.box((0.15, 2.4, -2.8), (0.95, 0.45, 0.05), "glass")                   # its emitter / receiver windows
    a.box((-1.2, 0.9, -1.25), (0.2, 1, 2), "att_metal", pattern="lines")    # control pad (left)
    a.box((-0.85, 2.25, -0.5), (0.75, 0.4, 1), "att_black", pattern="knurl")  # battery cap (top-left)
    a.locator("sight_lpvo_m157", (0, 1.25, 3.55))
    return a.bone


def add_micro_red_dot(m: Model, parent: str, origin):
    """Aimpoint Micro T-2 on a low mount: 1.5-wide HOLLOW tube (1x1 window) only 2 long, so the
    shooter looks through it; axis 1.0 above the rail."""
    a = _Att(m, "micro_red_dot", parent, origin)
    a.box((-0.75, 0, -1), (1.5, 0.25, 2), "att_metal")                      # low mount
    a.box((0.75, 0, -0.5), (0.4, 0.5, 1), "att_metal", pattern="knurl")     # clamp bolt (right)
    a.box((-0.75, 0.25, -1), (1.5, 0.25, 2), "att_black")                   # tube: bottom wall
    a.box((-0.75, 1.5, -1), (1.5, 0.25, 2), "att_black")                    # top wall
    a.box((-0.75, 0.5, -1), (0.25, 1, 2), "att_black")                      # left wall
    a.box((0.5, 0.5, -1), (0.25, 1, 2), "att_black")                        # right wall
    a.box((0.75, 0.75, -0.25), (0.4, 0.75, 0.75), "att_black", pattern="knurl")   # battery / brightness dial
    a.box((-0.25, 1.75, -0.25), (0.5, 0.2, 0.5), "att_black", pattern="knurl")    # elevation turret
    a.locator("sight_micro_red_dot", (0, 1.0, 1.0))
    return a.bone


# ======================================================================================
# MUZZLE (origin: end of the barrel on the bore axis; grows toward -Z)
# ======================================================================================
def add_suppressor_556(m: Model, parent: str, origin):
    """5.56 can (KAC NT4 style): 5.5 long, 2 across, knurled rear collar. No flash locator."""
    a = _Att(m, "suppressor_556", parent, origin)
    a.cyl((0, 0, -2.75), 2.0, 5.5, "att_can", sides=8)                      # body (2 cubes)
    a.cyl((0, 0, -0.5), 2.3, 0.75, "att_metal", sides=8, pattern="knurl")   # QD collar (2 cubes)
    a.box((-0.5, -0.5, -5.75), (1, 1, 0.25), "att_metal")                   # front cap
    return a.bone


def add_compensator_556(m: Model, parent: str, origin):
    """Short ported compensator, 2.25 long. `flash_compensator_556` at its front."""
    a = _Att(m, "compensator_556", parent, origin)
    a.box((-0.5, -0.5, -2), (1, 1, 2), "att_metal", inflate=0.25)                 # body
    a.box((-0.5, -0.5, -0.5), (1, 1, 0.5), "att_metal", pattern="knurl", inflate=0.4)  # jam nut
    a.box((-0.85, -0.25, -1.9), (1.7, 0.5, 0.5), "att_black")                     # side ports
    a.box((-0.85, -0.25, -1.2), (1.7, 0.5, 0.5), "att_black")
    a.box((-0.25, 0.25, -1.9), (0.5, 0.6, 1.2), "att_black")                      # top port
    a.box((-0.5, -0.5, -2.25), (1, 1, 0.25), "att_metal", inflate=0.1)            # crown
    a.locator("flash_compensator_556", (0, 0, -2.25))
    return a.bone


def add_suppressor_300(m: Model, parent: str, origin):
    """AAC Titan-QD (.300 Win Mag): 7.5 long, 2 across. No flash locator."""
    a = _Att(m, "suppressor_300", parent, origin)
    a.cyl((0, 0, -3.75), 2.0, 7.5, "att_can", sides=8)                      # body (2 cubes)
    a.box((-0.5, -0.5, -7.75), (1, 1, 0.25), "att_metal")                   # front cap
    return a.bone


def add_suppressor_9mm(m: Model, parent: str, origin):
    """9 mm pistol / SMG can: 5 long, 1.75 across, slim booster collar. No flash locator."""
    a = _Att(m, "suppressor_9mm", parent, origin)
    a.cyl((0, 0, -2.5), 1.75, 5.0, "att_can", sides=8)                      # body (2 cubes)
    a.cyl((0, 0, -0.3), 1.4, 0.6, "att_metal", sides=8, pattern="knurl")    # booster / piston housing
    a.box((-0.4, -0.4, -5.2), (0.8, 0.8, 0.2), "att_metal")                 # front cap
    return a.bone


def add_suppressor_762(m: Model, parent: str, origin):
    """.30-cal can (fits 7.62 NATO / 7.62x39 / 6.8 / .300 BLK; SureFire SOCOM762 style):
    6.5 long, 2.25 across, knurled QD collar. No flash locator."""
    a = _Att(m, "suppressor_762", parent, origin)
    a.cyl((0, 0, -3.25), 2.25, 6.5, "att_can", sides=8)                     # body (2 cubes)
    a.cyl((0, 0, -0.5), 2.6, 0.75, "att_metal", sides=8, pattern="knurl")   # QD collar (2 cubes)
    a.box((-0.5, -0.5, -6.75), (1, 1, 0.25), "att_metal")                   # front cap
    return a.bone


def add_suppressor_68_short(m: Model, parent: str, origin):
    """SIG SLX68-style short 6.8 can for the carbine: 4.5 long, 2 across, with a 2.5-wide
    heat-shield sleeve over the middle and a knurled QD collar. No flash locator."""
    a = _Att(m, "suppressor_68_short", parent, origin)
    a.cyl((0, 0, -2.25), 2.0, 4.5, "att_can", sides=8)                      # body (2 cubes)
    a.cyl((0, 0, -2.5), 2.5, 3.0, "att_shield", sides=8, pattern="slots")   # heat-shield sleeve (2 cubes)
    a.cyl((0, 0, -0.4), 2.3, 0.6, "att_metal", sides=8, pattern="knurl")    # QD collar (2 cubes)
    a.box((-0.5, -0.5, -4.7), (1, 1, 0.2), "att_metal")                     # flow-through front cap
    return a.bone


def add_suppressor_762_short(m: Model, parent: str, origin):
    """Short .30-cal can (Surgeon / SilencerCo SRM6 style): 4 long (60% of `suppressor_762`),
    a stubby 2.5 across, knurled QD collar. No flash locator."""
    a = _Att(m, "suppressor_762_short", parent, origin)
    a.cyl((0, 0, -2.0), 2.5, 4.0, "att_can", sides=8)                       # body (2 cubes)
    a.cyl((0, 0, -0.5), 2.8, 0.75, "att_metal", sides=8, pattern="knurl")   # QD collar (2 cubes)
    a.box((-0.5, -0.5, -4.25), (1, 1, 0.25), "att_metal")                   # front cap
    return a.bone


def add_suppressor_338(m: Model, parent: str, origin):
    """.338 magnum can (AI / TiTAN style): 8.5 long, 2.5 across, stepped rear over the brake.
    No flash locator."""
    a = _Att(m, "suppressor_338", parent, origin)
    a.cyl((0, 0, -4.25), 2.5, 8.5, "att_can", sides=8)                      # body (2 cubes)
    a.cyl((0, 0, -0.75), 2.85, 1.0, "att_metal", sides=8, pattern="knurl")  # locking collar (2 cubes)
    a.box((-0.6, -0.6, -8.75), (1.2, 1.2, 0.25), "att_metal")               # front cap
    return a.bone


def add_compensator_762(m: Model, parent: str, origin):
    """.30-cal ported compensator, 2.75 long, a size up from the 5.56 one.
    `flash_compensator_762` at its front."""
    a = _Att(m, "compensator_762", parent, origin)
    a.box((-0.5, -0.5, -2.5), (1, 1, 2.5), "att_metal", inflate=0.35)                  # body
    a.box((-0.5, -0.5, -0.5), (1, 1, 0.5), "att_metal", pattern="knurl", inflate=0.5)  # jam nut
    a.box((-0.95, -0.3, -2.35), (1.9, 0.6, 0.5), "att_black")                          # side ports
    a.box((-0.95, -0.3, -1.65), (1.9, 0.6, 0.5), "att_black")
    a.box((-0.95, -0.3, -0.95), (1.9, 0.6, 0.3), "att_black")
    a.box((-0.3, 0.3, -2.35), (0.6, 0.65, 1.5), "att_black")                           # top port
    a.box((-0.5, -0.5, -2.75), (1, 1, 0.25), "att_metal", inflate=0.15)                # crown
    a.locator("flash_compensator_762", (0, 0, -2.75))
    return a.bone


def add_muzzle_brake_50(m: Model, parent: str, origin):
    """.50 BMG arrowhead brake (Barrett M82/M107 style): 4 long, 4 wide, two swept baffles that
    vent to the sides. `flash_muzzle_brake_50` at its front. (There is no .50 suppressor.)"""
    a = _Att(m, "muzzle_brake_50", parent, origin)
    a.box((-0.75, -0.75, -1), (1.5, 1.5, 1), "att_metal", pattern="knurl")  # threaded collar
    a.box((-0.5, -0.5, -4), (1, 1, 3), "att_metal")                         # core tube
    a.box((-2, -0.75, -2), (4, 1.5, 0.5), "att_metal")                      # rear baffle
    a.box((-1.75, -0.75, -3.25), (3.5, 1.5, 0.5), "att_metal")              # front baffle
    a.box((-1, -0.75, -4), (2, 1.5, 0.5), "att_metal")                      # nose
    for sx in (-1, 1):                                                      # swept side plates
        x = -2 if sx < 0 else 1.5
        a.box((x, -0.75, -2.75), (0.5, 1.5, 0.75), "att_metal", rot=(0, sx * 20, 0),
              pivot=(sx * 1.75, 0, -2))
    a.box((-0.5, 0.75, -3.9), (1, 0.1, 2.4), "att_black")                   # top face shading (ports)
    a.locator("flash_muzzle_brake_50", (0, 0, -4))
    return a.bone


# ======================================================================================
# UNDERBARREL (origin: bottom face of the bottom rail; hangs below)
# ======================================================================================
def add_vertical_grip(m: Model, parent: str, origin):
    """KAC-style vertical foregrip, 3.25 tall."""
    a = _Att(m, "vertical_grip", parent, origin)
    a.box((-0.75, -0.5, -1), (1.5, 0.5, 2), "att_polymer")                  # rail clamp
    a.box((0.75, -0.5, -0.25), (0.25, 0.5, 0.5), "att_metal", pattern="knurl")  # clamp screw
    a.box((-0.5, -3, -0.5), (1, 2.5, 1), "att_polymer", pattern="lines")    # ribbed shaft
    a.box((-0.5, -3.25, -0.5), (1, 0.25, 1), "att_polymer", inflate=0.1)    # end cap
    return a.bone


def add_angled_grip(m: Model, parent: str, origin):
    """Magpul AFG: 4 long, a ramp that drops toward the rear, with a finger shelf in front."""
    a = _Att(m, "angled_grip", parent, origin)
    a.box((-0.75, -0.5, -2), (1.5, 0.5, 4), "att_polymer")                  # rail saddle
    a.box((-0.75, -1, -2), (1.5, 0.5, 0.5), "att_polymer")                  # front finger shelf
    a.wedge((0, -0.75, -1.6), (0, -1.6, 1.2), 1.5, 0.75, "att_polymer", pattern="lines")  # ramp
    a.box((-0.75, -2, 1), (1.5, 1.5, 1), "att_polymer", pattern="stipple")  # rear heel
    a.box((-0.75, -1.25, -0.25), (1.5, 0.75, 1.25), "att_polymer")          # fill between saddle and ramp
    return a.bone


def add_bipod(m: Model, parent: str, origin):
    """Harris bipod, legs folded forward under the barrel (reaches 8 in front of the mount)."""
    a = _Att(m, "bipod", parent, origin)
    a.box((-1, -0.5, -1), (2, 0.5, 1.5), "att_metal")                       # mount
    a.box((-1, -0.5, -7), (0.5, 0.5, 6), "att_metal")                       # legs folded forward
    a.box((0.5, -0.5, -7), (0.5, 0.5, 6), "att_metal")
    a.box((-1, -0.5, -8), (2, 0.5, 1), "rubber", inflate=0.1)               # feet
    return a.bone


# ======================================================================================
# SIDE (origin: outer face of the right rail; grows toward +X)
# ======================================================================================
def add_laser(m: Model, parent: str, origin):
    """AN/PEQ-15 on the right rail: 3 long, 2 tall, 1 thick. `beam_laser` at the emitter."""
    a = _Att(m, "laser", parent, origin)
    a.box((0, -1, -1.5), (1, 2, 3), "att_tan")                              # housing
    a.box((0.25, 0.25, -1.6), (0.5, 0.5, 0.1), "red")                       # aiming laser aperture
    a.box((0.25, -0.75, -1.75), (0.5, 0.5, 0.25), "att_black")              # IR illuminator bezel
    a.box((1, 0.25, -0.75), (0.25, 0.5, 0.5), "att_black", pattern="knurl")  # adjuster
    a.box((1, -0.75, 0.25), (0.25, 0.5, 0.75), "att_black")                 # fire button / mode switch
    a.box((0.25, 1, 0.5), (0.5, 0.2, 0.75), "att_black", pattern="lines")   # battery cap
    a.locator("beam_laser", (0.5, 0.5, -1.6))
    return a.bone


# ======================================================================================
# STOCK (origin: the `stock` bone pivot = front of the buffer tube on the bore axis)
# ======================================================================================
def add_stock_ctr(m: Model, parent: str, origin):
    """Magpul CTR on its own buffer tube: slim A-frame with an open triangle, 7.5 long."""
    a = _Att(m, "stock_ctr", parent, origin)
    a.box((-0.5, -0.5, 0), (1, 1, 3), "att_metal", pattern="lines")         # buffer tube
    a.box((-1, -1, 3), (2, 2, 3.5), "att_polymer")                          # body round the tube
    a.box((-0.5, -1.5, 3), (1, 0.5, 1.5), "att_polymer", pattern="lines")   # friction lock / latch
    a.wedge((0, -1.25, 3.5), (0, -3.25, 6.5), 1, 0.5, "att_polymer")        # lower A-frame strut
    a.box((-1, -3.5, 6.5), (2, 4.5, 0.5), "att_polymer")                    # butt
    a.box((-1, -3.5, 7), (2, 4.5, 0.5), "rubber")                           # butt pad
    return a.bone


# ======================================================================================
# MAGAZINE (origin: the `magazine` bone pivot; M4: 2 below it is the mouth of the mag well)
# ======================================================================================
def add_pmag_40(m: Model, parent: str, origin):
    """40-round PMAG: straight top, then two curved sections (15 and 30 deg), 6 long in all
    (the 30-rounder is 4)."""
    a = _Att(m, "pmag_40", parent, origin)
    seg = 2.5
    a.box((-0.5, -3, -1), (1, 1, 2), "att_polymer", pattern="lines")        # straight part in the well
    p1 = (0, -3, 1)                                                         # rear top edge of section 1
    a.box((-0.5, p1[1] - seg, -1), (1, seg, 2), "att_polymer", pattern="lines",
          rot=(15, 0, 0), pivot=p1, inflate=-0.05)
    r = math.radians(15)
    p2 = (0, p1[1] - seg * math.cos(r), p1[2] - seg * math.sin(r))          # rear bottom edge of section 1
    a.box((-0.5, p2[1] - seg, p2[2] - 2), (1, seg, 2), "att_polymer", pattern="lines",
          rot=(30, 0, 0), pivot=p2, inflate=-0.05)
    a.box((-0.5, p2[1] - seg - 0.25, p2[2] - 2), (1, 0.25, 2), "att_polymer",
          rot=(30, 0, 0), pivot=p2, inflate=0.1)                            # floor plate
    return a.bone
