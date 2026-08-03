package com.wdin.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdin.WDINConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime-adjustable HUD preferences (position, scale, opacity), persisted to
 * a small JSON file so the config menu and the mouse wheel can change them live.
 */
public final class HudPrefs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static double x = 0.02;
    private static double y = 0.02;
    private static double scale = 1.0;
    private static double opacity = 1.0;
    private static int recipesPerColumn = 10;

    private HudPrefs() {
    }

    public static double x() {
        return x;
    }

    public static double y() {
        return y;
    }

    public static double scale() {
        return scale;
    }

    public static double opacity() {
        return opacity;
    }

    public static int recipesPerColumn() {
        return recipesPerColumn;
    }

    public static void set(double newX, double newY) {
        x = clamp(newX, 0.0, 1.0);
        y = clamp(newY, 0.0, 1.0);
        save();
    }

    public static void setScale(double newScale) {
        scale = clamp(newScale, 0.5, 2.0);
    }

    public static void setOpacity(double newOpacity) {
        opacity = clamp(newOpacity, 0.0, 1.0);
    }

    public static void setRecipesPerColumn(int value) {
        recipesPerColumn = Math.max(1, Math.min(64, value));
    }

    public static void saveAll() {
        save();
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) {
                scale = WDINConfig.hudScale();
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("x")) {
                x = clamp(o.get("x").getAsDouble(), 0.0, 1.0);
            }
            if (o.has("y")) {
                y = clamp(o.get("y").getAsDouble(), 0.0, 1.0);
            }
            if (o.has("scale")) {
                scale = clamp(o.get("scale").getAsDouble(), 0.5, 2.0);
            } else {
                scale = WDINConfig.hudScale();
            }
            if (o.has("opacity")) {
                opacity = clamp(o.get("opacity").getAsDouble(), 0.0, 1.0);
            }
            if (o.has("recipesPerColumn")) {
                recipesPerColumn = Math.max(1, Math.min(64, o.get("recipesPerColumn").getAsInt()));
            }
        } catch (Exception ignored) {
            scale = WDINConfig.hudScale();
        }
    }

    private static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("scale", scale);
            o.addProperty("opacity", opacity);
            o.addProperty("recipesPerColumn", recipesPerColumn);
            Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("wdin").resolve("hud.json");
    }
}
