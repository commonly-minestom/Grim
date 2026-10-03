package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufAllocationOperator;
import com.github.retrooper.packetevents.netty.buffer.ByteBufOperator;
import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import io.github.retrooper.packetevents.impl.netty.buffer.ByteBufAllocationOperatorImpl;
import io.github.retrooper.packetevents.impl.netty.buffer.ByteBufOperatorImpl;
import lombok.Getter;

@Getter
final class MinestomNettyManager implements NettyManager {
    private final ChannelOperator channelOperator = new MinestomChannelOperator();
    private final ByteBufOperator byteBufOperator = new ByteBufOperatorImpl();
    private final ByteBufAllocationOperator byteBufAllocationOperator = new ByteBufAllocationOperatorImpl();
}
