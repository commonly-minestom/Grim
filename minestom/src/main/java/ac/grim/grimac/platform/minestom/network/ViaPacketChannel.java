package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import com.viaversion.minestom.network.connection.ViaPlayerConnection;
import com.viaversion.minestom.network.intercept.PacketInterceptor;
import com.viaversion.minestom.network.intercept.PacketSink;
import com.viaversion.minestom.network.intercept.PacketStage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import java.net.SocketAddress;

/**
 * The channel of a player connected through ViaMinestom.
 * <p>
 * ViaMinestom shows every packet twice, in the version of the client and in the version of the server, which
 * are the two places PacketEvents listens at on a Netty pipeline with ViaVersion in it. Everything happens on
 * the event loop of the connection, so no locking is needed to keep the listeners in order.
 */
final class ViaPacketChannel implements PacketChannel, PacketInterceptor {
    private final PacketScratch scratch = new PacketScratch();
    private final ViaPlayerConnection connection;
    private volatile User user;
    private boolean scratchInUse;

    ViaPacketChannel(ViaPlayerConnection connection) {
        this.connection = connection;
        this.user = new User(this, ConnectionState.HANDSHAKING, null, new UserProfile(null, null));

        UserConnectEvent event = new UserConnectEvent(user);
        PacketEvents.getAPI().getEventManager().callEvent(event);
        if (event.isCancelled()) {
            connection.disconnect();
            return;
        }
        PacketEvents.getAPI().getProtocolManager().setUser(this, user);
    }

    @Override
    public PlayerSocketConnection connection() {
        return connection;
    }

    @Override
    public User user() {
        return user;
    }

    @Override
    public void user(User user) {
        this.user = user;
    }

    @Override
    public @Nullable SocketAddress localAddress() {
        return connection.getLocalAddress();
    }

    @Override
    public void execute(Runnable task) {
        connection.execute(task);
    }

    @Override
    public void flush() {
        connection.flush();
    }

    @Override
    public void write(ByteBuf buffer, boolean silent) {
        try {
            if (!connection.isOnline()) return;
            NetworkBuffer packet = scratch.view(buffer);
            if (silent) {
                connection.write(PacketStage.SERVER, packet);
            } else {
                connection.write(packet);
            }
        } finally {
            buffer.release();
        }
    }

    @Override
    public void read(ByteBuf buffer, ListenerScope scope) {
        try {
            if (!connection.isOnline()) return;
            NetworkBuffer packet = scratch.view(buffer);
            switch (scope) {
                case ALL -> connection.read(packet);
                case POST_VIA -> connection.read(PacketStage.CLIENT, packet);
                case NONE -> connection.read(PacketStage.SERVER, packet);
            }
        } finally {
            buffer.release();
        }
    }

    @Override
    public @Nullable NetworkBuffer inbound(PacketStage stage, NetworkBuffer packet) {
        if (skips(stage)) return packet;

        ByteBuf buffer = acquire(packet);
        try {
            boolean translated = stage == PacketStage.SERVER;
            boolean handshake = translated && user.getDecoderState() == ConnectionState.HANDSHAKING;
            PacketReceiveEvent event = PacketEventsImplHelper.handleServerBoundPacket(this, user, connection.getPlayer(), buffer, translated);
            // The handshake names the version of the server by the time it gets here
            if (handshake) user.setClientVersion(ClientVersion.getById(connection.clientVersion().getVersion()));
            if (!buffer.isReadable()) return null;
            return event != null && event.getLastUsedWrapper() != null ? detach(buffer) : packet;
        } catch (Throwable throwable) {
            PacketListeners.fail(this, throwable);
            return null;
        } finally {
            release(buffer);
        }
    }

    @Override
    public void outbound(PacketStage stage, NetworkBuffer packet, PacketSink sink) {
        if (skips(stage)) {
            sink.write(packet);
            return;
        }

        ByteBuf buffer = acquire(packet);
        try {
            PacketSendEvent event = PacketEventsImplHelper.handleClientBoundPacket(this, user, connection.getPlayer(), buffer, stage == PacketStage.SERVER);
            if (buffer.isReadable()) {
                sink.write(PacketListeners.untouched(event) ? packet : scratch.view(buffer));
            }
            PacketListeners.runTasksAfterSend(event);
        } catch (Throwable throwable) {
            PacketListeners.fail(this, throwable);
        } finally {
            release(buffer);
        }
    }

    @Override
    public void close() {
        PacketEventsImplHelper.handleDisconnection(this, user.getUUID());
    }

    private static boolean skips(PacketStage stage) {
        return stage == PacketStage.CLIENT && !PacketEvents.getAPI().getSettings().isPreViaInjection();
    }

    /**
     * Listeners can send and receive packets of their own, in which case the scratch buffer is already taken.
     */
    private ByteBuf acquire(NetworkBuffer packet) {
        int length = Math.toIntExact(packet.readableBytes());
        if (!scratchInUse) {
            scratchInUse = true;
            return scratch.copy(packet, packet.readIndex(), length);
        }
        ByteBuf buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(length);
        packet.copyTo(packet.readIndex(), buffer.array(), buffer.arrayOffset(), length);
        return buffer.writerIndex(length);
    }

    private void release(ByteBuf buffer) {
        if (scratch.owns(buffer)) {
            scratch.recycle(buffer);
            scratchInUse = false;
        } else {
            buffer.release();
        }
    }

    private static NetworkBuffer detach(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return NetworkBuffer.wrap(bytes, 0, bytes.length, MinecraftServer.getRegistries());
    }
}
