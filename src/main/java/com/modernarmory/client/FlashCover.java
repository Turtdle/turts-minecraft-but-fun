package com.modernarmory.client;

import java.util.HashMap;
import java.util.Map;

import com.modernarmory.network.FlashCoverPayload;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * A flashbanged player throws their free hand over their eyes, like in CS: seen in third person by everyone around
 * (FlashCoverPayload) for as long as their white-out lasts, then the hand comes down as their sight returns. The
 * gun arm holds the gun one-handed meanwhile. Applied as the gun arm's {@link #ARM_POSE} (set in
 * ClientState.onRenderPlayer), a two-handed pose that poses both arms.
 */
public final class FlashCover {
    /** Enum extension (META-INF/enumextensions.json): HumanoidModel.ArmPose MODERNARMORY_FLASHED. */
    public static final EnumProxy<HumanoidModel.ArmPose> ARM_POSE = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) FlashCover::transform);

    /** The hand goes up this fast, and comes down over the last {@link #LOWER_S} of the cover. */
    private static final float RAISE_S = 0.18f, LOWER_S = 0.45f;
    /** Share of the white-out the hand stays up: it drops as the sight comes back. */
    private static final float KEEP = 0.85f;
    /** Entity id -> {start, end} of its cover, System.nanoTime(). */
    private static final Map<Integer, long[]> COVERS = new HashMap<>();
    /** For tests: covers started on this client. */
    public static int started;

    public static void onCover(FlashCoverPayload p) {
        long now = System.nanoTime(), end = now + (long) (p.seconds() * KEEP * 1e9);
        long[] old = COVERS.get(p.entityId());
        // already covering: keep the hand up for the longer of the two
        if (old != null && old[1] > now) COVERS.put(p.entityId(), new long[] {old[0], Math.max(old[1], end)});
        else COVERS.put(p.entityId(), new long[] {now, end});
        started++;
    }

    /** How far entity {@code id}'s hand is over its eyes right now: 0 (down) to 1 (covering), eased. */
    public static float progress(int id) {
        long[] c = COVERS.get(id);
        if (c == null) return 0;
        long now = System.nanoTime();
        if (now >= c[1]) {
            COVERS.remove(id);
            return 0;
        }
        float up = (now - c[0]) / 1e9f / RAISE_S, down = (c[1] - now) / 1e9f / LOWER_S;
        float x = Math.max(0, Math.min(1, Math.min(up, down)));
        return x * x * (3 - 2 * x);
    }

    /**
     * The pose: the usual two-handed hold, then, by {@code p}, the free arm swung up in front of the face (palm over
     * the eyes) and the gun arm lowered, holding the gun one-handed.
     */
    static void pose(HumanoidModel<?> model, boolean rightHanded, float p) {
        AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, rightHanded);
        if (p <= 0) return;
        ModelPart gunArm = rightHanded ? model.rightArm : model.leftArm, free = rightHanded ? model.leftArm : model.rightArm;
        float inward = rightHanded ? 1 : -1; // the left arm turns in with +yRot, the right with -yRot
        // the shoulder-to-eyes line: up 135 degrees from hanging, turned in so the hand is over both eyes (following the head)
        free.xRot = Mth.lerp(p, free.xRot, -2.39f + model.head.xRot * 0.6f);
        free.yRot = Mth.lerp(p, free.yRot, inward * 1.0f + model.head.yRot);
        free.zRot = Mth.lerp(p, free.zRot, 0);
        gunArm.xRot += 0.4f * p; // the gun drops a little
        gunArm.yRot = Mth.lerp(p, gunArm.yRot, model.head.yRot);
    }

    private static void transform(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        pose(model, entity.getMainArm() == HumanoidArm.RIGHT, progress(entity.getId()));
    }

    private FlashCover() {}
}
