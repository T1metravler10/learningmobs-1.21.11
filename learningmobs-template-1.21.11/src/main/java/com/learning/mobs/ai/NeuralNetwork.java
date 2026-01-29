package com.learning.mobs.ai;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.util.RandomSource;

public final class NeuralNetwork {
    private final int inputCount;
    private final int outputCount;
    private final double[] weights;

    public NeuralNetwork(int inputCount, int outputCount, double[] weights) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        int expected = outputCount * (inputCount + 1);
        if (weights.length != expected) {
            throw new IllegalArgumentException("Expected " + expected + " weights, got " + weights.length);
        }
        this.weights = weights;
        sanitize();
    }

    public static NeuralNetwork random(int inputCount, int outputCount, RandomSource random) {
        int size = outputCount * (inputCount + 1);
        double[] weights = new double[size];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (random.nextDouble() * 2.0D) - 1.0D;
        }
        return new NeuralNetwork(inputCount, outputCount, weights);
    }

    public double[] evaluate(double[] inputs) {
        Objects.requireNonNull(inputs, "inputs");
        if (inputs.length != inputCount) {
            throw new IllegalArgumentException("Expected " + inputCount + " inputs, got " + inputs.length);
        }
        double[] outputs = new double[outputCount];
        int stride = inputCount + 1;
        for (int out = 0; out < outputCount; out++) {
            int offset = out * stride;
            double sum = weights[offset + inputCount];
            for (int in = 0; in < inputCount; in++) {
                sum += inputs[in] * weights[offset + in];
            }
            outputs[out] = Math.tanh(sum);
        }
        return outputs;
    }

    public void mutate(double mutationRate, double mutationStdDev, RandomSource random) {
        if (mutationRate <= 0 || mutationStdDev <= 0) {
            return;
        }
        for (int i = 0; i < weights.length; i++) {
            if (random.nextDouble() <= mutationRate) {
                weights[i] += random.nextGaussian() * mutationStdDev;
            }
        }
        sanitize();
    }

    public static NeuralNetwork crossover(NeuralNetwork parentA, NeuralNetwork parentB, RandomSource random) {
        if (parentA.inputCount != parentB.inputCount || parentA.outputCount != parentB.outputCount) {
            throw new IllegalArgumentException("Mismatched network shapes.");
        }
        double[] weights = new double[parentA.weights.length];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = random.nextBoolean() ? parentA.weights[i] : parentB.weights[i];
        }
        return new NeuralNetwork(parentA.inputCount, parentA.outputCount, weights);
    }

    public NeuralNetwork copy() {
        return new NeuralNetwork(inputCount, outputCount, Arrays.copyOf(weights, weights.length));
    }

    public int inputCount() {
        return inputCount;
    }

    public int outputCount() {
        return outputCount;
    }

    public double[] weights() {
        return weights;
    }

    private void sanitize() {
        for (int i = 0; i < weights.length; i++) {
            double value = weights[i];
            if (!Double.isFinite(value)) {
                weights[i] = 0.0D;
            }
        }
    }
}
