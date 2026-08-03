package com.wdin.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * Identity of a required material: registry item id + (optional) NBT fingerprint.
 */
public final class MaterialKey {
    private final String itemId;
    private final String nbt;
    private transient Item cachedItem;

    public MaterialKey(String itemId, String nbt) {
        this.itemId = itemId;
        this.nbt = nbt == null ? "" : nbt;
    }

    public static MaterialKey from(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new MaterialKey(id.toString(), stack.getComponentsPatch().toString());
    }

    public String itemId() {
        return itemId;
    }

    public String nbt() {
        return nbt;
    }

    public Item item() {
        if (cachedItem == null) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            cachedItem = id != null ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
        }
        return cachedItem;
    }

    public boolean matches(ItemStack other, boolean ignoreNbt) {
        if (other == null || other.isEmpty() || !other.is(item())) {
            return false;
        }
        if (ignoreNbt) {
            return true;
        }
        return nbt.equals(other.getComponentsPatch().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MaterialKey that)) {
            return false;
        }
        return itemId.equals(that.itemId) && nbt.equals(that.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, nbt);
    }

    @Override
    public String toString() {
        return nbt.isEmpty() ? itemId : itemId + "#" + nbt;
    }
}
