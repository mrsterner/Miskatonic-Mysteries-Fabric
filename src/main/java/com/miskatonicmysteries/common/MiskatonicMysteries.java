package com.miskatonicmysteries.common;

import com.miskatonicmysteries.common.handler.InsanityHandler;
import com.miskatonicmysteries.common.handler.networking.PacketHandler;
import com.miskatonicmysteries.common.lib.*;
import me.sargunvohra.mcmods.autoconfig1u.AutoConfig;
import me.sargunvohra.mcmods.autoconfig1u.serializer.JanksonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import software.bernie.geckolib3.GeckoLib;

public class MiskatonicMysteries implements ModInitializer {
    public static MMConfig config;

    static {
        AutoConfig.register(MMConfig.class, JanksonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(MMConfig.class).getConfig(); //is static init even a good idea
    }

    @Override
    public void onInitialize() {
        GeckoLib.initialize();
        MMObjects.init();
        MMEntities.init();
        MMMiscRegistries.init();
        MMRecipes.init();
        InsanityHandler.init();
        PacketHandler.registerC2S();
        MMWorld.init();
    }
}
