package com.learning.mobs.util;

import java.util.List;

import net.minecraft.world.entity.ai.goal.Goal;

public record GoalSnapshot(List<GoalEntry> goals, List<GoalEntry> targets) {
    public record GoalEntry(int priority, Goal goal) {
    }
}
