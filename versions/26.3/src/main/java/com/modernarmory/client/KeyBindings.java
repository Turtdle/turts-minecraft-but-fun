package com.modernarmory.client;

import com.modernarmory.ModernArmory;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class KeyBindings {
    /**
     * 26.x: key categories are registered objects; the label's lang key is "key.category.modernarmory.main"
     * (added through src/main/lang-overlay, since the shared root lang file uses the 1.21.1 key).
     */
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(ModernArmory.id("main"));

    public static final KeyMapping RELOAD = new KeyMapping("key.modernarmory.reload", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping FIRE_MODE = new KeyMapping("key.modernarmory.fire_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY);

    /** Leave a drone's camera view and return to your body. */
    public static final KeyMapping DRONE_EXIT = new KeyMapping("key.modernarmory.drone_exit", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD, InputConstants.KEY_B, CATEGORY);

    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(DRONE_EXIT);
        event.register(RELOAD);
        event.register(FIRE_MODE);
    }

    private KeyBindings() {}
}
