package com.learning.mobs.mobs;

import java.util.function.BooleanSupplier;

import com.learning.mobs.Config;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

public enum MobLearningType {
    CREEPER("creeper", EntityType.CREEPER, () -> Config.ENABLE_CREEPER.getAsBoolean(), 15, 8),
    ZOMBIE("zombie", EntityType.ZOMBIE, () -> Config.ENABLE_ZOMBIE.getAsBoolean(), 16, 8),
    SKELETON("skeleton", EntityType.SKELETON, () -> Config.ENABLE_SKELETON.getAsBoolean(), 15, 8);

    private final String id;
    private final EntityType<? extends Mob> entityType;
    private final BooleanSupplier enabled;
    private final int inputCount;
    private final int outputCount;

    MobLearningType(String id,
            EntityType<? extends Mob> entityType,
            BooleanSupplier enabled,
            int inputCount,
            int outputCount) {
        this.id = id;
        this.entityType = entityType;
        this.enabled = enabled;
        this.inputCount = inputCount;
        this.outputCount = outputCount;
    }

    public String id() {
        return id;
    }

    public EntityType<? extends Mob> entityType() {
        return entityType;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    public int inputCount() {
        return inputCount;
    }

    public int outputCount() {
        return outputCount;
    }

    public static MobLearningType fromEntity(Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        for (MobLearningType type : values()) {
            if (mob.getType() == type.entityType) {
                return type;
            }
        }
        return null;
    }
}
