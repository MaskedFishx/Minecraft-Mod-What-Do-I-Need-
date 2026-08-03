package com.wdin.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One ingredient of a tracked recipe. Besides the representative stack and the
 * per-craft count, it carries the items and tags the recipe ACTUALLY accepts
 * (read from the recipe's Ingredient definitions, so e.g. a "any planks" slot
 * accepts the planks tag while a specific item slot accepts only that item).
 */
public final class RecipeIngredient {
    private final ItemStack stack;
    private final int count;
    private final List<String> acceptedItems;
    private final List<String> tags;

    public RecipeIngredient(ItemStack stack, int count) {
        this(stack, count, List.of(itemId(stack)), List.of());
    }

    public RecipeIngredient(ItemStack stack, int count, List<String> acceptedItems, List<String> tags) {
        this.stack = stack.copy();
        this.stack.setCount(1);
        this.count = Math.max(1, count);
        this.acceptedItems = new ArrayList<>(acceptedItems == null ? List.of() : acceptedItems);
        if (this.acceptedItems.isEmpty()) {
            this.acceptedItems.add(itemId(stack));
        }
        this.tags = new ArrayList<>(tags == null ? List.of() : tags);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public ItemStack stack() {
        return stack.copy();
    }

    public int count() {
        return count;
    }

    public List<String> acceptedItems() {
        return List.copyOf(acceptedItems);
    }

    public List<String> tags() {
        return List.copyOf(tags);
    }

    public MaterialKey key() {
        return MaterialKey.from(stack);
    }

    /**
     * True when the stack is one of the recipe-accepted items (NBT per config).
     */
    public boolean accepts(ItemStack other, boolean ignoreNbt) {
        if (other == null || other.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(other.getItem()).toString();
        if (!acceptedItems.contains(id)) {
            return false;
        }
        if (ignoreNbt) {
            return true;
        }
        return key().matches(other, false);
    }

    /**
     * True when the stack carries one of the recipe-declared tags.
     */
    public boolean matchesTag(ItemStack other) {
        if (other == null || other.isEmpty() || tags.isEmpty()) {
            return false;
        }
        return other.getTags().anyMatch(t -> tags.contains(t.location().toString()));
    }
}
