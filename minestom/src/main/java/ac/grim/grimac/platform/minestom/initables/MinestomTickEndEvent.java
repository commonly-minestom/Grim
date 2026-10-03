package ac.grim.grimac.platform.minestom.initables;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.manager.init.start.AbstractTickEndEvent;
import ac.grim.grimac.manager.init.stop.StoppableInitable;
import ac.grim.grimac.player.GrimPlayer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.server.ServerTickMonitorEvent;

public final class MinestomTickEndEvent extends AbstractTickEndEvent implements StoppableInitable {
    private final EventListener<ServerTickMonitorEvent> listener = EventListener.of(ServerTickMonitorEvent.class, event -> tickAllPlayers());

    @Override
    public void start() {
        if (!shouldInjectEndTick()) return;
        MinecraftServer.getGlobalEventHandler().addListener(listener);
    }

    @Override
    public void stop() {
        MinecraftServer.getGlobalEventHandler().removeListener(listener);
    }

    private void tickAllPlayers() {
        for (GrimPlayer player : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
            if (player.disableGrim) continue;
            onEndOfTick(player, true);
        }
    }
}
