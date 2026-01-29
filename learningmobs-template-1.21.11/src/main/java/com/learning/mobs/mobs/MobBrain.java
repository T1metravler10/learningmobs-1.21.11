package com.learning.mobs.mobs;

import com.learning.mobs.ai.NeuralNetwork;

import net.minecraft.world.entity.Mob;

public interface MobBrain {
    void tick(Mob mob);

    double calculateFitness();

    default void recordDamageDealt(double amount) {
    }

    default void recordDamageTaken(double amount) {
    }

    default void recordKill() {
    }

    default double[] lastOutputs() {
        return new double[0];
    }

    default double[] lastInputs() {
        return new double[0];
    }

    default int ticksAlive() {
        return 0;
    }

    default double damageDealt() {
        return 0.0D;
    }

    default double damageTaken() {
        return 0.0D;
    }

    default int kills() {
        return 0;
    }

    NeuralNetwork network();

    MobLearningType type();
}
