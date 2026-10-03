package ac.grim.grimac.platform.minestom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.extras.mojangAuth.MojangCrypt;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketReading;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.PacketWriting;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientKeepAlivePacket;
import net.minestom.server.network.packet.client.common.ClientPongPacket;
import net.minestom.server.network.packet.client.common.ClientSettingsPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientEncryptionResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientChunkBatchReceivedPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerLoadedPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.client.play.ClientTickEndPacket;
import net.minestom.server.network.packet.client.status.StatusRequestPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.KeepAlivePacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.login.EncryptionRequestPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.packet.server.play.ChunkBatchFinishedPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.network.player.ClientSettings;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.zip.DataFormatException;

final class TestClient implements AutoCloseable {
    private static final int BUFFER_SIZE = 1 << 16;
    private static final int MIN_FREE_SPACE = 1 << 12;
    private static final int TICK_MILLIS = 50;
    private static final int POSITION_REMINDER_TICKS = 20;
    private static final float CHUNKS_PER_TICK = 20;
    private static final int SECRET_LENGTH = 16;
    private static final String KEY_EXCHANGE_ALGORITHM = "RSA";
    private static final String SECRET_ALGORITHM = "AES";

    private final SocketChannel channel;
    private final NetworkBuffer inbound = NetworkBuffer.resizableBuffer(BUFFER_SIZE, MinecraftServer.getRegistries());
    private final BlockingQueue<ServerPacket> packets = new LinkedBlockingQueue<>();
    private final AtomicInteger pongs = new AtomicInteger();
    private final AtomicInteger teleports = new AtomicInteger();
    private final Object writeLock = new Object();
    private ConnectionState readState;
    private ConnectionState writeState = ConnectionState.HANDSHAKE;
    private volatile boolean compressed;
    private volatile Cipher encryption;
    private volatile Cipher decryption;
    private volatile boolean ticking;
    private volatile Pos position;
    private volatile UUID uuid;
    private int ticks;

    private TestClient(SocketChannel channel, ConnectionState readState) {
        this.channel = channel;
        this.readState = readState;
    }

    static TestClient status(InetSocketAddress address) throws IOException {
        TestClient client = open(address, ClientHandshakePacket.Intent.STATUS, ConnectionState.STATUS);
        client.send(new StatusRequestPacket());
        return client;
    }

    static TestClient login(InetSocketAddress address, String username) throws IOException {
        TestClient client = open(address, ClientHandshakePacket.Intent.LOGIN, ConnectionState.LOGIN);
        client.send(new ClientLoginStartPacket(username, UUID.randomUUID()));
        return client;
    }

    UUID uuid() {
        return uuid;
    }

    Pos position() {
        return position;
    }

    int pongs() {
        return pongs.get();
    }

    int teleports() {
        return teleports.get();
    }

    boolean encrypted() {
        return encryption != null;
    }

    void clearReceived() {
        packets.clear();
    }

    void move(Pos position, boolean onGround) {
        synchronized (writeLock) {
            this.position = position;
            send(new ClientPlayerPositionPacket(position, onGround, false));
        }
    }

    void rotate(float yaw, float pitch) {
        synchronized (writeLock) {
            this.position = position.withView(yaw, pitch);
            send(new ClientPlayerRotationPacket(yaw, pitch, true, false));
        }
    }

