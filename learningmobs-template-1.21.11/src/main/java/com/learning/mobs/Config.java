package com.learning.mobs;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_CREEPER;
    public static final ModConfigSpec.BooleanValue ENABLE_ZOMBIE;
    public static final ModConfigSpec.BooleanValue ENABLE_SKELETON;

    public static final ModConfigSpec.IntValue GENERATION_LENGTH_TICKS;
    public static final ModConfigSpec.IntValue MAX_NETWORKS_PER_MOB;
    public static final ModConfigSpec.IntValue LEARNING_TICK_INTERVAL;
    public static final ModConfigSpec.DoubleValue MUTATION_RATE;
    public static final ModConfigSpec.DoubleValue MUTATION_STDDEV;
    public static final ModConfigSpec.IntValue LEARNING_GOAL_PRIORITY;
    public static final ModConfigSpec.DoubleValue LEARNING_CONTROL_STRENGTH;
    public static final ModConfigSpec.DoubleValue LEARNING_SPEED_MULTIPLIER;

    static {
        BUILDER.comment("Learning Mobs settings").push("learning");

        BUILDER.comment("Enable learning per mob type").push("mobs");
        ENABLE_CREEPER = BUILDER.define("creeper", true);
        ENABLE_ZOMBIE = BUILDER.define("zombie", true);
        ENABLE_SKELETON = BUILDER.define("skeleton", true);
        BUILDER.pop();

        GENERATION_LENGTH_TICKS = BUILDER
                .comment("Ticks per generation (24000 = one day/night cycle).")
                .defineInRange("generationLengthTicks", 24000, 1200, Integer.MAX_VALUE);
        MAX_NETWORKS_PER_MOB = BUILDER
                .comment("Population size per mob type.")
                .defineInRange("maxNetworksPerMob", 24, 2, 256);
        LEARNING_TICK_INTERVAL = BUILDER
                .comment("How often brains update (in ticks).")
                .defineInRange("learningTickInterval", 5, 1, 200);
        MUTATION_RATE = BUILDER
                .comment("Chance per weight to mutate.")
                .defineInRange("mutationRate", 0.08D, 0.0D, 1.0D);
        MUTATION_STDDEV = BUILDER
                .comment("Standard deviation for weight mutation.")
                .defineInRange("mutationStdDev", 0.35D, 0.0D, 5.0D);
        LEARNING_GOAL_PRIORITY = BUILDER
                .comment("Goal priority for learning control (0 is highest).")
                .defineInRange("learningGoalPriority", 0, 0, 10);
        LEARNING_CONTROL_STRENGTH = BUILDER
                .comment("How strongly learning outputs steer mobs (0.0 - 1.0).")
                .defineInRange("learningControlStrength", 1.0D, 0.0D, 1.0D);
        LEARNING_SPEED_MULTIPLIER = BUILDER
                .comment("Speed multiplier applied when learning steers movement.")
                .defineInRange("learningSpeedMultiplier", 1.35D, 0.2D, 3.0D);

        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
