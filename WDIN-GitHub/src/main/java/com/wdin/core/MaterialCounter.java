package com.wdin.core;

import com.wdin.WDINConfig;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Counts available items for each requirement. Exact matches against the
 * recipe-accepted items are consumed first, then matches via the tags the
 * recipe itself declared (never arbitrary tags).
 */
public final class MaterialCounter {
    private MaterialCounter() {
    }

    public static void count(List<MaterialNeed> needs, InventorySnapshot snapshot) {
        boolean ignoreNbt = WDINConfig.ignoreNbt();
        List<ItemStack> stacks = snapshot.stacks();
        int[] used = new int[stacks.size()];

        for (MaterialNeed need : needs) {
            int have = 0;
            int remaining = need.required();

            // exact accepted items
            for (int i = 0; i < stacks.size() && remaining > 0; i++) {
                ItemStack s = stacks.get(i);
                int avail = s.getCount() - used[i];
                if (avail <= 0 || !need.matches(s, ignoreNbt)) {
                    continue;
                }
                int take = Math.min(avail, remaining);
                used[i] += take;
                remaining -= take;
                have += take;
            }

            // recipe-declared tags for the remainder
            if (remaining > 0 && !need.tags().isEmpty()) {
                for (int i = 0; i < stacks.size() && remaining > 0; i++) {
                    ItemStack s = stacks.get(i);
                    int avail = s.getCount() - used[i];
                    if (avail <= 0) {
                        continue;
                    }
                    if (need.matches(s, ignoreNbt)) {
                        continue; // exact match already consumed
                    }
                    if (need.matchesTag(s)) {
                        int take = Math.min(avail, remaining);
                        used[i] += take;
                        remaining -= take;
                        have += take;
                    }
                }
            }

            need.setHave(have);
        }
    }

    /**
     * Raw "available" count per need (no cross-recipe consumption), used for
     * per-recipe HUD display.
     */
    public static void countAvailable(List<MaterialNeed> needs, InventorySnapshot snapshot) {
        boolean ignoreNbt = WDINConfig.ignoreNbt();
        List<ItemStack> stacks = snapshot.stacks();

        for (MaterialNeed need : needs) {
            int have = 0;
            for (ItemStack s : stacks) {
                if (!s.isEmpty() && need.matches(s, ignoreNbt)) {
                    have += s.getCount();
                }
            }
            for (ItemStack s : stacks) {
                if (s.isEmpty() || need.matches(s, ignoreNbt)) {
                    continue;
                }
                if (need.matchesTag(s)) {
                    have += s.getCount();
                }
            }
            need.setHave(have);
        }
    }
}
