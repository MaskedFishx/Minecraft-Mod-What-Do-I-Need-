package com.wdin.client;

import com.wdin.WDIN;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class WDINJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(WDIN.MOD_ID, "plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEIHooks.setRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JEIHooks.clearRuntime();
    }
}
