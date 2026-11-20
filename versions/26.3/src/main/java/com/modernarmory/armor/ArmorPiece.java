package com.modernarmory.armor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.modernarmory.ballistics.ArmorLevel;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * One wearable piece of body armour (an item), modelled on real gear.
 *
 * @param id          item id
 * @param name        display name
 * @param model       the tier's GeckoLib armour model (tools/modelgen/armor/&lt;model&gt;.py): every piece of a
 *                    tier shares it, GeckoLib shows the bones of the slot the piece is worn in
 * @param slot        the armour slot it is worn in
 * @param covers      the body zones it protects (a vest only the torso; the bomb-suit trousers legs and feet)
 * @param level       what it stops (NIJ level, {@link ArmorLevel})
 * @param blunt       share of a stopped bullet's damage still felt as blunt trauma (soft armour flexes more
 *                    than a plate; the bomb suit's padding spreads it most)
 * @param massKg      weight
 * @param durability  wear points: a stopped bullet takes about its damage, one through it half again
 * @param defense     vanilla armour points (melee, mobs, falls of debris; bullets ignore them)
 * @param toughness   vanilla armour toughness
 * @param slowdown    share of walking speed it costs (by weight)
 * @param noSprint    too heavy to sprint in
 * @param realGear    what it is in real life
 * @param extraGuns   extra guns it lets you carry (gun.GunCarry): a heavy load-bearing vest carries a third
 */
public record ArmorPiece(String id, String name, String model, EquipmentSlot slot, Set<BodyZone> covers, ArmorLevel level,
                         float blunt, double massKg, int durability, int defense, float toughness, double slowdown,
                         boolean noSprint, String realGear, int extraGuns) {

    /** Whether it protects body zone {@code z}. */
    public boolean covers(BodyZone z) {
        return covers.contains(z);
    }

    /** Tooltip: protection level, coverage, weight (kept to one line). */
    public List<String> tooltip() {
        List<String> lines = new ArrayList<>();
        String zones = String.join(" + ", covers.stream().sorted().map(z -> z.name().toLowerCase(Locale.ROOT)).toList());
        lines.add(String.format(Locale.ROOT, "%s, covers %s, %.1f kg, -%d%% speed%s", level.label, zones, massKg,
                Math.round(slowdown * 100), noSprint ? ", no sprinting" : ""));
        return lines;
    }
}
