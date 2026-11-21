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
import com.modernarmory.gun.ShotMath;
import com.modernarmory.gun.SoundSet;
import com.modernarmory.item.GunAnimations;
import com.modernarmory.item.GunItem;
import com.modernarmory.mastery.MasteryHandler;
import com.modernarmory.entity.BulletEntity;
import com.modernarmory.network.GunStatePayload;
import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.network.ShootPayload;
import com.modernarmory.network.ShotAckPayload;
import com.modernarmory.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.modernarmory.ballistics.Tracer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import software.bernie.geckolib.animatable.GeoItem;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-authoritative shots, ammo and reloads. The shooter's client decides when the trigger fires a round
 * (fire mode, cyclic rate: client.ShotPredictor, so the shot shows at once) and sends a {@link ShootPayload};
 * the server checks the gun may fire (in that slot, loaded, not reloading, not faster than its cyclic rate)
 * and fires the real projectiles from the same random seed ({@link ShotMath}), so they fly where the
 * client's did. Damage is only ever done here. Clients also send "trigger held / aiming" state and reload /
 * fire-select requests.
 *
 * <p>Nothing here is gun-specific: reloading is the spec's {@link com.modernarmory.gun.Feed}, and
 * what leaves the muzzle is the cartridge's {@link com.modernarmory.gun.ProjectileLoad}.
 */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class GunHandler {
    public static final int ACTION_RELOAD = 0;
    public static final int ACTION_FIRE_MODE = 1;
    /**
     * Shots a client may fire ahead of the cyclic rate, to absorb network jitter (packets arrive bunched);
     * over any longer stretch it can't beat the rate.
     */
    private static final double MAX_CREDIT = 4;
    /** A client's aim may differ from the server's latest by this much (degrees) before the server's is used. */
    private static final float MAX_AIM_DIFF = 30f;
    /** The client's spread is used (so its tracer matches) unless tighter than this share of the server's own. */
    private static final double MIN_SPREAD_SHARE = 0.75;

    private static final class State {
        boolean aiming;
        ReloadTask reload;
        int reloadSlot = -1;
        /** Rounds that may be fired now (fire-rate limit), refilled at the gun's cyclic rate. */
        double credit = MAX_CREDIT;
        long creditNanos = System.nanoTime();
        /** What the client was last told (GunStatePayload); null = nothing yet. */
        Boolean sentReloading, sentInfinite;

        boolean reloading() {
            return reload != null;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static void setInput(ServerPlayer player, boolean trigger, boolean aiming) {
        state(player).aiming = aiming;
    }

    private static boolean infiniteAmmo(ServerPlayer player) {
        return player.isCreative() && Config.CREATIVE_INFINITE_AMMO.get();
    }

    /** Tells the shooter's client the reload / ammo state it predicts with, if it changed (or {@code force}). */
    private static void sendState(ServerPlayer player, State s, boolean force) {
        boolean reloading = s.reloading(), infinite = infiniteAmmo(player);
        if (!force && Boolean.valueOf(reloading).equals(s.sentReloading) && Boolean.valueOf(infinite).equals(s.sentInfinite)) return;
        s.sentReloading = reloading;
        s.sentInfinite = infinite;
        int slot = player.getInventory().selected;
        ItemStack stack = player.getInventory().getItem(slot);
        int ammo = stack.getItem() instanceof GunItem g ? g.ammo(stack) : 0;
        PacketDistributor.sendToPlayer(player, new GunStatePayload(reloading, reloading && s.reload.interruptible(), slot, ammo, infinite));
    }

    /** A round the shooter's client fired (and already shows): fire the real one, or refuse it. */
    public static void shoot(ServerPlayer player, ShootPayload p) {
        State s = state(player);
        int slot = player.getInventory().selected;
        ItemStack stack = player.getMainHandItem();
        boolean ok = p.slot() == slot && stack.getItem() instanceof GunItem && player.isAlive() && !player.isSpectator();
        GunItem gun = ok ? (GunItem) stack.getItem() : null;
        if (ok && s.reloading()) {
            if (s.reload.interruptible()) {
                s.reload = null; // tube magazine: stop loading and fire the chambered round
                sendState(player, s, false);
            } else ok = false;
        }
        if (ok) {
            long now = System.nanoTime();
            s.credit = Math.min(MAX_CREDIT, s.credit + (now - s.creditNanos) / 1e9 / gun.spec().cycleTime());
            s.creditNanos = now;
            ok = s.credit >= 1 - 1e-6;
        }
        boolean infinite = infiniteAmmo(player);
        if (ok && gun.ammo(stack) <= 0 && !infinite) ok = false;
        if (ok) {
            s.credit -= 1;
            fire(player, stack, gun, s, p);
            if (!infinite) gun.setAmmo(stack, gun.ammo(stack) - 1);
        }
        PacketDistributor.sendToPlayer(player, new ShotAckPayload(p.seq(), ok, slot,
                stack.getItem() instanceof GunItem g ? g.ammo(stack) : 0));
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

    /** A reload in progress, a held trigger, the cooldown: none of it survives respawning. */
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    private static void tick(ServerPlayer player) {
        State s = state(player);
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem) || player.isSpectator()) {
            s.reload = null;
        } else {
            // the GeckoLib instance id, so the shooter's client can play the fire animation itself (ShotPredictor)
            GeoItem.getOrAssignId(stack, player.serverLevel());
            if (s.reloading()) {
                if (player.getInventory().selected != s.reloadSlot) s.reload = null; // switching weapons cancels the reload
                else if (s.reload.tick()) s.reload = null;
            }
        }
        sendState(player, s, false);
    }

    /**
     * The real round for the client's shot {@code p}: rolled from the client's seed, so it follows the
     * client's prediction exactly (same spread, same pellets), aimed where the client aimed unless that
     * is implausibly far from where the server last saw the player look.
     */
    private static void fire(ServerPlayer player, ItemStack stack, GunItem gun, State s, ShootPayload p) {
        GunSpec spec = gun.spec();
        ServerLevel level = player.serverLevel();
        RandomSource rand = RandomSource.create(p.seed());
        GunStats stats = GunStats.of(gun, stack);

        boolean aimOk = Math.abs(Mth.wrapDegrees(p.yRot() - player.getYRot())) < MAX_AIM_DIFF
                && Math.abs(p.xRot() - player.getXRot()) < MAX_AIM_DIFF;
        float xRot = aimOk ? p.xRot() : player.getXRot(), yRot = aimOk ? p.yRot() : player.getYRot();
        double ours = ShotMath.spreadDeg(player, gun, stack, s.aiming);
        double spread = p.spread() >= ours * MIN_SPREAD_SHARE ? p.spread() : ours;
        ShotMath.Plan plan = ShotMath.plan(player, gun, stack, s.aiming, xRot, yRot, spread, rand);

        Tracer tracer = gun.loadedTracer(stack) ? spec.cartridge().tracer() : null;
        BulletEntity.shooterPredicts = true; // the shooter already drew these; only others are sent them
        try {
            spec.projectileLoad().launch(level, player, player.getEyePosition(), plan.dir(), spec,
                    spec.muzzleVelocity() * stats.velocity(), gun.gunId(), tracer, rand);
        } finally {
            BulletEntity.shooterPredicts = false;
        }
        MasteryHandler.onShot(player, gun, stack);

        // the shooter's client played its own report, smoke and fire animation; everyone else gets them here
        SoundSet sounds = spec.sounds();
        SoundEvent shot = (stats.suppressed() ? sounds.fireSuppressed() : sounds.fire()).get();
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), shot, SoundSource.PLAYERS, 1.0f, plan.soundPitch());
        // manual action (bolt / pump) sound only in a manual mode (the SPAS-12 also fires semi-auto)
        if (sounds.cycle() != null && gun.fireMode(stack).manualCycle())
            level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), sounds.cycle().get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        Vec3 smoke = ShotMath.muzzleSmoke(player, s.aiming);
        for (ServerPlayer other : level.players())
            if (other != player) level.sendParticles(other, ParticleTypes.SMOKE, false, smoke.x, smoke.y, smoke.z, 1, 0.02, 0.02, 0.02, 0.01);
        GunAnimations.triggerForOthers(gun, player, stack, GunAnimations.FIRE);
    }

    private static void startReload(ServerPlayer player, GunItem gun, State s) {
        if (!s.reloading()) {
            ReloadTask task = gun.spec().feed().startReload(new Reloading(player, gun, player.getInventory().selected));
            if (task != null) {
                s.reload = task;
                s.reloadSlot = player.getInventory().selected;
            }
        }
        sendState(player, s, true); // the client holds its fire until it hears whether a reload started
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
