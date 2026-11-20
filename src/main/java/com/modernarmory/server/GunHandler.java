package com.modernarmory.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.modernarmory.Config;
import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.gun.FireMode;
import com.modernarmory.gun.GunSpec;
import com.modernarmory.gun.ReloadContext;
import com.modernarmory.gun.ReloadTask;
import com.modernarmory.gun.ShooterSpread;
import com.modernarmory.gun.SoundSet;
import com.modernarmory.gun.Support;
import com.modernarmory.gun.TriggerState;
import com.modernarmory.item.GunAnimations;
import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.MasteryHandler;
import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.network.ShotFiredPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.modernarmory.ballistics.Tracer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative trigger, fire-rate, ammo and reload handling. Clients only send
 * "trigger held / aiming" state and reload / fire-select requests.
 *
 * <p>Nothing here is gun-specific: what a trigger pull does is the fire mode's
 * {@link FireMode.Trigger} strategy, reloading is the spec's {@link com.modernarmory.gun.Feed}, and
 * what leaves the muzzle is the cartridge's {@link com.modernarmory.gun.ProjectileLoad}.
 */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class GunHandler {
    public static final int ACTION_RELOAD = 0;
    public static final int ACTION_FIRE_MODE = 1;
    private static final double TICK = 0.05;

    private static final class State {
        final TriggerState trigger = new TriggerState();
        boolean aiming, prevTrigger;
        double cooldown; // seconds until the action can cycle again
        ReloadTask reload;
        int reloadSlot = -1;

        boolean reloading() {
            return reload != null;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static void setInput(ServerPlayer player, boolean trigger, boolean aiming) {
        State s = state(player);
        s.trigger.held = trigger;
        s.aiming = aiming;
    }

    public static void action(ServerPlayer player, int action) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        State s = state(player);
        switch (action) {
            case ACTION_RELOAD -> startReload(player, gun, s);
            case ACTION_FIRE_MODE -> {
                if (s.reloading()) return;
                FireMode mode = gun.cycleFireMode(stack);
                player.displayClientMessage(Component.literal("Fire mode: " + mode.label()), true);
                gunSound(player, ModSounds.FIRE_SELECT.get(), 0.8f, 1.0f);
            }
            default -> {}
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tick(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private static void tick(ServerPlayer player) {
        State s = state(player);
        TriggerState t = s.trigger;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun) || player.isSpectator()) {
            s.reload = null;
            t.stopBurst();
            s.prevTrigger = t.held;
            return;
        }
        GunSpec spec = gun.spec();
        boolean pressed = t.held && !s.prevTrigger;

        if (s.reloading()) {
            if (player.getInventory().selected != s.reloadSlot) {
                s.reload = null; // switching weapons cancels the reload
            } else if (pressed && s.reload.interruptible()) {
                s.reload = null; // tube magazine: stop loading and fire the chambered round
            } else {
                if (s.reload.tick()) s.reload = null;
                s.cooldown = Math.max(0, s.cooldown - TICK);
                s.prevTrigger = t.held;
                return;
            }
        }

        FireMode mode = gun.fireMode(stack);
        if (pressed) t.press(mode, spec);
        if (!t.held) t.stopBurst();

        // Fire as many rounds as the cyclic rate allows within this 50 ms tick.
        while (s.cooldown < TICK) {
            if (!mode.trigger().wantsShot(t)) break;
            int ammo = gun.ammo(stack);
            boolean infinite = player.isCreative() && Config.CREATIVE_INFINITE_AMMO.get();
            if (ammo <= 0 && !infinite) {
                if (pressed) {
                    gunSound(player, ModSounds.DRY_FIRE.get(), 1.0f, 1.0f);
                }
                t.dryFire();
                break;
            }
            shoot(player, stack, gun, s, (float) s.cooldown);
            if (!infinite) gun.setAmmo(stack, ammo - 1);
            mode.trigger().onShot(t);
            s.cooldown += spec.cycleTime();
        }
        s.cooldown = Math.max(0, s.cooldown - TICK);
        s.prevTrigger = t.held;
    }

    /**
     * Where the muzzle smoke puff goes: ~1.3 m ahead of the eye, under the line of sight when
     * aiming; from the hip, out to the gun-hand side and lower, where the muzzle is, so the puff
     * does not sit on the crosshair and hide the impacts.
     */
    private static Vec3 muzzleSmoke(ServerPlayer player, boolean aiming) {
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = player.getEyePosition().add(look.scale(1.3)).add(0, -0.15, 0);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (aiming || right.lengthSqr() < 1e-6) return muzzle;
        double side = player.getMainArm() == HumanoidArm.RIGHT ? 0.25 : -0.25;
        return muzzle.add(right.normalize().scale(side)).add(0, -0.1, 0);
    }

    /** @param offset seconds into this 50 ms tick at which the round fires (keeps the cadence even) */
    private static void shoot(ServerPlayer player, ItemStack stack, GunItem gun, State s, float offset) {
        GunSpec spec = gun.spec();
        ServerLevel level = player.serverLevel();
        RandomSource rand = player.getRandom();

        GunStats stats = GunStats.of(gun, stack);
        Support support = spec.support();
        boolean crouching = player.isCrouching();
        boolean braced = support.braced(crouching, stats.bipod());
        // Spread: gun+ammo mechanical accuracy, plus shooter-induced error.
        double spread = spec.mechanicalMoa() / 60.0
                + (s.aiming ? spec.adsSpreadDeg() * (braced ? Support.BRACED_AIM_SPREAD : 1)
                            : spec.hipSpreadDeg() * stats.hipSpread() * MasteryHandler.effects(player, gun).hipSpread()); // mastery: tighter from the hip
        float masteryMove = MasteryHandler.effects(player, gun).movingSpread(); // mastery: steadier on the move
        double moving = ShooterSpread.movement(player, s.aiming);
        if (moving > 0) spread += moving * masteryMove;
        if (crouching) spread *= ShooterSpread.CROUCH;
        spread *= support.spread(crouching); // e.g. a tripod gun fired standing
        double angle = Math.abs(rand.nextGaussian()) * spread * 0.5;
        double azimuth = rand.nextDouble() * Math.PI * 2;
        float pitch = player.getXRot() + (float) (angle * Math.sin(azimuth));
        float yaw = player.getYRot() + (float) (angle * Math.cos(azimuth));
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);

        Tracer tracer = gun.loadedTracer(stack) ? spec.cartridge().tracer() : null;
        spec.projectileLoad().launch(level, player, player.getEyePosition(), dir, spec,
                spec.muzzleVelocity() * stats.velocity(), gun.gunId(), tracer, rand);
        MasteryHandler.onShot(player, gun, stack);

        SoundSet sounds = spec.sounds();
        SoundEvent shot = (stats.suppressed() ? sounds.fireSuppressed() : sounds.fire()).get();
        gunSound(player, shot, 1.0f, 0.95f + rand.nextFloat() * 0.1f, offset);
        // manual action (bolt / pump) sound only in a manual mode (the SPAS-12 also fires semi-auto)
        if (sounds.cycle() != null && gun.fireMode(stack).manualCycle())
            gunSound(player, sounds.cycle().get(), 1.0f, 1.0f, offset + sounds.cycleDelay());
        Vec3 smoke = muzzleSmoke(player, s.aiming);
        level.sendParticles(ParticleTypes.SMOKE, smoke.x, smoke.y, smoke.z, 1, 0.02, 0.02, 0.02, 0.01);

        // Recoil from free-recoil energy (~5 J for an M4), less when aiming, scaled by attachments
        // and by stance: a bipod / tripod deployed (crouching) soaks up most of it (gun.Support).
        float kick = (float) (spec.recoilEnergy() * 0.14) * stats.recoil();
        if (s.aiming) kick *= 0.8f;
        kick *= support.recoil(crouching, stats.bipod());
        float side = (rand.nextFloat() - 0.45f) * kick * 0.6f;
        PacketDistributor.sendToPlayer(player, new ShotFiredPayload(kick, side, offset));
        GunAnimations.trigger(gun, player, stack, GunAnimations.FIRE);
    }

    private static void startReload(ServerPlayer player, GunItem gun, State s) {
        if (s.reloading()) return;
        ReloadTask task = gun.spec().feed().startReload(new Reloading(player, gun, player.getInventory().selected));
        if (task == null) return;
        s.reload = task;
        s.reloadSlot = player.getInventory().selected;
        s.trigger.stopBurst();
    }

    /** The server side of a reload: the shooter's gun stack, inventory, sounds and animations. */
    private record Reloading(ServerPlayer player, GunItem gun, int slot) implements ReloadContext {
        private ItemStack stack() {
            return player.getInventory().getItem(slot);
        }

        private GunStats stats() {
            return GunStats.of(gun, stack());
        }

        @Override
        public int loaded() {
            return gun.ammo(stack());
        }

        @Override
        public void setLoaded(int rounds) {
            gun.setAmmo(stack(), rounds);
        }

        @Override
        public int magazineSize() {
            return stats().magazineSize();
        }

        @Override
        public int available() {
            return player.isCreative() ? Integer.MAX_VALUE : countAmmo(player, gun);
        }

        @Override
        public int take(int rounds) {
            // the first matching stack in the inventory (hotbar first) decides ball or tracer
            Item first = firstAmmo(player, gun);
            if (first != null) gun.setLoadedTracer(stack(), first == gun.tracerAmmoItem());
            return player.isCreative() ? rounds : takeAmmo(player, gun, first, rounds);
        }

        @Override
        public int reloadTicks(boolean empty) {
            return Math.max(1, Math.round(stats().reloadTicks(empty) * MasteryHandler.effects(player, gun).reload()));
        }

        @Override
        public SoundSet sounds() {
            return gun.spec().sounds();
        }

        @Override
        public void cue(SoundEvent sound, String animation, boolean restart) {
            // longer reloads (bigger mags) play a touch slower
            gunSound(player, sound, 1.0f, 1.0f / Math.max(0.5f, stats().reload()));
            if (restart) GunAnimations.restart(gun, player, stack(), animation);
            else GunAnimations.trigger(gun, player, stack(), animation);
        }

        @Override
        public void noAmmo() {
            player.displayClientMessage(Component.literal("No " + gun.spec().cartridge().name() + " ammo"), true);
        }
    }

    /** Others hear it at the shooter's position; the shooter hears it attached to themself. */
    private static void gunSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        gunSound(player, sound, volume, pitch, 0f);
    }

    private static void gunSound(ServerPlayer player, SoundEvent sound, float volume, float pitch, float delay) {
        player.level().playSound(player, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
        PacketDistributor.sendToPlayer(player, new LocalSoundPayload(sound.getLocation(), volume, pitch, delay));
    }

    private static int countAmmo(ServerPlayer player, GunItem gun) {
        int n = 0;
        for (ItemStack st : player.getInventory().items) if (gun.isAmmo(st)) n += st.getCount();
        return n;
    }

    /** The ammo kind (ball or tracer) of the first matching stack, hotbar first; null if none. */
    private static Item firstAmmo(ServerPlayer player, GunItem gun) {
        for (ItemStack st : player.getInventory().items) if (gun.isAmmo(st)) return st.getItem();
        return null;
    }

    /** Takes up to {@code want} rounds of {@code kind} (ball or tracer; never mixes them in one load). */
    private static int takeAmmo(ServerPlayer player, GunItem gun, Item kind, int want) {
        if (kind == null) return 0;
        int got = 0;
        for (ItemStack st : player.getInventory().items) {
            if (got >= want) break;
            if (st.is(kind)) {
                int take = Math.min(want - got, st.getCount());
                st.shrink(take);
                got += take;
            }
        }
        return got;
    }

    private GunHandler() {}
}
