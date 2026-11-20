package com.modernarmory.mastery;

import com.modernarmory.ModernArmory;
import com.modernarmory.entity.BulletEntity;
import com.modernarmory.item.GunItem;
import com.modernarmory.network.LocalSoundPayload;
import com.modernarmory.network.MasterySyncPayload;
import com.modernarmory.registry.ModItems;
import com.modernarmory.registry.ModPlayerData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Earning mastery XP (shots, hostile kills) and looking up a player's mastery effects for a gun. */
@EventBusSubscriber(modid = ModernArmory.MODID)
public final class MasteryHandler {

    public static GunMasteryData data(Player player) {
        return player.getData(ModPlayerData.MASTERY);
    }

    /** The mastery effects a player has with this gun type (works on both sides; clients get synced data). */
    public static Mastery.Effects effects(Player player, GunItem gun) {
        return Mastery.effects(data(player).level(gun.gunId()));
    }

    /** Called by GunHandler for every round fired. */
    public static void onShot(ServerPlayer player, GunItem gun, ItemStack stack) {
        boolean manual = gun.fireMode(stack).manualCycle();
        award(player, gun.gunId(), manual ? Mastery.XP_PER_SHOT_MANUAL : Mastery.XP_PER_SHOT);
    }

    @SubscribeEvent
    static void onKill(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Enemy)) return; // hostile mobs only
        if (!(event.getSource().getDirectEntity() instanceof BulletEntity bullet)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || bullet.gunId() == null) return;
        int xp = Mastery.XP_KILL_BASE + Math.round(victim.getMaxHealth() * Mastery.XP_KILL_PER_MAX_HEALTH);
        if (bullet.lastHitWasHeadshot()) xp += Mastery.XP_HEADSHOT_BONUS;
        award(player, bullet.gunId(), xp);
    }

    private static void award(ServerPlayer player, String gunId, int amount) {
        GunMasteryData before = data(player);
        int oldLevel = before.level(gunId);
        GunMasteryData after = before.add(gunId, amount);
        player.setData(ModPlayerData.MASTERY, after);
        int newLevel = after.level(gunId);
        if (newLevel > oldLevel) {
            player.sendOverlayMessage(Component.literal(gunName(gunId) + " Mastery " + Mastery.roman(newLevel))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            // 26.x has no ServerPlayer#playNotifySound: send it as an own-player sound like the gun sounds
            PacketDistributor.sendToPlayer(player, new LocalSoundPayload(SoundEvents.PLAYER_LEVELUP.location(), 0.6f, 1.4f, 0));
        }
        // every round on full auto would be ~13 syncs a second: a level-up goes at once, the rest at most once a second
        long now = player.level().getGameTime();
        long[] st = SYNCED.computeIfAbsent(player, k -> new long[] {Long.MIN_VALUE / 2, 0});
        if (newLevel > oldLevel || now - st[0] >= SYNC_EVERY) sync(player);
        else st[1] = 1; // owed: the tick handler sends it
    }

    /** Per player: game time of the last sync, and whether one is owed. */
    private static final java.util.Map<ServerPlayer, long[]> SYNCED = new java.util.WeakHashMap<>();
    /** Least ticks between two mastery syncs while XP keeps coming in. */
    private static final int SYNC_EVERY = 20;

    @SubscribeEvent
    static void onPlayerTickSync(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long[] st = SYNCED.get(p);
        if (st != null && st[1] != 0 && p.level().getGameTime() - st[0] >= SYNC_EVERY) sync(p);
    }

    private static String gunName(String gunId) {
        var item = BuiltInRegistries.ITEM.getValue(ModernArmory.id(gunId));
        return item instanceof GunItem g ? Component.translatable(g.getDescriptionId()).getString() : gunId;
    }

    public static void sync(ServerPlayer player) {
        long[] st = SYNCED.computeIfAbsent(player, k -> new long[2]);
        st[0] = player.level().getGameTime();
        st[1] = 0;
        PacketDistributor.sendToPlayer(player, new MasterySyncPayload(data(player)));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    private MasteryHandler() {}
}
