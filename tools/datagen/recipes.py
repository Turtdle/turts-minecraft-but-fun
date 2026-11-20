"""
Every crafting recipe of the mod, in ONE table, written in both recipe formats:

  * Minecraft 1.21.1 (root project):   src/main/resources/data/modernarmory/recipe/<id>.json
    ingredients as {"item": "<id>"}
  * Minecraft 26.3 (versions/26.3):     versions/26.3/src/main/resources/data/modernarmory/recipe/<id>.json
    ingredients as plain "<id>" strings

    python tools/datagen/recipes.py

Costs follow the ammunition / gun class: pistol rounds are cheap, magnum and .50 BMG rounds take
more copper and powder; pistols are a few ingots, rifles an iron block, machine guns, .338 and .50
guns several blocks. Letters: I iron ingot, B iron block, N iron nugget, T tripwire hook
(trigger), D dye (finish), S spyglass (scope) or stick, C copper ingot.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_121 = ROOT / "src/main/resources/data/modernarmory/recipe"
OUT_263 = ROOT / "versions/26.3/src/main/resources/data/modernarmory/recipe"

MC = "minecraft:"
I, B, N, T = MC + "iron_ingot", MC + "iron_block", MC + "iron_nugget", MC + "tripwire_hook"
C, G, S, SG = MC + "copper_ingot", MC + "gunpowder", MC + "stick", MC + "spyglass"
BLACK, BROWN = MC + "black_dye", MC + "brown_dye"


def shaped(pattern, key, count=1):
    return ("shaped", pattern, key, count)


def shapeless(ingredients, count=1):
    return ("shapeless", ingredients, None, count)


RECIPES = {
    # ---- block ---------------------------------------------------------------------------------
    "gunsmith_table": shaped(["III", "TCT"], {"I": I, "T": T, "C": MC + "crafting_table"}),

    # ---- pistols -------------------------------------------------------------------------------
    "m17": shaped(["II", "TD"], {"I": I, "T": T, "D": BROWN}),
    "glock17": shaped(["II", "TD"], {"I": I, "T": T, "D": BLACK}),
    "glock19": shaped(["IN", "TD"], {"I": I, "N": N, "T": T, "D": BLACK}),
    "usp": shaped(["IN", "DT"], {"I": I, "N": N, "D": BLACK, "T": T}),
    "m9": shaped(["II", "TN"], {"I": I, "T": T, "N": N}),
    # ---- submachine guns -----------------------------------------------------------------------
    "apc9": shaped(["III", " TD"], {"I": I, "T": T, "D": BLACK}),
    "mpx": shaped(["III", "NTD"], {"I": I, "N": N, "T": T, "D": BLACK}),
    "vector": shaped(["III", "DT ", "N  "], {"I": I, "D": BLACK, "T": T, "N": N}),
    # ---- carbines / assault rifles -------------------------------------------------------------
    "m4": shaped(["III", "DBT"], {"I": I, "D": BLACK, "B": B, "T": T}),
    "m4a1": shapeless(["modernarmory:m4", MC + "redstone"]),
    "m16a4": shaped(["III", "DBT", "S  "], {"I": I, "D": BLACK, "B": B, "T": T, "S": S}),
    "hk416": shaped(["III", "DBT", "C  "], {"I": I, "D": BLACK, "B": B, "T": T, "C": C}),
    "mcx": shaped(["III", "DBT"], {"I": I, "D": BROWN, "B": B, "T": T}),
    "famas": shaped(["III", "DBT", " N "], {"I": I, "D": MC + "gray_dye", "B": B, "T": T, "N": N}),
    "aug": shaped(["III", "DBT", "G  "], {"I": I, "D": MC + "green_dye", "B": B, "T": T, "G": MC + "glass_pane"}),
    "ak47": shaped(["III", "PBT"], {"I": I, "P": MC + "oak_planks", "B": B, "T": T}),
    # ---- battle rifles -------------------------------------------------------------------------
    "m7": shaped(["III", "DBT", "BC "], {"I": I, "D": BROWN, "B": B, "T": T, "C": C}),
    "xm8": shaped(["III", "DBT", "CC "], {"I": I, "D": BROWN, "B": B, "T": T, "C": C}),
    "mk17": shaped(["III", "DBT", "B  "], {"I": I, "D": BROWN, "B": B, "T": T}),
    "m14": shaped(["III", "PBT", "PP "], {"I": I, "P": MC + "oak_planks", "B": B, "T": T}),
    # ---- designated marksman rifles ------------------------------------------------------------
    "mk20": shaped([" S ", "IIB", "D T"], {"S": SG, "I": I, "B": B, "D": BROWN, "T": T}),
    "m110": shaped([" S ", "IIB", "D T"], {"S": SG, "I": I, "B": B, "D": BLACK, "T": T}),
    "m110a1": shaped([" S ", "IIB", "DCT"], {"S": SG, "I": I, "B": B, "D": BROWN, "C": C, "T": T}),
    # ---- sniper rifles -------------------------------------------------------------------------
    "m2010": shaped([" S ", "IIB", "  T"], {"S": SG, "I": I, "B": B, "T": T}),
    "mk13": shaped([" S ", "IIB", "B T"], {"S": SG, "I": I, "B": B, "T": T}),
    "mk21": shaped([" S ", "IBB", "D T"], {"S": SG, "I": I, "B": B, "D": BROWN, "T": T}),
    "mk22": shaped([" S ", "IBB", "D T"], {"S": SG, "I": I, "B": B, "D": BLACK, "T": T}),
    "awm": shaped([" S ", "IBB", "DGT"], {"S": SG, "I": I, "B": B, "D": MC + "green_dye", "G": MC + "gold_ingot", "T": T}),
    "m24": shaped([" S ", "IIB", "DGT"], {"S": SG, "I": I, "B": B, "D": BLACK, "G": MC + "green_dye", "T": T}),
    # ---- shotguns ------------------------------------------------------------------------------
    "m590": shaped(["III", "SDT"], {"I": I, "S": S, "D": BLACK, "T": T}),
    "benelli_m4": shaped(["III", "RDT"], {"I": I, "R": MC + "redstone", "D": BLACK, "T": T}),
    "m26mass": shaped(["III", "NDT"], {"I": I, "N": N, "D": BLACK, "T": T}),
    "spas12": shaped(["III", "BDT"], {"I": I, "B": B, "D": BLACK, "T": T}),
    "aa12": shaped(["III", "BRT", "B  "], {"I": I, "B": B, "R": MC + "redstone", "T": T}),
    "saiga12": shaped(["III", "NBT"], {"I": I, "N": N, "B": B, "T": T}),
    # ---- machine guns --------------------------------------------------------------------------
    "m249": shaped(["III", "BBT"], {"I": I, "B": B, "T": T}),
    "m250": shaped(["III", "BBT", "DC "], {"I": I, "B": B, "T": T, "D": BROWN, "C": C}),
    "m240": shaped(["III", "BBT", "III"], {"I": I, "B": B, "T": T}),
    "m2a1": shaped(["BBB", "ITI", "I I"], {"B": B, "I": I, "T": T}),
    # ---- anti-materiel -------------------------------------------------------------------------
    "m107": shaped([" S ", "BBB", "DIT"], {"S": SG, "B": B, "D": BLACK, "I": I, "T": T}),

    # ---- ammunition (copper = brass case, gunpowder = charge, nuggets = steel / lead) ----------
    "ammo_9x19": shapeless([C, G], 16),
    "ammo_556": shapeless([C, G, N], 8),
    "ammo_68x51": shapeless([C, G, N, N], 8),
    "ammo_762x51": shapeless([C, C, G, G, N, N], 12),
    "ammo_762_m118lr": shapeless([C, C, G, G, MC + "gold_nugget"], 10),
    "ammo_300wm": shapeless([C, G, G, N], 4),
    "ammo_338lm": shapeless([C, C, G, G, G, N], 4),
    "ammo_338nm": shapeless([C, C, G, G, G, MC + "gold_nugget"], 4),
    "ammo_50bmg": shapeless([C, C, G, G, G, I], 2),
    "ammo_12ga_00": shapeless([MC + "paper", MC + "red_dye", G, C, N, N], 6),
    "ammo_762x39": shapeless([C, G, N, N, N], 10),
    "ammo_45acp": shapeless([C, C, G], 16),

    # ---- attachments ---------------------------------------------------------------------------
    "red_dot": shapeless([MC + "glass_pane", MC + "redstone", I]),
    "holographic": shapeless([MC + "glass_pane", MC + "glass_pane", MC + "redstone", I]),
    "acog": shapeless([SG, MC + "glass_pane", I, MC + "glowstone_dust"]),
    "sniper_scope": shapeless([SG, SG, I, I]),
    "suppressor_9mm": shapeless([I, MC + "white_wool"]),
    "suppressor_556": shapeless([I, I, MC + "white_wool"]),
    "suppressor_300": shapeless([I, I, I, MC + "white_wool"]),
    "suppressor_762": shapeless([I, I, I, MC + "white_wool", N]),
    "suppressor_338": shapeless([I, I, I, I, MC + "white_wool", MC + "white_wool"]),
    "compensator_556": shapeless([I, N, N]),
    "compensator_762": shapeless([I, I, N]),
    "muzzle_brake_50": shapeless([B, N, N]),
    "vertical_grip": shapeless([S, BLACK, N]),
    "angled_grip": shapeless([S, BLACK, N, N]),
    "bipod": shapeless([S, S, I]),
    "laser": shapeless([MC + "redstone_torch", MC + "glass_pane", I]),
    "stock_ctr": shapeless([S, S, BLACK, N]),
    "pmag_40": shapeless([I, BLACK, BLACK, N]),

    # ---- body armour (armor.Kevlar): F aramid fabric, R Level III rifle plate, P Level IV ceramic plate
    # aramid is woven from string and comes out golden yellow, like real Kevlar; plates are steel over fabric,
    # ceramic plates a fired ceramic face (brick) on a rifle plate's backing
    "aramid_fabric": shapeless([MC + "string"] * 4 + [MC + "yellow_dye"], 2),
    "rifle_plate": shaped(["SS", "FF"], {"S": "modernarmory:steel_ingot", "F": "modernarmory:aramid_fabric"}),
    "ceramic_plate": shaped(["KK", "RF"], {"K": MC + "brick", "R": "modernarmory:rifle_plate",
                                           "F": "modernarmory:aramid_fabric"}),
    # 1. police: soft armour, all fabric
    "police_vest": shaped(["F F", "FFF", "FFF"], {"F": "modernarmory:aramid_fabric"}),
    "police_helmet": shaped(["FFF", "F F"], {"F": "modernarmory:aramid_fabric"}),
    # 2. light military: a carrier with front and back rifle plates; FAST helmet with its NVG mount
    "plate_carrier": shaped(["F F", "RFR", "FFF"], {"F": "modernarmory:aramid_fabric", "R": "modernarmory:rifle_plate"}),
    "fast_helmet": shaped(["FNF", "F F"], {"F": "modernarmory:aramid_fabric", "N": N}),
    # 3. heavy military: ceramic front / back plates, rifle side plates; the ECH a plate-grade shell
    "iotv": shaped(["F F", "PFP", "RFR"], {"F": "modernarmory:aramid_fabric", "P": "modernarmory:ceramic_plate",
                                           "R": "modernarmory:rifle_plate"}),
    "ech_helmet": shaped(["FRF", "F F"], {"F": "modernarmory:aramid_fabric", "R": "modernarmory:rifle_plate"}),
    # 4. juggernaut: the bomb suit, ceramic all over; the helmet's visor is glass
    "eod_jacket": shaped(["P P", "PBP", "PPP"], {"P": "modernarmory:ceramic_plate", "B": B}),
    "eod_trousers": shaped(["PPP", "P P", "P P"], {"P": "modernarmory:ceramic_plate"}),
    "eod_helmet": shaped(["PPP", "PGP"], {"P": "modernarmory:ceramic_plate", "G": MC + "glass_pane"}),
}


def ingredient(item, fmt):
    return {"item": item} if fmt == "1.21.1" else item


def recipe_json(rid, spec, fmt):
    kind, a, key, count = spec
    out = {"type": "minecraft:crafting_" + kind, "category": "equipment"}
    if kind == "shaped":
        used = {ch for row in a for ch in row if ch != " "}
        assert used == set(key), f"{rid}: pattern letters {used} vs key {set(key)}"
        out["pattern"] = a
        out["key"] = {k: ingredient(v, fmt) for k, v in key.items()}
    else:
        out["ingredients"] = [ingredient(v, fmt) for v in a]
    out["result"] = {"id": "modernarmory:" + rid, "count": count}
    return out


def check_unique():
    """Two shapeless recipes with the same ingredients, or two shaped ones with the same (or mirrored) grid, conflict."""
    seen = {}
    for rid, (kind, a, key, _) in RECIPES.items():
        if kind == "shapeless":
            sigs = [("shapeless", tuple(sorted(a)))]
        else:
            grid = tuple(tuple(key.get(ch) for ch in row) for row in a)
            sigs = [("shaped", grid), ("shaped", tuple(tuple(reversed(r)) for r in grid))]
        for sig in sigs:
            if sig in seen and seen[sig] != rid:
                raise SystemExit(f"recipe conflict: {rid} and {seen[sig]}")
            seen[sig] = rid


def main():
    check_unique()
    for fmt, out in (("1.21.1", OUT_121), ("26.3", OUT_263)):
        out.mkdir(parents=True, exist_ok=True)
        for rid, spec in RECIPES.items():
            (out / f"{rid}.json").write_text(json.dumps(recipe_json(rid, spec, fmt), indent=2) + "\n", encoding="utf-8")
    print(f"{len(RECIPES)} recipes x 2 formats")


if __name__ == "__main__":
    main()
