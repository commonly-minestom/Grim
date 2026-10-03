package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.protocol.player.User;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.network.player.PlayerSocketConnection;

final class MinestomChannelInjector implements ChannelInjector {
    private static final String UNTRACKED_CONNECTION = "The server is still starting, please reconnect";

    private final MinestomConnectionServer server = new MinestomConnectionServer();
    private final EventListener<AsyncPlayerConfigurationEvent> guard = EventListener.of(AsyncPlayerConfigurationEvent.class, MinestomChannelInjector::rejectUntracked);

    @Override
    public void inject() {
        MinecraftServer.getGlobalEventHandler().addListener(guard);
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> server.replace(MinecraftServer.getServer()));
    }

    @Override
    public void uninject() {
        MinecraftServer.getGlobalEventHandler().removeListener(guard);
        server.stop();
    }

    @Override
    public void updateUser(Object channel, User user) {
        ((MinestomConnection) channel).user(user);
    }

    @Override
    public void setPlayer(Object channel, Object player) {
    }

    @Override
    public boolean isPlayerSet(Object channel) {
        return channel instanceof MinestomConnection connection && connection.getPlayer() != null;
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    private static void rejectUntracked(AsyncPlayerConfigurationEvent event) {
        Player player = event.getPlayer();
        if (player.getPlayerConnection() instanceof PlayerSocketConnection connection && !(connection instanceof MinestomConnection)) {
            player.kick(UNTRACKED_CONNECTION);
        }
    }
}
