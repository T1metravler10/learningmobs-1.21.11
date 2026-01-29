package com.learning.mobs.mobs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.learning.mobs.Config;
import com.learning.mobs.LearningMobs;
import com.learning.mobs.ai.NeuralNetwork;
import com.learning.mobs.data.LearningStorage;
import com.learning.mobs.genetics.MobLearningPool;
import com.learning.mobs.util.GoalBackup;
import com.learning.mobs.util.GoalSnapshot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

public class LearningManager {
    private final EnumMap<MobLearningType, MobLearningPool> pools = new EnumMap<>(MobLearningType.class);
    private final EnumMap<MobLearningType, List<MobBrain>> generationBrains = new EnumMap<>(MobLearningType.class);
    private final Map<UUID, ActiveBrain> activeBrains = new HashMap<>();
    private final Map<UUID, String> lastErrors = new HashMap<>();
    private final Map<UUID, Boolean> fullTakeover = new HashMap<>();
    private final Map<UUID, Integer> suppressTicks = new HashMap<>();
    private final EnumMap<MobLearningType, GenerationStats> lastGenerationStats = new EnumMap<>(MobLearningType.class);
    private final RandomSource random = RandomSource.create();

    private LearningStorage storage;
    private long lastGenerationTick = -1L;
    private boolean initialized;

