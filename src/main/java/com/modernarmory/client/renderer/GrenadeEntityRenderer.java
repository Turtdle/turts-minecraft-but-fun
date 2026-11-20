package com.modernarmory.client.renderer;

import com.modernarmory.grenade.GrenadeEntity;
import com.modernarmory.grenade.GrenadeTumble;
import com.modernarmory.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** A thrown grenade: its item model (pin and spoon hidden, GrenadeRenderer), tumbling end over end as it flies and rolling on its side (GrenadeTumble). */
public class GrenadeEntityRenderer extends EntityRenderer<GrenadeEntity> {
    /** Model units -> blocks for the thrown grenade (the models are a little larger than life to read in game). */
    static float SCALE = 1.0f;

    public GrenadeEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.1f;
    }

    @Override
    public void render(GrenadeEntity grenade, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        var item = ModItems.grenade(grenade.type());
        if (item == null) return;
        pose.pushPose();
        GrenadeTumble t = grenade.tumble();
        pose.translate(0, t.lift(partialTick), 0);
        pose.mulPose(Axis.YP.rotationDegrees(t.yaw(partialTick)));  // face the way it is going
        pose.mulPose(Axis.ZP.rotationDegrees(t.tilt(partialTick))); // banked left / right (random per throw)
        pose.mulPose(Axis.XP.rotationDegrees(t.roll(partialTick))); // end over end / rolling forward
        pose.mulPose(Axis.ZP.rotationDegrees(t.lean(partialTick))); // tipped onto its side on the ground
        pose.scale(SCALE, SCALE, SCALE);
        // no extra half-block shift: the item renderer already centres the model, and shifting again put the
        // grenade ~0.7 blocks off the point it spins about, so it flew in loops
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, grenade.level(), grenade.getId());
        pose.popPose();
        super.render(grenade, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GrenadeEntity grenade) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
