package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.entity.BulletEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(ModernArmory.MODID);

    /**
     * Bullets are simulated on both sides with the same deterministic physics. The server never
     * sends position updates (updateInterval is longer than a bullet lives). Clients get the exact
     * starting velocity in the spawn packet and fly the bullet themselves.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BulletEntity>> BULLET =
            // 26.x: registerEntityType builds the type with its ResourceKey
            ENTITIES.registerEntityType("bullet", BulletEntity::new, MobCategory.MISC, b -> b
                    .sized(0.05f, 0.05f)
                    .clientTrackingRange(16)
                    .updateInterval(200)
                    .noSave()
                    .noSummon()
                    .fireImmune());

    /** A thrown hand grenade (grenade.GrenadeEntity): server-simulated, position sent every tick. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.modernarmory.grenade.GrenadeEntity>> GRENADE =
            ENTITIES.registerEntityType("grenade", com.modernarmory.grenade.GrenadeEntity::new, MobCategory.MISC, b -> b
                    .sized(0.2f, 0.2f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune());

    /**
     * A deployed drone (drone.DroneEntity). Flown by its pilot's client or the server; position sent every
     * tick, tracked out to the drone link's game range. Hitbox per type (DroneEntity.getDimensions).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.modernarmory.drone.DroneEntity>> DRONE =
            ENTITIES.registerEntityType("drone", com.modernarmory.drone.DroneEntity::new, MobCategory.MISC, b -> b
                    .sized(0.85f, 0.2f)
                    .clientTrackingRange(10)
                    .updateInterval(1));

    /**
     * The laser turret operator's seat and fire control (laser.TurretSeat): made on boarding. 26.x only lets entities ride
     * a type that can be saved, so it is saved (unlike 1.21.1), and a loaded seat with nobody on it removes itself.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.modernarmory.laser.TurretSeat>> TURRET_SEAT =
            ENTITIES.registerEntityType("turret_seat", com.modernarmory.laser.TurretSeat::new, MobCategory.MISC, b -> b
                    .sized(0.4f, 0.1f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune());

    private ModEntities() {}
}
