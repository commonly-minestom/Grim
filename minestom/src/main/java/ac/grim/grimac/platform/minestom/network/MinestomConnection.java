package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.exception.InvalidDisconnectPacketSend;
import com.github.retrooper.packetevents.exception.PacketProcessException;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.ExceptionUtil;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerDisconnect;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.extras.mojangAuth.MojangCrypt;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketRegistry;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.BufferedPacket;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.network.packet.server.FramedPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.zip.DataFormatException;

public final class MinestomConnection extends PlayerSocketConnection {
    private static final ListenerHandle<PlayerPacketOutEvent> OUTGOING = EventDispatcher.getHandle(PlayerPacketOutEvent.class);
    private static final Component INVALID_PACKET = Component.text("Invalid packet");
    private static final int ENCRYPT_MODE = 1;

    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<Object> mailbox = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean signaled = new AtomicBoolean();
    private final PacketScratch scratch = new PacketScratch();
    private final OutboundBuffer outbound = new OutboundBuffer();
    private volatile User user;
    private volatile boolean closed;

    public MinestomConnection(SocketChannel channel, SocketAddress remoteAddress, Thread readThread, Thread writeThread) {
        super(channel, remoteAddress, readThread, writeThread);
        this.user = new User(this, com.github.retrooper.packetevents.protocol.ConnectionState.HANDSHAKING, null, new UserProfile(null, null));
    }

    public User user() {
        return user;
    }

    void user(User user) {
        this.user = user;
    }

    @Override
    public void sendPacket(SendablePacket packet) {
        if (closed) return;
        mailbox.offer(packet);
        signal();
    }

    @Override
    public void sendPackets(Collection<SendablePacket> packets) {
        if (closed) return;
        for (SendablePacket packet : packets) {
            mailbox.offer(packet);
        }
        signal();
    }

