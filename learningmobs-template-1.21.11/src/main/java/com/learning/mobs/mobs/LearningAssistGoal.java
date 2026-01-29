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
        return manager.getBrain(mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (meleeCooldown > 0) {
            meleeCooldown -= 1;
        }
        if (rangedCooldown > 0) {
            rangedCooldown -= 1;
        }
        if (wanderCooldown > 0) {
            wanderCooldown -= 1;
        }
        MobBrain brain = manager.getBrain(mob);
        if (brain == null) {
            return;
        }
        double[] outputs = brain.lastOutputs();
        if (outputs.length < type.outputCount()) {
            return;
        }
        boolean fullTakeover = manager.isFullTakeover(mob);
        switch (type) {
            case CREEPER -> applyCreeper(outputs, fullTakeover);
            case ZOMBIE -> applyZombie(outputs, fullTakeover);
            case SKELETON -> applySkeleton(outputs, fullTakeover);
        }
    }

    private void applyCreeper(double[] outputs, boolean fullTakeover) {
        LivingEntity target = mob.getTarget();
        double moveToward = toSignal(outputs[0]);
        double moveAway = toSignal(outputs[1]);
        double strafe = outputs[2];
        double jump = toSignal(outputs[3]);
        double explode = toSignal(outputs[4]);
        double stop = toSignal(outputs[5]);
        double wander = toSignal(outputs[6]);
        double sprint = toSignal(outputs[7]);
        double speed = moveSpeed();

        mob.setSprinting(sprint > threshold(0.5D, fullTakeover));

        if (stop > threshold(0.6D, fullTakeover) && fullTakeover) {
            mob.getNavigation().stop();
            return;
        }

        if (target != null) {
            if (moveToward > threshold(0.5D, fullTakeover) && moveToward >= moveAway) {
                moveTo(target, speed);
            } else if (moveAway > threshold(0.5D, fullTakeover)) {
                moveAwayFrom(target, speed, 7.0D);
            }
            if (Math.abs(strafe) > threshold(0.45D, fullTakeover)) {
                strafeAround(target, strafe, 1.5D);
            }
            if (explode > threshold(0.5D, fullTakeover) && mob instanceof Creeper creeper && mob.distanceTo(target) < 4.5D) {
                creeper.setSwellDir(1);
            }
        } else if (wander > threshold(0.45D, fullTakeover)) {
            wanderRandom(speed);
        }

        if (jump > threshold(0.7D, fullTakeover) && mob.onGround()) {
            mob.getJumpControl().jump();
        }
    }

    private void applyZombie(double[] outputs, boolean fullTakeover) {
        LivingEntity target = mob.getTarget();
        double attack = toSignal(outputs[0]);
        double chase = toSignal(outputs[1]);
        double retreat = toSignal(outputs[2]);
        double strafe = outputs[3];
        double jump = toSignal(outputs[4]);
        double stop = toSignal(outputs[5]);
        double wander = toSignal(outputs[6]);
        double sprint = toSignal(outputs[7]);
        double speed = moveSpeed();

        mob.setSprinting(sprint > threshold(0.5D, fullTakeover));

        if (stop > threshold(0.6D, fullTakeover) && fullTakeover) {
            mob.getNavigation().stop();
            return;
        }

        if (target != null) {
            if (retreat > threshold(0.5D, fullTakeover) && retreat > chase) {
                moveAwayFrom(target, speed, 7.0D);
            } else if (chase > threshold(0.45D, fullTakeover) || attack > threshold(0.45D, fullTakeover)) {
                moveTo(target, speed);
            }
            if (Math.abs(strafe) > threshold(0.45D, fullTakeover)) {
                strafeAround(target, strafe, 1.4D);
            }
            mob.setAggressive(attack > threshold(0.5D, fullTakeover) || chase > threshold(0.5D, fullTakeover));
            if (attack > threshold(0.5D, fullTakeover) && meleeCooldown == 0 && mob.distanceTo(target) < 3.0D) {
                if (mob.level() instanceof ServerLevel serverLevel) {
                    mob.doHurtTarget(serverLevel, target);
                    meleeCooldown = 20;
                }
            }
        } else if (wander > threshold(0.45D, fullTakeover)) {
            wanderRandom(speed);
        }

        if (jump > threshold(0.65D, fullTakeover) && mob.onGround()) {
            mob.getJumpControl().jump();
        }
    }

    private void applySkeleton(double[] outputs, boolean fullTakeover) {
        LivingEntity target = mob.getTarget();
        double strafe = outputs[0];
        double retreat = toSignal(outputs[1]);
        double advance = toSignal(outputs[2]);
        double ranged = toSignal(outputs[3]);
        double jump = toSignal(outputs[4]);
        double stop = toSignal(outputs[5]);
        double wander = toSignal(outputs[6]);
        double sprint = toSignal(outputs[7]);
        double speed = moveSpeed();

        mob.setSprinting(sprint > threshold(0.5D, fullTakeover));

        if (stop > threshold(0.6D, fullTakeover) && fullTakeover) {
            mob.getNavigation().stop();
            return;
        }

        if (target != null) {
            if (retreat > threshold(0.5D, fullTakeover) && retreat >= advance) {
                moveAwayFrom(target, speed, 7.0D);
            } else if (advance > threshold(0.45D, fullTakeover)) {
                moveTo(target, speed);
            }
            if (Math.abs(strafe) > threshold(0.45D, fullTakeover)) {
                strafeAround(target, strafe, 1.5D);
            }
            if (ranged > threshold(0.5D, fullTakeover) && rangedCooldown == 0
                    && mob instanceof RangedAttackMob rangedMob && mob.hasLineOfSight(target)) {
                rangedMob.performRangedAttack(target, 1.0F);
                rangedCooldown = 25;
            }
        } else if (wander > threshold(0.45D, fullTakeover)) {
            wanderRandom(speed);
        }

        if (jump > threshold(0.65D, fullTakeover) && mob.onGround()) {
            mob.getJumpControl().jump();
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
