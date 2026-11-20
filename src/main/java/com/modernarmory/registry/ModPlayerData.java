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
            () -> AttachmentType.builder(() -> GunMasteryData.EMPTY).serialize(GunMasteryData.CODEC).copyOnDeath().build());

    private ModPlayerData() {}
}
