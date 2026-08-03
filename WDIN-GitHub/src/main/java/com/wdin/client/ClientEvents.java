package com.wdin.client;

import com.wdin.core.TrackedRecipe;
import com.wdin.core.TrackingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KeyBindings.register(event);
    }

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(HudPrefs::load);
    }

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            TrackingManager.instance().load(mc.level.registryAccess());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            TrackingManager.instance().flushSave(mc.level.registryAccess());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        TrackingManager.instance().flushSave(mc.level.registryAccess());
        if (mc.screen instanceof QuantityScreen || mc.screen instanceof HudConfigScreen) {
            return;
        }

        while (KeyBindings.REMOVE_SELECTED.consumeClick()) {
            handleRemoveSelected(mc);
        }
        while (KeyBindings.OPEN_QUANTITY.consumeClick()) {
            handleOpenQuantity(mc);
        }
        while (KeyBindings.QUICK_TRACK.consumeClick()) {
            handleQuickTrack(mc);
        }
        if (mc.screen == null) {
            while (KeyBindings.SELECT_UP.consumeClick()) {
                HUDOverlay.selectPrevious();
            }
            while (KeyBindings.SELECT_DOWN.consumeClick()) {
                HUDOverlay.selectNext();
            }
        }
        handleTrackOrSelect(mc);
    }

    /**
     * With no screen open, a single click of the track key cycles the HUD selection.
     * Holding the key does nothing by itself; hold + mouse wheel switches up/down.
     */
    private static void handleTrackOrSelect(Minecraft mc) {
        if (mc.screen != null) {
            return;
        }
        if (KeyBindings.TRACK_RECIPE.consumeClick()) {
            cycleSelection(mc);
        }
    }

    private static void cycleSelection(Minecraft mc) {
        if (TrackingManager.instance().isEmpty()) {
            overlay(mc, Component.translatable("wdin.message.no_recipes").withStyle(ChatFormatting.GRAY));
            return;
        }
        HUDOverlay.selectNext();
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.screen instanceof QuantityScreen
                || mc.screen instanceof HudConfigScreen) {
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) {
            return;
        }
        double delta = event.getScrollDeltaY();

        // hold G + wheel: switch selection up/down
        if (KeyBindings.TRACK_RECIPE.isDown() && mc.screen == null && !TrackingManager.instance().isEmpty()) {
            if (delta > 0) {
                HUDOverlay.selectPrevious();
            } else if (delta < 0) {
                HUDOverlay.selectNext();
            }
            event.setCanceled(true);
            return;
        }

        // wheel over the HUD: resize it (opacity is only changed in the config menu)
        if (HUDOverlay.contains(JEIHooks.mouseX(mc), JEIHooks.mouseY(mc))) {
            double step = 0.1;
            HudPrefs.setScale(HudPrefs.scale() + (delta > 0 ? step : -step));
            HudPrefs.saveAll();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuantityScreen || mc.screen instanceof HudConfigScreen) {
            return;
        }
        if (KeyBindings.TRACK_RECIPE.matches(event.getKeyCode(), event.getScanCode())) {
            ItemStack hovered = JEIHooks.hoveredItem(mc);
            if (hovered == null || hovered.isEmpty()) {
                // backpack/container open but nothing under the cursor: cycle selection
                cycleSelection(mc);
            } else {
                handleTrackRecipe(mc);
            }
            event.setCanceled(true);
        } else if (KeyBindings.QUICK_TRACK.matches(event.getKeyCode(), event.getScanCode())) {
            handleQuickTrack(mc);
            event.setCanceled(true);
        } else if (KeyBindings.REMOVE_SELECTED.matches(event.getKeyCode(), event.getScanCode())) {
            handleRemoveSelected(mc);
            event.setCanceled(true);
        } else if (KeyBindings.OPEN_QUANTITY.matches(event.getKeyCode(), event.getScanCode())) {
            handleOpenQuantity(mc);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        HUDOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        ContainerHighlighter.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        HUDOverlay.onMouseButtonPre(event);
    }

    @SubscribeEvent
    public static void onMouseButtonPost(InputEvent.MouseButton.Post event) {
        HUDOverlay.onMouseButtonPost(event);
    }

    // ------------------------------------------------------------------ actions

    private static void handleTrackRecipe(Minecraft mc) {
        TrackedRecipe recipe = JEIHooks.trackFromContext(mc);
        if (recipe == null) {
            overlay(mc, Component.translatable("wdin.message.no_target").withStyle(ChatFormatting.GRAY));
            return;
        }
        TrackedRecipe added = TrackingManager.instance().addRecipe(recipe);
        HUDOverlay.select(added.id());
        overlay(mc, Component.translatable("wdin.message.tracked", added.output().getHoverName()));
    }

    private static void handleQuickTrack(Minecraft mc) {
        ItemStack hovered = JEIHooks.hoveredItem(mc);
        if (hovered == null || hovered.isEmpty()) {
            overlay(mc, Component.translatable("wdin.message.no_target").withStyle(ChatFormatting.GRAY));
            return;
        }
        TrackedRecipe raw = TrackedRecipe.raw(hovered);
        TrackingManager manager = TrackingManager.instance();
        if (manager.containsSignature(raw)) {
            manager.removeSignature(raw);
            HUDOverlay.clearSelection();
            overlay(mc, Component.translatable("wdin.message.quick_removed", raw.output().getHoverName()));
        } else {
            TrackedRecipe added = manager.addRecipe(raw);
            HUDOverlay.select(added.id());
            overlay(mc, Component.translatable("wdin.message.quick_tracked", added.output().getHoverName()));
        }
    }

    private static void handleRemoveSelected(Minecraft mc) {
        TrackedRecipe selected = HUDOverlay.selectedRecipe();
        if (selected == null) {
            overlay(mc, Component.translatable("wdin.message.no_recipes").withStyle(ChatFormatting.GRAY));
            return;
        }
        TrackingManager.instance().remove(selected.id());
        HUDOverlay.clearSelection();
        overlay(mc, Component.translatable("wdin.message.removed_selected", selected.output().getHoverName()));
    }

    private static void handleOpenQuantity(Minecraft mc) {
        TrackedRecipe selected = HUDOverlay.selectedRecipe();
        if (selected == null) {
            overlay(mc, Component.translatable("wdin.message.no_recipes").withStyle(ChatFormatting.GRAY));
            return;
        }
        mc.setScreen(new QuantityScreen(selected));
    }

    private static void overlay(Minecraft mc, Component text) {
        mc.gui.setOverlayMessage(text, false);
    }
}
