package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.shot;
import static com.modernarmory.client.AutoTest.mc;

import java.util.Locale;
import java.util.function.Consumer;

import com.modernarmory.ModernArmory;
import com.modernarmory.armor.BallisticArmor.Outcome;
import com.modernarmory.entity.BulletEntity;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.Guns;
import com.modernarmory.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Body armour ("kevlar" section). Live bullets fired from 2 m into a villager (20 health, no armour of its own)
 * wearing a piece, at the torso, head or leg; each case checks what the armour did (stopped / through / vanilla /
 * none), that the villager lived or died as it should, and that the piece wore. Then armour stands in each tier
 * are photographed from the front and back.
 */
final class AutoTestKevlar {
    /** gun, worn item ids (slot by item), zone ("torso", "head", "leg"), expected outcome, expect death. */
    private record Case(String gun, String[] worn, String zone, Outcome expect, boolean dies) {}

    private static final Case[] CASES = {
            new Case("glock17", new String[]{"police_vest"}, "torso", Outcome.STOPPED, false),
            new Case("m4", new String[]{"police_vest"}, "torso", Outcome.THROUGH, false),
            new Case("ak47", new String[]{"plate_carrier"}, "torso", Outcome.STOPPED, false),
            new Case("m14", new String[]{"iotv"}, "torso", Outcome.STOPPED, false),
            new Case("awm", new String[]{"iotv"}, "torso", Outcome.THROUGH, false),
            new Case("awm", new String[]{"eod_jacket"}, "torso", Outcome.STOPPED, false),
            new Case("m107", new String[]{"eod_jacket"}, "torso", Outcome.THROUGH, true),
            new Case("ak47", new String[]{"minecraft:netherite_chestplate"}, "torso", Outcome.STOPPED, false),
            new Case("m4", new String[]{"minecraft:netherite_chestplate"}, "torso", Outcome.THROUGH, false),
            new Case("glock17", new String[]{"minecraft:iron_chestplate"}, "torso", Outcome.VANILLA, false),
            new Case("glock17", new String[]{"minecraft:leather_chestplate"}, "torso", Outcome.NONE, false),
            new Case("glock17", new String[]{"police_helmet", "police_vest"}, "head", Outcome.STOPPED, false),
            new Case("glock17", new String[]{"police_vest"}, "head", Outcome.NONE, true),
            new Case("m4", new String[]{"eod_trousers"}, "leg", Outcome.STOPPED, false),
    };
    private static final String[][] TIERS = {{"police_helmet", "police_vest"}, {"fast_helmet", "plate_carrier"},
            {"ech_helmet", "iotv"}, {"eod_helmet", "eod_jacket", "eod_trousers"}};
    private static final int EACH = 10;
    private static final int STANDS_AT = 4 + CASES.length * EACH + 4;
    static final int LENGTH = STANDS_AT + 125;
    private static final AABB AREA = new AABB(-6, -62, -10, 6, -54, 2);
    private static volatile LivingEntity target;
    private static volatile BulletEntity bullet;
    private static volatile int durBefore, armorMarkers;
    private static volatile int passed;

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("kill @e[type=!player]");
            cmd("fill -6 -60 -10 6 -54 4 minecraft:air");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        passed = 0;
        int t = t0 + 4;
        for (Case c : CASES) {
            at(t, () -> { cmd("kill @e[tag=kv]"); cmd("summon villager 0.5 -60 -2.5 {NoAI:1b,Tags:[\"kv\"]}"); });
            at(t + 2, () -> server(sp -> {
                target = level(sp).getEntitiesOfClass(LivingEntity.class, AREA, e -> e.isAlive() && e.entityTags().contains("kv"))
                        .stream().findFirst().orElse(null);
                if (target == null) return;
                ItemStack first = null;
                for (String id : c.worn()) {
                    ItemStack s = stack(id);
                    EquipmentSlot slot = target.getEquipmentSlotForItem(s);
                    target.setItemSlot(slot, s);
                    if (first == null) first = s;
                }
                durBefore = first.getDamageValue();
                armorMarkers = com.modernarmory.server.HitMarkers.sentArmor;
                AABB box = target.getBoundingBox();
                double y = switch (c.zone()) {
                    case "head" -> target.getEyeY();
                    case "leg" -> box.minY + 0.25 * box.getYsize();
                    default -> box.minY + 0.62 * box.getYsize();
                };
                Vec3 at = new Vec3(target.getX(), y, target.getZ());
                Vec3 from = at.add(0, 0, 2.0);
                GunDefinition g = Guns.byId(c.gun());
                bullet = BulletEntity.fire(level(sp), sp, from, at.subtract(from), g.spec().projectile(), g.spec().muzzleVelocity(), c.gun(), null);
            }));
            at(t + 7, () -> server(sp -> {
                if (target == null) { log(c, "no target", false); return; }
                boolean dead = !target.isAlive();
                Outcome got = bullet == null ? null : bullet.lastArmor();
                ItemStack first = firstWorn(c);
                boolean wore = c.expect() == Outcome.NONE || first == null || !first.isDamageableItem() || first.getDamageValue() > durBefore || dead;
                boolean marker = (com.modernarmory.server.HitMarkers.sentArmor > armorMarkers) == (c.expect() == Outcome.STOPPED);
                boolean ok = got == c.expect() && dead == c.dies() && wore && marker;
                if (ok) passed++;
                log(c, String.format(Locale.ROOT, "%s, health %.1f%s, wear %d, armour marker %s", got, dead ? 0f : target.getHealth(), dead ? " (dead)" : "",
                        first == null ? 0 : first.getDamageValue() - durBefore, com.modernarmory.server.HitMarkers.sentArmor > armorMarkers), ok);
            }));
            t += EACH;
        }
        at(t + 1, () -> logSummary());
        // armour stands in each tier (left to right: police, light, heavy, juggernaut), photographed
        at(t0 + STANDS_AT, () -> {
            cmd("kill @e[type=!player]");
            cmd("time set noon");
            for (int i = 0; i < TIERS.length; i++)
                cmd(String.format(Locale.ROOT, "summon armor_stand %.1f -60 -3.5 {ShowArms:1b,Tags:[\"kvs\"]}", -2.5 + 2.0 * i));
            cmd("tp @s 0.5 -59.2 2.0 180 8");
        });
        at(t0 + STANDS_AT + 3, () -> server(sp -> {
            var stands = level(sp).getEntitiesOfClass(LivingEntity.class, AREA, e -> e.entityTags().contains("kvs"));
            for (LivingEntity st : stands) {
                int i = (int) Math.round((st.getX() + 2.5) / 2.0);
                if (i < 0 || i >= TIERS.length) continue;
                for (String id : TIERS[i]) {
                    ItemStack s = stack(id);
                    st.setItemSlot(st.getEquipmentSlotForItem(s), s);
                }
            }
        }));
        at(t0 + STANDS_AT + 50, () -> { AutoTest.setHudHidden(true); look(180, 8); });
        at(t0 + STANDS_AT + 60, () -> shot("62_kevlar_front"));
        at(t0 + STANDS_AT + 62, () -> cmd("tp @s 0.5 -59.2 -9.0 0 8"));
        at(t0 + STANDS_AT + 64, () -> look(0, 8));
        at(t0 + STANDS_AT + 74, () -> shot("62_kevlar_back"));
        at(t0 + STANDS_AT + 76, () -> cmd("tp @s 4.5 -59.4 -0.5 150 12"));
        at(t0 + STANDS_AT + 78, () -> look(150, 12));
        at(t0 + STANDS_AT + 88, () -> shot("62_kevlar_angle"));
        at(t0 + STANDS_AT + 90, () -> {
            cmd("kill @e[type=!player]"); cmd("clear @s"); cmd("give @s modernarmory:m4");
            cmd("tp @s 0.5 -60 0.5 180 0");
        });
        at(t0 + STANDS_AT + 93, () -> server(sp -> {
            for (String id : TIERS[3]) { ItemStack s = stack(id); sp.setItemSlot(sp.getEquipmentSlotForItem(s), s); }
        }));
        at(t0 + STANDS_AT + 95, () -> { look(180, 0); mc().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); });
        at(t0 + STANDS_AT + 108, () -> shot("62_kevlar_worn_gun"));
        at(t0 + STANDS_AT + 109, () -> look(115, 0));
        at(t0 + STANDS_AT + 118, () -> shot("62_kevlar_worn_gun_side"));
        at(t0 + STANDS_AT + 120, () -> {
            mc().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); AutoTest.setHudHidden(false); cmd("clear @s");
            server(sp -> { for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS}) sp.setItemSlot(slot, ItemStack.EMPTY); });
        });
    }

    private static void logSummary() {
        ModernArmory.LOGGER.info("[autotest] kevlar: {} of {} cases -> {}", passed, CASES.length, passed == CASES.length ? "PASS" : "FAIL");
    }

    private static void log(Case c, String what, boolean ok) {
        ModernArmory.LOGGER.info("[autotest] kevlar {} vs {} ({}): expected {}{}, got {} -> {}", c.gun(), String.join("+", c.worn()), c.zone(),
                c.expect(), c.dies() ? " and dead" : "", what, ok ? "PASS" : "FAIL");
    }

    private static ItemStack firstWorn(Case c) {
        LivingEntity e = target;
        if (e == null) return null;
        ItemStack s = stack(c.worn()[0]);
        return e.getItemBySlot(e.getEquipmentSlotForItem(s));
    }

    private static ItemStack stack(String id) {
        if (id.startsWith("minecraft:")) {
            String path = id.substring("minecraft:".length());
            return switch (path) {
                case "netherite_chestplate" -> new ItemStack(Items.NETHERITE_CHESTPLATE);
                case "iron_chestplate" -> new ItemStack(Items.IRON_CHESTPLATE);
                case "leather_chestplate" -> new ItemStack(Items.LEATHER_CHESTPLATE);
                default -> throw new IllegalArgumentException(id);
            };
        }
        return new ItemStack(ModItems.kevlar().get(id).get());
    }

    private static void server(Consumer<ServerPlayer> job) {
        var server = mc().getSingleplayerServer();
        server.execute(() -> job.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static ServerLevel level(ServerPlayer sp) {
        return (ServerLevel) sp.level();
    }

    private AutoTestKevlar() {}
}
