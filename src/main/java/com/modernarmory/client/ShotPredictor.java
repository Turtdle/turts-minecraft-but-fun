package com.modernarmory.client;

import com.modernarmory.attachment.GunStats;
import com.modernarmory.ballistics.Tracer;
import com.modernarmory.gun.FireMode;
import com.modernarmory.gun.GunSpec;
import com.modernarmory.gun.ShotMath;
import com.modernarmory.gun.SoundSet;
import com.modernarmory.gun.TriggerState;
import com.modernarmory.item.GunAnimations;
import com.modernarmory.item.GunItem;
import com.modernarmory.network.GunAnimPayload;
import com.modernarmory.network.GunStatePayload;
import com.modernarmory.network.ShootPayload;
import com.modernarmory.network.ShotAckPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side shot prediction, as in most online shooters: the trigger is handled here (fire mode, cyclic
 * rate, ammo, reloads), so a round fires the moment you pull it. Its recoil, flash, report, smoke, fire
 * animation and bullets (client-only copies that fly, hit and leave holes like real ones) show at once,
 * and a {@link ShootPayload} asks the server to fire the real round. The server rolls it from the same
 * seed ({@link ShotMath}), so the real bullet flies where this one did, and answers with a
 * {@link ShotAckPayload}; it also keeps this side's ammo and reload state right ({@link GunStatePayload}).
 * Hits are still decided by the server, against targets rewound to where you saw them (ballistics.LagCompensation).
 *
 * <p>A refused shot (rarely: the server saw the gun empty or reloading) has already shown; its ack puts the ammo count back.
 */
public final class ShotPredictor {
    private static final double TICK = 0.05;
    /** With no shot in flight for this many ticks, the gun's own (synced) round count is the truth again. */
    private static final int SETTLE_TICKS = 10;
    /** How long a reload request may wait for the server's answer before firing is allowed again (ticks). */
    private static final int RELOAD_WAIT_TICKS = 20;
    private static final java.util.Random SEEDS = new java.util.Random();

    private static final TriggerState trigger = new TriggerState();
    private static boolean prevHeld, clicked;
    private static double cooldown; // seconds until the action can cycle again
    private static int seq;
    private static LocalPlayer player;
    /** The gun predicted for: hotbar slot and item. */
    private static int slot = -1;
    private static Item item;
    /** Rounds in the gun as the server last said, and our shots it hasn't answered yet. */
    private static int confirmedAmmo, outstanding, sinceConfirm = Integer.MAX_VALUE / 2;
    private static boolean infinite, reloading, interruptible;
    private static int reloadWait;
    /** Client-only entity ids, negative so they never meet the server's. */
    private static int nextPredictedId = -1_000_000;
    /** For tests: shots fired here, and the server's answers. */
    public static int sent, accepted, refused;

    /** An attack click while holding a gun (a tap shorter than a tick still fires a round). */
    static void onAttackClick() {
        clicked = true;
    }

    /** The reload key: hold fire until the server says whether a reload started. */
    static void onReloadRequested() {
        reloadWait = RELOAD_WAIT_TICKS;
    }

    /** Once per client tick. {@code ready}: holding a gun with nothing in the way (no screen, not piloting a drone). */
    static void tick(LocalPlayer p, boolean ready, boolean held, boolean aiming) {
        if (p != player) reset(p);
        sinceConfirm++;
        if (reloadWait > 0) reloadWait--;
        ItemStack stack = p.getMainHandItem();
        boolean click = clicked;
        clicked = false;
        if (!(stack.getItem() instanceof GunItem gun) || !ready) {
            trigger.held = false;
            trigger.stopBurst();
            prevHeld = false;
            cooldown = Math.max(0, cooldown - TICK);
            return;
        }
        int sel = p.getInventory().selected;
        if (sel != slot || stack.getItem() != item) { // another gun: what we knew was about the old one
            slot = sel;
            item = stack.getItem();
            outstanding = 0;
            sinceConfirm = Integer.MAX_VALUE / 2;
            reloading = false;
            trigger.stopBurst();
        }

        trigger.held = held;
        boolean pressed = (held && !prevHeld) || click;
        boolean tap = pressed && !held; // pressed and let go within the tick: one round
        prevHeld = held;
        if (reloadWait > 0 || (reloading && !(pressed && interruptible))) {
            if (!held) trigger.stopBurst();
            cooldown = Math.max(0, cooldown - TICK);
            return;
        }
        if (reloading) reloading = false; // tube magazine: the shot stops the reload (the server does the same)

        GunSpec spec = gun.spec();
        FireMode mode = gun.fireMode(stack);
        if (pressed) trigger.press(mode, spec);
        if (!held) trigger.stopBurst();
        // as many rounds as the cyclic rate allows within this 50 ms tick, each at its moment in the tick
        while (cooldown < TICK) {
            if (!mode.trigger().wantsShot(trigger) && !tap) break;
            if (ammo(gun, stack) <= 0 && !infinite) {
                if (pressed) ClientState.playOwnSound(ModSounds.DRY_FIRE.get(), 1.0f, 1.0f, 0f);
                trigger.dryFire();
                break;
            }
            fire(p, gun, stack, aiming, (float) cooldown);
            mode.trigger().onShot(trigger);
            cooldown += spec.cycleTime();
            if (tap) break;
        }
        cooldown = Math.max(0, cooldown - TICK);
    }

