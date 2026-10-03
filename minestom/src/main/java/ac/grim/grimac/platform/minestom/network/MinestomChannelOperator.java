package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;

final class MinestomChannelOperator implements ChannelOperator {
    private static final String PRE_VIA_PREFIX = "pre-";

    @Override
    public SocketAddress remoteAddress(Object channel) {
        return connection(channel).getRemoteAddress();
    }

    @Override
    public SocketAddress localAddress(Object channel) {
        try {
            return connection(channel).getChannel().getLocalAddress();
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public boolean isOpen(Object channel) {
        return connection(channel).isOnline();
    }

    @Override
    public Object close(Object channel) {
        connection(channel).disconnect();
        return null;
    }

    @Override
    public Object write(Object channel, Object buffer) {
        connection(channel).write((ByteBuf) buffer, false);
        return null;
    }

    @Override
    public Object flush(Object channel) {
        connection(channel).flush();
        return null;
    }

    @Override
    public Object writeAndFlush(Object channel, Object buffer) {
        return write(channel, buffer);
    }

    @Override
    public Object fireChannelRead(Object channel, Object buffer) {
        connection(channel).read((ByteBuf) buffer, ListenerScope.ALL);
        return null;
    }

    @Override
    public Object writeInContext(Object channel, String ctx, Object buffer) {
        connection(channel).write((ByteBuf) buffer, true);
        return null;
    }

    @Override
    public Object flushInContext(Object channel, String ctx) {
        return flush(channel);
    }

    @Override
    public Object writeAndFlushInContext(Object channel, String ctx, Object buffer) {
        return writeInContext(channel, ctx, buffer);
    }

    @Override
    public Object fireChannelReadInContext(Object channel, String ctx, Object buffer) {
        boolean preVia = ctx.equals(PRE_VIA_PREFIX + PacketEvents.DECODER_NAME);
        connection(channel).read((ByteBuf) buffer, preVia ? ListenerScope.POST_VIA : ListenerScope.NONE);
        return null;
    }

    @Override
    public List<String> pipelineHandlerNames(Object channel) {
        return List.of(PacketEvents.DECODER_NAME, PacketEvents.ENCODER_NAME);
    }

    @Override
    public Object getPipelineHandler(Object channel, String name) {
        return pipelineHandlerNames(channel).contains(name) ? channel : null;
    }

    @Override
    public Object getPipelineContext(Object channel, String name) {
        return getPipelineHandler(channel, name);
    }

    @Override
    public Object getPipeline(Object channel) {
        return channel;
    }

    @Override
    public void runInEventLoop(Object channel, Runnable runnable) {
        connection(channel).execute(runnable);
    }

    @Override
    public Object pooledByteBuf(Object channel) {
        return UnpooledByteBufAllocator.DEFAULT.heapBuffer();
    }

    private static MinestomConnection connection(Object channel) {
        return (MinestomConnection) channel;
    }
}
