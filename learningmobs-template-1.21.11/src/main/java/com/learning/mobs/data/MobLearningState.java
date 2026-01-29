package com.learning.mobs.data;

import java.util.List;

import com.learning.mobs.ai.NeuralNetwork;

public record MobLearningState(int generation, List<NeuralNetwork> networks) {
}
