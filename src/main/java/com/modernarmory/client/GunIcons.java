package com.modernarmory.client;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.modernarmory.ModernArmory;
import com.modernarmory.attachment.GunAttachments;
import com.modernarmory.attachment.GunStats;
import com.modernarmory.gun.GunParts;
import com.modernarmory.item.GunItem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Guns' inventory icons with the attachments actually fitted: the gun's sprite is repainted from its icon geometry
 * ({@link GunIconPainter}) with the bones the 3D model would hide for this stack left out ({@link GunParts}, the
 * rule GunRenderer follows), so a suppressor, a scope or a drum shows in the hotbar too. One texture per
 * loadout, made the first time it is drawn and kept (least recently used dropped past {@link #MAX_TEXTURES}).
 * Drawn by GunRenderer for the GUI display context; a gun without geometry shows its shipped sprite.
 */
public final class GunIcons {
    private static final int MAX_TEXTURES = 256;
    private static final Map<String, Optional<GunIconPainter.Geometry>> GEOMETRY = new HashMap<>();
    private static final LinkedHashMap<String, ResourceLocation> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            if (size() <= MAX_TEXTURES) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            return true;
        }
    };
    private static int next;
    /** For tests: icons painted so far. */
    public static int painted;

    /** The icon texture for {@code stack}: its loadout's painted sprite, or the shipped sprite if the gun has no geometry. */
    public static ResourceLocation texture(GunItem gun, ItemStack stack) {
        String model = gun.modelId();
        GunIconPainter.Geometry g = geometry(model);
        if (g == null) return ModernArmory.id("textures/item/icon/" + model + ".png");
        GunAttachments atts = GunStats.of(gun, stack).attachments();
        Set<String> fitted = GunParts.fitted(gun, atts);
        boolean[] hidden = new boolean[g.bones().length];
        StringBuilder key = new StringBuilder(model).append(':');
        for (int i = 0; i < hidden.length; i++) {
            Boolean h = GunParts.hidden(g.bones()[i], gun, atts, fitted, false); // an icon is never first person
            hidden[i] = h != null && h;
            if (hidden[i]) key.append(i).append(',');
        }
        ResourceLocation id = TEXTURES.get(key.toString());
        if (id == null) {
            id = upload(model, GunIconPainter.paint(g, hidden), g.size());
            TEXTURES.put(key.toString(), id);
        }
        return id;
    }

    private static ResourceLocation upload(String model, int[] argb, int size) {
        NativeImage img = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int c = argb[y * size + x]; // ARGB -> NativeImage's ABGR
                img.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF);
            }
        }
        ResourceLocation id = ModernArmory.id("gun_icon/" + model + "_" + next++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
        painted++;
        return id;
    }

    private static GunIconPainter.Geometry geometry(String model) {
        return GEOMETRY.computeIfAbsent(model, m -> {
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(ModernArmory.id("icon_geometry/" + m + ".json"));
                if (res.isEmpty()) return Optional.empty();
                try (Reader in = res.get().openAsReader()) {
                    return Optional.of(GunIconPainter.Geometry.read(in));
                }
            } catch (Exception e) {
                ModernArmory.LOGGER.warn("Gun icon geometry for {} unreadable; using the sprite", m, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * Draws {@code stack}'s icon as a flat square over the item's unit cube, the way a 2D item model is drawn
     * (GUI display context: the slot, +y up, facing the viewer).
     */
    public static void drawGui(GunItem gun, ItemStack stack, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(gun, stack)));
        PoseStack.Pose pose = ps.last();
        vertex(vc, pose, 0, 0, 0, 1, light, overlay);
        vertex(vc, pose, 1, 0, 1, 1, light, overlay);
        vertex(vc, pose, 1, 1, 1, 0, light, overlay);
        vertex(vc, pose, 0, 1, 0, 0, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay) {
        vc.addVertex(pose, x, y, 0.5f).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
    }

    /** Forget cached geometry and textures (resources reloaded). */
    public static void clear() {
        GEOMETRY.clear();
        TEXTURES.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        TEXTURES.clear();
    }

    private GunIcons() {}
}
