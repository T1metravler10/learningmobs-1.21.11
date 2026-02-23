package com.learning.mobs.mobs;

import java.util.List;

import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.util.BrainMath;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.Level;

public class SkeletonBrain extends AbstractMobBrain {
    private static final double MAX_TARGET_DISTANCE = 48.0D;
    private static final int MAX_GROUP_SIZE = 8;

    public SkeletonBrain(NeuralNetwork network) {
        super(MobLearningType.SKELETON, network);
    }

    @Override
    protected double[] buildInputs(Mob mob) {
        double[] inputs = createStandardInputs(mob);

        boolean hasBow = mob.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof net.minecraft.world.item.BowItem
                || mob.getItemBySlot(EquipmentSlot.MAINHAND).getItem() instanceof net.minecraft.world.item.CrossbowItem;
        inputs[38] = hasBow ? 1.0D : 0.0D;
        inputs[39] = !mob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() ? 1.0D : 0.0D;

        return inputs;
    }
}
