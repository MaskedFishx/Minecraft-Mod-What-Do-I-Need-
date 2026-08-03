package com.wdin.client;

import com.wdin.core.RecipeIngredient;
import com.wdin.core.TrackedRecipe;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.resources.ResourceLocation;

/**
 * JEI bridge. Uses only the public JEI API plus two small reflective calls
 * into JEI's RecipesGui to obtain the exact recipe layout under the mouse.
 */
public final class JEIHooks {
    private static IJeiRuntime runtime;

    private JEIHooks() {
    }

    public static void setRuntime(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    public static void clearRuntime() {
        runtime = null;
    }

    public static double mouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth() * mc.getWindow().getGuiScaledWidth();
    }

    public static double mouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight() * mc.getWindow().getGuiScaledHeight();
    }

    /**
     * Resolve a trackable recipe from the current context:
     * JEI recipe view -> inventory/container slot -> JEI item list overlay.
     */
    public static TrackedRecipe trackFromContext(Minecraft mc) {
        if (runtime == null || mc.player == null) {
            return null;
        }
        Screen screen = mc.screen;
        if (screen != null) {
            if (screen instanceof IRecipesGui) {
                TrackedRecipe fromLayout = recipeFromRecipesGui(mc, screen);
                if (fromLayout != null) {
                    return fromLayout;
                }
                ItemStack hovered = hoveredFromRecipesGui(screen);
                if (hovered != null) {
                    return findRecipeOrRaw(hovered);
                }
                return null;
            }
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                Slot slot = ScreenReflect.getSlotAt(containerScreen, mouseX(mc), mouseY(mc));
                if (slot != null && slot.hasItem()) {
                    return findRecipeOrRaw(slot.getItem());
                }
            }
        }
        ItemStack overlay = overlayItemUnderMouse();
        if (overlay != null) {
            return findRecipeOrRaw(overlay);
        }
        return null;
    }

    /**
     * The item currently under the cursor (JEI recipe view, inventory slot, JEI overlay).
     */
    public static ItemStack hoveredItem(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen != null) {
            if (screen instanceof IRecipesGui) {
                ItemStack h = hoveredFromRecipesGui(screen);
                if (h != null) {
                    return h;
                }
            }
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                Slot slot = ScreenReflect.getSlotAt(containerScreen, mouseX(mc), mouseY(mc));
                if (slot != null && slot.hasItem()) {
                    return slot.getItem();
                }
            }
        }
        return overlayItemUnderMouse();
    }

    // ------------------------------------------------------------------ helpers

    private static TrackedRecipe recipeFromRecipesGui(Minecraft mc, Screen screen) {
        double mx = mouseX(mc);
        double my = mouseY(mc);
        // strategy 1: newer JEI (19.4x+) exposes the hovered recipe layout directly
        try {
            Method layoutMethod = screen.getClass().getMethod("getRecipeLayoutUnderMouse", double.class, double.class);
            Object optional = layoutMethod.invoke(screen, mx, my);
            if (optional instanceof Optional<?> o && o.isPresent()) {
                TrackedRecipe tr = recipeFromLayoutWithButtons(o.get());
                if (tr != null) {
                    return tr;
                }
            }
        } catch (Throwable ignored) {
        }
        // strategy 2: JEI 19.39 - RecipesGui.layouts.recipeLayoutsWithButtons
        try {
            Field layoutsField = screen.getClass().getDeclaredField("layouts");
            layoutsField.setAccessible(true);
            Object layouts = layoutsField.get(screen);
            Field listField = layouts.getClass().getDeclaredField("recipeLayoutsWithButtons");
            listField.setAccessible(true);
            Object listObj = listField.get(layouts);
            if (listObj instanceof List<?> list) {
                for (Object wb : list) {
                    if (layoutUnderMouse(wb, mx, my)) {
                        TrackedRecipe tr = recipeFromLayoutWithButtons(wb);
                        if (tr != null) {
                            return tr;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static TrackedRecipe recipeFromLayoutWithButtons(Object withButtons) {
        try {
            Object drawable = invokeLayoutGetter(withButtons);
            if (drawable instanceof IRecipeLayoutDrawable<?> d) {
                return extract(d);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean layoutUnderMouse(Object withButtons, double mx, double my) {
        try {
            Object drawable = invokeLayoutGetter(withButtons);
            return drawable instanceof IRecipeLayoutDrawable<?> d && d.isMouseOver(mx, my);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * JEI 19.39+ uses the interface method getRecipeLayout(), JEI 19.21 uses the
     * record accessor recipeLayout(). Both return an IRecipeLayoutDrawable.
     */
    private static Object invokeLayoutGetter(Object withButtons) throws Exception {
        try {
            return withButtons.getClass().getMethod("getRecipeLayout").invoke(withButtons);
        } catch (NoSuchMethodException e) {
            return withButtons.getClass().getMethod("recipeLayout").invoke(withButtons);
        }
    }

    private static ItemStack hoveredFromRecipesGui(Screen screen) {
        if (screen instanceof IRecipesGui recipesGui) {
            Optional<ItemStack> o = recipesGui.getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
            return o.filter(s -> !s.isEmpty()).orElse(null);
        }
        return null;
    }

    private static ItemStack overlayItemUnderMouse() {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return null;
        }
        Optional<ITypedIngredient<?>> typed = rt.getIngredientListOverlay().getIngredientUnderMouse();
        return typed.flatMap(ITypedIngredient::getItemStack).filter(s -> !s.isEmpty()).orElse(null);
    }

    private static TrackedRecipe findRecipeOrRaw(ItemStack item) {
        TrackedRecipe found = findRecipe(item);
        return found != null ? found : TrackedRecipe.raw(item);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TrackedRecipe findRecipe(ItemStack item) {
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return null;
        }
        try {
            IFocusFactory focusFactory = rt.getJeiHelpers().getFocusFactory();
            IFocus<ItemStack> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, item);
            IRecipeManager manager = rt.getRecipeManager();

            IRecipeCategoriesLookup lookup = manager.createRecipeCategoryLookup().limitFocus(List.of(focus));
            List<IRecipeCategory<?>> categories = new ArrayList<>(lookup.get().limit(40).toList());
            categories.sort(Comparator.comparingInt(c -> c.getRecipeType().equals(RecipeTypes.CRAFTING) ? 0 : 1));

            IFocusGroup emptyFocus = focusFactory.getEmptyFocusGroup();
            for (IRecipeCategory<?> category : categories) {
                try {
                    Object recipe = manager.createRecipeLookup(category.getRecipeType())
                            .limitFocus(List.of(focus))
                            .get()
                            .findFirst()
                            .orElse(null);
                    if (recipe == null) {
                        continue;
                    }
                    IRecipeManager rawManager = manager;
                    Optional<?> drawableOpt = rawManager.createRecipeLayoutDrawable((IRecipeCategory) category, recipe, emptyFocus);
                    if (drawableOpt.isPresent() && drawableOpt.get() instanceof IRecipeLayoutDrawable<?> drawable) {
                        TrackedRecipe tr = extract(drawable);
                        if (tr != null) {
                            return tr;
                        }
                    }
                } catch (Exception ignored) {
                    // try next category
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static TrackedRecipe extract(IRecipeLayoutDrawable<?> drawable) {
        IRecipeSlotsView slotsView = drawable.getRecipeSlotsView();
        ItemStack output = null;
        for (IRecipeSlotView view : slotsView.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            Optional<ItemStack> o = view.getDisplayedItemStack();
            if (o.isPresent() && !o.get().isEmpty()) {
                output = o.get();
                break;
            }
        }
        if (output == null || output.isEmpty()) {
            return null;
        }

        // accepted item/tag sets read from the real recipe object when possible
        List<AcceptedSet> accepted = extractAccepted(drawable.getRecipe());
        List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        List<RecipeIngredient> ingredients = new ArrayList<>();
        for (int i = 0; i < inputSlots.size(); i++) {
            IRecipeSlotView view = inputSlots.get(i);
            Optional<ItemStack> o = view.getDisplayedItemStack();
            if (o.isPresent() && !o.get().isEmpty()) {
                ItemStack s = o.get();
                AcceptedSet acc = (accepted != null && i < accepted.size())
                        ? accepted.get(i)
                        : slotAccepted(view);
                ingredients.add(new RecipeIngredient(s, s.getCount(), acc.items(), acc.tags()));
            }
        }
        if (ingredients.isEmpty()) {
            return null;
        }
        String stationName = null;
        String stationType = null;
        try {
            IRecipeCategory<?> category = drawable.getRecipeCategory();
            stationName = category.getTitle().getString();
            stationType = category.getRecipeType().getUid().toString();
        } catch (Throwable ignored) {
        }
        return new TrackedRecipe(UUID.randomUUID(), output.copy(), ingredients, 1, stationName, stationType);
    }

    // ------------------------------------------------------------------ station icon

    private static final Map<String, IRecipeCategory<?>> STATION_CACHE = new HashMap<>();

    /**
     * The crafting-station icon (work block) for a recipe type, cached per type.
     */
    public static IDrawable stationIcon(String typeUid) {
        IRecipeCategory<?> category = stationCategory(typeUid);
        if (category == null) {
            return null;
        }
        try {
            return category.getIcon();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IRecipeCategory<?> stationCategory(String typeUid) {
        if (typeUid == null || typeUid.isEmpty()) {
            return null;
        }
        IRecipeCategory<?> cached = STATION_CACHE.get(typeUid);
        if (cached != null) {
            return cached;
        }
        IJeiRuntime rt = runtime;
        if (rt == null) {
            return null;
        }
        try {
            Optional<RecipeType<?>> type = rt.getRecipeManager().getRecipeType(ResourceLocation.parse(typeUid));
            if (type.isPresent()) {
                IRecipeCategory<?> category = rt.getRecipeManager().getRecipeCategory(type.get());
                STATION_CACHE.put(typeUid, category);
                return category;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ accepted sets

    private record AcceptedSet(List<String> items, List<String> tags) {
        private AcceptedSet {
            items = List.copyOf(items);
            tags = List.copyOf(tags);
        }
    }

    /**
     * Reads the recipe's actual Ingredient definitions (reflection so every JEI
     * recipe type can be handled uniformly). Returns null when the recipe object
     * cannot be introspected.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<AcceptedSet> extractAccepted(Object recipe) {
        try {
            Object value = recipe;
            if (recipe != null && recipe.getClass().getName().equals("net.minecraft.world.item.crafting.RecipeHolder")) {
                value = recipe.getClass().getMethod("value").invoke(recipe);
            }
            if (value == null) {
                return null;
            }
            try {
                Method gi = value.getClass().getMethod("getIngredients");
                Object listObj = gi.invoke(value);
                if (listObj instanceof List<?> list) {
                    List<AcceptedSet> out = new ArrayList<>();
                    for (Object ing : list) {
                        AcceptedSet a = acceptedFromIngredient(ing);
                        if (a != null) {
                            out.add(a);
                        }
                    }
                    return out.isEmpty() ? null : out;
                }
            } catch (NoSuchMethodException ignored) {
                // try the single-ingredient variant below
            }
            try {
                Method g = value.getClass().getMethod("getIngredient");
                AcceptedSet a = acceptedFromIngredient(g.invoke(value));
                return a == null ? null : List.of(a);
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static AcceptedSet acceptedFromIngredient(Object ingredient) {
        if (ingredient == null) {
            return null;
        }
        List<String> items = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        try {
            Object arr = ingredient.getClass().getMethod("getItems").invoke(ingredient);
            if (arr instanceof ItemStack[] stacks) {
                for (ItemStack s : stacks) {
                    if (s != null && !s.isEmpty()) {
                        items.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object stream = ingredient.getClass().getMethod("getTags").invoke(ingredient);
            if (stream instanceof Stream<?> st) {
                st.forEach(t -> {
                    if (t instanceof TagKey<?> tk) {
                        tags.add(tk.location().toString());
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        if (items.isEmpty() && tags.isEmpty()) {
            return null;
        }
        return new AcceptedSet(items, tags);
    }

    /**
     * Fallback: whatever items JEI lists in the slot (single item for specific
     * slots, the expanded items for tag slots when the recipe object is not
     * introspectable).
     */
    private static AcceptedSet slotAccepted(IRecipeSlotView view) {
        List<String> items = new ArrayList<>();
        for (ITypedIngredient<?> typed : view.getAllIngredientsList()) {
            typed.getItemStack().ifPresent(st -> {
                if (!st.isEmpty()) {
                    items.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
                }
            });
        }
        if (items.isEmpty()) {
            view.getDisplayedItemStack().ifPresent(st -> {
                if (!st.isEmpty()) {
                    items.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
                }
            });
        }
        return new AcceptedSet(items, List.of());
    }
}
