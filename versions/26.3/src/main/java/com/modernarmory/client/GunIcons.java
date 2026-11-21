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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Guns' inventory icons with the attachments actually fitted: the gun's sprite is repainted from its icon geometry
 * ({@link GunIconPainter}) with the bones the 3D model would hide for this stack left out ({@link GunParts}, the
 * rule GunRenderer follows), so a suppressor, a scope or a drum shows in the hotbar too. One texture per
 * loadout, made the first time it is drawn and kept (least recently used dropped past {@link #MAX_TEXTURES}).
 * Drawn in GUIs by {@link GunIconSpecialRenderer} (the item definition's GUI case); a gun without geometry shows
 * its shipped sprite.
 */
public final class GunIcons {
    private static final int MAX_TEXTURES = 256;
    private static final Map<String, Optional<GunIconPainter.Geometry>> GEOMETRY = new HashMap<>();
    private static final LinkedHashMap<String, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Identifier> eldest) {
            if (size() <= MAX_TEXTURES) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            return true;
        }
    };
    private static int next;
    /** For tests: icons painted so far. */
    public static int painted;

    /** The icon texture for {@code stack}: its loadout's painted sprite, or the shipped sprite if the gun has no geometry. */
    public static Identifier texture(GunItem gun, ItemStack stack) {
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
        Identifier id = TEXTURES.get(key.toString());
        if (id == null) {
            id = upload(model, GunIconPainter.paint(g, hidden), g.size());
            TEXTURES.put(key.toString(), id);
        }
        return id;
    }

    private static Identifier upload(String model, int[] argb, int size) {
        NativeImage img = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setPixel(x, y, argb[y * size + x]); // 26.x NativeImage takes ARGB
            }
        }
        Identifier id = ModernArmory.id("gun_icon/" + model + "_" + next++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, img));
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

    /** Forget cached geometry and textures (resources reloaded). */
    public static void clear() {
        GEOMETRY.clear();
        TEXTURES.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        TEXTURES.clear();
    }

    private GunIcons() {}
}
