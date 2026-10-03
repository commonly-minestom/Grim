package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class MinestomProtocolManager extends ProtocolManagerAbstract {
    private final Map<UUID, Object> channels = new ConcurrentHashMap<>();
    private final Map<Object, User> users = new ConcurrentHashMap<>();

    @Override
    public ProtocolVersion getPlatformVersion() {
        return ProtocolVersion.UNKNOWN;
    }

    @Override
    public Collection<User> getUsers() {
        return users.values();
    }

    @Override
    public Collection<Object> getChannels() {
        return channels.values();
    }

    @Override
    public User getUser(Object channel) {
        return channel == null ? null : users.get(channel);
    }

    @Override
    public User removeUser(Object channel) {
        return users.remove(channel);
    }

    @Override
    public void setUser(Object channel, User user) {
        users.put(channel, user);
        ((PacketChannel) channel).user(user);
    }

    @Override
    public Object getChannel(UUID uuid) {
        return channels.get(uuid);
    }

    @Override
    public void setChannel(UUID uuid, Object channel) {
        channels.put(uuid, channel);
    }

    @Override
    public void removeChannel(Object channel) {
        channels.values().remove(channel);
    }

    @Override
    public void removeChannelById(UUID uuid) {
        channels.remove(uuid);
    }

    @Override
    public boolean hasChannel(Object channel) {
        return channels.containsValue(channel);
    }
}
