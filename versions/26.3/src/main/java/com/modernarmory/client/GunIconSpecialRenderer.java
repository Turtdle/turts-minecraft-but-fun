package com.modernarmory.client;

import java.util.function.Consumer;

import com.modernarmory.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A gun's inventory icon for the attachments fitted ({@link GunIcons}): a flat textured square over the item's unit
 * square, the way a 2D item model is drawn. The item definitions use it for the GUI display context (task
 * prepareSharedAssets in build.gradle), with the gun's sprite model as the base for its transforms.
 */
public final class GunIconSpecialRenderer implements SpecialModelRenderer<Identifier> {
    @Override
    public @Nullable Identifier extractArgument(ItemStack stack) {
        return stack.getItem() instanceof GunItem gun ? GunIcons.texture(gun, stack) : null;
    }

    @Override
    public void submit(@Nullable Identifier texture, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       boolean hasFoil, int outlineColor) {
        if (texture == null) return;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, vc) -> {
            vertex(vc, pose, 0, 0, 0, 1, light, overlay);
            vertex(vc, pose, 1, 0, 1, 1, light, overlay);
            vertex(vc, pose, 1, 1, 1, 0, light, overlay);
            vertex(vc, pose, 0, 1, 0, 0, light, overlay);
        });
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay) {
        vc.addVertex(pose, x, y, 0.5f).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 0.5f));
        output.accept(new Vector3f(1, 0, 0.5f));
        output.accept(new Vector3f(1, 1, 0.5f));
        output.accept(new Vector3f(0, 1, 0.5f));
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Identifier> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Identifier> bake(SpecialModelRenderer.BakingContext context) {
            return new GunIconSpecialRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
