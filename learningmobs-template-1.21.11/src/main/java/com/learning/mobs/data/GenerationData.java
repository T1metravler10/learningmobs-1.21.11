package com.learning.mobs.data;

public class GenerationData {
    public int generation;
    public long lastSavedTick;

    public GenerationData() {
    }

    public GenerationData(int generation, long lastSavedTick) {
        this.generation = generation;
        this.lastSavedTick = lastSavedTick;
    }
}
