package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.platform.api.entity.GrimEntity;
import ac.grim.grimac.platform.api.scheduler.EntityScheduler;
import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import lombok.RequiredArgsConstructor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.timer.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
final class MinestomEntityScheduler implements EntityScheduler {
    private final TickScheduler ticks;

    @Override
    public void execute(@NotNull GrimEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable run, @Nullable Runnable retired, long delay) {
        runDelayed(entity, plugin, run, retired, delay);
    }

    @Override
    public TaskHandle run(@NotNull GrimEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired) {
        return runDelayed(entity, plugin, task, retired, 0);
    }

    @Override
    public TaskHandle runDelayed(@NotNull GrimEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return ticks.run(scheduler(entity), plugin, bound(entity, task, retired), delayTicks);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimEntity entity, @NotNull GrimPlugin plugin, @NotNull Runnable task, @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        return ticks.repeat(scheduler(entity), plugin, bound(entity, task, retired), initialDelayTicks, periodTicks);
    }

    private static Scheduler scheduler(GrimEntity entity) {
        return entity.getNative() instanceof Entity nativeEntity && !nativeEntity.isRemoved()
                ? nativeEntity.scheduler()
                : MinecraftServer.getSchedulerManager();
    }

    private static Runnable bound(GrimEntity entity, Runnable task, @Nullable Runnable retired) {
        if (retired == null) return task;
        return () -> {
            if (entity.isDead()) {
                retired.run();
            } else {
                task.run();
            }
        };
    }
}
