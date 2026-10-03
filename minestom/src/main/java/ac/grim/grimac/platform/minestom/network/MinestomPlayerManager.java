package ac.grim.grimac.platform.minestom.network;

import io.github.retrooper.packetevents.impl.netty.manager.player.PlayerManagerAbstract;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

final class MinestomPlayerManager extends PlayerManagerAbstract {
    @Override
    public int getPing(@NotNull Object player) {
        return ((Player) player).getLatency();
    }

    @Override
    public Object getChannel(@NotNull Object player) {
        return ((Player) player).getPlayerConnection() instanceof MinestomConnection connection ? connection : null;
    }
}
