package com.wdin.client;

import com.wdin.WDIN;
import com.wdin.WDINConfig;
import com.wdin.core.InventoryScanner;
import com.wdin.core.InventorySnapshot;
import com.wdin.core.MaterialCounter;
import com.wdin.core.MaterialNeed;
import com.wdin.core.TrackedRecipe;
import com.wdin.core.TrackingManager;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HUD listing tracked recipes. One recipe is selected (highlighted). When the
 * number of recipes exceeds the configured recipes-per-column limit, the list
 * wraps into additional columns to the right; the first column stays anchored
 * at the HUD position. Left-drag moves the HUD, right-click selects a row,
 * double-click opens the HUD config menu.
 */
public final class HUDOverlay {
    private static final List<Block> blocks = new ArrayList<>();
    private static boolean dragging = false;
    private static double grabDx = 0;
    private static double grabDy = 0;
    private static int hudLeft = 0;
    private static int hudTop = 0;
    private static int hudWidth = 0;
    private static int hudHeight = 0;
    private static UUID selectedId = null;
    private static long lastLeftClick = 0L;
    private static double lastLeftX = 0;
    private static double lastLeftY = 0;

    private record Block(TrackedRecipe recipe, int x, int y, int w, int h) {
    }

    private HUDOverlay() {
    }

    // ------------------------------------------------------------------ selection

    public static TrackedRecipe selectedRecipe() {
        List<TrackedRecipe> recipes = TrackingManager.instance().recipes();
        if (recipes.isEmpty()) {
            selectedId = null;
            return null;
        }
        if (selectedId != null) {
            for (TrackedRecipe r : recipes) {
                if (r.id().equals(selectedId)) {
                    return r;
                }
            }
        }
        selectedId = recipes.get(0).id();
        return recipes.get(0);
    }

    public static void selectNext() {
        List<TrackedRecipe> recipes = TrackingManager.instance().recipes();
        if (recipes.isEmpty()) {
            selectedId = null;
            return;
        }
        int idx = indexOf(recipes);
        idx = (idx + 1) % recipes.size();
        selectedId = recipes.get(idx).id();
    }

    public static void selectPrevious() {
        List<TrackedRecipe> recipes = TrackingManager.instance().recipes();
        if (recipes.isEmpty()) {
            selectedId = null;
            return;
        }
        int idx = indexOf(recipes);
        idx = (idx - 1 + recipes.size()) % recipes.size();
        selectedId = recipes.get(idx).id();
    }

    public static void select(UUID id) {
        selectedId = id;
    }

    public static void clearSelection() {
        selectedId = null;
    }

    private static int indexOf(List<TrackedRecipe> recipes) {
        if (selectedId != null) {
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i).id().equals(selectedId)) {
                    return i;
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ render

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        blocks.clear();

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (dragging) {
            updatePosition(mc, sw, sh);
        }

        int x = Math.max(0, (int) (HudPrefs.x() * sw));
        int y = Math.max(0, (int) (HudPrefs.y() * sh));
        hudLeft = x;
        hudTop = y;

        List<TrackedRecipe> recipes = TrackingManager.instance().recipes();
        if (recipes.isEmpty()) {
            // 1.0.1 behavior: hide the whole HUD while nothing is tracked
            hudWidth = 0;
            hudHeight = 0;
            return;
        }

        InventorySnapshot snapshot = InventoryScanner.snapshot(mc);
        TrackedRecipe selected = selectedRecipe();
        boolean details = WDINConfig.showMaterialDetails();
        boolean names = WDINConfig.showItemNames();

        List<List<MaterialNeed>> needsList = new ArrayList<>();
        boolean anyMissing = false;
        for (TrackedRecipe r : recipes) {
            List<MaterialNeed> all = r.computeNeeds();
            MaterialCounter.countAvailable(all, snapshot);
            for (MaterialNeed n : all) {
                if (!n.isMet()) {
                    anyMissing = true;
                    break;
                }
            }
            List<MaterialNeed> display = WDINConfig.onlyShowMissing()
                    ? all.stream().filter(n -> !n.isMet()).toList()
                    : all;
            needsList.add(display);
        }
        if (WDINConfig.autoHideWhenComplete() && !anyMissing) {
            hudWidth = 0;
            hudHeight = 0;
            return;
        }

        double scale = HudPrefs.scale();
        double opacity = HudPrefs.opacity();
        int rowHeight = 18;
        int indent = 14;
        int pad = 4;
        int colGap = 8;
        int perColumn = HudPrefs.recipesPerColumn();

        // chunk recipe indices into columns
        List<List<Integer>> columns = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            if (columns.isEmpty() || columns.get(columns.size() - 1).size() >= perColumn) {
                columns.add(new ArrayList<>());
            }
            columns.get(columns.size() - 1).add(i);
        }

