package ac.grim.grimac.platform.minestom.network;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

/**
 * Takes over the socket acceptor of Minestom so that every client is given a {@link MinestomConnection}.
 */
final class SocketTransport implements ConnectionTransport {
    private static final String UNTRACKED_CONNECTION = "The server is still starting, please reconnect";

    private final MinestomConnectionServer server = new MinestomConnectionServer();
    private final EventListener<AsyncPlayerConfigurationEvent> guard = EventListener.of(AsyncPlayerConfigurationEvent.class, SocketTransport::rejectUntracked);

    @Override
    public void install() {
        MinecraftServer.getGlobalEventHandler().addListener(guard);
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> server.replace(MinecraftServer.getServer()));
    }

    @Override
    public void uninstall() {
        MinecraftServer.getGlobalEventHandler().removeListener(guard);
        server.stop();
    }

    @Override
    public @Nullable PacketChannel channel(PlayerConnection connection) {
        return connection instanceof MinestomConnection channel ? channel : null;
    }

    private static void rejectUntracked(AsyncPlayerConfigurationEvent event) {
        Player player = event.getPlayer();
        if (player.getPlayerConnection() instanceof PlayerSocketConnection connection && !(connection instanceof MinestomConnection)) {
            player.kick(UNTRACKED_CONNECTION);
        }
    }
}
