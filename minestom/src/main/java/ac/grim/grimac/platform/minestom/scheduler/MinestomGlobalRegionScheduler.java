package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.platform.api.scheduler.GlobalRegionScheduler;
import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import lombok.RequiredArgsConstructor;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
final class MinestomGlobalRegionScheduler implements GlobalRegionScheduler {
    private final TaskRegistry registry;
    private final TickScheduler ticks;

    @Override
    public void execute(@NotNull GrimPlugin plugin, @NotNull Runnable task) {
        run(plugin, task);
    }

    @Override
    public TaskHandle run(@NotNull GrimPlugin plugin, @NotNull Runnable task) {
        return runDelayed(plugin, task, 0);
    }

    @Override
    public TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull Runnable task, long delay) {
        return ticks.run(MinecraftServer.getSchedulerManager(), plugin, task, delay);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return ticks.repeat(MinecraftServer.getSchedulerManager(), plugin, task, initialDelayTicks, periodTicks);
    }

    @Override
    public void cancel(@NotNull GrimPlugin plugin) {
        registry.cancel(plugin);
    }
}
