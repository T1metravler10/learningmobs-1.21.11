package com.learning.mobs.mobs;

import java.util.List;

import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.util.BrainMath;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class ZombieBrain extends AbstractMobBrain {
    private static final double MAX_TARGET_DISTANCE = 48.0D;
    private static final int MAX_GROUP_SIZE = 8;

    public ZombieBrain(NeuralNetwork network) {
        super(MobLearningType.ZOMBIE, network);
    }

    @Override
    protected double[] buildInputs(Mob mob) {
        Level level = mob.level();
        LivingEntity target = mob.getTarget();
        double targetDistance = target == null ? MAX_TARGET_DISTANCE : mob.distanceTo(target);
        double targetVisible = target != null && mob.hasLineOfSight(target) ? 1.0D : 0.0D;
        double targetHealth = target == null ? 0.0D : BrainMath.normalizeDistance(target.getHealth(), Math.max(1.0D, target.getMaxHealth()));
        double armorValue = BrainMath.clamp(mob.getArmorValue() / 20.0D, 0.0D, 1.0D);

        List<? extends Mob> nearby = level.getEntitiesOfClass(mob.getClass(), mob.getBoundingBox().inflate(16.0D));
        boolean hasWeapon = !mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();

        double[] inputs = new double[type.inputCount()];
        inputs[0] = target == null ? 0.0D : 1.0D;
        inputs[1] = BrainMath.normalizeDistance(targetDistance, MAX_TARGET_DISTANCE);
        inputs[2] = targetVisible;
        inputs[3] = targetHealth;
        inputs[4] = armorValue;
        inputs[5] = BrainMath.normalizeHealth(mob);
        inputs[6] = BrainMath.normalizeLight(level, mob.blockPosition());
        inputs[7] = BrainMath.normalizeTime(level);
        inputs[8] = BrainMath.normalizeGroupSize(nearby.size(), MAX_GROUP_SIZE);
        inputs[9] = mob.onGround() ? 1.0D : 0.0D;
        inputs[10] = mob.isInWaterOrRain() ? 1.0D : 0.0D;
        inputs[11] = mob.isBaby() ? 1.0D : 0.0D;
        inputs[12] = mob.isAggressive() ? 1.0D : 0.0D;
        inputs[13] = BrainMath.clamp(mob.getDeltaMovement().length() * 3.0D, 0.0D, 1.0D);
        inputs[14] = hasWeapon ? 1.0D : 0.0D;
        inputs[15] = mob.isOnFire() ? 1.0D : 0.0D;
        return inputs;
    }
}
