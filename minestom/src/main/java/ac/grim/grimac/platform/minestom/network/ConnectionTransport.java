package ac.grim.grimac.platform.minestom.network;

import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.Nullable;

/**
 * The way player connections are made to run their packets through PacketEvents.
 */
interface ConnectionTransport {
    void install();

    void uninstall();

    @Nullable PacketChannel channel(PlayerConnection connection);

    static ConnectionTransport detect() {
        return new SocketTransport();
    }
}
