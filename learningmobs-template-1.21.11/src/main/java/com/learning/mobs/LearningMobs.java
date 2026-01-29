package com.learning.mobs;

import org.slf4j.Logger;

import com.learning.mobs.mobs.LearningEvents;
import com.learning.mobs.mobs.LearningManager;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(LearningMobs.MODID)
public class LearningMobs {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "learningmobs";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    private static LearningManager MANAGER;

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public LearningMobs(IEventBus modEventBus, ModContainer modContainer) {
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MANAGER = new LearningManager();
        NeoForge.EVENT_BUS.register(new LearningEvents(MANAGER));
    }

    public static LearningManager getManager() {
        return MANAGER;
    }
}
