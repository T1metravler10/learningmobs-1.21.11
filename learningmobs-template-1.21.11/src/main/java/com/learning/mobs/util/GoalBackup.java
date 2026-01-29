package com.learning.mobs.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.learning.mobs.LearningMobs;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

public final class GoalBackup {
    private static Field availableGoalsField;
    private static Method wrappedGoalGetGoal;
    private static Method wrappedGoalGetPriority;
    private static Field wrappedGoalGoalField;
    private static Field wrappedGoalPriorityField;

    private GoalBackup() {
    }

    public static GoalSnapshot capture(Mob mob) {
        List<GoalSnapshot.GoalEntry> goals = snapshotSelector(mob.goalSelector);
        List<GoalSnapshot.GoalEntry> targets = snapshotSelector(mob.targetSelector);
        return new GoalSnapshot(goals, targets);
    }

    public static void restore(Mob mob, GoalSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        restoreSelector(mob.goalSelector, snapshot.goals());
        restoreSelector(mob.targetSelector, snapshot.targets());
    }

    public static void clearGoals(Mob mob) {
        clearSelector(mob.goalSelector);
    }

    private static List<GoalSnapshot.GoalEntry> snapshotSelector(GoalSelector selector) {
        Set<?> wrapped = getAvailableGoals(selector);
        if (wrapped == null) {
            return List.of();
        }
        List<GoalSnapshot.GoalEntry> entries = new ArrayList<>();
        for (Object wrappedGoal : wrapped) {
            GoalSnapshot.GoalEntry entry = toEntry(wrappedGoal);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static void restoreSelector(GoalSelector selector, List<GoalSnapshot.GoalEntry> entries) {
        Set<?> wrapped = getAvailableGoals(selector);
        if (wrapped != null) {
            List<Goal> existing = new ArrayList<>();
            for (Object wrappedGoal : wrapped) {
                GoalSnapshot.GoalEntry entry = toEntry(wrappedGoal);
                if (entry != null) {
                    existing.add(entry.goal());
                }
            }
            for (Goal goal : existing) {
                selector.removeGoal(goal);
            }
        }
        for (GoalSnapshot.GoalEntry entry : entries) {
            selector.addGoal(entry.priority(), entry.goal());
        }
    }

    private static void clearSelector(GoalSelector selector) {
        Set<?> wrapped = getAvailableGoals(selector);
        if (wrapped == null) {
            return;
        }
        List<Goal> existing = new ArrayList<>();
        for (Object wrappedGoal : wrapped) {
            GoalSnapshot.GoalEntry entry = toEntry(wrappedGoal);
            if (entry != null) {
                existing.add(entry.goal());
            }
        }
        for (Goal goal : existing) {
            selector.removeGoal(goal);
        }
    }

    private static GoalSnapshot.GoalEntry toEntry(Object wrappedGoal) {
        Goal goal = readGoal(wrappedGoal);
        Integer priority = readPriority(wrappedGoal);
        if (goal == null || priority == null) {
            return null;
        }
        return new GoalSnapshot.GoalEntry(priority, goal);
    }

    private static Goal readGoal(Object wrappedGoal) {
        if (wrappedGoal == null) {
            return null;
        }
        try {
            if (wrappedGoalGetGoal == null) {
                wrappedGoalGetGoal = findGoalMethod(wrappedGoal.getClass());
            }
            if (wrappedGoalGetGoal != null) {
                return (Goal) wrappedGoalGetGoal.invoke(wrappedGoal);
            }
            if (wrappedGoalGoalField == null) {
                wrappedGoalGoalField = findGoalField(wrappedGoal.getClass());
            }
            if (wrappedGoalGoalField != null) {
                return (Goal) wrappedGoalGoalField.get(wrappedGoal);
            }
        } catch (Exception ex) {
            LearningMobs.LOGGER.debug("Failed reading wrapped goal.", ex);
        }
        return null;
    }

    private static Integer readPriority(Object wrappedGoal) {
        if (wrappedGoal == null) {
            return null;
        }
        try {
            if (wrappedGoalGetPriority == null) {
                wrappedGoalGetPriority = findPriorityMethod(wrappedGoal.getClass());
            }
            if (wrappedGoalGetPriority != null) {
                return (Integer) wrappedGoalGetPriority.invoke(wrappedGoal);
            }
            if (wrappedGoalPriorityField == null) {
                wrappedGoalPriorityField = findPriorityField(wrappedGoal.getClass());
            }
            if (wrappedGoalPriorityField != null) {
                return (Integer) wrappedGoalPriorityField.get(wrappedGoal);
            }
        } catch (Exception ex) {
            LearningMobs.LOGGER.debug("Failed reading wrapped goal priority.", ex);
        }
        return null;
    }

    private static Method findGoalMethod(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && Goal.class.isAssignableFrom(method.getReturnType())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method findPriorityMethod(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == int.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Field findGoalField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (Goal.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Field findPriorityField(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getType() == int.class) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Set<?> getAvailableGoals(GoalSelector selector) {
        try {
            if (availableGoalsField != null) {
                return (Set<?>) availableGoalsField.get(selector);
            }
            Field fallback = null;
            for (Field field : GoalSelector.class.getDeclaredFields()) {
                if (!Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(selector);
                if (!(value instanceof Set<?> set)) {
                    continue;
                }
                if (!set.isEmpty() && isWrappedGoal(set.iterator().next())) {
                    availableGoalsField = field;
                    return set;
                }
                if (fallback == null) {
                    fallback = field;
                }
            }
            if (fallback != null) {
                availableGoalsField = fallback;
                return (Set<?>) fallback.get(selector);
            }
        } catch (Exception ex) {
            LearningMobs.LOGGER.debug("Failed accessing GoalSelector goals.", ex);
        }
        return null;
    }

    private static boolean isWrappedGoal(Object value) {
        return value != null && value.getClass().getSimpleName().contains("WrappedGoal");
    }
}
