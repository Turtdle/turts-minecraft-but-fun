package com.modernarmory.client.renderer;

import com.modernarmory.grenade.GrenadeEntity;
import com.modernarmory.grenade.GrenadeTumble;
import com.modernarmory.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A thrown grenade: its item model (pin and spoon hidden, GrenadeRenderer), tumbling end over end as it flies and rolling on its side (GrenadeTumble).
 * <p>
 * 26.x: the item is resolved into an ItemStackRenderState when the render state is extracted (display context
 * NONE, as vanilla's ThrownItemRenderer does with GROUND) and submitted from that state.
 */
public class GrenadeEntityRenderer extends EntityRenderer<GrenadeEntity, GrenadeEntityRenderer.State> {
    /** Model units -> blocks for the thrown grenade (the models are a little larger than life to read in game). */
    static float SCALE = 1.0f;

    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yaw, tilt, roll, lean, lift;
    }

    private final ItemModelResolver itemModelResolver;

    public GrenadeEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.itemModelResolver = ctx.getItemModelResolver();
        this.shadowRadius = 0.1f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GrenadeEntity grenade, State state, float partialTick) {
        super.extractRenderState(grenade, state, partialTick);
        GrenadeTumble t = grenade.tumble();
        state.yaw = t.yaw(partialTick);
        state.tilt = t.tilt(partialTick);
        state.roll = t.roll(partialTick);
        state.lean = t.lean(partialTick);
        state.lift = t.lift(partialTick);
        var item = ModItems.grenade(grenade.type());
        if (item == null) state.item.clear();
        else itemModelResolver.updateForNonLiving(state.item, new ItemStack(item), ItemDisplayContext.NONE, grenade);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.item.isEmpty()) {
            pose.pushPose();
            pose.translate(0, state.lift, 0);
            pose.rotate(Axis.YP.rotationDegrees(state.yaw));  // 26.x: PoseStack#rotate (mulPose takes matrices)
            pose.rotate(Axis.ZP.rotationDegrees(state.tilt)); // banked left / right (random per throw)
            pose.rotate(Axis.XP.rotationDegrees(state.roll)); // end over end / rolling forward
            pose.rotate(Axis.ZP.rotationDegrees(state.lean)); // tipped onto its side on the ground
            pose.scale(SCALE, SCALE, SCALE);
            // no extra half-block shift: the item renderer already centres the model, and shifting again put the
            // grenade ~0.7 blocks off the point it spins about, so it flew in loops
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }
}
