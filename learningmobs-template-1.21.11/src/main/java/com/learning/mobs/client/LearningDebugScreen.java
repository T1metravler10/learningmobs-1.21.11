package com.learning.mobs.client;

import java.util.ArrayList;
import java.util.List;

import com.learning.mobs.LearningMobs;
import com.learning.mobs.mobs.LearningManager;
import com.learning.mobs.mobs.MobLearningType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class LearningDebugScreen extends Screen {
    private static final Identifier WINDOW_TEXTURE = Identifier.fromNamespaceAndPath("minecraft",
            "textures/gui/advancements/window.png");
    private static final int WINDOW_WIDTH = 252;
    private static final int WINDOW_HEIGHT = 140;
    private static final int TAB_WIDTH = 78;
    private static final int TAB_HEIGHT = 22;

    private final List<MobLearningType> tabs = List.of(MobLearningType.CREEPER, MobLearningType.ZOMBIE, MobLearningType.SKELETON);
    private int selectedTab = 0;
    private final List<TabBounds> tabBounds = new ArrayList<>();

    public LearningDebugScreen() {
        super(Component.literal("Learning Mobs Debug"));
    }

    @Override
    protected void init() {
        super.init();
        tabBounds.clear();
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        int tabY = top - TAB_HEIGHT + 2;
        for (int i = 0; i < tabs.size(); i++) {
            int tabX = left + (i * (TAB_WIDTH + 2));
            tabBounds.add(new TabBounds(tabX, tabY, TAB_WIDTH, TAB_HEIGHT));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isInside) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            for (int i = 0; i < tabBounds.size(); i++) {
                TabBounds bounds = tabBounds.get(i);
                if (bounds.contains(mouseX, mouseY)) {
                    selectedTab = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isInside);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        graphics.blit(WINDOW_TEXTURE, left, top, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, 256, 256);
        renderTabs(graphics, left, top);
        renderStatus(graphics, left, top + 20);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics graphics, int left, int top) {
        for (int i = 0; i < tabs.size(); i++) {
            TabBounds bounds = tabBounds.get(i);
            int fill = i == selectedTab ? 0xFFBBAA66 : 0xFF776655;
            int border = 0xFF33281E;
            graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, fill);
            graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + 1, border);
            graphics.fill(bounds.x, bounds.y + bounds.height - 1, bounds.x + bounds.width, bounds.y + bounds.height, border);
            graphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.y + bounds.height, border);
            graphics.fill(bounds.x + bounds.width - 1, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, border);
            String label = tabs.get(i).id();
            int textX = bounds.x + (bounds.width - font.width(label)) / 2;
            int textY = bounds.y + 6;
            graphics.drawString(font, label, textX, textY, 0xFFF2E6C8, false);
        }
        graphics.drawString(font, "Learning Mobs Debug", left + 12, top + 8, 0xFFEEE3C4, false);
    }

    private void renderStatus(GuiGraphics graphics, int left, int top) {
        MobLearningType type = tabs.get(selectedTab);
        LearningManager manager = LearningMobs.getManager();
        Minecraft minecraft = Minecraft.getInstance();
        int lineY = top + 12;
        if (manager == null || minecraft.getSingleplayerServer() == null) {
            graphics.drawString(font, "Server data unavailable (dedicated server).", left + 14, lineY, 0xFFD0B38A,
                    false);
            return;
        }
        LearningManager.TypeStatus status = manager.getTypeStatus(type);
        graphics.drawString(font, "Type: " + status.type().id(), left + 14, lineY, 0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, "Generation: " + status.generation(), left + 14, lineY, 0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, "Population: " + status.populationSize(), left + 14, lineY, 0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, "Active brains: " + status.activeBrains(), left + 14, lineY, 0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, "Errors: " + status.errorCount(), left + 14, lineY, 0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, "Last samples: " + status.lastSampleCount(), left + 14, lineY, 0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, String.format("Avg fitness: %.2f", status.lastAverageFitness()), left + 14, lineY,
                0xFFD0B38A, false);
        lineY += 12;
        graphics.drawString(font, String.format("Best fitness: %.2f", status.lastBestFitness()), left + 14, lineY,
                0xFFD0B38A, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    private record TabBounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
