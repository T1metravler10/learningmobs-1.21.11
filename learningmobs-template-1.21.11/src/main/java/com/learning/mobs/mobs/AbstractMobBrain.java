package com.learning.mobs.mobs;

import com.learning.mobs.ai.NeuralNetwork;

import net.minecraft.world.entity.Mob;

public abstract class AbstractMobBrain implements MobBrain {
    protected final MobLearningType type;
    protected final NeuralNetwork network;
    protected int ticksAlive;
    protected double damageDealt;
    protected double damageTaken;
    protected int kills;
    protected double[] lastOutputs = new double[0];
    protected double[] lastInputs = new double[0];

    protected AbstractMobBrain(MobLearningType type, NeuralNetwork network) {
        this.type = type;
        this.network = network;
    }

    @Override
    public void tick(Mob mob) {
        ticksAlive += 1;
        double[] inputs = buildInputs(mob);
        lastInputs = inputs;
        lastOutputs = network.evaluate(inputs);
        applyOutputs(mob, lastOutputs);
    }

    @Override
    public void recordDamageDealt(double amount) {
        damageDealt += amount;
    }

    @Override
    public void recordDamageTaken(double amount) {
        damageTaken += amount;
    }

    @Override
    public void recordKill() {
        kills += 1;
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
        return ticksAlive + (damageDealt * 2.0D) - damageTaken + (kills * 6.0D);
    }

    protected abstract double[] buildInputs(Mob mob);

    protected void applyOutputs(Mob mob, double[] outputs) {
        // Intentionally no-op for additive learning; vanilla AI remains intact.
    }
}