    @Override
    public void setEncryptionKey(SecretKey secretKey) {
        super.setEncryptionKey(secretKey);
        Cipher cipher = MojangCrypt.getCipher(ENCRYPT_MODE, secretKey);
        lock.lock();
        try {
            outbound.encrypt(cipher);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void flushSync() throws IOException {
        if (flushPending()) return;
        signaled.set(false);
        if (flushPending()) return;
        if (isOnline()) LockSupport.park(this);
    }

    boolean flushPending() throws IOException {
        NetworkBuffer batch = drain();
        if (batch == null) return false;
        try {
            SocketChannel channel = getChannel();
            if (!channel.isConnected()) throw new EOFException("Channel is closed");
            boolean written;
            do {
                written = batch.writeChannel(channel);
            } while (!written);
            return true;
        } finally {
            PacketVanilla.PACKET_POOL.add(batch);
        }
    }

    void execute(Runnable task) {
        mailbox.offer(task);
        if (closed) {
            runPendingTasks();
            return;
        }
        signal();
    }

    void flush() {
        signal();
    }

    void write(ByteBuf buffer, boolean silent) {
        if (lock.isHeldByCurrentThread()) {
            transmit(buffer, silent);
            return;
        }
        execute(() -> transmit(buffer, silent));
    }

    void read(ByteBuf buffer, ListenerScope scope) {
        if (lock.isHeldByCurrentThread()) {
            inject(buffer, scope);
            return;
        }
        execute(() -> inject(buffer, scope));
    }

    <T> @Nullable T receive(int id, NetworkBuffer.Type<T> serializer, NetworkBuffer payload) {
        lock.lock();
        try {
            ByteBuf buffer = scratch.copy(id, payload);
            try {
                Verdict verdict = accept(buffer, ListenerScope.ALL);
                if (verdict == Verdict.DROP) return null;
                if (verdict == Verdict.REWRITE) {
                    ByteBufHelper.readVarInt(buffer);
                    payload.readIndex(payload.writeIndex());
                    return serializer.read(scratch.view(buffer));
                }
            } finally {
                scratch.recycle(buffer);
            }
        } finally {
            boolean pending = outbound.pending();
            lock.unlock();
            if (pending) signal();
        }
        return serializer.read(payload);
    }

    void close() {
        NetworkBuffer batch;
        lock.lock();
        try {
            closed = true;
            batch = outbound.take();
        } finally {
            lock.unlock();
        }
        if (batch != null) PacketVanilla.PACKET_POOL.add(batch);
        runPendingTasks();
        try {
            getChannel().close();
        } catch (IOException ignored) {
        }
        cleanup();
        PacketEventsImplHelper.handleDisconnection(this, user.getUUID());
    }

    private void signal() {
        if (signaled.compareAndSet(false, true)) LockSupport.unpark(writeThread());
    }

    private @Nullable NetworkBuffer drain() {
        Object item;
        while ((item = mailbox.poll()) != null) {
            if (process(item)) break;
        }
        lock.lock();
        try {
            return outbound.take();
        } finally {
            lock.unlock();
        }
    }

    private void runPendingTasks() {
        Object item;
        while ((item = mailbox.poll()) != null) {
            if (item instanceof Runnable task) run(task);
        }
    }

    private boolean process(Object item) {
        if (item instanceof Runnable task) return run(task);
        try {
            return switch ((SendablePacket) item) {
                case ServerPacket packet -> send(packet, null);
                case CachedPacket packet -> {
                    ConnectionState state = getServerState();
                    yield send(packet.packet(state), packet.body(state));
                }
                case FramedPacket packet -> send(packet.packet(), packet.body());
                case BufferedPacket packet -> send(packet);
            };
        } catch (Throwable throwable) {
            fail(throwable);
            return false;
        }
    }

    private boolean run(Runnable task) {
        lock.lock();
        try {
            task.run();
            return outbound.full();
        } catch (Throwable throwable) {
            MinecraftServer.getExceptionManager().handleException(throwable);
            return false;
        } finally {
            lock.unlock();
        }
    }

    private boolean send(ServerPacket packet, @Nullable NetworkBuffer frame) throws Exception {
        Player player = getPlayer();
        if (player != null) {
            if (OUTGOING.hasListener()) {
                PlayerPacketOutEvent event = new PlayerPacketOutEvent(player, packet);
                OUTGOING.call(event);
                if (event.isCancelled()) return false;
            }
            if (frame == null && ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION && packet instanceof ServerPacket.ComponentHolding holder) {
                packet = translate(holder, player);
            }
        }
        lock.lock();
        try {
            ConnectionState state = getServerState();
            PacketRegistry.PacketInfo<ServerPacket> info = registry(state).packetInfo(packet);
            ByteBuf buffer = scratch.encode(info.id(), info.serializer(), packet);
            try {
                ConnectionState next = PacketVanilla.nextServerState(packet, state);
                if (next != state) setServerState(next);
                transmit(buffer, false, frame, 0, frame == null ? 0 : frame.capacity());
                if (packet instanceof SetCompressionPacket) outbound.compress();
            } finally {
                scratch.recycle(buffer);
            }
            return outbound.full();
        } finally {
            lock.unlock();
        }
    }

    private boolean send(BufferedPacket packet) throws Exception {
        boolean compressed = MinecraftServer.getCompressionThreshold() > 0;
        NetworkBuffer frames = packet.buffer().slice(packet.index(), packet.length(), 0, packet.length());
        lock.lock();
        try {
            while (frames.readableBytes() > 0) {
                long frameStart = frames.readIndex();
                int length = frames.read(NetworkBuffer.VAR_INT);
                long frameEnd = frames.readIndex() + length;
                ByteBuf buffer = decode(frames, frameEnd, compressed);
                try {
                    frames.readIndex(frameEnd);
                    transmit(buffer, false, frames, frameStart, frameEnd - frameStart);
                } finally {
                    scratch.recycle(buffer);
                }
            }
            return outbound.full();
        } finally {
            lock.unlock();
        }
    }

    private ByteBuf decode(NetworkBuffer frames, long frameEnd, boolean compressed) throws DataFormatException {
        if (!compressed) {
            long start = frames.readIndex();
            return scratch.copy(frames, start, Math.toIntExact(frameEnd - start));
        }
        int size = frames.read(NetworkBuffer.VAR_INT);
        long start = frames.readIndex();
        if (size == 0) return scratch.copy(frames, start, Math.toIntExact(frameEnd - start));
        return scratch.inflate(frames, start, frameEnd - start, size);
    }

    private void transmit(ByteBuf buffer, boolean silent) {
        try {
            if (!closed) transmit(buffer, silent, null, 0, 0);
        } catch (Throwable throwable) {
            fail(throwable);
        } finally {
            buffer.release();
        }
    }

    private void transmit(ByteBuf buffer, boolean silent, @Nullable NetworkBuffer frame, long frameIndex, long frameLength) throws Exception {
        PacketSendEvent preVia = null;
        PacketSendEvent postVia = null;
        if (!silent) {
            User user = this.user;
            Player player = getPlayer();
            if (PacketEvents.getAPI().getSettings().isPreViaInjection()) {
                preVia = PacketEventsImplHelper.handleClientBoundPacket(this, user, player, buffer, false);
            }
            postVia = PacketEventsImplHelper.handleClientBoundPacket(this, user, player, buffer, true);
        }
        if (buffer.isReadable()) {
            boolean compressedFrame = MinecraftServer.getCompressionThreshold() > 0;
            if (frame != null && untouched(preVia) && untouched(postVia) && outbound.accepts(compressedFrame)) {
                outbound.copy(frame, frameIndex, frameLength);
            } else {
                outbound.frame(scratch.view(buffer));
            }
        }
        runTasksAfterSend(preVia);
        runTasksAfterSend(postVia);
    }

    private void inject(ByteBuf buffer, ListenerScope scope) {
        try {
            if (closed || accept(buffer, scope) == Verdict.DROP) return;
            int id = ByteBufHelper.readVarInt(buffer);
            ClientPacket packet = MinecraftServer.getPacketParser().parse(getClientState(), id, scratch.view(buffer));
            Player player = getPlayer();
            if (player == null) {
                MinecraftServer.getPacketListenerManager().processClientPacket(packet, this);
            } else {
                player.addPacketToQueue(packet);
            }
        } catch (Throwable throwable) {
            fail(throwable);
        } finally {
            buffer.release();
        }
    }

    private Verdict accept(ByteBuf buffer, ListenerScope scope) {
        if (scope == ListenerScope.NONE) return Verdict.KEEP;
        try {
            User user = this.user;
            Player player = getPlayer();
            boolean rewritten = false;
            if (scope == ListenerScope.ALL && PacketEvents.getAPI().getSettings().isPreViaInjection()) {
                PacketReceiveEvent event = PacketEventsImplHelper.handleServerBoundPacket(this, user, player, buffer, false);
                if (!buffer.isReadable()) return Verdict.DROP;
                rewritten = event != null && event.getLastUsedWrapper() != null;
            }
            PacketReceiveEvent event = PacketEventsImplHelper.handleServerBoundPacket(this, user, player, buffer, true);
            if (!buffer.isReadable()) return Verdict.DROP;
            return rewritten || (event != null && event.getLastUsedWrapper() != null) ? Verdict.REWRITE : Verdict.KEEP;
        } catch (Throwable throwable) {
            fail(throwable);
            return Verdict.DROP;
        }
    }

    private void fail(Throwable throwable) {
        if (ExceptionUtil.isException(throwable, InvalidDisconnectPacketSend.class)) return;
        if (!ExceptionUtil.isException(throwable, PacketProcessException.class)) {
            MinecraftServer.getExceptionManager().handleException(throwable);
            disconnect();
            return;
        }
        User user = this.user;
        PacketEventsSettings settings = PacketEvents.getAPI().getSettings();
        boolean handshaking = user.getDecoderState() == com.github.retrooper.packetevents.protocol.ConnectionState.HANDSHAKING;
        if (settings.isDebugEnabled() || !handshaking) {
            if (settings.isFullStackTraceEnabled()) {
                PacketEvents.getAPI().getLogManager().warn("An error occurred while processing a packet of "
                        + user.getName() + " (state: " + user.getDecoderState() + ", serverVersion: "
                        + PacketEvents.getAPI().getServerManager().getVersion().getReleaseName() + ")", throwable);
            } else {
                PacketEvents.getAPI().getLogManager().warn(throwable.getMessage());
            }
        }
        if (!settings.isKickOnPacketExceptionEnabled()) return;
        PacketWrapper<?> packet = switch (user.getEncoderState()) {
            case LOGIN -> new WrapperLoginServerDisconnect(INVALID_PACKET);
            case CONFIGURATION -> new WrapperConfigServerDisconnect(INVALID_PACKET);
            case PLAY -> new WrapperPlayServerDisconnect(INVALID_PACKET);
            default -> null;
        };
        if (packet != null) user.sendPacket(packet);
        disconnect();
        if (user.getName() != null) {
            PacketEvents.getAPI().getLogManager().warn("Disconnected " + user.getName() + " due to an invalid packet!");
        }
    }

    private static void runTasksAfterSend(@Nullable PacketSendEvent event) {
        if (event == null || !event.hasTasksAfterSend()) return;
        for (Runnable task : event.getTasksAfterSend()) {
            try {
                task.run();
            } catch (Throwable throwable) {
                MinecraftServer.getExceptionManager().handleException(throwable);
            }
        }
    }

    private static boolean untouched(@Nullable PacketSendEvent event) {
        return event == null || event.getLastUsedWrapper() == null;
    }

    private static ServerPacket translate(ServerPacket.ComponentHolding holder, Player player) {
        Locale locale = Objects.requireNonNullElseGet(player.getLocale(), MinestomAdventure::getDefaultLocale);
        return holder.copyWithOperator(translator(MinestomAdventure.COMPONENT_TRANSLATOR, locale));
    }

    private static <C> UnaryOperator<C> translator(BiFunction<C, Locale, C> translator, Locale locale) {
        return component -> translator.apply(component, locale);
    }

    @SuppressWarnings("unchecked")
    private static PacketRegistry<ServerPacket> registry(ConnectionState state) {
        return (PacketRegistry<ServerPacket>) PacketVanilla.SERVER_PACKET_PARSER.stateRegistry(state);
    }

    private enum Verdict {
        DROP,
        KEEP,
        REWRITE
    }
}
