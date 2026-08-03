package com.wdin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.wdin";

    // Track the recipe currently under the cursor (JEI recipe view / inventory slot / JEI overlay)
    public static final KeyMapping TRACK_RECIPE = new KeyMapping(
            "key.wdin.track_recipe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            CATEGORY);

    // Quick toggle: add/remove the item under the cursor
    public static final KeyMapping QUICK_TRACK = new KeyMapping(
            "key.wdin.quick_track",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    // Remove the currently selected tracked recipe
    public static final KeyMapping REMOVE_SELECTED = new KeyMapping(
            "key.wdin.remove_selected",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    // Open the quantity-adjust screen for the selected recipe
    public static final KeyMapping OPEN_QUANTITY = new KeyMapping(
            "key.wdin.open_quantity",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY);

    // Select previous/next tracked recipe in the HUD list
    public static final KeyMapping SELECT_UP = new KeyMapping(
            "key.wdin.select_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            CATEGORY);

    public static final KeyMapping SELECT_DOWN = new KeyMapping(
            "key.wdin.select_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            CATEGORY);

    private KeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TRACK_RECIPE);
        event.register(QUICK_TRACK);
        event.register(REMOVE_SELECTED);
        event.register(OPEN_QUANTITY);
        event.register(SELECT_UP);
        event.register(SELECT_DOWN);
    }
}
