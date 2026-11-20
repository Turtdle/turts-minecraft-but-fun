package com.modernarmory.network;

import com.modernarmory.ModernArmory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Server -> pilot: the server refused your drone's last move; it is really here. */
public record DroneCorrectPayload(int drone, Vec3 pos) implements CustomPacketPayload {
    public static final Type<DroneCorrectPayload> TYPE = new Type<>(ModernArmory.id("drone_correct"));
    public static final StreamCodec<FriendlyByteBuf, DroneCorrectPayload> STREAM_CODEC = CustomPacketPayload.codec(
            (p, buf) -> { buf.writeVarInt(p.drone); buf.writeDouble(p.pos.x).writeDouble(p.pos.y).writeDouble(p.pos.z); },
            buf -> new DroneCorrectPayload(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
