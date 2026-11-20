package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;
import static com.modernarmory.client.AutoTest.look;
import static com.modernarmory.client.AutoTest.mc;

import java.util.Locale;
import java.util.function.Consumer;

import com.modernarmory.ModernArmory;
import com.modernarmory.grenade.GrenadeEntity;
import com.modernarmory.grenade.Grenades;
import com.modernarmory.grenade.MobSenses;
import com.modernarmory.grenade.ThrowKind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Grenades on mob senses ("mobsenses" section), with a live skeleton at night 13 m away and the player in survival
 * (Resistance V: arrows do nothing). 1) it targets and shoots the player; 2) the player drops an M18 at their feet
 * and stands in the cloud: once it is up the skeleton drops the player and stops shooting; 3) smoke gone: it finds the player again; 4) it throws an
 * M84 toward the player: dazed, it has no target, staggers about and shoots nothing.
 */
final class AutoTestMobSenses {
    static final int LENGTH = 560;
    private static final AABB AREA = new AABB(-12, -62, -24, 12, -50, 8);
    private static volatile Mob skel;
    private static volatile GrenadeEntity smoke;
    private static volatile boolean shotSmoke, shotDazed, sees0, shot0, hidden, quiet, sees2, dazed, blindAll = true, quiet2;
    private static volatile Vec3 dazedFrom;
    private static volatile double moved;

    static void script(int t0) {
        at(t0 + 1, () -> {
            cmd("difficulty normal"); cmd("time set midnight"); cmd("gamemode survival");
            cmd("effect give @s resistance 99999 4 true");
            cmd("kill @e[type=!player]");
            cmd("fill -12 -60 -24 12 -52 8 minecraft:air");
            cmd("fill -12 -61 -24 12 -61 8 minecraft:stone");
            cmd("tp @s 0.5 -60 0.5 180 0");
            look(180, 0);
        });
        at(t0 + 4, () -> cmd("summon skeleton 0.5 -60 -12.5 {Tags:[\"ms\"],PersistenceRequired:1b}"));
        at(t0 + 6, () -> server(sp -> {
            skel = level(sp).getEntitiesOfClass(Mob.class, AREA, e -> e.entityTags().contains("ms")).stream().findFirst().orElse(null);
            // summoned with NBT, so it skipped its spawn gear: hand it a bow (a skeleton re-picks its attack for it)
            if (skel != null) skel.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }));
        // 1) it sees and shoots
        at(t0 + 40, () -> server(sp -> sees0 = skel != null && skel.getTarget() == sp));
        for (int d = 15; d <= 90; d++) at(t0 + d, () -> server(sp -> shot0 |= arrows(sp) > 0)); // draws, aims, looses: give it ~4 s
        // 2) the player drops smoke at their own feet and stands in it (arrows can't knock them out of it)
        at(t0 + 92, () -> server(sp -> {
            sp.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            sp.setXRot(90);
            smoke = GrenadeEntity.throwFrom(sp, Grenades.M18, ThrowKind.UNDERHAND);
            sp.setXRot(0);
        }));
        at(t0 + 290, () -> server(sp -> { hidden = skel.getTarget() == null; clearArrows(sp); }));
        for (int d = 291; d <= 350; d++) at(t0 + d, () -> server(sp -> shotSmoke |= arrows(sp) > 0));
        at(t0 + 350, () -> server(sp -> {
            quiet = !shotSmoke && skel.getTarget() == null;
            ModernArmory.LOGGER.info(String.format(Locale.ROOT,
                    "[autotest] mobsenses smoke: skeleton saw player %s, shot %s; behind smoke target dropped %s, no arrows %s, smoke on sight line %.1f m -> %s",
                    sees0, shot0, hidden, quiet, MobSenses.smokeBetween(level(sp), skel.getEyePosition(), sp.getEyePosition()),
                    sees0 && shot0 && hidden && quiet ? "PASS" : "FAIL"));
            if (smoke != null) smoke.discard();
            sp.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        }));
        // 3) smoke gone: it sees you again
        at(t0 + 400, () -> server(sp -> {
            sees2 = skel.getTarget() == sp;
            ModernArmory.LOGGER.info("[autotest] mobsenses smoke gone: skeleton targets player again {} -> {}", sees2, sees2 ? "PASS" : "FAIL");
        }));
        // 4) flashed: it throws an M84 toward the player (lands a few metres in front of it, goes off 1.5 s later)
        at(t0 + 402, () -> server(sp -> GrenadeEntity.throwFrom(skel, Grenades.M84, ThrowKind.UNDERHAND)));
        at(t0 + 445, () -> server(sp -> { dazed = MobSenses.dazed(skel); dazedFrom = skel.position(); clearArrows(sp); }));
        for (int d = 450; d <= 540; d += 10)
            at(t0 + d, () -> server(sp -> { if (skel.getTarget() != null) blindAll = false; moved = Math.max(moved, skel.position().distanceTo(dazedFrom)); }));
        for (int d = 446; d <= 540; d++) at(t0 + d, () -> server(sp -> shotDazed |= arrows(sp) > 0));
        at(t0 + 545, () -> server(sp -> {
            quiet2 = !shotDazed;
            boolean ok = dazed && blindAll && quiet2 && moved > 1.0;
            ModernArmory.LOGGER.info(String.format(Locale.ROOT,
                    "[autotest] mobsenses flash: skeleton dazed %s, no target throughout %s, no arrows %s, staggered %.1f m -> %s",
                    dazed, blindAll, quiet2, moved, ok ? "PASS" : "FAIL"));
        }));
        at(t0 + 550, () -> { cmd("kill @e[type=!player]"); cmd("effect clear @s"); cmd("gamemode creative"); cmd("difficulty peaceful"); cmd("time set noon"); });
    }

    private static void server(Consumer<ServerPlayer> job) {
        var server = mc().getSingleplayerServer();
        server.execute(() -> job.accept(server.getPlayerList().getPlayers().get(0)));
    }

    private static ServerLevel level(ServerPlayer sp) {
        return (ServerLevel) sp.level();
    }

    private static int arrows(ServerPlayer sp) {
        return level(sp).getEntitiesOfClass(AbstractArrow.class, AREA.inflate(8)).size();
    }

    private static void clearArrows(ServerPlayer sp) {
        level(sp).getEntitiesOfClass(AbstractArrow.class, AREA.inflate(8)).forEach(a -> a.discard());
    }

    private AutoTestMobSenses() {}
}
