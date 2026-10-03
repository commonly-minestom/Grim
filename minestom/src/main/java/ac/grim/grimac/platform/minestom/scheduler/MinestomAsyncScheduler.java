package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.platform.api.scheduler.AsyncScheduler;
import ac.grim.grimac.platform.api.scheduler.PlatformScheduler;
import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class MinestomAsyncScheduler implements AsyncScheduler {
    private static final int MIN_THREADS = 4;

    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(
            Math.max(MIN_THREADS, Runtime.getRuntime().availableProcessors() / 2),
            Thread.ofPlatform().daemon().name("Grim-Async-", 1).factory(),
            new ThreadPoolExecutor.DiscardPolicy()
    );
    private final TaskRegistry registry;

    MinestomAsyncScheduler(TaskRegistry registry) {
        this.registry = registry;
    }

    @Override
    public TaskHandle runNow(@NotNull GrimPlugin plugin, @NotNull Runnable task) {
        MinestomTaskHandle handle = registry.register(plugin, false);
        return bind(handle, executor.submit(registry.guard(handle, task, false)));
    }

    @Override
    public TaskHandle runDelayed(@NotNull GrimPlugin plugin, @NotNull Runnable task, long delay, @NotNull TimeUnit timeUnit) {
        MinestomTaskHandle handle = registry.register(plugin, false);
        return bind(handle, executor.schedule(registry.guard(handle, task, false), delay, timeUnit));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Runnable task, long delay, long period, @NotNull TimeUnit timeUnit) {
        MinestomTaskHandle handle = registry.register(plugin, false);
        return bind(handle, executor.scheduleAtFixedRate(registry.guard(handle, task, true), delay, period, timeUnit));
    }

    @Override
    public TaskHandle runAtFixedRate(@NotNull GrimPlugin plugin, @NotNull Runnable task, long initialDelayTicks, long periodTicks) {
        return runAtFixedRate(plugin, task,
                PlatformScheduler.convertTicksToTime(initialDelayTicks, TimeUnit.MILLISECONDS),
                PlatformScheduler.convertTicksToTime(periodTicks, TimeUnit.MILLISECONDS),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void cancel(@NotNull GrimPlugin plugin) {
        registry.cancel(plugin);
    }

    void schedule(Runnable task, long delay, TimeUnit timeUnit) {
        executor.schedule(task, delay, timeUnit);
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private static TaskHandle bind(MinestomTaskHandle handle, Future<?> future) {
        handle.bind(() -> future.cancel(false));
        return handle;
    }
}
