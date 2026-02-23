package com.learning.mobs.client;

import java.util.ArrayList;
import java.util.List;

import com.learning.mobs.LearningMobs;
import com.learning.mobs.mobs.LearningManager;
import com.learning.mobs.mobs.MobLearningType;
import com.learning.mobs.util.LearningIOMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public class LearningDebugScreen extends Screen {
    private static final int WINDOW_WIDTH = 520;
    private static final int WINDOW_HEIGHT = 240;
    private static final int TAB_WIDTH = 78;
    private static final int TAB_HEIGHT = 22;

    private final List<MobLearningType> tabs = List.of(MobLearningType.CREEPER, MobLearningType.ZOMBIE, MobLearningType.SKELETON);
    private int selectedTab = 0;
    private final List<TabBounds> tabBounds = new ArrayList<>();
    
    private Mob cachedSample;
    private int lastTab = -1;
    private long lastScanTime = 0;

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
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (width - WINDOW_WIDTH) / 2;
        int top = (height - WINDOW_HEIGHT) / 2;
        
        // Dark translucent background
        graphics.fill(left, top, left + WINDOW_WIDTH, top + WINDOW_HEIGHT, 0xDD101010);
        
        renderTabs(graphics, left, top);
        renderStatus(graphics, left, top + 20, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics graphics, int left, int top) {
        for (int i = 0; i < tabs.size(); i++) {
            TabBounds bounds = tabBounds.get(i);
            int fill = i == selectedTab ? 0xFFBBAA66 : 0xFF444444;
            int border = 0xFF222222;
            graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, fill);
            graphics.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + 1, border);
            graphics.fill(bounds.x, bounds.y + bounds.height - 1, bounds.x + bounds.width, bounds.y + bounds.height, border);
            graphics.fill(bounds.x, bounds.y, bounds.x + 1, bounds.y + bounds.height, border);
            graphics.fill(bounds.x + bounds.width - 1, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, border);
            String label = tabs.get(i).id();
            int textX = bounds.x + (bounds.width - font.width(label)) / 2;
            int textY = bounds.y + 6;
            graphics.drawString(font, label, textX, textY, 0xFFFFFFFF, false);
        }
        graphics.drawString(font, "Learning Mobs Debug", left + 140, top + 8, 0xFF999999, false);
    }

    private void renderStatus(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
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
        graphics.drawString(font, "Type: " + status.type().id(), left + 14, lineY, 0xFFFFFFFF, false);
        lineY += 12;
        graphics.drawString(font, "Generation: " + status.generation(), left + 14, lineY, 0xFFFFFFFF, false);
        lineY += 12;
        long ticks = manager.getTicksUntilRollover(minecraft.getSingleplayerServer());
        graphics.drawString(font, "Rollover in: " + com.learning.mobs.util.BrainMath.formatTime(ticks), left + 14, lineY, 0xFFFFFFFF, false);
        lineY += 12;
        graphics.drawString(font, "Active brains: " + status.activeBrains(), left + 14, lineY, 0xFFFFFFFF, false);
        lineY += 12;
        graphics.drawString(font, String.format("Highest Fitness: %.2f", status.allTimeBestFitness()), left + 14, lineY, 0xFF55FF55, false);
        lineY += 12;
        graphics.drawString(font, "Errors: " + status.errorCount(), left + 14, lineY, 0xFFFF5555, false);
        
        Mob sample = findAnyMobForType(type, minecraft);
        LearningManager.BrainStatus brainStatus = sample == null ? null : manager.getStatus(sample);
        
        if (brainStatus != null && brainStatus.hasBrain()) {
            lineY += 12;
            graphics.drawString(font, String.format("Sample Fitness: %.2f", brainStatus.fitness()), left + 14, lineY, 0xFFBBAA66, false);
            lineY += 12;
            graphics.drawString(font, "Sample Gen: " + brainStatus.generation(), left + 14, lineY, 0xFFBBAA66, false);
        }

        // Render the NN Visualization.
        // x shifted to left (190) to accommodate output labels within smaller window.
        // width/height slightly reduced (150) to fit better vertically.
        NeuralNetworkRenderer.render(graphics, font, brainStatus, left + 200, top + 15, 150, 180, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TabBounds(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private Mob findAnyMobForType(MobLearningType type, Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        
        long time = minecraft.level.getGameTime();
        if (cachedSample != null && lastTab == selectedTab && time - lastScanTime < 20) {
            if (cachedSample.isAlive() && !cachedSample.isRemoved()) {
                return cachedSample;
            }
        }

        lastTab = selectedTab;
        lastScanTime = time;
        cachedSample = null;
        
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Mob mob && MobLearningType.fromEntity(mob) == type) {
                cachedSample = mob;
                break;
            }
        }
        return cachedSample;
    }
}
