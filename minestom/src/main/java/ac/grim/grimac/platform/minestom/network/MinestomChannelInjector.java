package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class MinestomChannelInjector implements ChannelInjector {
    private final ConnectionTransport transport;

    @Override
    public void inject() {
        transport.install();
    }

    @Override
    public void uninject() {
        transport.uninstall();
    }

    @Override
    public void updateUser(Object channel, User user) {
        ((PacketChannel) channel).user(user);
    }

    @Override
    public void setPlayer(Object channel, Object player) {
    }

    @Override
    public boolean isPlayerSet(Object channel) {
        return channel instanceof PacketChannel packetChannel && packetChannel.connection().getPlayer() != null;
    }

    @Override
    public boolean isProxy() {
        return false;
    }
}
