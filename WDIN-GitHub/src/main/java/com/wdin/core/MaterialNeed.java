package com.wdin.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated requirement for one material across all tracked recipes.
 */
public final class MaterialNeed {
    private final MaterialKey key;
    private final ItemStack icon;
    private final List<String> acceptedItems;
    private final List<String> tags;
    private int required;
    private int have;

    public MaterialNeed(MaterialKey key, ItemStack icon, List<String> acceptedItems, List<String> tags, int required, int have) {
        this.key = key;
        this.icon = icon.copy();
        this.icon.setCount(1);
        this.acceptedItems = new ArrayList<>(acceptedItems == null ? List.of() : acceptedItems);
        if (this.acceptedItems.isEmpty() && !icon.isEmpty()) {
            this.acceptedItems.add(BuiltInRegistries.ITEM.getKey(icon.getItem()).toString());
        }
        this.tags = new ArrayList<>(tags == null ? List.of() : tags);
        this.required = Math.max(0, required);
        this.have = Math.max(0, have);
    }

    public MaterialKey key() {
        return key;
    }

    public ItemStack icon() {
        return icon.copy();
    }

    public List<String> acceptedItems() {
        return List.copyOf(acceptedItems);
    }

    public List<String> tags() {
        return List.copyOf(tags);
    }

    public int required() {
        return required;
    }

    public int have() {
        return have;
    }

    public void setHave(int have) {
        this.have = Math.max(0, have);
    }

    public int missing() {
        return Math.max(0, required - have);
    }

    public boolean isMet() {
        return have >= required;
    }

    /**
     * Exact match against the recipe-accepted items.
     */
    public boolean matches(ItemStack other, boolean ignoreNbt) {
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
        return key.matches(other, false);
    }

    /**
     * Match against the recipe-declared accepted tags.
     */
    public boolean matchesTag(ItemStack other) {
        if (other == null || other.isEmpty() || tags.isEmpty()) {
            return false;
        }
        return other.getTags().anyMatch(t -> tags.contains(t.location().toString()));
    }

    public void merge(MaterialNeed other) {
        this.required += other.required;
        for (String a : other.acceptedItems) {
            if (!acceptedItems.contains(a)) {
                acceptedItems.add(a);
            }
        }
        for (String t : other.tags) {
            if (!tags.contains(t)) {
                tags.add(t);
            }
        }
    }
}
