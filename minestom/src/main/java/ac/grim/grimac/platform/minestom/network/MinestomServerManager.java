package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.mappings.GlobalRegistryHolder;
import io.github.retrooper.packetevents.impl.netty.manager.server.ServerManagerAbstract;
import net.minestom.server.MinecraftServer;

final class MinestomServerManager extends ServerManagerAbstract {
    private final ServerVersion version = resolveVersion();

    @Override
    public ServerVersion getVersion() {
        return version;
    }

    @Override
    public Object getRegistryCacheKey(User user, ClientVersion version) {
        return GlobalRegistryHolder.getGlobalRegistryCacheKey(user, version);
    }

    private static ServerVersion resolveVersion() {
        ServerVersion[] versions = ServerVersion.reversedValues();
        for (ServerVersion version : versions) {
            if (version.getReleaseName().equals(MinecraftServer.VERSION_NAME)) return version;
        }
        for (ServerVersion version : versions) {
            if (version.getProtocolVersion() == MinecraftServer.PROTOCOL_VERSION) return version;
        }
        throw new IllegalStateException("PacketEvents does not support Minecraft " + MinecraftServer.VERSION_NAME
                + " (protocol " + MinecraftServer.PROTOCOL_VERSION + ")");
    }
}
