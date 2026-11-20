package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server -> pilot: your drone's charge went off here, while flying with this velocity (m/s). */
public record DroneBlastPayload(Vec3 pos, Vec3 velocity) implements CustomPacketPayload {
    public static final Type<DroneBlastPayload> TYPE = new Type<>(ModernArmory.id("drone_blast"));
    public static final StreamCodec<FriendlyByteBuf, DroneBlastPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> {
                buf.writeDouble(p.pos.x).writeDouble(p.pos.y).writeDouble(p.pos.z);
                buf.writeDouble(p.velocity.x).writeDouble(p.velocity.y).writeDouble(p.velocity.z);
            },
            buf -> new DroneBlastPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
