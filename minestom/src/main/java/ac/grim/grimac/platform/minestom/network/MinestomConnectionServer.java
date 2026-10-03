package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.network.packet.PacketParser;
import net.minestom.server.network.socket.Server;

import java.io.EOFException;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

final class MinestomConnectionServer {
    private static final int INET4_ADDRESS_LENGTH = 4;
    private static final Duration BIND_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration BIND_RETRY = Duration.ofMillis(10);

    private final PacketParser.Client parser = MinestomPacketParser.wrap(MinecraftServer.getPacketParser());
    private volatile ServerSocketChannel serverSocket;
    private volatile boolean stopped;

    void replace(Server server) {
        SocketAddress address = boundAddress(server);
        server.stop();
        Thread.ofVirtual().name("Grim-Socket-Server").start(() -> listen(address));
    }

    void stop() {
        this.stopped = true;
        ServerSocketChannel serverSocket = this.serverSocket;
        if (serverSocket == null) return;
        try {
            serverSocket.close();
        } catch (IOException exception) {
            MinecraftServer.getExceptionManager().handleException(exception);
        }
    }

    private void listen(SocketAddress address) {
        try (ServerSocketChannel serverSocket = bind(address)) {
            this.serverSocket = serverSocket;
            accept(serverSocket);
        } catch (IOException | InterruptedException exception) {
            MinecraftServer.getExceptionManager().handleException(new IllegalStateException("Failed to bind " + address, exception));
        }
    }

    private ServerSocketChannel bind(SocketAddress address) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + BIND_TIMEOUT.toNanos();
        while (true) {
            ServerSocketChannel serverSocket = ServerSocketChannel.open(family(address));
            try {
                serverSocket.bind(address);
                return serverSocket;
            } catch (BindException exception) {
                serverSocket.close();
                if (stopped || System.nanoTime() > deadline) throw exception;
                Thread.sleep(BIND_RETRY);
            }
        }
    }

    private void accept(ServerSocketChannel serverSocket) {
        Thread.Builder readBuilder = Thread.ofVirtual().name("Grim-Socket-Reader-", 0);
        Thread.Builder writeBuilder = Thread.ofVirtual().name("Grim-Socket-Writer-", 0);
        while (!stopped) {
            SocketChannel client;
            try {
                client = serverSocket.accept();
            } catch (ClosedChannelException exception) {
                break;
            } catch (IOException exception) {
                MinecraftServer.getExceptionManager().handleException(exception);
                continue;
            }
            try {
                configure(client);
                AtomicReference<MinestomConnection> reference = new AtomicReference<>();
                Thread readThread = readBuilder.unstarted(() -> read(reference.get()));
                Thread writeThread = writeBuilder.unstarted(() -> write(reference.get()));
                MinestomConnection connection = new MinestomConnection(client, client.getRemoteAddress(), readThread, writeThread);
                reference.set(connection);
                if (!register(connection)) {
                    client.close();
                    continue;
                }
                readThread.start();
                writeThread.start();
            } catch (IOException exception) {
                close(client);
            }
        }
    }

    private boolean register(MinestomConnection connection) {
        UserConnectEvent event = new UserConnectEvent(connection.user());
        PacketEvents.getAPI().getEventManager().callEvent(event);
        if (event.isCancelled()) return false;
        PacketEvents.getAPI().getProtocolManager().setUser(connection, connection.user());
        return true;
    }

    private void read(MinestomConnection connection) {
        ScopedValue.where(MinestomPacketParser.CONNECTION, connection).run(() -> {
            while (connection.isOnline()) {
                try {
                    connection.read(parser);
                } catch (ClosedChannelException | EOFException exception) {
                    connection.disconnect();
                    break;
                } catch (Throwable throwable) {
                    boolean reset = throwable instanceof SocketException && "Connection reset".equals(throwable.getMessage());
                    if (!reset) MinecraftServer.getExceptionManager().handleException(throwable);
                    connection.disconnect();
                    break;
                }
            }
        });
    }

    private void write(MinestomConnection connection) {
        try {
            while (connection.isOnline()) {
                try {
                    connection.flushSync();
                } catch (ClosedChannelException | EOFException exception) {
                    connection.disconnect();
                } catch (Throwable throwable) {
                    boolean broken = throwable instanceof IOException && "Broken pipe".equals(throwable.getMessage());
                    if (!broken) MinecraftServer.getExceptionManager().handleException(throwable);
                    connection.disconnect();
                }
            }
            try {
                boolean pending;
                do {
                    pending = connection.flushPending();
                } while (pending);
            } catch (IOException ignored) {
            }
        } finally {
            connection.close();
        }
    }

    private static SocketAddress boundAddress(Server server) {
        SocketAddress address = server.socketAddress();
        if (address instanceof InetSocketAddress inet && inet.getPort() == 0) {
            return new InetSocketAddress(inet.getAddress(), server.getPort());
        }
        return address;
    }

    private static ProtocolFamily family(SocketAddress address) {
        return switch (address) {
            case InetSocketAddress inet -> inet.getAddress().getAddress().length == INET4_ADDRESS_LENGTH
                    ? StandardProtocolFamily.INET
                    : StandardProtocolFamily.INET6;
            case UnixDomainSocketAddress ignored -> StandardProtocolFamily.UNIX;
            default -> throw new IllegalArgumentException("Unsupported address: " + address);
        };
    }

    private static void configure(SocketChannel channel) throws IOException {
        if (!(channel.getLocalAddress() instanceof InetSocketAddress)) return;
        Socket socket = channel.socket();
        socket.setSendBufferSize(ServerFlag.SOCKET_SEND_BUFFER_SIZE);
        socket.setReceiveBufferSize(ServerFlag.SOCKET_RECEIVE_BUFFER_SIZE);
        socket.setTcpNoDelay(ServerFlag.SOCKET_NO_DELAY);
        socket.setSoTimeout(ServerFlag.SOCKET_TIMEOUT);
    }

    private static void close(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
