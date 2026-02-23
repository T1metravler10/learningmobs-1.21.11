package com.learning.mobs.mobs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<MobLearningType, MobLearningPool> pools = new ConcurrentHashMap<>();
    private final Map<MobLearningType, List<MobBrain>> generationBrains = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveBrain> activeBrains = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastErrors = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> fullTakeover = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> suppressTicks = new ConcurrentHashMap<>();
    private final Map<MobLearningType, GenerationStats> lastGenerationStats = new ConcurrentHashMap<>();
    private final Map<MobLearningType, Double> allTimeBestFitness = new ConcurrentHashMap<>();
    private final Map<MobLearningType, TakeoverMode> typeModes = new ConcurrentHashMap<>();
    private final RandomSource random = RandomSource.create();

    private LearningStorage storage;
    private volatile long lastGenerationTick = -1L;
    private boolean initialized;

    public void onServerStarting(MinecraftServer server) {
        Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("learningmobs").resolve("learning");
        storage = new LearningStorage(root);
        
        // Clear old state to support world switching in singleplayer
        pools.clear();
        generationBrains.clear();
        activeBrains.clear();
        lastErrors.clear();
        fullTakeover.clear();
        suppressTicks.clear();
        lastGenerationStats.clear();
        allTimeBestFitness.clear();
        typeModes.clear();

        for (MobLearningType type : MobLearningType.values()) {
            pools.put(type, new MobLearningPool(type, storage, random));
            generationBrains.put(type, Collections.synchronizedList(new ArrayList<>()));
            typeModes.put(type, TakeoverMode.LEARN);
            allTimeBestFitness.put(type, 0.0D);
        }
        initialized = true;
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            lastGenerationTick = overworld.getGameTime();
        }
        LearningMobs.LOGGER.info("Learning data path for this world: {}", root.toAbsolutePath());
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
        
        // Reset state for next potential server start (singleplayer)
        pools.clear();
        generationBrains.clear();
        activeBrains.clear();
        lastErrors.clear();
        fullTakeover.clear();
        suppressTicks.clear();
        lastGenerationStats.clear();
        allTimeBestFitness.clear();
        typeModes.clear();
        initialized = false;
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
                brain.recordDamageDealt(victim, amount);
            }
        }
    }

    public void onKill(Entity victim, Entity source) {
        if (source instanceof Mob mobSource) {
            MobBrain brain = getBrain(mobSource);
            if (brain != null && !isSuppressed(mobSource)) {
                brain.recordKill(victim);
            }
        }
    }

    public MobBrain getBrain(Mob mob) {
        ActiveBrain active = activeBrains.get(mob.getUUID());
        return active == null ? null : active.brain();
    }

    public Iterable<Mob> getActiveMobs() {
        List<Mob> mobs = new ArrayList<>();
        for (ActiveBrain active : activeBrains.values()) {
            mobs.add(active.mob());
        }
        return mobs;
    }

    public long getTicksUntilRollover(MinecraftServer server) {
        if (!initialized) {
            return -1L;
        }
        long worldTick = resolveWorldTick(server);
        if (lastGenerationTick < 0) {
            return Config.GENERATION_LENGTH_TICKS.getAsInt();
        }
        long elapsed = worldTick - lastGenerationTick;
        return Math.max(0, Config.GENERATION_LENGTH_TICKS.getAsInt() - elapsed);
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
                MobBrain brain = active.brain();
                brain.tick(mob);
                
                // Track all-time best fitness
                double fitness = brain.calculateFitness();
                allTimeBestFitness.merge(brain.type(), fitness, Math::max);
                
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
            allTimeBestFitness.put(type, 0.0D); // Reset highest fitness for the new generation
            rolloverActiveBrains(type, pool, brains);
        }
    }

    private void rolloverActiveBrains(MobLearningType type, MobLearningPool pool, List<MobBrain> brains) {
        if (pool == null || brains == null) {
            return;
        }
        List<ActiveBrain> toRollover = new ArrayList<>();
        for (ActiveBrain active : activeBrains.values()) {
            if (active.brain().type() == type) {
                toRollover.add(active);
            }
        }
        if (toRollover.isEmpty()) {
            return;
        }
        int generation = pool.generation();
        for (ActiveBrain active : toRollover) {
            Mob mob = active.mob();
            if (mob.isRemoved() || !mob.isAlive()) {
                continue;
            }
            NeuralNetwork network = pool.nextNetwork();
            MobBrain brain = createBrain(type, network);
            if (brain == null) {
                continue;
            }
            activeBrains.put(mob.getUUID(), new ActiveBrain(mob, brain, active.backup()));
            brains.add(brain);
            CompoundTag data = getCustomDataTag(mob);
            data.putInt("learningmobs_generation", generation);
            setCustomDataTag(mob, data);
        }
    }

    private long resolveWorldTick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld == null ? 0L : overworld.getGameTime();
    }

    private MobBrain createBrain(MobLearningType type, NeuralNetwork network) {
        return switch (type) {
            case CREEPER -> new CreeperBrain(network);
            case ZOMBIE -> new ZombieBrain(network);
            case SKELETON -> new SkeletonBrain(network);
        };
    }

    public void resetBrain(Mob mob, boolean usePool) {
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

        // Re-attach brain
        MobLearningType type = MobLearningType.fromEntity(mob);
        if (type != null && type.isEnabled()) {
            if (usePool) {
                attachBrain(mob, type, false);
            } else {
                attachFreshBrain(mob, type);
            }
        }
    }

    public void resetBrain(Mob mob) {
        resetBrain(mob, false);
    }

    public TakeoverMode getTakeoverMode(MobLearningType type) {
        return typeModes.getOrDefault(type, TakeoverMode.LEARN);
    }

    public int setTakeoverMode(MobLearningType type, TakeoverMode mode) {
        typeModes.put(type, mode);
        int count = 0;
        for (ActiveBrain active : activeBrains.values()) {
            if (active.brain().type() == type) {
                applyModeToMob(active.mob(), mode, active);
                count++;
            }
        }
        return count;
    }

    public int setTakeoverModeAll(TakeoverMode mode) {
        int count = 0;
        for (MobLearningType type : MobLearningType.values()) {
            count += setTakeoverMode(type, mode);
        }
        return count;
    }

    private void applyModeToMob(Mob mob, TakeoverMode mode, ActiveBrain active) {
        if (mode == TakeoverMode.FULL) {
            fullTakeover.put(mob.getUUID(), Boolean.TRUE);
            GoalBackup.clearGoals(mob);
            mob.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(mob));
            mob.goalSelector.addGoal(Config.LEARNING_GOAL_PRIORITY.getAsInt(), new LearningAssistGoal(mob, this, active.brain().type()));
        } else {
            if (fullTakeover.containsKey(mob.getUUID())) {
                fullTakeover.remove(mob.getUUID());
                GoalBackup.restore(mob, active.backup());
                // Re-add learning goal as it's not in the backup
                mob.goalSelector.addGoal(Config.LEARNING_GOAL_PRIORITY.getAsInt(), new LearningAssistGoal(mob, this, active.brain().type()));
            }
        }
    }

    public int resetAllBrains(boolean usePool) {
        List<Mob> toReset = new ArrayList<>();
        for (ActiveBrain active : activeBrains.values()) {
            toReset.add(active.mob());
        }
        for (Mob mob : toReset) {
            resetBrain(mob, usePool);
        }
        return toReset.size();
    }

    public int resetAllBrains() {
        return resetAllBrains(false);
    }

    public int resetBrainsByType(MobLearningType type, boolean usePool) {
        List<Mob> toReset = new ArrayList<>();
        for (ActiveBrain active : activeBrains.values()) {
            if (active.brain().type() == type) {
                toReset.add(active.mob());
            }
        }
        for (Mob mob : toReset) {
            resetBrain(mob, usePool);
        }
        return toReset.size();
    }

    public int resetBrainsByType(MobLearningType type) {
        return resetBrainsByType(type, false);
    }

    public void saveData(MobLearningType type, MinecraftServer server) {
        long tick = resolveWorldTick(server);
        if (type == null) {
            for (MobLearningPool pool : pools.values()) {
                pool.save(tick);
            }
        } else {
            MobLearningPool pool = pools.get(type);
            if (pool != null) {
                pool.save(tick);
            }
        }
    }

    public int loadData(String sourceWorldName, MobLearningType type, MinecraftServer server) {
        Path savesDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).getParent();
        Path sourcePath = savesDir.resolve(sourceWorldName).resolve("learningmobs").resolve("learning");
        
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source world '" + sourceWorldName + "' not found or has no learning data.");
        }

        Path targetPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("learningmobs").resolve("learning");
        int count = 0;

        try {
            if (type == null) {
                for (MobLearningType t : MobLearningType.values()) {
                    if (copyTypeData(sourcePath, targetPath, t)) {
                        pools.get(t).reload();
                        count++;
                    }
                }
            } else {
                if (copyTypeData(sourcePath, targetPath, type)) {
                    pools.get(type).reload();
                    count++;
                }
            }
        } catch (Exception e) {
            LearningMobs.LOGGER.error("Failed to load data from world {}", sourceWorldName, e);
            throw new RuntimeException("Failed to load data: " + e.getMessage());
        }

        return count;
    }

    private boolean copyTypeData(Path sourceRoot, Path targetRoot, MobLearningType type) throws Exception {
        Path sourceDir = sourceRoot.resolve(type.id());
        if (!Files.exists(sourceDir)) return false;

        Path targetDir = targetRoot.resolve(type.id());
        Files.createDirectories(targetDir);

        Path genJson = sourceDir.resolve("generation.json");
        Path netDat = sourceDir.resolve("networks.dat");

        if (Files.exists(genJson)) {
            Files.copy(genJson, targetDir.resolve("generation.json"), StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(netDat)) {
            Files.copy(netDat, targetDir.resolve("networks.dat"), StandardCopyOption.REPLACE_EXISTING);
        }

        return true;
    }

    private void attachFreshBrain(Mob mob, MobLearningType type) {
        MobLearningPool pool = pools.get(type);
        if (pool == null) return;

        GoalSnapshot backup = GoalBackup.capture(mob);
        // Create a completely random network instead of taking one from the pool
        NeuralNetwork network = NeuralNetwork.random(type.inputCount(), type.outputCount(), random);
        MobBrain brain = createBrain(type, network);
        
        if (brain != null) {
            activeBrains.put(mob.getUUID(), new ActiveBrain(mob, brain, backup));
            generationBrains.get(type).add(brain);
            
            TakeoverMode mode = getTakeoverMode(type);
            if (mode == TakeoverMode.FULL) {
                fullTakeover.put(mob.getUUID(), Boolean.TRUE);
                GoalBackup.clearGoals(mob);
                mob.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(mob));
            }
            
            // Re-add the goal if not present
            mob.goalSelector.addGoal(Config.LEARNING_GOAL_PRIORITY.getAsInt(), new LearningAssistGoal(mob, this, type));
            
            CompoundTag data = getCustomDataTag(mob);
            data.putString("learningmobs_type", type.id());
            data.putInt("learningmobs_generation", 0); // Mark as Gen 0/Fresh
            setCustomDataTag(mob, data);
        }
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

    public int enableFullTakeoverAll() {
        int count = 0;
        List<Mob> targets = new ArrayList<>();
        for (ActiveBrain active : activeBrains.values()) {
            targets.add(active.mob());
        }
        for (Mob mob : targets) {
            if (enableFullTakeover(mob)) {
                count++;
            }
        }
        return count;
    }

    public int enableFullTakeoverByType(MobLearningType type) {
        int count = 0;
        List<Mob> targets = new ArrayList<>();
        for (ActiveBrain active : activeBrains.values()) {
            if (active.brain().type() == type) {
                targets.add(active.mob());
            }
        }
        for (Mob mob : targets) {
            if (enableFullTakeover(mob)) {
                count++;
            }
        }
        return count;
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
                    new double[0], new double[0], new double[0], error);
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
                brain.network().weights(),
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
                allTimeBestFitness.getOrDefault(type, 0.0D),
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
            synchronized (brains) {
                for (MobBrain brain : brains) {
                    double fitness = brain.calculateFitness();
                    total += fitness;
                    best = Math.max(best, fitness);
                    count += 1;
                }
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
            double[] weights,
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
            double allTimeBestFitness,
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
        
        TakeoverMode mode = getTakeoverMode(type);
        if (mode == TakeoverMode.FULL) {
            fullTakeover.put(mob.getUUID(), Boolean.TRUE);
            GoalBackup.clearGoals(mob);
            mob.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(mob));
        }
        
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
