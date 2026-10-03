package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import lombok.RequiredArgsConstructor;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

@RequiredArgsConstructor
final class TickScheduler {
    private final TaskRegistry registry;

    TaskHandle run(Scheduler scheduler, GrimPlugin plugin, Runnable task, long delayTicks) {
        return schedule(scheduler, plugin, task, delayTicks, TaskSchedule.stop(), false);
    }

    TaskHandle repeat(Scheduler scheduler, GrimPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return schedule(scheduler, plugin, task, delayTicks, ticks(periodTicks), true);
    }

    private TaskHandle schedule(Scheduler scheduler, GrimPlugin plugin, Runnable task, long delayTicks, TaskSchedule repeat, boolean repeating) {
        MinestomTaskHandle handle = registry.register(plugin, true);
        Task scheduled = scheduler.buildTask(registry.guard(handle, task, repeating))
                .delay(ticks(delayTicks))
                .repeat(repeat)
                .schedule();
        handle.bind(scheduled::cancel);
        return handle;
    }

    private static TaskSchedule ticks(long ticks) {
        return ticks <= 0 ? TaskSchedule.immediate() : TaskSchedule.tick(Math.toIntExact(ticks));
    }
}
