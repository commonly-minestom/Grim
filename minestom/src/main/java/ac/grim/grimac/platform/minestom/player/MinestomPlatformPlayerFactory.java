package ac.grim.grimac.platform.minestom.player;

import ac.grim.grimac.platform.api.player.AbstractPlatformPlayerFactory;
import ac.grim.grimac.platform.api.player.OfflinePlatformPlayer;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import lombok.RequiredArgsConstructor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

@RequiredArgsConstructor
public final class MinestomPlatformPlayerFactory extends AbstractPlatformPlayerFactory<Player> {
    private static final String OFFLINE_UUID_PREFIX = "OfflinePlayer:";

    private final MinestomSenderFactory senderFactory;

    @Override
    protected Player getNativePlayer(@NotNull UUID uuid) {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
    }

    @Override
    protected Player getNativePlayer(@NotNull String name) {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name);
    }

    @Override
    protected PlatformPlayer createPlatformPlayer(@NotNull Player nativePlayer) {
        return new MinestomPlatformPlayer(nativePlayer, senderFactory);
    }

    @Override
    protected UUID getPlayerUUID(@NotNull Player nativePlayer) {
        return nativePlayer.getUuid();
    }

    @Override
    protected Collection<Player> getNativeOnlinePlayers() {
        return MinecraftServer.getConnectionManager().getOnlinePlayers();
    }

    @Override
    public OfflinePlatformPlayer getOfflineFromUUID(@NotNull UUID uuid) {
        PlatformPlayer online = getFromUUID(uuid);
        return online != null ? online : new MinestomOfflinePlatformPlayer(uuid, "");
    }

    @Override
    public OfflinePlatformPlayer getOfflineFromName(@NotNull String name) {
        PlatformPlayer online = getFromName(name);
        if (online != null) return online;
        UUID uuid = UUID.nameUUIDFromBytes((OFFLINE_UUID_PREFIX + name).getBytes(StandardCharsets.UTF_8));
        return new MinestomOfflinePlatformPlayer(uuid, name);
    }

    @Override
    public Collection<OfflinePlatformPlayer> getOfflinePlayers() {
        return new ArrayList<>(getOnlinePlayers());
    }
}
