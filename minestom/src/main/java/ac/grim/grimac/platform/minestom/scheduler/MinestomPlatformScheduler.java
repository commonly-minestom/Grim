package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.platform.api.scheduler.PlatformScheduler;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class MinestomPlatformScheduler implements PlatformScheduler {
    private final TaskRegistry registry = new TaskRegistry();
    private final TickScheduler ticks = new TickScheduler(registry);
    @Getter
    private final @NotNull MinestomAsyncScheduler asyncScheduler = new MinestomAsyncScheduler(registry);
    @Getter
    private final @NotNull MinestomGlobalRegionScheduler globalRegionScheduler = new MinestomGlobalRegionScheduler(registry, ticks);
    @Getter
    private final @NotNull MinestomEntityScheduler entityScheduler = new MinestomEntityScheduler(ticks);
    @Getter
    private final @NotNull MinestomRegionScheduler regionScheduler = new MinestomRegionScheduler(ticks);

    @Override
    public void runOnConnection(@NotNull Object channel, @NotNull Runnable task, long delay, @NotNull TimeUnit timeUnit) {
        asyncScheduler.schedule(() -> ChannelHelper.runInEventLoop(channel, task), delay, timeUnit);
    }

    public void shutdown() {
        registry.cancelAll();
        asyncScheduler.shutdown();
    }
}
