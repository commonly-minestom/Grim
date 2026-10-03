package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.platform.api.scheduler.RegionScheduler;
import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import ac.grim.grimac.platform.api.world.PlatformWorld;
import ac.grim.grimac.platform.minestom.world.MinestomPlatformWorld;
import ac.grim.grimac.utils.math.Location;
import lombok.RequiredArgsConstructor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Scheduler;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
final class MinestomRegionScheduler implements RegionScheduler {
    private final TickScheduler ticks;

    @Override
    public void execute(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task) {
        run(plugin, world, chunkX, chunkZ, task);
    }

    @Override
    public void execute(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task) {
        run(plugin, location, task);
    }

    @Override
    public TaskHandle run(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task) {
        return runDelayed(plugin, world, chunkX, chunkZ, task, 0);
    }

    @Override
    public TaskHandle run(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task) {
        return runDelayed(plugin, location, task, 0);
    }

    @Override
    public TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task, long delayTicks) {
        return ticks.run(scheduler(world), plugin, task, delayTicks);
    }

    @Override
    public TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return ticks.run(scheduler(location.getWorld()), plugin, task, delayTicks);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull PlatformWorld world, int chunkX, int chunkZ, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return ticks.repeat(scheduler(world), plugin, task, initialDelayTicks, periodTicks);
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Location location, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return ticks.repeat(scheduler(location.getWorld()), plugin, task, initialDelayTicks, periodTicks);
    }

    private static Scheduler scheduler(PlatformWorld world) {
        return world instanceof MinestomPlatformWorld(Instance instance) && instance != null && instance.isRegistered()
                ? instance.scheduler()
                : MinecraftServer.getSchedulerManager();
    }
}
