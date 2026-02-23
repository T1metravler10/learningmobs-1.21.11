package com.learning.mobs.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import com.learning.mobs.util.LearningIOMap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public class LearningEvents {
    private final LearningManager manager;

    public LearningEvents(LearningManager manager) {
        this.manager = manager;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        manager.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        manager.onServerStopped(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SuggestionProvider<CommandSourceStack> brainMobSuggestions = (context, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Mob mob : manager.getActiveMobs()) {
                String uuidStr = mob.getUUID().toString();
                String name = mob.getName().getString();
                MobLearningType type = MobLearningType.fromEntity(mob);
                String typeId = type != null ? type.id() : "unknown";

                // Use @e[uuid=...] as the actual suggestion value for EntityArgument
                String suggestionValue = "@e[uuid=" + uuidStr + "]";

                if (uuidStr.toLowerCase(Locale.ROOT).contains(remaining) || name.toLowerCase(Locale.ROOT).contains(remaining) || typeId.contains(remaining) || suggestionValue.contains(remaining)) {
                    builder.suggest(suggestionValue, Component.literal(name + " (" + typeId + ") at " + mob.blockPosition().toShortString()));
                }
            }
            return builder.buildFuture();
        };

        LiteralArgumentBuilder<CommandSourceStack> resetCommand = Commands.literal("reset")
                .then(Commands.literal("all").executes(context -> {
                    int count = manager.resetAllBrains();
                    context.getSource().sendSuccess(() -> Component.literal("Reset all " + count + " learning mobs to a fresh state."), true);
                    return count;
                }));

        for (MobLearningType type : MobLearningType.values()) {
            resetCommand.then(Commands.literal(type.id()).executes(context -> {
                int count = manager.resetBrainsByType(type);
                context.getSource().sendSuccess(() -> Component.literal("Reset all " + count + " " + type.id() + "s to a fresh state."), true);
                return count;
            }));
        }

        resetCommand.then(Commands.argument("mob", EntityArgument.entity())
                .suggests(brainMobSuggestions)
                .executes(context -> {
                    Entity entity = EntityArgument.getEntity(context, "mob");
                    if (!(entity instanceof Mob mob)) {
                        context.getSource().sendFailure(Component.literal("Target is not a mob."));
                        return 0;
                    }
                    MobLearningType type = MobLearningType.fromEntity(mob);
                    manager.resetBrain(mob);
                    context.getSource().sendSuccess(() -> Component.literal("Reset " + mob.getName().getString() + " (" + (type != null ? type.id() : "unknown") + ") to a fresh state. They are now relearning from scratch."), true);
                    return 1;
                }));

        LiteralArgumentBuilder<CommandSourceStack> fulltakeoverCommand = Commands.literal("fulltakeover");
        for (TakeoverMode mode : TakeoverMode.values()) {
            String modeName = mode.name().toLowerCase(Locale.ROOT);
            // Capitalize first letter: Learn, Vanilla, Full
            modeName = modeName.substring(0, 1).toUpperCase(Locale.ROOT) + modeName.substring(1);
            
            final TakeoverMode finalMode = mode;
            final String finalModeName = modeName;
            LiteralArgumentBuilder<CommandSourceStack> modeSub = Commands.literal(modeName);
            
            // Mode <Mob Type>
            for (MobLearningType type : MobLearningType.values()) {
                modeSub.then(Commands.literal(type.id()).executes(context -> {
                    int count = manager.setTakeoverMode(type, finalMode);
                    context.getSource().sendSuccess(() -> Component.literal("Set " + type.id() + " takeover mode to " + finalModeName + " (" + count + " mobs affected)."), true);
                    return count;
                }));
            }
            
            // Mode all
            modeSub.then(Commands.literal("all").executes(context -> {
                int count = manager.setTakeoverModeAll(finalMode);
                context.getSource().sendSuccess(() -> Component.literal("Set all learning mobs takeover mode to " + finalModeName + " (" + count + " mobs affected)."), true);
                return count;
            }));

            fulltakeoverCommand.then(modeSub);
        }

        event.getDispatcher().register(Commands.literal("learningmobs")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(resetCommand)
                .then(Commands.literal("status")
                        .then(Commands.argument("mob", EntityArgument.entity())
                                .suggests(brainMobSuggestions)
                                .executes(context -> {
                                    Entity entity = EntityArgument.getEntity(context, "mob");
                                    if (!(entity instanceof Mob mob)) {
                                        context.getSource().sendFailure(Component.literal("Target is not a mob."));
                                        return 0;
                                    }
                                    LearningManager.BrainStatus status = manager.getStatus(mob);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Learning status for " + mob.getName().getString()),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Brain: " + (status.hasBrain() ? "active" : "none")),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Type: " + (status.type() == null ? "untracked" : status.type().id())),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Generation: " + status.generation() + " (pool " + status.poolGeneration() + ")"),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Full takeover: " + (status.fullTakeover() ? "yes" : "no")),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(String.format(Locale.ROOT,
                                                    "Fitness: %.2f | Ticks: %d | Kills: %d",
                                                    status.fitness(), status.ticksAlive(), status.kills())),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(String.format(Locale.ROOT,
                                                    "Damage dealt: %.2f | Damage taken: %.2f",
                                                    status.damageDealt(), status.damageTaken())),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Inputs 0-9: "
                                                    + com.learning.mobs.util.LearningIOMap.formatRange(status.lastInputs(), com.learning.mobs.util.LearningIOMap.INPUT_LABELS, 0, 10)),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Inputs 10-19: "
                                                    + com.learning.mobs.util.LearningIOMap.formatRange(status.lastInputs(), com.learning.mobs.util.LearningIOMap.INPUT_LABELS, 10, 20)),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Inputs 20-29: "
                                                    + com.learning.mobs.util.LearningIOMap.formatRange(status.lastInputs(), com.learning.mobs.util.LearningIOMap.INPUT_LABELS, 20, 30)),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Inputs 30-39: "
                                                    + com.learning.mobs.util.LearningIOMap.formatRange(status.lastInputs(), com.learning.mobs.util.LearningIOMap.INPUT_LABELS, 30, 40)),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Outputs 0-6: "
                                                    + com.learning.mobs.util.LearningIOMap.formatRange(status.lastOutputs(), com.learning.mobs.util.LearningIOMap.OUTPUT_LABELS, 0, 7)),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Outputs 7-12: "
                                                    + com.learning.mobs.util.LearningIOMap.formatRange(status.lastOutputs(), com.learning.mobs.util.LearningIOMap.OUTPUT_LABELS, 7, 13)),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Brain errors: " + (status.lastError() == null ? "none" : status.lastError())),
                                            false);
                                    return 1;
                                })))
                .then(fulltakeoverCommand)
                .then(Commands.literal("data")
                        .then(Commands.literal("save")
                                .then(Commands.literal("all").executes(context -> {
                                    manager.saveData(null, context.getSource().getServer());
                                    context.getSource().sendSuccess(() -> Component.literal("Saved all AI data to world folder."), true);
                                    return 1;
                                }))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(MobLearningType.values()).map(MobLearningType::id), builder))
                                        .executes(context -> {
                                            String typeId = StringArgumentType.getString(context, "type");
                                            MobLearningType type = java.util.Arrays.stream(MobLearningType.values())
                                                    .filter(t -> t.id().equals(typeId)).findFirst().orElse(null);
                                            if (type == null) {
                                                context.getSource().sendFailure(Component.literal("Unknown mob type: " + typeId));
                                                return 0;
                                            }
                                            manager.saveData(type, context.getSource().getServer());
                                            context.getSource().sendSuccess(() -> Component.literal("Saved " + typeId + " AI data to world folder."), true);
                                            return 1;
                                        })))
                        .then(Commands.argument("worldName", StringArgumentType.string())
                                .then(Commands.literal("load")
                                        .then(Commands.literal("all").executes(context -> {
                                            String world = StringArgumentType.getString(context, "worldName");
                                            try {
                                                int count = manager.loadData(world, null, context.getSource().getServer());
                                                manager.resetAllBrains(true);
                                                context.getSource().sendSuccess(() -> Component.literal("Loaded " + count + " AI types from world '" + world + "'. Active mobs have been reset to apply new brains."), true);
                                                return count;
                                            } catch (Exception e) {
                                                context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                                                return 0;
                                            }
                                        }))
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("type", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        java.util.Arrays.stream(MobLearningType.values()).map(MobLearningType::id), builder))
                                                .executes(context -> {
                                                    String world = StringArgumentType.getString(context, "worldName");
                                                    String typeId = StringArgumentType.getString(context, "type");
                                                    MobLearningType type = java.util.Arrays.stream(MobLearningType.values())
                                                            .filter(t -> t.id().equals(typeId)).findFirst().orElse(null);
                                                    if (type == null) {
                                                        context.getSource().sendFailure(Component.literal("Unknown mob type: " + typeId));
                                                        return 0;
                                                    }
                                                    try {
                                                        manager.loadData(world, type, context.getSource().getServer());
                                                        manager.resetBrainsByType(type, true);
                                                        context.getSource().sendSuccess(() -> Component.literal("Loaded " + typeId + " AI from world '" + world + "'. Active mobs of this type have been reset."), true);
                                                        return 1;
                                                    } catch (Exception e) {
                                                        context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                                                        return 0;
                                                    }
                                                })))))
                .then(Commands.literal("countdown")
                        .executes(context -> {
                            long ticks = manager.getTicksUntilRollover(context.getSource().getServer());
                            if (ticks < 0) {
                                context.getSource().sendFailure(Component.literal("Learning manager not initialized."));
                                return 0;
                            }
                            String time = com.learning.mobs.util.BrainMath.formatTime(ticks);
                            Component message = Component.literal("Time until rollover: " + time);
                            
                            Entity entity = context.getSource().getEntity();
                            if (entity instanceof ServerPlayer player) {
                                player.sendSystemMessage(message, true);
                            } else {
                                context.getSource().sendSuccess(() -> message, false);
                            }
                            
                            return 1;
                        }))
                .then(Commands.literal("test")
                        .then(Commands.literal("summon")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(context -> {
                                            ServerLevel level = context.getSource().getLevel();
                                            Vec3 pos = Vec3Argument.getVec3(context, "pos");
                                            int spawned = manager.summonTestMobs(level, pos);
                                            if (spawned == 0) {
                                                context.getSource().sendFailure(Component.literal("No test mobs spawned (brain error or disabled)."));
                                                return 0;
                                            }
                                            context.getSource()
                                                    .sendSuccess(() -> Component.literal("Spawned " + spawned + " test mobs with brains."),
                                                            true);
                                            return 1;
                                        })))
                        .then(Commands.literal("explode")
                                .executes(context -> {
                                    ServerLevel level = context.getSource().getLevel();
                                    int exploded = manager.triggerTestExplode(level);
                                    if (exploded == 0) {
                                        context.getSource().sendFailure(Component.literal("No creepers with functioning brains found."));
                                        return 0;
                                    }
                                    context.getSource()
                                            .sendSuccess(() -> Component.literal("Triggered " + exploded + " brain creeper explosions."),
                                                    true);
                                    return 1;
                                })))
                .then(Commands.literal("ui")
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("UI is client-side. Use the debug keybind to open it."));
                            return 0;
                        })));
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob) {
            manager.onMobSpawn(mob);
        }
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Mob mob) {
            manager.onMobDespawn(mob);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        manager.onServerTick(server);
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity victim = event.getEntity();
        Entity source = event.getSource().getEntity();
        manager.onDamage(victim, source, event.getNewDamage());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Mob mob) {
            MobBrain brain = manager.getBrain(mob);
            if (brain instanceof CreeperBrain creeperBrain) {
                creeperBrain.setDied();
            }
        }
        Entity source = event.getSource().getEntity();
        manager.onKill(victim, source);
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity exploder = event.getExplosion().getDirectSourceEntity();
        if (exploder == null) {
            exploder = event.getExplosion().getIndirectSourceEntity();
        }
        if (exploder instanceof net.minecraft.world.entity.monster.Creeper creeper) {
            MobBrain brain = manager.getBrain(creeper);
            if (brain instanceof CreeperBrain creeperBrain) {
                creeperBrain.setExploded();
            }
        }
    }

    // formatArray removed in favor of labeled IO map
}
