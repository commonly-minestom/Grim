package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.protocol.player.User;
import io.netty.buffer.ByteBuf;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;

/**
 * What PacketEvents treats as the channel of a player, whichever way the connection reaches the server.
 */
interface PacketChannel {
    PlayerSocketConnection connection();

    User user();

    void user(User user);

    @Nullable SocketAddress localAddress();

    /**
     * Runs a task in order with the outgoing packets of the connection.
     */
    void execute(Runnable task);

    void flush();

    void write(ByteBuf buffer, boolean silent);

    void read(ByteBuf buffer, ListenerScope scope);
}
