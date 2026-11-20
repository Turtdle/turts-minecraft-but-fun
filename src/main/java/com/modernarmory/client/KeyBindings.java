package com.modernarmory.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class KeyBindings {
    private static final String CATEGORY = "key.categories.modernarmory";

    public static final KeyMapping RELOAD = new KeyMapping("key.modernarmory.reload", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping FIRE_MODE = new KeyMapping("key.modernarmory.fire_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** Leave a drone's camera view and return to your body. */
    public static final KeyMapping DRONE_EXIT = new KeyMapping("key.modernarmory.drone_exit", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    static void register(RegisterKeyMappingsEvent event) {
        event.register(DRONE_EXIT);
        event.register(RELOAD);
        event.register(FIRE_MODE);
    }

    private KeyBindings() {}
}
