package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.network.NetworkBuffer;

import java.util.zip.DataFormatException;

final class PacketScratch {
    private static final int INITIAL_CAPACITY = 256;
    private static final int RETAINED_CAPACITY = ServerFlag.POOLED_BUFFER_SIZE;
    private static final int MAX_VAR_INT_SIZE = 5;

    private ByteBuf buffer;
    private NetworkBuffer view;
    private byte[] viewArray;

    <T> ByteBuf encode(int id, NetworkBuffer.Type<T> serializer, T packet) {
        ByteBuf buffer = acquire(INITIAL_CAPACITY);
        try {
            return write(buffer, id, serializer, packet);
        } catch (IndexOutOfBoundsException exception) {
            long size = serializer.sizeOf(packet, MinecraftServer.getRegistries());
            if (size > ServerFlag.MAX_PACKET_SIZE) {
                throw new IllegalStateException("Packet too large: " + size);
            }
            buffer.capacity(Math.toIntExact(size) + MAX_VAR_INT_SIZE);
            return write(buffer, id, serializer, packet);
        }
    }

    ByteBuf copy(int id, NetworkBuffer payload) {
        int length = Math.toIntExact(payload.readableBytes());
        ByteBuf buffer = acquire(length + MAX_VAR_INT_SIZE);
        ByteBufHelper.writeVarInt(buffer, id);
        int index = buffer.writerIndex();
        payload.copyTo(payload.readIndex(), buffer.array(), buffer.arrayOffset() + index, length);
        return buffer.writerIndex(index + length);
    }

    ByteBuf copy(NetworkBuffer source, long index, int length) {
        ByteBuf buffer = acquire(length);
        source.copyTo(index, buffer.array(), buffer.arrayOffset(), length);
        return buffer.writerIndex(length);
    }

    ByteBuf inflate(NetworkBuffer source, long index, long length, int size) throws DataFormatException {
        ByteBuf buffer = acquire(size);
        long written = source.decompress(index, length, view(buffer).clear());
        if (written != size) {
            throw new DataFormatException("Decompressed length mismatch: expected " + size + ", got " + written);
        }
        return buffer.writerIndex(size);
    }

    NetworkBuffer view(ByteBuf buffer) {
        if (!buffer.hasArray()) {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            return NetworkBuffer.wrap(bytes, 0, bytes.length, MinecraftServer.getRegistries());
        }
        int offset = buffer.arrayOffset();
        if (buffer != this.buffer) {
            return NetworkBuffer.wrap(buffer.array(), offset + buffer.readerIndex(), offset + buffer.writerIndex(), MinecraftServer.getRegistries());
        }
        byte[] array = buffer.array();
        NetworkBuffer view = this.view;
        if (view == null || array != this.viewArray) {
            view = NetworkBuffer.wrap(array, 0, 0, MinecraftServer.getRegistries());
            this.view = view;
            this.viewArray = array;
        }
        return view.index(offset + buffer.readerIndex(), offset + buffer.writerIndex());
    }

    void recycle(ByteBuf buffer) {
        if (buffer.refCnt() == 1 && buffer.capacity() <= RETAINED_CAPACITY) return;
        buffer.release();
        this.buffer = null;
        this.view = null;
        this.viewArray = null;
    }

    private <T> ByteBuf write(ByteBuf buffer, int id, NetworkBuffer.Type<T> serializer, T packet) {
        NetworkBuffer view = view(buffer).clear();
        view.write(NetworkBuffer.VAR_INT, id);
        view.write(serializer, packet);
        return buffer.setIndex(0, Math.toIntExact(view.writeIndex()));
    }

    private ByteBuf acquire(int capacity) {
        ByteBuf buffer = this.buffer;
        if (buffer == null) {
            buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(Math.max(capacity, INITIAL_CAPACITY));
            this.buffer = buffer;
        }
        buffer.clear();
        if (buffer.capacity() < capacity) buffer.capacity(capacity);
        return buffer;
    }
}
