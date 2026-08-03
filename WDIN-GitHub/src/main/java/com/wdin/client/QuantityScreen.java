package com.wdin.client;

import com.wdin.core.InventoryScanner;
import com.wdin.core.MaterialCounter;
import com.wdin.core.MaterialNeed;
import com.wdin.core.TrackedRecipe;
import com.wdin.core.TrackingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Centered quantity-adjust screen for the selected tracked recipe.
 * A slider limits the craft quantity to 1..256; the +/- buttons sit below it.
 */
public class QuantityScreen extends Screen {
    private static final int MAX_MATERIAL_ROWS = 8;
    private static final int MIN_QTY = 1;
    private static final int MAX_QTY = 256;

    private final UUID recipeId;
    private TrackedRecipe recipe;
    private Button qtyButton;

    public QuantityScreen(TrackedRecipe recipe) {
        super(Component.translatable("wdin.gui.title"));
        this.recipeId = recipe.id();
        this.recipe = recipe;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        this.recipe = TrackingManager.instance().byId(recipeId);
        if (this.recipe == null) {
            this.onClose();
            return;
        }
        int cx = this.width / 2;
        int x0 = cx - 123;

        // quantity slider (1..256)
        int sliderY = 56 + materialRows() * 18 + 10;
        addRenderableWidget(new QuantitySlider(cx - 100, sliderY, 200, 20,
                Component.translatable("wdin.gui.quantity"),
                (recipe.quantity() - MIN_QTY) / (double) (MAX_QTY - MIN_QTY),
                this::applySlider));

        // +/- buttons below the slider
        int y = sliderY + 28;
        final TrackedRecipe r = this.recipe;
        this.qtyButton = Button.builder(Component.literal(String.valueOf(r.quantity())), b -> {}).bounds(x0 + 68, y, 56, 18).build();
        addRenderableWidget(Button.builder(Component.literal("-10"), b -> adjust(r, -10)).bounds(x0, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> adjust(r, -1)).bounds(x0 + 38, y, 26, 18).build());
        addRenderableWidget(this.qtyButton);
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> adjust(r, 1)).bounds(x0 + 128, y, 26, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+10"), b -> adjust(r, 10)).bounds(x0 + 158, y, 34, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("wdin.gui.remove"), b -> {
            TrackingManager.instance().remove(recipeId);
            this.onClose();
        }).bounds(x0 + 196, y, 50, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("wdin.gui.close"), b -> this.onClose()).bounds(cx - 30, y + 26, 60, 20).build());
    }

    private int materialRows() {
        List<MaterialNeed> needs = recipe.computeNeeds();
        return Math.min(needs.size(), MAX_MATERIAL_ROWS);
    }

    private void applySlider(double fraction) {
        int qty = MIN_QTY + (int) Math.round(fraction * (MAX_QTY - MIN_QTY));
        setQuantity(qty);
    }

    private void adjust(TrackedRecipe r, int delta) {
        setQuantity(r.quantity() + delta);
    }

    private void setQuantity(int qty) {
        qty = Math.max(MIN_QTY, Math.min(MAX_QTY, qty));
        TrackingManager.instance().setQuantity(recipeId, qty);
        refresh();
    }

    private void refresh() {
        this.recipe = TrackingManager.instance().byId(recipeId);
        if (this.recipe != null && this.qtyButton != null) {
            this.qtyButton.setMessage(Component.literal(String.valueOf(this.recipe.quantity())));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        if (recipe == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int cx = this.width / 2;

        // centered output row
        ItemStack out = recipe.output();
        String name = out.getHoverName().getString() + "  x" + recipe.quantity();
        int nameWidth = Math.max(this.font.width(name) + 24, 120);
        int outLeft = cx - nameWidth / 2;
        g.renderItem(out, outLeft, 28);
        g.drawString(this.font, name, outLeft + 20, 32, 0xFFFFFF);

        // centered material list
        List<MaterialNeed> needs = recipe.computeNeeds();
        MaterialCounter.countAvailable(needs, InventoryScanner.snapshot(Minecraft.getInstance()));
        int maxLine = 0;
        int shownCount = 0;
        for (MaterialNeed n : needs) {
            if (shownCount >= MAX_MATERIAL_ROWS) {
                break;
            }
            maxLine = Math.max(maxLine, this.font.width(n.have() + " / " + n.required() + "  " + n.icon().getHoverName().getString()));
            shownCount++;
        }
        int matLeft = cx - (maxLine + 26) / 2;
        int y = 56;
        int shown = 0;
        for (MaterialNeed n : needs) {
            if (shown >= MAX_MATERIAL_ROWS) {
                break;
            }
            g.renderItem(n.icon(), matLeft, y);
            String line = n.have() + " / " + n.required() + "  " + n.icon().getHoverName().getString();
            g.drawString(this.font, line, matLeft + 22, y + 4, n.isMet() ? 0x55FF55 : 0xFF5555);
            y += 18;
            shown++;
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class QuantitySlider extends AbstractSliderButton {
        private final String label;
        private final DoubleConsumer onChange;

        QuantitySlider(int x, int y, int width, int height, Component label, double value, DoubleConsumer onChange) {
            super(x, y, width, height, label, value);
            this.label = label.getString();
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int qty = MIN_QTY + (int) Math.round(this.value * (MAX_QTY - MIN_QTY));
            this.setMessage(Component.literal(this.label + ": " + qty));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(this.value);
        }
    }
}
