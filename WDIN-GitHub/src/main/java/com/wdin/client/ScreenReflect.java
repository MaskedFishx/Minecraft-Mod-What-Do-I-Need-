package com.wdin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small version-safe accessors for AbstractContainerScreen internals
 * (leftPos/topPos and hovered-slot lookup differ across MC versions).
 */
public final class ScreenReflect {
    private static Field leftField;
    private static Field topField;
    private static Method slotAtMethod;

    private ScreenReflect() {
    }

    public static int leftPos(AbstractContainerScreen<?> screen) {
        try {
            if (leftField == null) {
                leftField = AbstractContainerScreen.class.getDeclaredField("leftPos");
                leftField.setAccessible(true);
            }
            return leftField.getInt(screen);
        } catch (Throwable t) {
            return methodPos(screen, true);
        }
    }

    public static int topPos(AbstractContainerScreen<?> screen) {
        try {
            if (topField == null) {
                topField = AbstractContainerScreen.class.getDeclaredField("topPos");
                topField.setAccessible(true);
            }
            return topField.getInt(screen);
        } catch (Throwable t) {
            return methodPos(screen, false);
        }
    }

    private static int methodPos(AbstractContainerScreen<?> screen, boolean left) {
        try {
            String[] names = left ? new String[]{"getGuiLeft", "leftPos"} : new String[]{"getGuiTop", "topPos"};
            for (String name : names) {
                try {
                    Method m = AbstractContainerScreen.class.getMethod(name);
                    Object v = m.invoke(screen);
                    if (v instanceof Number n) {
                        return n.intValue();
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static Slot getSlotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        try {
            if (slotAtMethod == null) {
                slotAtMethod = AbstractContainerScreen.class.getDeclaredMethod("getSlotAt", double.class, double.class);
                slotAtMethod.setAccessible(true);
            }
            Object o = slotAtMethod.invoke(screen, mouseX, mouseY);
            return o instanceof Slot slot ? slot : null;
        } catch (Throwable t) {
            return fallbackSlotAt(screen, mouseX, mouseY);
        }
    }

    private static Slot fallbackSlotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        int left = leftPos(screen);
        int top = topPos(screen);
        for (Slot slot : screen.getMenu().slots) {
            int x0 = left + slot.x - 1;
            int y0 = top + slot.y - 1;
            if (mouseX >= x0 && mouseX < x0 + 18 && mouseY >= y0 && mouseY < y0 + 18) {
                return slot;
            }
        }
        return null;
    }
}
