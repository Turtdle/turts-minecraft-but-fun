package com.modernarmory.registry;

import com.modernarmory.ModernArmory;
import com.modernarmory.mastery.GunMasteryData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data stored on players (NeoForge data attachments; unrelated to gun attachments). */
public final class ModPlayerData {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ModernArmory.MODID);

    /** Gun mastery XP per gun type; saved with the player and kept on death. */
    public static final java.util.function.Supplier<AttachmentType<GunMasteryData>> MASTERY = TYPES.register("gun_mastery",
            () -> AttachmentType.builder(() -> GunMasteryData.EMPTY)
                    // 26.x: serialize() takes a MapCodec (or an IAttachmentSerializer), so the xp map is stored under "xp"
                    .serialize(GunMasteryData.CODEC.fieldOf("xp")).copyOnDeath().build());

    private ModPlayerData() {}
}
