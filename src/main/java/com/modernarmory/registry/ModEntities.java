package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.entity.BulletEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ModernArmory.MODID);

    /**
     * Bullets are simulated on both sides with the same deterministic physics. The server never
     * sends position updates (updateInterval is longer than a bullet lives). Clients get the exact
     * starting velocity in the spawn packet and fly the bullet themselves.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<BulletEntity>> BULLET =
            ENTITIES.register("bullet", () -> EntityType.Builder.<BulletEntity>of(BulletEntity::new, MobCategory.MISC)
                    .sized(0.05f, 0.05f)
                    .clientTrackingRange(16)
                    .updateInterval(200)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("bullet"));

    /** A thrown hand grenade (grenade.GrenadeEntity): server-simulated, position sent every tick. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.modernarmory.grenade.GrenadeEntity>> GRENADE =
            ENTITIES.register("grenade", () -> EntityType.Builder.<com.modernarmory.grenade.GrenadeEntity>of(
                            com.modernarmory.grenade.GrenadeEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build("grenade"));

    /**
     * A deployed drone (drone.DroneEntity). Flown by its pilot's client or the server; position sent every
     * tick, tracked out to the drone link's game range. Hitbox per type (DroneEntity.getDimensions).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.modernarmory.drone.DroneEntity>> DRONE =
            ENTITIES.register("drone", () -> EntityType.Builder.<com.modernarmory.drone.DroneEntity>of(
                            com.modernarmory.drone.DroneEntity::new, MobCategory.MISC)
                    .sized(0.75f, 0.2f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("drone"));

    /** The laser turret operator's seat and fire control (laser.TurretSeat): made on boarding, never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.modernarmory.laser.TurretSeat>> TURRET_SEAT =
            ENTITIES.register("turret_seat", () -> EntityType.Builder.<com.modernarmory.laser.TurretSeat>of(
                            com.modernarmory.laser.TurretSeat::new, MobCategory.MISC)
                    .sized(0.4f, 0.1f)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("turret_seat"));

    private ModEntities() {}
}
