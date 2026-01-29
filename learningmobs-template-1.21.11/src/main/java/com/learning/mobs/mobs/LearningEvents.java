package com.learning.mobs.mobs;

import java.util.Locale;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
        event.getDispatcher().register(Commands.literal("learningmobs")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("reset")
                        .then(Commands.argument("mob", EntityArgument.entity())
                                .executes(context -> {
                                    Entity entity = EntityArgument.getEntity(context, "mob");
                                    if (!(entity instanceof Mob mob)) {
                                        context.getSource().sendFailure(Component.literal("Target is not a mob."));
                                        return 0;
                                    }
                                    manager.resetBrain(mob);
                                    context.getSource()
                                            .sendSuccess(() -> Component.literal("Reset learning brain for " + mob.getName().getString()),
                                                    true);
                                    return 1;
                                })))
                .then(Commands.literal("status")
                        .then(Commands.argument("mob", EntityArgument.entity())
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
                                            () -> Component.literal("Last inputs: " + formatArray(status.lastInputs())),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Last outputs: " + formatArray(status.lastOutputs())),
                                            false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Brain errors: " + (status.lastError() == null ? "none" : status.lastError())),
                                            false);
                                    return 1;
                                })))
                .then(Commands.literal("full")
                        .then(Commands.literal("takeover")
                                .then(Commands.argument("mob", EntityArgument.entity())
                                        .executes(context -> {
                                            Entity entity = EntityArgument.getEntity(context, "mob");
                                            if (!(entity instanceof Mob mob)) {
                                                context.getSource().sendFailure(Component.literal("Target is not a mob."));
                                                return 0;
                                            }
                                            boolean enabled = manager.enableFullTakeover(mob);
                                            if (!enabled) {
                                                context.getSource().sendFailure(Component.literal("Mob is not tracked or learning is disabled."));
                                                return 0;
                                            }
                                            context.getSource()
                                                    .sendSuccess(() -> Component.literal(
                                                            "Learning brain set to full takeover for " + mob.getName().getString()),
                                                            true);
                                            return 1;
                                        }))))
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
        Entity source = event.getSource().getEntity();
        manager.onKill(victim, source);
    }

    private static String formatArray(double[] values) {
        if (values == null || values.length == 0) {
            return "[]";
        }
        int length = Math.min(values.length, 8);
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(String.format(Locale.ROOT, "%.2f", values[i]));
        }
        if (values.length > 8) {
            builder.append(", ...");
        }
        builder.append("]");
        return builder.toString();
    }
}