    public void onServerStarting(MinecraftServer server) {
        if (initialized) {
            return;
        }
        Path root = FMLPaths.CONFIGDIR.get().resolve("learningmobs").resolve("learning");
        storage = new LearningStorage(root);
        for (MobLearningType type : MobLearningType.values()) {
            pools.put(type, new MobLearningPool(type, storage, random));
            generationBrains.put(type, new ArrayList<>());
        }
        initialized = true;
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            lastGenerationTick = overworld.getDayTime();
        }
        LearningMobs.LOGGER.info("Learning data path: {}", root.toAbsolutePath());
    }

    public void onServerStopped(MinecraftServer server) {
        if (!initialized) {
            return;
        }
        long tick = resolveWorldTick(server);
        for (MobLearningType type : MobLearningType.values()) {
            MobLearningPool pool = pools.get(type);
            if (pool != null) {
                pool.save(tick);
            }
        }
    }

    public void onMobSpawn(Mob mob) {
        if (!initialized) {
            return;
        }
        if (activeBrains.containsKey(mob.getUUID())) {
            return;
        }
        lastErrors.remove(mob.getUUID());
        fullTakeover.remove(mob.getUUID());
        MobLearningType type = MobLearningType.fromEntity(mob);
        attachBrain(mob, type, false);
    }

    public void onMobDespawn(Mob mob) {
        ActiveBrain active = activeBrains.remove(mob.getUUID());
        if (active != null) {
            GoalBackup.restore(mob, active.backup());
        }
        lastErrors.remove(mob.getUUID());
        fullTakeover.remove(mob.getUUID());
        suppressTicks.remove(mob.getUUID());
    }

    public void onServerTick(MinecraftServer server) {
        if (!initialized) {
            return;
        }
        long worldTick = resolveWorldTick(server);
        if (lastGenerationTick < 0) {
            lastGenerationTick = worldTick;
        }
        if (worldTick - lastGenerationTick >= Config.GENERATION_LENGTH_TICKS.getAsInt()) {
            endGeneration(worldTick);
            lastGenerationTick = worldTick;
        }

        int interval = Config.LEARNING_TICK_INTERVAL.getAsInt();
        if (interval <= 1 || worldTick % interval == 0) {
            tickBrains();
        }

        if (!suppressTicks.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> iterator = suppressTicks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = iterator.next();
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    iterator.remove();
                } else {
                    entry.setValue(remaining);
                }
            }
        }
    }

    public void onDamage(Entity victim, Entity source, double amount) {
        if (victim instanceof Mob mobVictim) {
            MobBrain brain = getBrain(mobVictim);
            if (brain != null && !isSuppressed(mobVictim)) {
                brain.recordDamageTaken(amount);
            }
        }
        if (source instanceof Mob mobSource) {
            MobBrain brain = getBrain(mobSource);
            if (brain != null && !isSuppressed(mobSource)) {
                brain.recordDamageDealt(amount);
            }
        }
    }

    public void onKill(Entity victim, Entity source) {
        if (source instanceof Mob mobSource) {
            MobBrain brain = getBrain(mobSource);
            if (brain != null && !isSuppressed(mobSource)) {
                brain.recordKill();
            }
        }
    }

    public MobBrain getBrain(Mob mob) {
        ActiveBrain active = activeBrains.get(mob.getUUID());
        return active == null ? null : active.brain();
    }

    private void tickBrains() {
        Iterator<Map.Entry<UUID, ActiveBrain>> iterator = activeBrains.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveBrain active = iterator.next().getValue();
            Mob mob = active.mob();
            if (mob.isRemoved() || !mob.isAlive()) {
                iterator.remove();
                continue;
            }
            try {
                active.brain().tick(mob);
            } catch (Exception ex) {
                LearningMobs.LOGGER.warn("Learning brain failed for {}, restoring vanilla AI.", mob.getUUID(), ex);
                GoalBackup.restore(mob, active.backup());
                lastErrors.put(mob.getUUID(), formatError(ex));
                List<MobBrain> brains = generationBrains.get(active.brain().type());
                if (brains != null) {
                    brains.remove(active.brain());
                }
                fullTakeover.remove(mob.getUUID());
                iterator.remove();
            }
        }
    }

    private void endGeneration(long worldTick) {
        for (MobLearningType type : MobLearningType.values()) {
            if (!type.isEnabled()) {
                continue;
            }
            MobLearningPool pool = pools.get(type);
            if (pool == null) {
                continue;
            }
            List<MobBrain> brains = generationBrains.get(type);
            GenerationStats stats = GenerationStats.fromBrains(brains, worldTick);
            lastGenerationStats.put(type, stats);
            if (stats.sampleCount() > 0) {
                LearningMobs.LOGGER.info("Generation {} {} stats: avgFitness={} bestFitness={} samples={}",
                        pool.generation(),
                        type.id(),
                        String.format("%.2f", stats.averageFitness()),
                        String.format("%.2f", stats.bestFitness()),
                        stats.sampleCount());
            } else {
                LearningMobs.LOGGER.info("Generation {} {} stats: no samples.", pool.generation(), type.id());
            }
            pool.evolve(brains, worldTick);
            brains.clear();
        }
    }

    private long resolveWorldTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld == null ? 0L : overworld.getDayTime();
    }

    private MobBrain createBrain(MobLearningType type, NeuralNetwork network) {
        return switch (type) {
            case CREEPER -> new CreeperBrain(network);
            case ZOMBIE -> new ZombieBrain(network);
            case SKELETON -> new SkeletonBrain(network);
        };
    }

    public void resetBrain(Mob mob) {
        ActiveBrain active = activeBrains.remove(mob.getUUID());
        if (active != null) {
            GoalBackup.restore(mob, active.backup());
            List<MobBrain> brains = generationBrains.get(active.brain().type());
            if (brains != null) {
                brains.remove(active.brain());
            }
        }
        lastErrors.remove(mob.getUUID());
        fullTakeover.remove(mob.getUUID());
        suppressTicks.remove(mob.getUUID());
    }

    public boolean enableFullTakeover(Mob mob) {
        if (!initialized) {
            return false;
        }
        MobLearningType type = MobLearningType.fromEntity(mob);
        if (type == null || !type.isEnabled()) {
            return false;
        }
        if (!activeBrains.containsKey(mob.getUUID())) {
            onMobSpawn(mob);
        }
        ActiveBrain active = activeBrains.get(mob.getUUID());
        if (active == null) {
            return false;
        }
        fullTakeover.put(mob.getUUID(), Boolean.TRUE);
        GoalBackup.clearGoals(mob);
        int learningPriority = Math.max(0, Config.LEARNING_GOAL_PRIORITY.getAsInt());
        if (learningPriority == 0) {
            learningPriority = 1;
        }
        mob.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(mob));
        mob.goalSelector.addGoal(learningPriority, new LearningAssistGoal(mob, this, type));
        return true;
    }

    public boolean isFullTakeover(Mob mob) {
        return fullTakeover.getOrDefault(mob.getUUID(), Boolean.FALSE);
    }

    public int summonTestMobs(ServerLevel level, Vec3 pos) {
        if (!initialized) {
            return 0;
        }
        int spawned = 0;
        int index = 0;
        for (MobLearningType type : MobLearningType.values()) {
            Vec3 offset = pos.add((index - 1) * 1.5D, 0.0D, 0.0D);
            if (summonTestMob(type, level, offset)) {
                spawned += 1;
            }
            index += 1;
        }
        return spawned;
    }

    public int triggerTestExplode(ServerLevel level) {
        int count = 0;
        for (ActiveBrain active : activeBrains.values()) {
            if (active.brain().type() != MobLearningType.CREEPER) {
                continue;
            }
            Mob mob = active.mob();
            if (!(mob instanceof Creeper creeper)) {
                continue;
            }
            if (mob.isRemoved() || !mob.isAlive()) {
                continue;
            }
            if (mob.level() != level) {
                continue;
            }
            if (lastErrors.containsKey(mob.getUUID())) {
                continue;
            }
            suppress(mob, 80);
            creeper.ignite();
            count += 1;
        }
        return count;
    }

    public BrainStatus getStatus(Mob mob) {
        MobLearningType type = MobLearningType.fromEntity(mob);
        ActiveBrain active = activeBrains.get(mob.getUUID());
        CompoundTag data = getCustomDataTag(mob);
        int generation = data.getIntOr("learningmobs_generation", 0);
        int poolGeneration = -1;
        if (type != null) {
            MobLearningPool pool = pools.get(type);
            if (pool != null) {
                poolGeneration = pool.generation();
            }
        }
        String error = lastErrors.get(mob.getUUID());
        boolean takeover = isFullTakeover(mob);
        if (active == null) {
            return new BrainStatus(false, takeover, type, generation, poolGeneration, 0.0D, 0, 0.0D, 0.0D, 0,
                    new double[0], new double[0], error);
        }
        MobBrain brain = active.brain();
        return new BrainStatus(true,
                takeover,
                brain.type(),
                generation,
                poolGeneration,
                brain.calculateFitness(),
                brain.ticksAlive(),
                brain.damageDealt(),
                brain.damageTaken(),
                brain.kills(),
                brain.lastInputs(),
                brain.lastOutputs(),
                error);
    }

    public TypeStatus getTypeStatus(MobLearningType type) {
        MobLearningPool pool = pools.get(type);
        int population = pool == null ? 0 : pool.populationSize();
        int active = 0;
        int errors = 0;
        for (ActiveBrain activeBrain : activeBrains.values()) {
            if (activeBrain.brain().type() == type) {
                active += 1;
            }
        }
        for (Map.Entry<UUID, String> entry : lastErrors.entrySet()) {
            ActiveBrain activeBrain = activeBrains.get(entry.getKey());
            if (activeBrain != null && activeBrain.brain().type() == type) {
                errors += 1;
            }
        }
        GenerationStats stats = lastGenerationStats.get(type);
        return new TypeStatus(type,
                pool == null ? -1 : pool.generation(),
                population,
                active,
                errors,
                stats == null ? 0 : stats.sampleCount(),
                stats == null ? 0.0D : stats.averageFitness(),
                stats == null ? 0.0D : stats.bestFitness(),
                stats == null ? 0L : stats.worldTick());
    }

    private String formatError(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            message = "";
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 120) {
            message = message.substring(0, 120) + "...";
        }
        return ex.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }

    private record ActiveBrain(Mob mob, MobBrain brain, GoalSnapshot backup) {
    }

    private record GenerationStats(int sampleCount, double averageFitness, double bestFitness, long worldTick) {
        static GenerationStats fromBrains(List<MobBrain> brains, long worldTick) {
            if (brains == null || brains.isEmpty()) {
                return new GenerationStats(0, 0.0D, 0.0D, worldTick);
            }
            double total = 0.0D;
            double best = Double.NEGATIVE_INFINITY;
            int count = 0;
            for (MobBrain brain : brains) {
                double fitness = brain.calculateFitness();
                total += fitness;
                best = Math.max(best, fitness);
                count += 1;
            }
            double average = count == 0 ? 0.0D : total / count;
            return new GenerationStats(count, average, best, worldTick);
        }
    }

    public record BrainStatus(boolean hasBrain,
            boolean fullTakeover,
            MobLearningType type,
            int generation,
            int poolGeneration,
            double fitness,
            int ticksAlive,
            double damageDealt,
            double damageTaken,
            int kills,
            double[] lastInputs,
            double[] lastOutputs,
            String lastError) {
    }

    public record TypeStatus(MobLearningType type,
            int generation,
            int populationSize,
            int activeBrains,
            int errorCount,
            int lastSampleCount,
            double lastAverageFitness,
            double lastBestFitness,
            long lastWorldTick) {
    }

    private boolean summonTestMob(MobLearningType type, ServerLevel level, Vec3 pos) {
        Mob mob = type.entityType().create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            return false;
        }
        mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND,
                    (SpawnGroupData) null);
        } catch (Exception ex) {
            LearningMobs.LOGGER.warn("Failed to finalize spawn for {}", type.id(), ex);
        }
        boolean attached = attachBrain(mob, type, true);
        if (!attached) {
            return false;
        }
        return level.addFreshEntity(mob);
    }

    private boolean attachBrain(Mob mob, MobLearningType type, boolean test) {
        if (type == null || !type.isEnabled()) {
            return false;
        }
        MobLearningPool pool = pools.get(type);
        if (pool == null) {
            return false;
        }
        GoalSnapshot backup = GoalBackup.capture(mob);
        NeuralNetwork network = pool.nextNetwork();
        MobBrain brain = createBrain(type, network);
        if (brain == null) {
            return false;
        }
        if (test) {
            try {
                brain.tick(mob);
            } catch (Exception ex) {
                lastErrors.put(mob.getUUID(), formatError(ex));
                return false;
            }
        }
        activeBrains.put(mob.getUUID(), new ActiveBrain(mob, brain, backup));
        generationBrains.get(type).add(brain);
        mob.goalSelector.addGoal(Config.LEARNING_GOAL_PRIORITY.getAsInt(), new LearningAssistGoal(mob, this, type));
        CompoundTag data = getCustomDataTag(mob);
        data.putString("learningmobs_type", type.id());
        data.putInt("learningmobs_generation", pool.generation());
        setCustomDataTag(mob, data);
        return true;
    }

    private void suppress(Mob mob, int ticks) {
        suppressTicks.put(mob.getUUID(), Math.max(ticks, suppressTicks.getOrDefault(mob.getUUID(), 0)));
    }

    private boolean isSuppressed(Mob mob) {
        return suppressTicks.containsKey(mob.getUUID());
    }

    private static CompoundTag getCustomDataTag(Mob mob) {
        CustomData data = mob.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void setCustomDataTag(Mob mob, CompoundTag tag) {
        mob.setComponent(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
