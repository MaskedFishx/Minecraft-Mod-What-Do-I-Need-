package com.wdin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wdin.WDIN;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side singleton holding the list of tracked recipes and its JSON persistence.
 */
public final class TrackingManager {
    private static final TrackingManager INSTANCE = new TrackingManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<TrackedRecipe> recipes = new ArrayList<>();
    private boolean dirty = false;

    private TrackingManager() {
    }

    public static TrackingManager instance() {
        return INSTANCE;
    }

    public synchronized TrackedRecipe addRecipe(TrackedRecipe recipe) {
        for (TrackedRecipe existing : recipes) {
            if (existing.sameSignature(recipe)) {
                existing.setQuantity(existing.quantity() + recipe.quantity());
                markDirty();
                return existing;
            }
        }
        recipes.add(recipe);
        markDirty();
        return recipe;
    }

    public boolean containsSignature(TrackedRecipe recipe) {
        for (TrackedRecipe existing : recipes) {
            if (existing.sameSignature(recipe)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean removeSignature(TrackedRecipe recipe) {
        boolean removed = recipes.removeIf(existing -> existing.sameSignature(recipe));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public synchronized void remove(UUID id) {
        boolean removed = recipes.removeIf(r -> r.id().equals(id));
        if (removed) {
            markDirty();
        }
    }

    public synchronized void clear() {
        if (!recipes.isEmpty()) {
            markDirty();
        }
        recipes.clear();
    }

    public synchronized void setQuantity(UUID id, int quantity) {
        for (TrackedRecipe r : recipes) {
            if (r.id().equals(id)) {
                r.setQuantity(quantity);
                markDirty();
                return;
            }
        }
    }

    public List<TrackedRecipe> recipes() {
        return List.copyOf(recipes);
    }

    public boolean isEmpty() {
        return recipes.isEmpty();
    }

    public synchronized List<MaterialNeed> computeAllNeeds() {
        Map<MaterialKey, MaterialNeed> merged = new LinkedHashMap<>();
        for (TrackedRecipe r : recipes) {
            for (MaterialNeed n : r.computeNeeds()) {
                MaterialNeed existing = merged.get(n.key());
                if (existing == null) {
                    merged.put(n.key(), n);
                } else {
                    existing.merge(n);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    public List<MaterialNeed> computeDisplayNeeds(InventorySnapshot snapshot) {
        List<MaterialNeed> needs = computeAllNeeds();
        MaterialCounter.count(needs, snapshot);
        return needs;
    }

    public TrackedRecipe byId(UUID id) {
        for (TrackedRecipe r : recipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- persistence

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("wdin").resolve("tracked.json");
    }

    public synchronized void load(RegistryAccess registryAccess) {
        recipes.clear();
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("recipes");
            if (arr == null) {
                return;
            }
            for (JsonElement el : arr) {
                TrackedRecipe r = deserialize(el.getAsJsonObject(), registryAccess);
                if (r != null) {
                    recipes.add(r);
                }
            }
            dirty = false;
        } catch (Exception e) {
            WDIN.LOGGER.warn("Failed to load tracked recipes", e);
        }
    }

    private void markDirty() {
        dirty = true;
    }

    public void flushSave(RegistryAccess registryAccess) {
        if (!dirty) {
            return;
        }
        synchronized (this) {
            if (dirty) {
                save(registryAccess);
                dirty = false;
            }
        }
    }

    private synchronized void save(RegistryAccess registryAccess) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonArray arr = new JsonArray();
            for (TrackedRecipe r : recipes) {
                arr.add(serialize(r, registryAccess));
            }
            root.add("recipes", arr);
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WDIN.LOGGER.warn("Failed to save tracked recipes", e);
        }
    }

    private static JsonObject serialize(TrackedRecipe r, RegistryAccess registryAccess) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id().toString());
        o.addProperty("quantity", r.quantity());
        o.addProperty("output", saveStack(r.output(), registryAccess));
        o.addProperty("stationName", r.stationName() == null ? "" : r.stationName());
        o.addProperty("stationType", r.stationType() == null ? "" : r.stationType());
        JsonArray ings = new JsonArray();
        for (RecipeIngredient ing : r.ingredients()) {
            JsonObject io = new JsonObject();
            io.addProperty("stack", saveStack(ing.stack(), registryAccess));
            io.addProperty("count", ing.count());
            ings.add(io);
        }
        o.add("ingredients", ings);
        return o;
    }

    private static String saveStack(ItemStack stack, RegistryAccess registryAccess) {
        try {
            CompoundTag tag = new CompoundTag();
            stack.save(registryAccess, tag);
            return tag.toString();
        } catch (Exception e) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
    }

    private static TrackedRecipe deserialize(JsonObject o, RegistryAccess registryAccess) {
        try {
            UUID id = UUID.fromString(o.get("id").getAsString());
            int quantity = o.has("quantity") ? o.get("quantity").getAsInt() : 1;
            String stationName = o.has("stationName") && !o.get("stationName").getAsString().isEmpty()
                    ? o.get("stationName").getAsString() : null;
            String stationType = o.has("stationType") && !o.get("stationType").getAsString().isEmpty()
                    ? o.get("stationType").getAsString() : null;
            ItemStack output = loadStack(o.get("output").getAsString(), registryAccess);
            if (output == null || output.isEmpty()) {
                return null;
            }
            JsonArray arr = o.getAsJsonArray("ingredients");
            if (arr == null || arr.isEmpty()) {
                return null;
            }
            List<RecipeIngredient> ings = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject io = el.getAsJsonObject();
                ItemStack s = loadStack(io.get("stack").getAsString(), registryAccess);
                if (s == null || s.isEmpty()) {
                    continue;
                }
                int count = io.has("count") ? io.get("count").getAsInt() : 1;
                ings.add(new RecipeIngredient(s, count));
            }
            if (ings.isEmpty()) {
                return null;
            }
            return new TrackedRecipe(id, output, ings, quantity, stationName, stationType);
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack loadStack(String s, RegistryAccess registryAccess) {
        try {
            CompoundTag tag = TagParser.parseTag(s);
            return ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
    }
}
