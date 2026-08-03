package com.wdin.core;

import com.wdin.WDINConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans the player inventory, offhand and the currently open container,
 * throttled by the configured interval (default 200 ms).
 */
public final class InventoryScanner {
    private static long lastScan = 0L;
    private static InventorySnapshot cached = new InventorySnapshot(List.of(), 0L);

    private InventoryScanner() {
    }

    public static InventorySnapshot snapshot(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastScan < WDINConfig.scanIntervalMs()) {
            return cached;
        }
        lastScan = now;

        List<ItemStack> stacks = new ArrayList<>();
        Player player = mc.player;
        if (player != null) {
            Inventory inv = player.getInventory();
            // main inventory + hotbar
            for (ItemStack s : inv.items) {
                if (!s.isEmpty()) {
                    stacks.add(s);
                }
            }
            // offhand
            ItemStack off = player.getOffhandItem();
            if (!off.isEmpty()) {
                stacks.add(off);
            }
            // open container (chest, barrel, ...) - non-player slots only
            AbstractContainerMenu menu = player.containerMenu;
            if (menu != null && menu != player.inventoryMenu) {
                for (Slot slot : menu.slots) {
                    if (slot.container instanceof Inventory) {
                        continue;
                    }
                    ItemStack s = slot.getItem();
                    if (!s.isEmpty()) {
                        stacks.add(s);
                    }
                }
            }
        }
        cached = new InventorySnapshot(stacks, now);
        return cached;
    }
}
