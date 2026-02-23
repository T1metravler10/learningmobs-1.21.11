package com.learning.mobs.mobs;

import java.util.List;
import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.util.BrainMath;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractMobBrain implements MobBrain {
    protected static final double MAX_TARGET_DISTANCE = 48.0D;
    protected static final double MAX_ALLY_DISTANCE = 16.0D;
    protected static final int MAX_GROUP_SIZE = 8;

    protected final MobLearningType type;
    protected final NeuralNetwork network;
    protected int ticksAlive;
    protected double damageDealt;
    protected double damageTaken;
    protected int kills;
    protected int attacks;
    protected double distanceClosed;
    private double lastTargetDistance = -1.0D;
    protected double[] lastOutputs = new double[0];
    protected double[] lastInputs = new double[0];

    protected AbstractMobBrain(MobLearningType type, NeuralNetwork network) {
        this.type = type;
        this.network = network;
    }

    protected double[] createStandardInputs(Mob mob) {
        double[] inputs = new double[type.inputCount()];
        Level level = mob.level();
        LivingEntity target = mob.getTarget();

        // 0-9: Target Info
        inputs[0] = target == null ? 0.0D : 1.0D;
        if (target != null) {
            inputs[1] = BrainMath.normalizeDistance(mob.distanceTo(target), MAX_TARGET_DISTANCE);
            inputs[2] = mob.hasLineOfSight(target) ? 1.0D : 0.0D;
            inputs[3] = BrainMath.normalizeDistance(target.getHealth(), Math.max(1.0D, target.getMaxHealth()));
            inputs[4] = BrainMath.clamp((target.getY() - mob.getY()) / 8.0D, -1.0D, 1.0D);
            
            Vec3 targetVel = target.getDeltaMovement();
            inputs[5] = BrainMath.clamp(targetVel.x * 2.0D, -1.0D, 1.0D);
            inputs[6] = BrainMath.clamp(targetVel.y * 2.0D, -1.0D, 1.0D);
            inputs[7] = BrainMath.clamp(targetVel.z * 2.0D, -1.0D, 1.0D);
            
            double angle = Math.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX());
            double yaw = Math.toRadians(mob.getYRot());
            double diff = Math.sin(angle - yaw);
            inputs[8] = diff;
            inputs[9] = target instanceof net.minecraft.world.entity.player.Player ? 1.0D : 0.0D;
        }

        // 10-19: Self State
        inputs[10] = BrainMath.normalizeHealth(mob);
        Vec3 vel = mob.getDeltaMovement();
        inputs[11] = BrainMath.clamp(vel.horizontalDistance() * 3.0D, 0.0D, 1.0D);
        inputs[12] = BrainMath.clamp(vel.y * 2.0D, -1.0D, 1.0D);
        inputs[13] = mob.onGround() ? 1.0D : 0.0D;
        inputs[14] = mob.isInWaterOrRain() ? 1.0D : 0.0D;
        inputs[15] = mob.isInLava() ? 1.0D : 0.0D;
        inputs[16] = mob.isOnFire() ? 1.0D : 0.0D;
        inputs[17] = mob.isAggressive() ? 1.0D : 0.0D;
        inputs[18] = mob.isCrouching() ? 1.0D : 0.0D;
        inputs[19] = mob.isSprinting() ? 1.0D : 0.0D;

        // 20-29: Environment & Group
        inputs[20] = BrainMath.normalizeLight(level, mob.blockPosition());
        inputs[21] = BrainMath.normalizeTime(level);
        
        List<? extends Mob> nearby = level.getEntitiesOfClass(mob.getClass(), mob.getBoundingBox().inflate(MAX_ALLY_DISTANCE));
        inputs[22] = BrainMath.normalizeGroupSize(nearby.size(), MAX_GROUP_SIZE);
        
        double nearestAllyDist = MAX_ALLY_DISTANCE;
        for (Mob other : nearby) {
            if (other != mob) nearestAllyDist = Math.min(nearestAllyDist, mob.distanceTo(other));
        }
        inputs[23] = BrainMath.normalizeDistance(nearestAllyDist, MAX_ALLY_DISTANCE);
        
        inputs[24] = mob.horizontalCollision ? 1.0D : 0.0D;
        inputs[25] = !level.getBlockState(mob.blockPosition().relative(mob.getDirection())).isAir() ? 0.0D : 1.0D; // Simple blocked forward/down proxy

        inputs[26] = (mob.getNavigation().getPath() != null) ? 1.0D : 0.0D;
        if (mob.getNavigation().getPath() != null) {
            inputs[27] = BrainMath.normalizeDistance(mob.getNavigation().getPath().getDistToTarget(), 64.0D);
        }
        inputs[28] = level.canSeeSky(mob.blockPosition()) ? 1.0D : 0.0D;
        inputs[29] = mob.isInLiquid() ? 1.0D : 0.0D;

        // 30-39: Combat & Attributes
        inputs[30] = BrainMath.clamp(mob.getArmorValue() / 20.0D, 0.0D, 1.0D);
        inputs[31] = BrainMath.clamp(mob.getAbsorptionAmount() / 20.0D, 0.0D, 1.0D);
        inputs[32] = BrainMath.clamp(mob.hurtTime / 10.0D, 0.0D, 1.0D);
        inputs[33] = BrainMath.clamp(mob.attackAnim, 0.0D, 1.0D);
        inputs[34] = BrainMath.clamp((double) mob.getAirSupply() / (double) mob.getMaxAirSupply(), 0.0D, 1.0D);
        inputs[35] = BrainMath.normalizeDistance(mob.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.5D);
        inputs[36] = BrainMath.normalizeDistance(mob.getAttributeValue(Attributes.FOLLOW_RANGE), 64.0D);
        inputs[37] = BrainMath.normalizeDistance(mob.getAttributeValue(Attributes.ATTACK_DAMAGE), 20.0D);
        // 38-39 reserved for mob-specific state

        return inputs;
    }

    @Override
    public void tick(Mob mob) {
        ticksAlive += 1;

        LivingEntity target = mob.getTarget();
        if (target != null) {
            double currentDist = mob.distanceTo(target);
            if (lastTargetDistance > 0) {
                double diff = lastTargetDistance - currentDist;
                if (diff > 0.01D) {
                    recordDistanceProgress(diff);
                }
            }
            lastTargetDistance = currentDist;
        } else {
            lastTargetDistance = -1.0D;
        }

        double[] inputs = buildInputs(mob);
        for (int i = 0; i < inputs.length; i++) {
            if (!Double.isFinite(inputs[i])) {
                inputs[i] = 0.0D;
            }
        }
        lastInputs = inputs;
        lastOutputs = network.evaluate(inputs);
        applyOutputs(mob, lastOutputs);
    }

    @Override
    public void recordDamageDealt(net.minecraft.world.entity.Entity victim, double amount) {
        damageDealt += amount;
    }

    @Override
    public void recordDamageTaken(double amount) {
        damageTaken += amount;
    }

    @Override
    public void recordKill(net.minecraft.world.entity.Entity victim) {
        kills += 1;
    }

    @Override
    public void recordAttack() {
        attacks += 1;
    }

    @Override
    public void recordDistanceProgress(double amount) {
        distanceClosed += amount;
    }

    @Override
    public double[] lastOutputs() {
        return lastOutputs;
    }

    @Override
    public double[] lastInputs() {
        return lastInputs;
    }

    @Override
    public int ticksAlive() {
        return ticksAlive;
    }

    @Override
    public double damageDealt() {
        return damageDealt;
    }

    @Override
    public double damageTaken() {
        return damageTaken;
    }

    @Override
    public int kills() {
        return kills;
    }

    @Override
    public NeuralNetwork network() {
        return network;
    }

    @Override
    public MobLearningType type() {
        return type;
    }

    @Override
    public double calculateFitness() {
        return ticksAlive + (damageDealt * 2.0D) - damageTaken + (kills * 6.0D) + (distanceClosed * 0.1D) + (attacks * 1.0D);
    }

    protected abstract double[] buildInputs(Mob mob);

    protected void applyOutputs(Mob mob, double[] outputs) {
        // Intentionally no-op for additive learning; vanilla AI remains intact.
    }
}
