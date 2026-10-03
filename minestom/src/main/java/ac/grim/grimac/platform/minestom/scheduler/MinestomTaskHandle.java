package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.platform.api.scheduler.TaskHandle;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MinestomTaskHandle implements TaskHandle {
    private final TaskRegistry registry;
    @Getter
    private final boolean sync;
    @Getter
    private volatile boolean cancelled;
    private volatile Runnable cancellation;

    void bind(Runnable cancellation) {
        this.cancellation = cancellation;
        if (cancelled) cancellation.run();
    }

    void complete() {
        registry.remove(this);
    }

    @Override
    public void cancel() {
        cancelled = true;
        registry.remove(this);
        Runnable cancellation = this.cancellation;
        if (cancellation != null) cancellation.run();
    }
}
