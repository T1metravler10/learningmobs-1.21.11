package com.learning.mobs.ai;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NetworkIO {
    private NetworkIO() {
    }

    public static void writeNetworks(Path path, List<NeuralNetwork> networks) throws IOException {
        Files.createDirectories(path.getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(networks.size());
            for (NeuralNetwork network : networks) {
                out.writeInt(network.inputCount());
                out.writeInt(network.outputCount());
                double[] weights = network.weights();
                out.writeInt(weights.length);
                for (double weight : weights) {
                    out.writeDouble(weight);
                }
            }
        }
    }

    public static List<NeuralNetwork> readNetworks(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        List<NeuralNetwork> networks = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int inputCount = in.readInt();
                int outputCount = in.readInt();
                int weightCount = in.readInt();
                double[] weights = new double[weightCount];
                for (int w = 0; w < weightCount; w++) {
                    weights[w] = in.readDouble();
                }
                networks.add(new NeuralNetwork(inputCount, outputCount, weights));
            }
        }
        return networks;
    }
}
