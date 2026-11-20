package com.modernarmory.gun;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modernarmory.ModernArmory;

/**
 * What a gun's 3D model provides, read from its GeckoLib geo file (bundled in the mod jar, so the
 * server sees it too). The model is the single source of which attachments a gun can wear: every
 * attachment it can show is a bone {@code att_<id>} (tools/modelgen/attachments.py), so a part the
 * model doesn't have can't be fitted (it would be invisible).
 */
public final class GunModels {
    private static final String GEO = "/assets/" + ModernArmory.MODID + "/geo/item/%s.geo.json";
    private static final Map<String, Optional<Set<String>>> ATTACHMENTS = new ConcurrentHashMap<>();

    /** Attachment ids modelled in {@code modelId}, or empty if the geo file can't be read. */
    public static Optional<Set<String>> attachments(String modelId) {
        return ATTACHMENTS.computeIfAbsent(modelId, GunModels::read);
    }

    /** Whether the model shows attachment {@code id} (true when the model can't be read, so nothing breaks). */
    public static boolean hasAttachment(String modelId, String id) {
        return attachments(modelId).map(s -> s.contains(id)).orElse(true);
    }

    private static Optional<Set<String>> read(String modelId) {
        String path = String.format(GEO, modelId);
        try (InputStream in = GunModels.class.getResourceAsStream(path)) {
            if (in == null) {
                ModernArmory.LOGGER.warn("gun model {} not found; not restricting its attachments", path);
                return Optional.empty();
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> ids = new TreeSet<>();
            for (JsonElement geo : root.getAsJsonArray("minecraft:geometry")) {
                for (JsonElement bone : geo.getAsJsonObject().getAsJsonArray("bones")) {
                    String name = bone.getAsJsonObject().get("name").getAsString();
                    if (name.startsWith("att_")) ids.add(name.substring(4));
                }
            }
            return Optional.of(Set.copyOf(ids));
        } catch (Exception e) {
            ModernArmory.LOGGER.warn("cannot read gun model {}: {}", path, e.toString());
            return Optional.empty();
        }
    }

    private GunModels() {}
}
