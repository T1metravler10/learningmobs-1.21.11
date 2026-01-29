package com.learning.mobs.genetics;

import java.util.ArrayList;
import java.util.List;

import com.learning.mobs.Config;
import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.data.LearningStorage;
import com.learning.mobs.data.MobLearningState;
import com.learning.mobs.mobs.MobBrain;
import com.learning.mobs.mobs.MobLearningType;

import net.minecraft.util.RandomSource;

public class MobLearningPool {
    private final MobLearningType type;
    private final LearningStorage storage;
    private final RandomSource random;

    private List<NeuralNetwork> networks;
    private int generation;
    private int nextIndex;

    public MobLearningPool(MobLearningType type, LearningStorage storage, RandomSource random) {
        this.type = type;
        this.storage = storage;
        this.random = random;
        MobLearningState state = storage.load(type);
        this.generation = state.generation();
        this.networks = new ArrayList<>(state.networks());
        ensurePopulation();
    }

    public int generation() {
        return generation;
    }

    public int populationSize() {
        ensurePopulation();
        return networks.size();
    }

    public NeuralNetwork nextNetwork() {
        ensurePopulation();
        if (networks.isEmpty()) {
            return NeuralNetwork.random(type.inputCount(), type.outputCount(), random);
        }
        NeuralNetwork selected = networks.get(nextIndex++ % networks.size());
        return selected.copy();
    }

    public void evolve(List<MobBrain> brains, long worldTick) {
        ensurePopulation();
        int targetSize = Config.MAX_NETWORKS_PER_MOB.getAsInt();
        double mutationRate = Config.MUTATION_RATE.getAsDouble();
        double mutationStdDev = Config.MUTATION_STDDEV.getAsDouble();
        networks = GeneticAlgorithm.evolve(networks, brains, targetSize, mutationRate, mutationStdDev, random);
        generation += 1;
        nextIndex = 0;
        storage.save(type, generation, worldTick, networks);
    }

    public void save(long worldTick) {
        ensurePopulation();
        storage.save(type, generation, worldTick, networks);
    }

    private void ensurePopulation() {
        int targetSize = Config.MAX_NETWORKS_PER_MOB.getAsInt();
        if (networks == null) {
            networks = new ArrayList<>();
        }
        networks.removeIf(network -> network.inputCount() != type.inputCount()
                || network.outputCount() != type.outputCount());
        if (networks.isEmpty()) {
            for (int i = 0; i < targetSize; i++) {
                networks.add(NeuralNetwork.random(type.inputCount(), type.outputCount(), random));
            }
            return;
        }
        while (networks.size() < targetSize) {
            networks.add(NeuralNetwork.random(type.inputCount(), type.outputCount(), random));
        }
        if (networks.size() > targetSize) {
            networks = new ArrayList<>(networks.subList(0, targetSize));
        }
    }
}
