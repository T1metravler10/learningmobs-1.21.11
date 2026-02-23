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

    private double villagerDamageDealt;
    private int villagerKills;

    public ZombieBrain(NeuralNetwork network) {
        super(MobLearningType.ZOMBIE, network);
    }

    @Override
    public void recordDamageDealt(net.minecraft.world.entity.Entity victim, double amount) {
        super.recordDamageDealt(victim, amount);
        if (victim.getType() == net.minecraft.world.entity.EntityType.VILLAGER) {
            villagerDamageDealt += amount;
        }
    }

    @Override
    public void recordKill(net.minecraft.world.entity.Entity victim) {
        super.recordKill(victim);
        if (victim.getType() == net.minecraft.world.entity.EntityType.VILLAGER) {
            villagerKills++;
        }
    }

    @Override
    protected double[] buildInputs(Mob mob) {
        double[] inputs = createStandardInputs(mob);

        inputs[38] = mob.isBaby() ? 1.0D : 0.0D;
        inputs[39] = !mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() ? 1.0D : 0.0D;

        return inputs;
    }

    @Override
    public double calculateFitness() {
        double fitness = super.calculateFitness();
        
        // Reward for villager damage: +1.0 per damage point (on top of super +2.0)
        fitness += villagerDamageDealt * 1.0D;
        
        // Reward for villager kills: +5.0 per kill (on top of super +6.0)
        fitness += villagerKills * 5.0D;
        
        return fitness;
    }
}
