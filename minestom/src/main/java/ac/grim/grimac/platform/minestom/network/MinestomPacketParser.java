package ac.grim.grimac.platform.minestom.network;

import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketParser;
import net.minestom.server.network.packet.PacketRegistry;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

final class MinestomPacketParser {
    static final ScopedValue<MinestomConnection> CONNECTION = ScopedValue.newInstance();

    private MinestomPacketParser() {
    }

    static PacketParser.Client wrap(PacketParser.Client parser) {
        return new PacketParser.Client(
                new Registry<>(parser.handshake()),
                new Registry<>(parser.status()),
                new Registry<>(parser.login()),
                new Registry<>(parser.configuration()),
                new Registry<>(parser.play())
        );
    }

    private static final class Registry<T extends ClientPacket> implements PacketRegistry<T> {
        private final PacketRegistry<T> delegate;
        private final PacketInfo<T>[] packets;

        @SuppressWarnings("unchecked")
        private Registry(PacketRegistry<T> delegate) {
            this.delegate = delegate;
            int size = 0;
            for (PacketInfo<? extends T> ignored : delegate) {
                size++;
            }
            PacketInfo<T>[] packets = new PacketInfo[size];
            for (PacketInfo<? extends T> info : delegate) {
                PacketInfo<T> packet = (PacketInfo<T>) info;
                packets[packet.id()] = new PacketInfo<>(packet.packetClass(), packet.id(), new Serializer<>(packet));
            }
            this.packets = packets;
        }

        @Override
        public T create(int packetId, NetworkBuffer reader) {
            return delegate.create(packetId, reader);
        }

        @Override
        public PacketInfo<T> packetInfo(Class<?> packetClass) {
            return delegate.packetInfo(packetClass);
        }

        @Override
        public PacketInfo<T> packetInfo(int packetId) {
            if (packetId < 0 || packetId >= packets.length) return delegate.packetInfo(packetId);
            return packets[packetId];
        }

        @Override
        public ConnectionState state() {
            return delegate.state();
        }

        @Override
        public ConnectionSide side() {
            return delegate.side();
        }

        @Override
        public Iterator<PacketInfo<? extends T>> iterator() {
            return delegate.iterator();
        }
    }

    private record Serializer<T>(PacketRegistry.PacketInfo<T> packet) implements NetworkBuffer.Type<T> {
        @Override
        public void write(NetworkBuffer buffer, T value) {
            packet.serializer().write(buffer, value);
        }

        @Override
        public T read(NetworkBuffer buffer) {
            if (!CONNECTION.isBound()) return packet.serializer().read(buffer);
            return CONNECTION.get().receive(packet.id(), packet.serializer(), buffer);
        }

        @Override
        public long sizeOf(T value, @Nullable Registries registries) {
            return packet.serializer().sizeOf(value, registries);
        }
    }
}
