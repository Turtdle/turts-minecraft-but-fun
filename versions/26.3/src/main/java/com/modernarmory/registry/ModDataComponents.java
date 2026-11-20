package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.GunAttachments;
import com.mojang.serialization.Codec;

import net.minecraft.core.registries.Registries;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ModernArmory.MODID);

    /** Rounds currently loaded (magazine + chamber). Missing = fresh gun with a full magazine. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AMMO =
            COMPONENTS.registerComponentType("ammo",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** The loaded rounds are the cartridge's tracer variant (set by the reload from the ammo it used). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> TRACER =
            COMPONENTS.registerComponentType("tracer",
                    b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** Index into the gun's fire-mode list. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FIRE_MODE =
            COMPONENTS.registerComponentType("fire_mode",
                    b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Attachments fitted to a gun (slot -> attachment id). Missing = the gun's factory defaults. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunAttachments>> ATTACHMENTS =
            COMPONENTS.registerComponentType("attachments",
                    b -> b.persistent(GunAttachments.CODEC).networkSynchronized(GunAttachments.STREAM_CODEC));

    /**
     * Draw this stack's 3D model even in a GUI (where items otherwise show their flat sprite). Client-side
     * only, on a copy: the gunsmith's big preview (the item definitions check it, build.gradle).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> RENDER_3D =
            COMPONENTS.registerComponentType("render_3d",
                    b -> b.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

    /** A packed drone's battery charge, 0..1. Missing = full. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> DRONE_BATTERY =
            COMPONENTS.registerComponentType("drone_battery",
                    b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));

    /** The drone (entity UUID) a controller is paired with. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> DRONE_LINK =
            COMPONENTS.registerComponentType("drone_link",
                    b -> b.persistent(net.minecraft.core.UUIDUtil.CODEC).networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));

    private ModDataComponents() {}
}
