package com.learning.mobs.mobs;

import java.util.List;

import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.util.BrainMath;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

public class CreeperBrain extends AbstractMobBrain {
    private static final double MAX_PLAYER_DISTANCE = 48.0D;
    private static final double MAX_CREEPER_DISTANCE = 16.0D;
    private static final int MAX_GROUP_SIZE = 8;

    public CreeperBrain(NeuralNetwork network) {
        super(MobLearningType.CREEPER, network);
    }

    @Override
    protected double[] buildInputs(Mob mob) {
        Level level = mob.level();
        Player nearestPlayer = level.getNearestPlayer(mob, MAX_PLAYER_DISTANCE);
        double playerDistance = nearestPlayer == null ? MAX_PLAYER_DISTANCE : mob.distanceTo(nearestPlayer);
        double targetVisible = nearestPlayer != null && mob.hasLineOfSight(nearestPlayer) ? 1.0D : 0.0D;
        double targetHealth = nearestPlayer == null ? 0.0D : BrainMath.normalizeDistance(nearestPlayer.getHealth(),
                Math.max(1.0D, nearestPlayer.getMaxHealth()));
        double targetDeltaY = nearestPlayer == null ? 0.0D
                : BrainMath.clamp((nearestPlayer.getY() - mob.getY()) / 8.0D, -1.0D, 1.0D);

        List<? extends Mob> nearby = level.getEntitiesOfClass(mob.getClass(), mob.getBoundingBox().inflate(MAX_CREEPER_DISTANCE));
        double nearestCreeper = MAX_CREEPER_DISTANCE;
        for (Mob other : nearby) {
            if (other == mob) {
                continue;
            }
            nearestCreeper = Math.min(nearestCreeper, mob.distanceTo(other));
        }

        double[] inputs = new double[type.inputCount()];
        inputs[0] = nearestPlayer == null ? 0.0D : 1.0D;
        inputs[1] = BrainMath.normalizeDistance(playerDistance, MAX_PLAYER_DISTANCE);
        inputs[2] = targetVisible;
        inputs[3] = targetHealth;
        inputs[4] = targetDeltaY;
        inputs[5] = BrainMath.normalizeDistance(nearestCreeper, MAX_CREEPER_DISTANCE);
        inputs[6] = BrainMath.normalizeHealth(mob);
        inputs[7] = BrainMath.normalizeLight(level, mob.blockPosition());
        inputs[8] = BrainMath.normalizeTime(level);
        inputs[9] = BrainMath.normalizeGroupSize(nearby.size(), MAX_GROUP_SIZE);
        inputs[10] = mob.onGround() ? 1.0D : 0.0D;
        inputs[11] = mob.isInWaterOrRain() ? 1.0D : 0.0D;
        inputs[12] = mob instanceof Creeper creeper && creeper.isPowered() ? 1.0D : 0.0D;
        inputs[13] = mob instanceof Creeper creeper && creeper.getSwellDir() > 0 ? 1.0D : 0.0D;
        inputs[14] = BrainMath.clamp(mob.getDeltaMovement().length() * 3.0D, 0.0D, 1.0D);
        return inputs;
    }
}
