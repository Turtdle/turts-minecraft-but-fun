package com.modernarmory.network;

import org.joml.Vector3f;

import com.modernarmory.ModernArmory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Pilot's client -> server, every tick while it flies the drone: where the drone is now, its velocity
 * (m/s), body attitude and camera pitch, collective thrust, the impact speed lost against blocks this tick (0 = none)
 * and whether it is resting on the ground.
 */
public record DronePilotPayload(int drone, Vec3 pos, Vector3f velocity, org.joml.Quaternionf attitude, float pitch, float thrust,
                                float impact, boolean onGround) implements CustomPacketPayload {
    public static final Type<DronePilotPayload> TYPE = new Type<>(ModernArmory.id("drone_pilot"));
    public static final StreamCodec<FriendlyByteBuf, DronePilotPayload> STREAM_CODEC =
            CustomPacketPayload.codec(DronePilotPayload::write, DronePilotPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(drone);
        buf.writeDouble(pos.x).writeDouble(pos.y).writeDouble(pos.z);
        buf.writeVector3f(velocity);
        buf.writeQuaternion(attitude);
        buf.writeFloat(pitch).writeFloat(thrust).writeFloat(impact);
        buf.writeBoolean(onGround);
    }

    private static DronePilotPayload read(FriendlyByteBuf buf) {
        return new DronePilotPayload(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readVector3f(), buf.readQuaternion(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
