package com.modernarmory.client;

import java.util.List;

import com.modernarmory.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/**
 * A gun's item model with the GUI view handed to the gun's renderer, which draws the icon for the stack's
 * attachments ({@link GunIcons}) instead of the fixed sprite; hands, the ground and item frames are unchanged.
 */
final class GunGuiModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel gui;

    private GunGuiModel(BakedModel original) {
        super(original);
        this.gui = new Gui(original);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        return context == ItemDisplayContext.GUI ? gui : super.applyTransform(context, poseStack, leftHand);
    }

    /** Wraps every gun's item model (ModelEvent.ModifyBakingResult). */
    static void wrap(ModelEvent.ModifyBakingResult event) {
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof GunItem)) continue;
            var key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
            BakedModel model = event.getModels().get(key);
            if (model != null) event.getModels().put(key, new GunGuiModel(model));
        }
        GunIcons.clear(); // new resources: geometry may have changed
    }

    /** The GUI view: no quads of its own, drawn by the item's renderer (GunRenderer -> GunIcons), flat-lit like a 2D item. */
    private record Gui(BakedModel original) implements BakedModel {
        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return true;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return original.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
