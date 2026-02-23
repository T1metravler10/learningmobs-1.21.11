package com.learning.mobs.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public final class BrainMath {
    private BrainMath() {
    }

    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public static double normalizeDistance(double distance, double maxDistance) {
        if (maxDistance <= 0) {
            return 0.0D;
        }
        return clamp(distance / maxDistance, 0.0D, 1.0D);
    }

    public static double normalizeHealth(Mob mob) {
        if (mob.getMaxHealth() <= 0) {
            return 0.0D;
        }
        return clamp(mob.getHealth() / mob.getMaxHealth(), 0.0D, 1.0D);
    }

    public static double normalizeLight(Level level, BlockPos pos) {
        return clamp(level.getMaxLocalRawBrightness(pos) / 15.0D, 0.0D, 1.0D);
    }

    public static double normalizeTime(Level level) {
        long time = level.getDayTime() % 24000L;
        return clamp(time / 24000.0D, 0.0D, 1.0D);
    }

    public static double normalizeGroupSize(int size, int max) {
        if (max <= 0) {
            return 0.0D;
        }
        return clamp((double) size / max, 0.0D, 1.0D);
    }

    public static String formatTime(long ticks) {
        if (ticks < 0) return "00:00";
        long totalSeconds = ticks / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);
    }
}
