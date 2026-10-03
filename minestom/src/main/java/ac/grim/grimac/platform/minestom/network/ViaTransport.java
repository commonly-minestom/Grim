package ac.grim.grimac.platform.minestom.network;

import com.viaversion.minestom.network.connection.ViaPlayerConnection;
import com.viaversion.minestom.network.intercept.PacketInterceptorFactory;
import com.viaversion.minestom.network.intercept.PacketInterceptors;
import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.Nullable;

/**
 * Leaves the connections to ViaMinestom and listens in on them once their packets are in the server version.
 */
final class ViaTransport implements ConnectionTransport {
    static final String PROVIDER_CLASS = "com.viaversion.minestom.network.intercept.PacketInterceptors";

    private final PacketInterceptorFactory factory = ViaPacketChannel::new;

    @Override
    public void install() {
        PacketInterceptors.register(factory);
    }

    @Override
    public void uninstall() {
        PacketInterceptors.unregister(factory);
    }

    @Override
    public @Nullable PacketChannel channel(PlayerConnection connection) {
        return connection instanceof ViaPlayerConnection via ? via.interceptor(ViaPacketChannel.class) : null;
    }
}
