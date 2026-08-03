package com.wdin;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public final class WDINConfig {
    public static final ModConfigSpec SPEC;

    // HUD style
    public static final ConfigValue<Double> HUD_SCALE;
    public static final ConfigValue<Boolean> HUD_BACKGROUND;
    public static final ConfigValue<Boolean> SHOW_ONLY_MISSING;
    public static final ConfigValue<Boolean> AUTO_HIDE_COMPLETE;
    public static final ConfigValue<Boolean> SHOW_ITEM_NAMES;
    public static final ConfigValue<Boolean> SHOW_MATERIAL_DETAILS;
    public static final ConfigValue<Integer> COLOR_FULFILLED;
    public static final ConfigValue<Integer> COLOR_MISSING;

    // Scanning
    public static final ConfigValue<Integer> SCAN_INTERVAL_MS;
    public static final ConfigValue<Boolean> IGNORE_NBT;
    public static final ConfigValue<Boolean> CONTAINER_HIGHLIGHT;

    // Controls
    public static final ConfigValue<Boolean> DRAG_ENABLED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("hud");
        HUD_SCALE = b.comment("HUD display scale.").defineInRange("hudScale", 1.0, 0.5, 2.0);
        HUD_BACKGROUND = b.comment("Draw a dark background behind the HUD list.").define("hudBackground", true);
        SHOW_ONLY_MISSING = b.comment("Only show materials that are still missing.").define("onlyShowMissing", false);
        AUTO_HIDE_COMPLETE = b.comment("Hide the whole HUD when every tracked material is fulfilled.").define("autoHideWhenComplete", false);
        SHOW_ITEM_NAMES = b.comment("Show item names on each HUD row.").define("showItemNames", true);
        SHOW_MATERIAL_DETAILS = b.comment("Show each recipe's material needs below its HUD row.").define("showMaterialDetails", true);
        COLOR_FULFILLED = b.comment("ARGB color for fulfilled materials, as a hex integer.").defineInRange("colorFulfilled", 0xFF55FF55, Integer.MIN_VALUE, Integer.MAX_VALUE);
        COLOR_MISSING = b.comment("ARGB color for missing materials, as a hex integer.").defineInRange("colorMissing", 0xFFFF5555, Integer.MIN_VALUE, Integer.MAX_VALUE);
        b.pop();

        b.push("scanning");
        SCAN_INTERVAL_MS = b.comment("Inventory scan interval in milliseconds (throttle).").defineInRange("scanIntervalMs", 200, 50, 5000);
        IGNORE_NBT = b.comment("Ignore NBT differences when matching items.").define("ignoreNbt", true);
        CONTAINER_HIGHLIGHT = b.comment("Highlight required materials inside open chests/containers.").define("containerHighlight", true);
        b.pop();

        b.push("controls");
        DRAG_ENABLED = b.comment("Allow dragging the HUD with the left mouse button.").define("hudDragEnabled", true);
        b.pop();

        SPEC = b.build();
    }

    private WDINConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    public static double hudScale() {
        return HUD_SCALE.get();
    }

    public static boolean hudBackground() {
        return HUD_BACKGROUND.get();
    }

    public static boolean onlyShowMissing() {
        return SHOW_ONLY_MISSING.get();
    }

    public static boolean autoHideWhenComplete() {
        return AUTO_HIDE_COMPLETE.get();
    }

    public static boolean showItemNames() {
        return SHOW_ITEM_NAMES.get();
    }

    public static boolean showMaterialDetails() {
        return SHOW_MATERIAL_DETAILS.get();
    }

    public static int colorFulfilled() {
        return COLOR_FULFILLED.get();
    }

    public static int colorMissing() {
        return COLOR_MISSING.get();
    }

    public static int scanIntervalMs() {
        return SCAN_INTERVAL_MS.get();
    }

    public static boolean ignoreNbt() {
        return IGNORE_NBT.get();
    }

    public static boolean containerHighlight() {
        return CONTAINER_HIGHLIGHT.get();
    }

    public static boolean dragEnabled() {
        return DRAG_ENABLED.get();
    }
}
