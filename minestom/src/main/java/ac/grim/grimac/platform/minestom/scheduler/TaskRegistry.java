package ac.grim.grimac.platform.minestom.scheduler;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.utils.anticheat.LogUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TaskRegistry {
    private final Map<MinestomTaskHandle, GrimPlugin> tasks = new ConcurrentHashMap<>();

    MinestomTaskHandle register(GrimPlugin plugin, boolean sync) {
        MinestomTaskHandle handle = new MinestomTaskHandle(this, sync);
        tasks.put(handle, plugin);
        return handle;
    }

    Runnable guard(MinestomTaskHandle handle, Runnable task, boolean repeating) {
        return () -> {
            try {
                task.run();
            } catch (Throwable throwable) {
                LogUtil.error("Error executing scheduled task", throwable);
            } finally {
                if (!repeating) handle.complete();
            }
        };
    }

    void remove(MinestomTaskHandle handle) {
        tasks.remove(handle);
    }

    void cancel(GrimPlugin plugin) {
        for (Map.Entry<MinestomTaskHandle, GrimPlugin> entry : tasks.entrySet()) {
            if (entry.getValue().equals(plugin)) entry.getKey().cancel();
        }
    }

    void cancelAll() {
        for (MinestomTaskHandle handle : tasks.keySet()) {
            handle.cancel();
        }
    }
}
