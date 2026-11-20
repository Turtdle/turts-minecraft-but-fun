package com.modernarmory.grenade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs a grenade's release a few ticks after the button is let go, in step with the throw animation. */
@EventBusSubscriber(modid = com.modernarmory.ModernArmory.MODID)
public final class GrenadeThrows {
    private record Pending(ServerLevel level, long dueTick, Runnable action) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    static void schedule(ServerLevel level, int ticks, Runnable action) {
        PENDING.add(new Pending(level, level.getGameTime() + ticks, action));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(p -> {
            if (p.level().getGameTime() < p.dueTick()) return false;
            due.add(p);
            return true;
        });
        due.forEach(p -> p.action().run());
    }

    private GrenadeThrows() {}
}
