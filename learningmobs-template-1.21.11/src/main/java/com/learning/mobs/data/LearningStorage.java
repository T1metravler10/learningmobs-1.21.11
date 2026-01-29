package com.learning.mobs.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.learning.mobs.LearningMobs;
import com.learning.mobs.ai.NetworkIO;
import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.mobs.MobLearningType;

public class LearningStorage {
    private final Path root;
    private final Gson gson;

    public LearningStorage(Path root) {
        this.root = root;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public MobLearningState load(MobLearningType type) {
        Path dir = root.resolve(type.id());
        Path generationPath = dir.resolve("generation.json");
        Path networksPath = dir.resolve("networks.dat");

        int generation = 0;
        if (Files.exists(generationPath)) {
            try {
                String json = Files.readString(generationPath, StandardCharsets.UTF_8);
                GenerationData data = gson.fromJson(json, GenerationData.class);
                if (data != null) {
                    generation = data.generation;
                }
            } catch (IOException ex) {
                LearningMobs.LOGGER.warn("Failed to read generation data for {}.", type.id(), ex);
            }
        }

        List<NeuralNetwork> networks;
        try {
            networks = NetworkIO.readNetworks(networksPath);
        } catch (IOException ex) {
            LearningMobs.LOGGER.warn("Failed to read networks for {}.", type.id(), ex);
            networks = List.of();
        }

        return new MobLearningState(generation, networks);
    }

    public void save(MobLearningType type, int generation, long worldTick, List<NeuralNetwork> networks) {
        Path dir = root.resolve(type.id());
        Path generationPath = dir.resolve("generation.json");
        Path networksPath = dir.resolve("networks.dat");
        try {
            Files.createDirectories(dir);
            GenerationData data = new GenerationData(generation, worldTick);
            Files.writeString(generationPath, gson.toJson(data), StandardCharsets.UTF_8);
            NetworkIO.writeNetworks(networksPath, networks);
        } catch (IOException ex) {
            LearningMobs.LOGGER.warn("Failed to save learning data for {}.", type.id(), ex);
        }
    }
}
