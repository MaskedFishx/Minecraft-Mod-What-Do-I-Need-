package com.wdin;

import com.wdin.client.ClientEvents;
import com.wdin.client.HudPrefs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(WDIN.MOD_ID)
public final class WDIN {
    public static final String MOD_ID = "wdin";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WDIN(IEventBus modEventBus, ModContainer modContainer) {
        WDINConfig.register(modContainer);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientEvents::registerKeyMappings);
            modEventBus.addListener(ClientEvents::clientSetup);
            NeoForge.EVENT_BUS.register(ClientEvents.class);
        }
    }
}
