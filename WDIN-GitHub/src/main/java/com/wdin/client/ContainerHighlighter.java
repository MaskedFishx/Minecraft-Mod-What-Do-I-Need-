package com.wdin.client;

import com.wdin.WDINConfig;
import com.wdin.core.InventoryScanner;
import com.wdin.core.MaterialNeed;
import com.wdin.core.TrackingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Highlights material slots inside open containers with a colored border.
 */
public final class ContainerHighlighter {
    private ContainerHighlighter() {
    }

    public static void render(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        if (!WDINConfig.containerHighlight()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        List<MaterialNeed> needs = TrackingManager.instance().computeDisplayNeeds(InventoryScanner.snapshot(mc));
        if (needs.isEmpty()) {
            return;
        }

        int left = ScreenReflect.leftPos(containerScreen);
        int top = ScreenReflect.topPos(containerScreen);
        boolean ignoreNbt = WDINConfig.ignoreNbt();

        for (Slot slot : containerScreen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            MaterialNeed need = match(needs, stack, ignoreNbt);
            if (need == null) {
                continue;
            }
            int sx = left + slot.x;
            int sy = top + slot.y;
            int fill = need.isMet() ? 0x3F55FF55 : 0x5FFF5555;
            int border = need.isMet() ? 0xFF55FF55 : 0xFFFF5555;
            g.fill(sx, sy, sx + 16, sy + 16, fill);
            g.fill(sx, sy, sx + 16, sy + 1, border);
            g.fill(sx, sy + 15, sx + 16, sy + 16, border);
            g.fill(sx, sy, sx + 1, sy + 16, border);
            g.fill(sx + 15, sy, sx + 16, sy + 16, border);
        }
    }

    private static MaterialNeed match(List<MaterialNeed> needs, ItemStack stack, boolean ignoreNbt) {
        for (MaterialNeed need : needs) {
            if (need.matches(stack, ignoreNbt) || need.matchesTag(stack)) {
                return need;
            }
        }
        return null;
    }
}
