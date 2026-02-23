package com.learning.mobs.mobs;

import java.util.EnumSet;

import com.learning.mobs.Config;
import com.learning.mobs.util.BrainMath;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class LearningAssistGoal extends Goal {
    private final Mob mob;
    private final LearningManager manager;
    private final MobLearningType type;
    private int meleeCooldown;
    private int rangedCooldown;
    private int wanderCooldown;

    public LearningAssistGoal(Mob mob, LearningManager manager, MobLearningType type) {
        this.mob = mob;
        this.manager = manager;
        this.type = type;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (manager.getBrain(mob) == null) return false;
        // Allow if not VANILLA mode OR if this specific mob is forced to FULL takeover
        return manager.getTakeoverMode(type) != TakeoverMode.VANILLA || manager.isFullTakeover(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (meleeCooldown > 0) meleeCooldown -= 1;
        if (rangedCooldown > 0) rangedCooldown -= 1;
        if (wanderCooldown > 0) wanderCooldown -= 1;

        MobBrain brain = manager.getBrain(mob);
        if (brain == null) return;

        TakeoverMode mode = manager.getTakeoverMode(type);
        if (mode == TakeoverMode.VANILLA) return;

        double[] outputs = brain.lastOutputs();
        if (outputs.length < type.outputCount()) return;

        boolean fullTakeover = mode == TakeoverMode.FULL || manager.isFullTakeover(mob) || toSignal(outputs[8]) > 0.7D;
        
        boolean stopRequested = applyUniversalOutputs(outputs, fullTakeover);

        switch (type) {
            case CREEPER -> applyCreeper(outputs, fullTakeover, stopRequested);
            case ZOMBIE -> applyZombie(outputs, fullTakeover, stopRequested);
            case SKELETON -> applySkeleton(outputs, fullTakeover, stopRequested);
        }
    }

    private boolean applyUniversalOutputs(double[] outputs, boolean fullTakeover) {
        double sprint = toSignal(outputs[4]);
        double sneak = toSignal(outputs[5]);
        double jump = toSignal(outputs[2]);
        double lookYaw = outputs[6];
        double lookPitch = outputs[7];
        double aggressive = toSignal(outputs[9]);
        double stop = toSignal(outputs[11]);

        mob.setSprinting(sprint > threshold(0.5D, fullTakeover));
        mob.setShiftKeyDown(sneak > threshold(0.5D, fullTakeover));
        mob.setAggressive(aggressive > threshold(0.5D, fullTakeover));

        boolean stopRequested = stop > threshold(0.6D, fullTakeover);
        if (stopRequested) {
            mob.getNavigation().stop();
        }

        if (jump > threshold(0.7D, fullTakeover) && mob.onGround()) {
            mob.getJumpControl().jump();
        }

        if (Math.abs(lookYaw) > 0.1D || Math.abs(lookPitch) > 0.1D) {
            mob.setYRot(mob.getYRot() + (float) lookYaw * 10.0F);
            mob.setXRot((float) BrainMath.clamp(mob.getXRot() + lookPitch * 10.0D, -90.0D, 90.0D));
        }
        return stopRequested;
    }

    private void applyCreeper(double[] outputs, boolean fullTakeover, boolean stopRequested) {
        LivingEntity target = mob.getTarget();
        MobBrain brain = manager.getBrain(mob);
        double moveForward = outputs[0];
        double strafe = outputs[1];
        double explode = toSignal(outputs[3]);
        double wander = toSignal(outputs[10]);
        double pathToTarget = toSignal(outputs[12]);
        double speed = moveSpeed();

        if (stopRequested) {
            return;
        }

        if (target != null) {
            if (pathToTarget > threshold(0.45D, fullTakeover)) {
                moveTo(target, speed);
            }
            if (moveForward > 0.1D) {
                moveTo(target, speed);
            } else if (moveForward < -0.1D) {
                moveAwayFrom(target, speed, 7.0D);
            }
            
            if (Math.abs(strafe) > threshold(0.4D, fullTakeover)) {
                strafeAround(target, strafe, 1.5D);
            }
            
            if (explode > threshold(0.5D, fullTakeover) && mob instanceof Creeper creeper && mob.distanceTo(target) < 4.5D) {
                if (creeper.getSwellDir() <= 0 && brain != null) {
                    brain.recordAttack();
                }
                creeper.setSwellDir(1);
            } else if (mob instanceof Creeper creeper) {
                creeper.setSwellDir(-1);
            }
        } else if (wander > threshold(0.5D, fullTakeover)) {
            wanderRandom(speed);
        }
    }

    private void applyZombie(double[] outputs, boolean fullTakeover, boolean stopRequested) {
        LivingEntity target = mob.getTarget();
        MobBrain brain = manager.getBrain(mob);
        double moveForward = outputs[0];
        double strafe = outputs[1];
        double attack = toSignal(outputs[3]);
        double wander = toSignal(outputs[10]);
        double pathToTarget = toSignal(outputs[12]);
        double speed = moveSpeed();

        if (stopRequested) {
            return;
        }

        if (target != null) {
            if (pathToTarget > threshold(0.45D, fullTakeover)) {
                moveTo(target, speed);
            }
            if (moveForward > 0.1D) {
                moveTo(target, speed);
            } else if (moveForward < -0.1D) {
                moveAwayFrom(target, speed, 7.0D);
            }

            if (Math.abs(strafe) > threshold(0.4D, fullTakeover)) {
                strafeAround(target, strafe, 1.4D);
            }

            mob.setAggressive(attack > 0.3D || moveForward > 0.3D);
            
            if (attack > threshold(0.5D, fullTakeover) && meleeCooldown == 0 && mob.distanceTo(target) < 3.0D) {
                if (mob.level() instanceof ServerLevel serverLevel) {
                    mob.doHurtTarget(serverLevel, target);
                    meleeCooldown = 20;
                    if (brain != null) brain.recordAttack();
                }
            }
        } else if (wander > threshold(0.5D, fullTakeover)) {
            wanderRandom(speed);
        }
    }

    private void applySkeleton(double[] outputs, boolean fullTakeover, boolean stopRequested) {
        LivingEntity target = mob.getTarget();
        MobBrain brain = manager.getBrain(mob);
        double moveForward = outputs[0];
        double strafe = outputs[1];
        double shoot = toSignal(outputs[3]);
        double wander = toSignal(outputs[10]);
        double pathToTarget = toSignal(outputs[12]);
        double speed = moveSpeed();

        if (stopRequested) {
            return;
        }

        if (target != null) {
            if (pathToTarget > threshold(0.45D, fullTakeover)) {
                moveTo(target, speed);
            }
            if (moveForward > 0.1D) {
                moveTo(target, speed);
            } else if (moveForward < -0.1D) {
                moveAwayFrom(target, speed, 7.0D);
            }

            if (Math.abs(strafe) > threshold(0.4D, fullTakeover)) {
                strafeAround(target, strafe, 1.5D);
            }

            if (shoot > threshold(0.5D, fullTakeover) && rangedCooldown == 0
                    && mob instanceof RangedAttackMob rangedMob && mob.hasLineOfSight(target)) {
                rangedMob.performRangedAttack(target, 1.0F);
                rangedCooldown = 25;
                if (brain != null) brain.recordAttack();
            }
        } else if (wander > threshold(0.5D, fullTakeover)) {
            wanderRandom(speed);
        }
    }

    private void moveTo(LivingEntity target, double speed) {
        mob.getNavigation().moveTo(target, speed);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void moveAwayFrom(LivingEntity target, double speed, double distance) {
        Vec3 away = mob.position().subtract(target.position());
        if (away.lengthSqr() < 0.0001D) {
            return;
        }
        Vec3 dest = mob.position().add(away.normalize().scale(distance));
        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void strafeAround(LivingEntity target, double direction, double radius) {
        Vec3 toTarget = target.position().subtract(mob.position());
        if (toTarget.lengthSqr() < 0.0001D) {
            return;
        }
        double sign = direction >= 0 ? 1.0D : -1.0D;
        Vec3 perpendicular = new Vec3(-toTarget.z, 0.0D, toTarget.x).normalize().scale(radius * sign);
        Vec3 dest = mob.position().add(perpendicular);
        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0D);
    }

    private void wanderRandom(double speed) {
        if (wanderCooldown > 0) {
            return;
        }
        RandomSource random = mob.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D + random.nextDouble() * 6.0D;
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;
        Vec3 dest = mob.position().add(dx, 0.0D, dz);
        mob.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
        wanderCooldown = 40;
    }

    private double toSignal(double output) {
        return BrainMath.clamp((output + 1.0D) * 0.5D, 0.0D, 1.0D);
    }

    private double moveSpeed() {
        double multiplier = BrainMath.clamp(Config.LEARNING_SPEED_MULTIPLIER.getAsDouble(), 0.2D, 3.0D);
        return multiplier;
    }

    private double threshold(double base, boolean fullTakeover) {
        double strength = BrainMath.clamp(Config.LEARNING_CONTROL_STRENGTH.getAsDouble(), 0.0D, 1.0D);
        if (fullTakeover) {
            strength = Math.max(strength, 0.9D);
        }
        double adjusted = base - (strength * 0.25D);
        return BrainMath.clamp(adjusted, 0.15D, 0.9D);
    }
}