        // measure every recipe block (rows, height, width)
        int[] blockHeights = new int[recipes.size()];
        int[] blockWidths = new int[recipes.size()];
        for (int i = 0; i < recipes.size(); i++) {
            TrackedRecipe r = recipes.get(i);
            List<MaterialNeed> disp = needsList.get(i);
            int rows = 1 + (details ? disp.size() : 0);
            blockHeights[i] = rows * rowHeight;
            int w = mc.font.width(r.output().getHoverName().getString() + "  x" + r.quantity());
            if (r.hasStation()) {
                w += mc.font.width(" 于 ") + 16 + mc.font.width(r.stationName() + " 制造");
            }
            if (details) {
                for (MaterialNeed n : disp) {
                    String text = n.have() + " / " + n.required()
                            + (names ? "  " + n.icon().getHoverName().getString() : "");
                    w = Math.max(w, indent + mc.font.width(text));
                }
            }
            blockWidths[i] = 22 + w + 8;
        }

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) scale, (float) scale, 1.0f);

        int colX = 0;
        int maxColHeight = 0;

        for (List<Integer> col : columns) {
            int colWidth = 0;
            int colHeight = 0;
            for (int i : col) {
                colWidth = Math.max(colWidth, blockWidths[i]);
                colHeight += blockHeights[i];
            }
            if (WDINConfig.hudBackground()) {
                int bgAlpha = (int) (0x99 * opacity);
                g.fill(colX - pad, -pad, colX + colWidth + pad, colHeight + pad, (bgAlpha << 24) | 0x000000);
            }

            int colRowY = 0;
            for (int i : col) {
                int blockH = blockHeights[i];
                int blockTop = colRowY;
                try {
                    TrackedRecipe r = recipes.get(i);
                    List<MaterialNeed> disp = needsList.get(i);
                    boolean sel = r.id().equals(selected.id());

                    if (sel) {
                        int hlAlpha = (int) (0x30 * opacity);
                        int barAlpha = (int) (0xFF * opacity);
                        g.fill(colX - 1, blockTop - 1, colX + colWidth, blockTop + blockH, (hlAlpha << 24) | 0xFFFFFF);
                        g.fill(colX - 2, blockTop - 1, colX, blockTop + blockH, (barAlpha << 24) | 0xFFAA00);
                    }
                    g.renderItem(r.output(), colX, blockTop);
                    int color = sel ? 0xFFFFFF : 0xC8C8C8;
                    String headerText = r.output().getHoverName().getString() + "  x" + r.quantity();
                    int tx = colX + 20;
                    g.drawString(mc.font, headerText, tx, blockTop + 4, color);
                    tx += mc.font.width(headerText);
                    if (r.hasStation()) {
                        g.drawString(mc.font, " 于 ", tx, blockTop + 4, 0x808080);
                        tx += mc.font.width(" 于 ");
                        try {
                            IDrawable icon = JEIHooks.stationIcon(r.stationType());
                            if (icon != null) {
                                icon.draw(g, tx, blockTop);
                            }
                        } catch (Throwable t) {
                            WDIN.LOGGER.error("Failed to draw station icon", t);
                        }
                        tx += 16;
                        g.drawString(mc.font, r.stationName(), tx, blockTop + 4, 0xB0B0B0);
                        tx += mc.font.width(r.stationName());
                        g.drawString(mc.font, " 制造", tx, blockTop + 4, 0x808080);
                    }
                    colRowY += rowHeight;
                    if (details) {
                        for (MaterialNeed n : disp) {
                            int ncolor = n.isMet() ? WDINConfig.colorFulfilled() : WDINConfig.colorMissing();
                            g.renderItem(n.icon(), colX + indent, colRowY);
                            String text = n.have() + " / " + n.required()
                                    + (names ? "  " + n.icon().getHoverName().getString() : "");
                            g.drawString(mc.font, text, colX + indent + 18, colRowY + 4, ncolor);
                            colRowY += rowHeight;
                        }
                    }
                    blocks.add(new Block(r,
                            x + (int) (colX * scale),
                            y + (int) (blockTop * scale),
                            (int) (colWidth * scale),
                            (int) (blockH * scale)));
                } catch (Throwable t) {
                    WDIN.LOGGER.error("Failed to render HUD recipe block " + i, t);
                    colRowY = blockTop + blockH;
                }
            }

            maxColHeight = Math.max(maxColHeight, colHeight);
            colX += colWidth + colGap;
        }

        g.pose().popPose();
        hudWidth = (int) (colX * scale);
        hudHeight = (int) (maxColHeight * scale);
    }

    // ------------------------------------------------------------------ mouse

    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.screen instanceof QuantityScreen
                || mc.screen instanceof HudConfigScreen) {
            return;
        }
        // HUD mouse interaction only works in-game or with the chat box open;
        // every other screen (pause menu, JEI, inventories, ...) disables it.
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        double mx = JEIHooks.mouseX(mc);
        double my = JEIHooks.mouseY(mc);
        int button = event.getButton();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (contains(mx, my)) {
                long now = System.currentTimeMillis();
                boolean doubleClick = now - lastLeftClick < 300L
                        && Math.abs(mx - lastLeftX) < 5
                        && Math.abs(my - lastLeftY) < 5;
                lastLeftClick = now;
                lastLeftX = mx;
                lastLeftY = my;
                if (doubleClick) {
                    dragging = false;
                    mc.setScreen(new HudConfigScreen());
                    event.setCanceled(true);
                    return;
                }
                if (WDINConfig.dragEnabled()) {
                    dragging = true;
                    grabDx = mx - HudPrefs.x() * mc.getWindow().getGuiScaledWidth();
                    grabDy = my - HudPrefs.y() * mc.getWindow().getGuiScaledHeight();
                    event.setCanceled(true);
                }
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            Block block = blockAt(mx, my);
            if (block != null) {
                selectedId = block.recipe().id();
                event.setCanceled(true);
            }
        }
    }

    public static void onMouseButtonPost(InputEvent.MouseButton.Post event) {
        if (dragging && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_RELEASE) {
            dragging = false;
        }
    }

    private static void updatePosition(Minecraft mc, int sw, int sh) {
        double mx = JEIHooks.mouseX(mc);
        double my = JEIHooks.mouseY(mc);
        HudPrefs.set((mx - grabDx) / sw, (my - grabDy) / sh);
    }

    public static boolean contains(double mx, double my) {
        return mx >= hudLeft && mx < hudLeft + hudWidth && my >= hudTop && my < hudTop + hudHeight;
    }

    private static Block blockAt(double mx, double my) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block b = blocks.get(i);
            if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                return b;
            }
        }
        return null;
    }
}
