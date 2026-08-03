package com.wdin.core;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Immutable snapshot of every item stack visible to the tracker
 * (player inventory, offhand, and the currently open container).
 */
public final class InventorySnapshot {
    private final List<ItemStack> stacks;
    private final long timestamp;

    public InventorySnapshot(List<ItemStack> stacks, long timestamp) {
        this.stacks = List.copyOf(stacks);
        this.timestamp = timestamp;
    }

    public List<ItemStack> stacks() {
        return stacks;
    }

    public long timestamp() {
        return timestamp;
    }
}