    void send(ClientPacket packet) {
        synchronized (writeLock) {
            NetworkBuffer buffer = NetworkBuffer.resizableBuffer(MinecraftServer.getRegistries());
            PacketWriting.writeFramedPacket(buffer, writeState, packet, compressed ? MinecraftServer.getCompressionThreshold() : 0);
            writeState = PacketVanilla.nextClientState(packet, writeState);
            Cipher cipher = encryption;
            if (cipher != null) buffer.cipher(cipher, 0, buffer.writeIndex());
            try {
                boolean written;
                do {
                    written = buffer.writeChannel(channel);
                } while (!written);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    <T extends ServerPacket> T await(Class<T> type, long timeout, TimeUnit unit) throws InterruptedException {
        return await(type, packet -> true, timeout, unit);
    }

    <T extends ServerPacket> T await(Class<T> type, Predicate<T> filter, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return null;
            ServerPacket packet = packets.poll(remaining, TimeUnit.NANOSECONDS);
            if (type.isInstance(packet) && filter.test(type.cast(packet))) return type.cast(packet);
        }
    }

    @Override
    public void close() throws IOException {
        ticking = false;
        channel.close();
    }

    private static TestClient open(InetSocketAddress address, ClientHandshakePacket.Intent intent, ConnectionState state) throws IOException {
        SocketChannel channel = SocketChannel.open(address);
        TestClient client = new TestClient(channel, state);
        client.send(new ClientHandshakePacket(MinecraftServer.PROTOCOL_VERSION, address.getHostString(), address.getPort(), intent));
        Thread.ofVirtual().name("Test-Client-Reader").start(client::read);
        return client;
    }

    private void read() {
        try {
            while (channel.isOpen()) {
                if (inbound.writableBytes() < MIN_FREE_SPACE) inbound.resize(inbound.capacity() * 2);
                long start = inbound.writeIndex();
                inbound.readChannel(channel);
                Cipher cipher = decryption;
                if (cipher != null) inbound.cipher(cipher, start, inbound.writeIndex() - start);
                parse();
                inbound.compact();
            }
        } catch (IOException | DataFormatException ignored) {
        }
    }

    private void parse() throws DataFormatException {
        while (PacketReading.readServer(inbound, readState, compressed) instanceof PacketReading.Result.Success<ServerPacket> success) {
            PacketReading.ParsedPacket<ServerPacket> parsed = success.packets().getFirst();
            readState = parsed.nextState();
            handle(parsed.packet());
        }
    }

    private void handle(ServerPacket packet) {
        switch (packet) {
            case EncryptionRequestPacket request -> encrypt(request);
            case SetCompressionPacket ignored -> compressed = true;
            case LoginSuccessPacket success -> {
                uuid = success.gameProfile().uuid();
                send(new ClientLoginAcknowledgedPacket());
                send(new ClientSettingsPacket(ClientSettings.DEFAULT));
            }
            case SelectKnownPacksPacket packs -> send(new ClientSelectKnownPacksPacket(packs.entries()));
            case FinishConfigurationPacket ignored -> send(new ClientFinishConfigurationPacket());
            case KeepAlivePacket keepAlive -> send(new ClientKeepAlivePacket(keepAlive.id()));
            case PingPacket ping -> {
                send(new ClientPongPacket(ping.id()));
                pongs.incrementAndGet();
            }
            case ChunkBatchFinishedPacket ignored -> send(new ClientChunkBatchReceivedPacket(CHUNKS_PER_TICK));
            case PlayerPositionAndLookPacket teleport -> teleport(teleport);
            default -> {
            }
        }
        packets.add(packet);
    }

    private void encrypt(EncryptionRequestPacket request) {
        try {
            PublicKey publicKey = KeyFactory.getInstance(KEY_EXCHANGE_ALGORITHM).generatePublic(new X509EncodedKeySpec(request.publicKey()));
            Cipher keyExchange = Cipher.getInstance(KEY_EXCHANGE_ALGORITHM);
            keyExchange.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] secret = new byte[SECRET_LENGTH];
            new SecureRandom().nextBytes(secret);
            SecretKey key = new SecretKeySpec(secret, SECRET_ALGORITHM);
            synchronized (writeLock) {
                send(new ClientEncryptionResponsePacket(keyExchange.doFinal(secret), keyExchange.doFinal(request.verifyToken())));
                encryption = MojangCrypt.getCipher(Cipher.ENCRYPT_MODE, key);
            }
            decryption = MojangCrypt.getCipher(Cipher.DECRYPT_MODE, key);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void teleport(PlayerPositionAndLookPacket teleport) {
        synchronized (writeLock) {
            Pos target = target(teleport);
            this.position = target;
            send(new ClientTeleportConfirmPacket(teleport.teleportId()));
            send(new ClientPlayerPositionAndRotationPacket(target, true, false));
        }
        teleports.incrementAndGet();
        if (ticking) return;
        ticking = true;
        send(new ClientPlayerLoadedPacket());
        Thread.ofVirtual().name("Test-Client-Ticker").start(this::tick);
    }

    private Pos target(PlayerPositionAndLookPacket teleport) {
        Pos current = position == null ? Pos.ZERO : position;
        int flags = teleport.flags();
        return new Pos(
                teleport.position().x() + relative(flags, RelativeFlags.X, current.x()),
                teleport.position().y() + relative(flags, RelativeFlags.Y, current.y()),
                teleport.position().z() + relative(flags, RelativeFlags.Z, current.z()),
                teleport.yaw() + (float) relative(flags, RelativeFlags.YAW, current.yaw()),
                teleport.pitch() + (float) relative(flags, RelativeFlags.PITCH, current.pitch())
        );
    }

    private void tick() {
        try {
            while (ticking && channel.isOpen()) {
                Thread.sleep(TICK_MILLIS);
                synchronized (writeLock) {
                    if (++ticks % POSITION_REMINDER_TICKS == 0) {
                        send(new ClientPlayerPositionPacket(position, true, false));
                    }
                    send(new ClientTickEndPacket());
                }
            }
        } catch (InterruptedException | IllegalStateException ignored) {
        }
    }

    private static double relative(int flags, int flag, double current) {
        return (flags & flag) == 0 ? 0 : current;
    }
}
