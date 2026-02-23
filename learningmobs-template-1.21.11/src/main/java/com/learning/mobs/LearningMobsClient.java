package com.learning.mobs;

import org.lwjgl.glfw.GLFW;

import com.learning.mobs.client.LearningDebugScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = LearningMobs.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = LearningMobs.MODID, value = Dist.CLIENT)
public class LearningMobsClient {
    private static final KeyMapping OPEN_DEBUG = new KeyMapping("key.learningmobs.debug",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            KeyMapping.Category.MISC);

    public LearningMobsClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        LearningMobs.LOGGER.info("Learning Mobs client setup complete.");
    }

    @SubscribeEvent
    static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DEBUG);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, net.minecraft.resources.Identifier.fromNamespaceAndPath(LearningMobs.MODID, "countdown"), (guiGraphics, partialTick) -> {
            com.learning.mobs.client.LearningHudOverlay.INSTANCE.render(guiGraphics, partialTick);
        });
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (OPEN_DEBUG.consumeClick()) {
            Screen current = minecraft.screen;
            if (!(current instanceof LearningDebugScreen)) {
                minecraft.setScreen(new LearningDebugScreen());
            }
        }
    }
}
