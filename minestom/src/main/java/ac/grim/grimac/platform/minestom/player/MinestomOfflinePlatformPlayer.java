package ac.grim.grimac.platform.minestom.player;

import ac.grim.grimac.platform.api.player.OfflinePlatformPlayer;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record MinestomOfflinePlatformPlayer(@NotNull UUID uniqueId, @NotNull String name) implements OfflinePlatformPlayer {
    @Override
    public boolean isOnline() {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uniqueId) != null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OfflinePlatformPlayer player && uniqueId.equals(player.getUniqueId());
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }
}
