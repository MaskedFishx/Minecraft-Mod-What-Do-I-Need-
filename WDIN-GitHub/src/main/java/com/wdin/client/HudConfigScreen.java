package com.wdin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * HUD configuration menu opened by double-clicking the HUD:
 * adjusts HUD opacity and scale.
 */
public class HudConfigScreen extends Screen {
    public HudConfigScreen() {
        super(Component.translatable("wdin.gui.hud_config"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x0 = cx - 110;
        int y = 58;

        addRenderableWidget(new LabeledSlider(x0, y, 220, 20,
                Component.translatable("wdin.gui.opacity"), HudPrefs.opacity(),
                v -> Math.round(v * 100) + "%",
                v -> HudPrefs.setOpacity(v)));
        y += 34;
        addRenderableWidget(new LabeledSlider(x0, y, 220, 20,
                Component.translatable("wdin.gui.size"),
                (HudPrefs.scale() - 0.5) / 1.5,
                v -> "x" + String.format("%.2f", 0.5 + v * 1.5),
                v -> HudPrefs.setScale(0.5 + v * 1.5)));
        y += 34;
        addRenderableWidget(new LabeledSlider(x0, y, 220, 20,
                Component.translatable("wdin.gui.recipes_per_column"),
                (HudPrefs.recipesPerColumn() - 1) / 63.0,
                v -> String.valueOf(1 + (int) Math.round(v * 63)),
                v -> HudPrefs.setRecipesPerColumn(1 + (int) Math.round(v * 63))));
        y += 44;
        addRenderableWidget(Button.builder(Component.translatable("wdin.gui.close"), b -> this.onClose())
                .bounds(cx - 30, y, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        HudPrefs.saveAll();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class LabeledSlider extends AbstractSliderButton {
        private final Component label;
        private final Function<Double, String> text;
        private final DoubleConsumer onChange;

        LabeledSlider(int x, int y, int width, int height, Component label, double value,
                      Function<Double, String> text, DoubleConsumer onChange) {
            super(x, y, width, height, label, value);
            this.label = label;
            this.text = text;
            this.onChange = onChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(this.label.getString() + ": " + this.text.apply(this.value)));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept(this.value);
        }
    }
}
