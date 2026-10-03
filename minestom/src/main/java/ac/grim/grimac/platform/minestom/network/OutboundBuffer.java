package ac.grim.grimac.platform.minestom.network;

import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.PacketWriting;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;

final class OutboundBuffer {
    private static final int FRAME_HEADER_SIZE = 32;

    private static final NetworkBuffer.Type<NetworkBuffer> BODY = new NetworkBuffer.Type<>() {
        @Override
        public void write(NetworkBuffer buffer, NetworkBuffer value) {
            long length = value.readableBytes();
            NetworkBuffer.copy(value, value.readIndex(), buffer, buffer.advanceWrite(length), length);
        }

        @Override
        public NetworkBuffer read(NetworkBuffer buffer) {
            throw new UnsupportedOperationException();
        }
    };

    private @Nullable NetworkBuffer buffer;
    private @Nullable Cipher cipher;
    private boolean compressed;

    void frame(NetworkBuffer packet) {
        long length = packet.readableBytes();
        if (length > ServerFlag.MAX_PACKET_SIZE) {
            throw new IllegalStateException("Packet too large: " + length);
        }
        NetworkBuffer buffer = reserve(length + (length >> 10) + FRAME_HEADER_SIZE);
        long start = buffer.writeIndex();
        int id = packet.read(NetworkBuffer.VAR_INT);
        PacketWriting.writeFramedPacket(buffer, BODY, id, packet, compressed ? MinecraftServer.getCompressionThreshold() : 0);
        encrypt(buffer, start);
    }

    void copy(NetworkBuffer frame, long index, long length) {
        NetworkBuffer buffer = reserve(length);
        long start = buffer.advanceWrite(length);
        NetworkBuffer.copy(frame, index, buffer, start, length);
        encrypt(buffer, start);
    }

    boolean accepts(boolean compressedFrame) {
        return compressed == compressedFrame;
    }

    void compress() {
        this.compressed = true;
    }

    void encrypt(Cipher cipher) {
        this.cipher = cipher;
    }

    boolean pending() {
        return buffer != null;
    }

    boolean full() {
        NetworkBuffer buffer = this.buffer;
        return buffer != null && buffer.writeIndex() >= ServerFlag.POOLED_BUFFER_SIZE;
    }

    @Nullable NetworkBuffer take() {
        NetworkBuffer buffer = this.buffer;
        this.buffer = null;
        return buffer;
    }

    private NetworkBuffer reserve(long length) {
        NetworkBuffer buffer = this.buffer;
        if (buffer == null) {
            buffer = PacketVanilla.PACKET_POOL.get();
            this.buffer = buffer;
        }
        if (buffer.writableBytes() < length) {
            buffer.resize(Math.max(buffer.capacity() * 2, buffer.writeIndex() + length));
        }
        return buffer;
    }

    private void encrypt(NetworkBuffer buffer, long start) {
        Cipher cipher = this.cipher;
        if (cipher != null) buffer.cipher(cipher, start, buffer.writeIndex() - start);
    }
}
