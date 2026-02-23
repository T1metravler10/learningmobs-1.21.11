package com.learning.mobs.genetics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.mobs.MobBrain;

import net.minecraft.util.RandomSource;

public final class GeneticAlgorithm {
    private GeneticAlgorithm() {
    }

    public static List<NeuralNetwork> evolve(List<NeuralNetwork> currentPopulation,
            List<MobBrain> brains,
            int targetSize,
            double mutationRate,
            double mutationStdDev,
            RandomSource random) {
        List<NeuralNetwork> basePopulation = new ArrayList<>(currentPopulation);
        if (basePopulation.isEmpty()) {
            return basePopulation;
        }

        List<BrainFitness> ranked = new ArrayList<>();
        synchronized (brains) {
            for (MobBrain brain : brains) {
                ranked.add(new BrainFitness(brain.network(), brain.calculateFitness()));
            }
        }
        ranked.sort(Comparator.comparingDouble(BrainFitness::fitness).reversed());

        int eliteCount = Math.max(1, Math.min(ranked.size(), Math.max(2, ranked.size() / 5)));
        List<NeuralNetwork> next = new ArrayList<>(targetSize);
        for (int i = 0; i < eliteCount && i < ranked.size(); i++) {
            next.add(ranked.get(i).network().copy());
        }

        if (ranked.isEmpty()) {
            // No fitness data; keep the existing population unchanged.
            for (NeuralNetwork network : basePopulation) {
                if (next.size() >= targetSize) {
                    break;
                }
                next.add(network.copy());
            }
        }

        while (next.size() < targetSize) {
            NeuralNetwork parentA = ranked.isEmpty()
                    ? basePopulation.get(random.nextInt(basePopulation.size()))
                    : ranked.get(random.nextInt(eliteCount)).network();
            NeuralNetwork parentB = ranked.isEmpty()
                    ? basePopulation.get(random.nextInt(basePopulation.size()))
                    : ranked.get(random.nextInt(eliteCount)).network();
            NeuralNetwork child = NeuralNetwork.crossover(parentA, parentB, random);
            child.mutate(mutationRate, mutationStdDev, random);
            next.add(child);
        }

        return next;
    }

    private record BrainFitness(NeuralNetwork network, double fitness) {
    }
}
