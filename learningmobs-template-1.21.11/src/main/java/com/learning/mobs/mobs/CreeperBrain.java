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

    private boolean exploded;
    private boolean died;
    private double playerDamageDealt;
    private int playerKills;

    public CreeperBrain(NeuralNetwork network) {
        super(MobLearningType.CREEPER, network);
    }

    @Override
    public void recordDamageDealt(net.minecraft.world.entity.Entity victim, double amount) {
        super.recordDamageDealt(victim, amount);
        if (victim instanceof net.minecraft.world.entity.player.Player) {
            playerDamageDealt += amount;
        }
    }

    @Override
    public void recordKill(net.minecraft.world.entity.Entity victim) {
        super.recordKill(victim);
        if (victim instanceof net.minecraft.world.entity.player.Player) {
            playerKills++;
        }
    }

    public void setExploded() {
        this.exploded = true;
        this.died = true;
    }

    public void setDied() {
        this.died = true;
    }

    @Override
    protected double[] buildInputs(Mob mob) {
        double[] inputs = createStandardInputs(mob);

        if (mob instanceof Creeper creeper) {
            inputs[38] = creeper.isPowered() ? 1.0D : 0.0D;
            inputs[39] = BrainMath.clamp(creeper.getSwellDir(), -1.0D, 1.0D);
        }

        return inputs;
    }

    @Override
    public double calculateFitness() {
        // Base fitness: ticksAlive + (damageDealt * 2.0D) - damageTaken + (kills * 6.0D)
        double fitness = super.calculateFitness();

        // Additional +6 points for every player killed (total 12 per player kill)
        fitness += playerKills * 6.0D;

        if (died) {
            if (exploded) {
                if (playerDamageDealt > 0) {
                    fitness += 5.0D; // +5 if damage is dealt to a player
                } else if (playerKills == 0) {
                    fitness -= 2.0D; // -2 if blows up and no damage done and no player killed
                }
            } else {
                // Killed but didn't explode
                if (playerDamageDealt == 0) {
                    fitness -= 4.0D; // -4 if killed and no damage dealt to player
                }
            }
        }

        return fitness;
    }
}
