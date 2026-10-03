package ac.grim.grimac.platform.minestom.initables;

import ac.grim.grimac.manager.init.start.StartableInitable;
import ac.grim.grimac.manager.init.stop.StoppableInitable;
import ac.grim.grimac.platform.minestom.MinestomPlatformServer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.server.ServerTickMonitorEvent;

public final class MinestomTickMonitor implements StartableInitable, StoppableInitable {
    private final EventListener<ServerTickMonitorEvent> listener;

    public MinestomTickMonitor(MinestomPlatformServer server) {
        this.listener = EventListener.of(ServerTickMonitorEvent.class, server::recordTick);
    }

    @Override
    public void start() {
        MinecraftServer.getGlobalEventHandler().addListener(listener);
    }

    @Override
    public void stop() {
        MinecraftServer.getGlobalEventHandler().removeListener(listener);
    }
}
