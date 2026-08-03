package com.wdin.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A tracked recipe: output item, the ingredients consumed per craft,
 * and how many crafts the player wants to perform.
 */
public final class TrackedRecipe {
    private final UUID id;
    private final ItemStack output;
    private final List<RecipeIngredient> ingredients;
    private int quantity;
    private final String stationName;
    private final String stationType;

    public TrackedRecipe(UUID id, ItemStack output, List<RecipeIngredient> ingredients, int quantity) {
        this(id, output, ingredients, quantity, null, null);
    }

    public TrackedRecipe(UUID id, ItemStack output, List<RecipeIngredient> ingredients, int quantity,
                         String stationName, String stationType) {
        this.id = id != null ? id : UUID.randomUUID();
        this.output = output.copy();
        this.ingredients = new ArrayList<>(ingredients);
        this.quantity = Math.max(1, quantity);
        this.stationName = stationName;
        this.stationType = stationType;
    }

    /**
     * A "recipe" for a single raw item: tracking the item itself needs 1 of it.
     */
    public static TrackedRecipe raw(ItemStack item) {
        ItemStack out = item.copy();
        out.setCount(1);
        return new TrackedRecipe(UUID.randomUUID(), out, List.of(new RecipeIngredient(item, 1)), 1);
    }

    public UUID id() {
        return id;
    }

    public ItemStack output() {
        return output.copy();
    }

    public int outputPerCraft() {
        return Math.max(1, output.getCount());
    }

    public List<RecipeIngredient> ingredients() {
        return List.copyOf(ingredients);
    }

    public int quantity() {
        return quantity;
    }

    public String stationName() {
        return stationName;
    }

    public String stationType() {
        return stationType;
    }

    public boolean hasStation() {
        return stationName != null && !stationName.isEmpty();
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, Math.min(9999, quantity));
    }

    public String displayName() {
        return output.getHoverName().getString();
    }

    public List<MaterialNeed> computeNeeds() {
        Map<MaterialKey, int[]> amounts = new LinkedHashMap<>();
        Map<MaterialKey, ItemStack> icons = new HashMap<>();
        Map<MaterialKey, List<String>> accepted = new HashMap<>();
        Map<MaterialKey, List<String>> tagMap = new HashMap<>();
        for (RecipeIngredient ing : ingredients) {
            MaterialKey k = ing.key();
            int[] arr = amounts.computeIfAbsent(k, x -> new int[1]);
            arr[0] += ing.count() * quantity;
            icons.putIfAbsent(k, ing.stack());
            accepted.computeIfAbsent(k, x -> new ArrayList<>()).addAll(ing.acceptedItems());
            tagMap.computeIfAbsent(k, x -> new ArrayList<>()).addAll(ing.tags());
        }
        List<MaterialNeed> needs = new ArrayList<>();
        amounts.forEach((k, v) -> needs.add(new MaterialNeed(k, icons.get(k), accepted.get(k), tagMap.get(k), v[0], 0)));
        return needs;
    }

    /**
     * Identity signature used to avoid tracking the same recipe twice.
     */
    public String signature() {
        List<String> parts = new ArrayList<>();
        parts.add(BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
        for (RecipeIngredient ing : ingredients) {
            parts.add(ing.key().toString());
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    public boolean sameSignature(TrackedRecipe other) {
        return signature().equals(other.signature());
    }
}
