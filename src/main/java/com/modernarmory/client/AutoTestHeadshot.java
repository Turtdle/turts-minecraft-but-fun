package com.modernarmory.client;

import static com.modernarmory.client.AutoTest.at;
import static com.modernarmory.client.AutoTest.cmd;

import java.util.ArrayList;
import java.util.List;

import com.modernarmory.Config;
import com.modernarmory.ModernArmory;
import com.modernarmory.ballistics.DamageModel;
import com.modernarmory.ballistics.ProjectileSpec;
import com.modernarmory.entity.BulletEntity;
import com.modernarmory.gun.GunDefinition;
import com.modernarmory.gun.Guns;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Point-blank headshots ("headshot" section): every gun's headshot damage at the muzzle must kill a 20-health
 * target unless it fires pellets. Then each of {@link #LIVE} fires one bullet from 2 blocks into the head of an
 * unarmoured villager (20 health): every bullet gun must kill it, the shotgun's single pellet must not.
 */
final class AutoTestHeadshot {
    private static final String[] LIVE = {"glock17", "usp", "m9", "mp5", "vector", "anaconda", "mcx", "m590"};
    private static final int EACH = 12;
    static final int LENGTH = 4 + LIVE.length * EACH + 4;
    private static int livePass;
    private static volatile boolean shotAt;
    /** The villager shot at (server thread). */
    private static volatile LivingEntity target;

    static void script(int t0) {
        at(t0 + 2, () -> {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                float mult = Config.HEADSHOT_MULTIPLIER.get().floatValue();
                List<String> bad = new ArrayList<>();
                for (GunDefinition g : Guns.all()) {
                    ProjectileSpec p = g.spec().projectile();
                    float body = p.baseDamage();
                    if (p.fragmentationVelocity() > 0 && g.spec().muzzleVelocity() >= p.fragmentationVelocity()) body *= 1.25f;
                    float head = DamageModel.headshot(body, mult, p.headshotDamage(), p.referenceEnergy(), p.referenceEnergy());
                    boolean pellets = g.spec().cartridge().isShot();
                    if (pellets == (head >= 20)) bad.add(g.id() + "=" + head);
                }
                ModernArmory.LOGGER.info("[autotest] headshot: {} guns, muzzle headshot lethal for all but pellets; wrong: {} -> {}",
                        Guns.all().size(), bad, bad.isEmpty() ? "PASS" : "FAIL");
            });
        });
        livePass = 0;
        int t = t0 + 4;
        for (String id : LIVE) {
            at(t, () -> {
                cmd("kill @e[tag=hs]");
                cmd("fill -3 -60 -2 3 -57 8 minecraft:air");
                cmd("summon villager 0.5 -60 5.5 {NoAI:1b,Tags:[\"hs\"]}");
            });
            at(t + 3, () -> {
                shotAt = false;
                target = null;
                var server = Minecraft.getInstance().getSingleplayerServer();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayers().get(0);
                    var vs = sp.level().getEntitiesOfClass(LivingEntity.class, new AABB(-20, -70, -20, 20, -40, 20), e -> e.isAlive() && e.getTags().contains("hs"));
                    ModernArmory.LOGGER.info("[autotest] headshot {}: target found {}, health {}", id, vs.size(), vs.isEmpty() ? 0 : vs.get(0).getHealth());
                    if (vs.isEmpty()) return;
                    target = vs.get(0);
                    Vec3 head = new Vec3(0.5, vs.get(0).getEyeY(), 5.5);
                    Vec3 from = head.add(0, 0, -2.0);
                    GunDefinition g = Guns.byId(id);
                    shotAt = true;
                    BulletEntity.fire(sp.level(), sp, from, head.subtract(from), g.spec().projectile(), g.spec().muzzleVelocity(), null, null);
                });
            });
            at(t + 9, () -> {
                var server = Minecraft.getInstance().getSingleplayerServer();
                server.execute(() -> {
                    var sp = server.getPlayerList().getPlayers().get(0);
                    boolean dead = target == null || !target.isAlive();
                    float hp = target == null ? 0 : target.getHealth();
                    boolean pellets = Guns.byId(id).spec().cartridge().isShot();
                    boolean ok = shotAt && (pellets ? !dead && hp < 20 : dead);
                    if (ok) livePass++;
                    ModernArmory.LOGGER.info("[autotest] headshot {}: point-blank head hit, villager {} -> {}", id,
                            dead ? "killed" : "left on " + hp, ok ? "PASS" : "FAIL");
                });
            });
            t += EACH;
        }
        at(t + 2, () -> ModernArmory.LOGGER.info("[autotest] headshot live: {} of {} -> {}", livePass, LIVE.length,
                livePass == LIVE.length ? "PASS" : "FAIL"));
    }

    private AutoTestHeadshot() {}
}