    private static void reset(LocalPlayer p) {
        player = p;
        slot = -1;
        item = null;
        outstanding = 0;
        sinceConfirm = Integer.MAX_VALUE / 2;
        reloading = interruptible = infinite = false;
        reloadWait = 0;
        cooldown = 0;
        prevHeld = false;
        trigger.held = false;
        trigger.stopBurst();
    }

    private static void fire(LocalPlayer p, GunItem gun, ItemStack stack, boolean aiming, float offset) {
        if (outstanding == 0 && sinceConfirm > SETTLE_TICKS) confirmedAmmo = gun.ammo(stack);
        outstanding++;
        long seed = SEEDS.nextLong();
        float spread = (float) ShotMath.spreadDeg(p, gun, stack, aiming); // sent as a float: roll with exactly that
        float xRot = p.getXRot(), yRot = p.getYRot();
        RandomSource rand = RandomSource.create(seed);
        ShotMath.Plan plan = ShotMath.plan(p, gun, stack, aiming, xRot, yRot, spread, rand);
        GunSpec spec = gun.spec();
        GunStats stats = GunStats.of(gun, stack);
        Tracer tracer = gun.loadedTracer(stack) ? spec.cartridge().tracer() : null;
        // our copy of the bullets (BulletEntity.fire adds them to the client world through addPredicted)
        spec.projectileLoad().launch(p.level(), p, p.getEyePosition(), plan.dir(), spec,
                spec.muzzleVelocity() * stats.velocity(), gun.gunId(), tracer, rand);
        PacketDistributor.sendToServer(new ShootPayload(++seq, seed, slot, xRot, yRot, spread));
        sent++;

        ClientState.onOwnShot(plan.kick(), plan.side(), offset); // recoil and muzzle flash
        SoundSet sounds = spec.sounds();
        ClientState.playOwnSound((stats.suppressed() ? sounds.fireSuppressed() : sounds.fire()).get(), 1.0f, plan.soundPitch(), offset);
        // manual action (bolt / pump) sound only in a manual mode (the SPAS-12 also fires semi-auto)
        if (sounds.cycle() != null && gun.fireMode(stack).manualCycle())
            ClientState.playOwnSound(sounds.cycle().get(), 1.0f, 1.0f, offset + sounds.cycleDelay());
        Vec3 smoke = ShotMath.muzzleSmoke(p, aiming);
        p.level().addParticle(ParticleTypes.SMOKE, smoke.x, smoke.y, smoke.z, 0, 0.01, 0);
        GunAnimations.playLocal(gun, stack, GunAnimations.FIRE);
    }

    /** A predicted bullet joins the client world under an id the server never uses. */
    public static void addPredicted(Entity bullet) {
        if (!(bullet.level() instanceof ClientLevel level)) return;
        bullet.setId(nextPredictedId--);
        level.addEntity(bullet);
    }

    /** Rounds in the held gun as the HUD should show them: the server's count less our shots still on the way. */
    public static int ammo(GunItem gun, ItemStack stack) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p != player || p.getInventory().selected != slot || stack.getItem() != item
                || (outstanding == 0 && sinceConfirm > SETTLE_TICKS)) return gun.ammo(stack);
        return infinite ? confirmedAmmo : Math.max(0, confirmedAmmo - outstanding);
    }

    public static void onAck(ShotAckPayload a) {
        if (a.accepted()) accepted++;
        else refused++;
        if (a.slot() != slot) return; // about a gun we've since put away
        outstanding = Math.max(0, outstanding - 1);
        confirmedAmmo = a.ammo();
        sinceConfirm = 0;
    }

    public static void onState(GunStatePayload s) {
        reloadWait = 0;
        reloading = s.reloading();
        interruptible = s.interruptible();
        infinite = s.infinite();
        if (s.slot() == slot && outstanding == 0) {
            confirmedAmmo = s.ammo();
            sinceConfirm = 0;
        }
    }

    /** Another player's fire animation (the server sends it to everyone but the shooter). */
    public static void onOthersAnim(GunAnimPayload a) {
        if (net.minecraft.core.registries.BuiltInRegistries.ITEM.get(com.modernarmory.ModernArmory.id(a.item())) instanceof GunItem gun)
            GunAnimations.playLocal(gun, a.instanceId(), a.anim());
    }

    /** Whether this is one of our predicted bullets (a client-only entity) rather than one the server sent. */
    public static boolean isPredicted(Entity e) {
        return e.getId() < 0;
    }

    /** For tests: shots sent and not yet answered. */
    public static int outstanding() {
        return outstanding;
    }

    private ShotPredictor() {}
}
