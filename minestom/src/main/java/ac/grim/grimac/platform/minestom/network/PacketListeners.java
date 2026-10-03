package ac.grim.grimac.platform.minestom.network;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.exception.InvalidDisconnectPacketSend;
import com.github.retrooper.packetevents.exception.PacketProcessException;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.ExceptionUtil;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerDisconnect;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerDisconnect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisconnect;
import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Runs packets through the PacketEvents listeners on behalf of a channel.
 */
final class PacketListeners {
    private static final Component INVALID_PACKET = Component.text("Invalid packet");

    private PacketListeners() {
    }

    static Verdict receive(PacketChannel channel, ByteBuf buffer, ListenerScope scope) {
        if (scope == ListenerScope.NONE) return Verdict.KEEP;
        try {
            User user = channel.user();
            Player player = channel.connection().getPlayer();
            boolean rewritten = false;
            if (scope == ListenerScope.ALL && PacketEvents.getAPI().getSettings().isPreViaInjection()) {
                PacketReceiveEvent event = PacketEventsImplHelper.handleServerBoundPacket(channel, user, player, buffer, false);
                if (!buffer.isReadable()) return Verdict.DROP;
                rewritten = event != null && event.getLastUsedWrapper() != null;
            }
            PacketReceiveEvent event = PacketEventsImplHelper.handleServerBoundPacket(channel, user, player, buffer, true);
            if (!buffer.isReadable()) return Verdict.DROP;
            return rewritten || (event != null && event.getLastUsedWrapper() != null) ? Verdict.REWRITE : Verdict.KEEP;
        } catch (Throwable throwable) {
            fail(channel, throwable);
            return Verdict.DROP;
        }
    }

    static void fail(PacketChannel channel, Throwable throwable) {
        if (ExceptionUtil.isException(throwable, InvalidDisconnectPacketSend.class)) return;
        if (!ExceptionUtil.isException(throwable, PacketProcessException.class)) {
            MinecraftServer.getExceptionManager().handleException(throwable);
            channel.connection().disconnect();
            return;
        }
        User user = channel.user();
        PacketEventsSettings settings = PacketEvents.getAPI().getSettings();
        boolean handshaking = user.getDecoderState() == ConnectionState.HANDSHAKING;
        if (settings.isDebugEnabled() || !handshaking) {
            if (settings.isFullStackTraceEnabled()) {
                PacketEvents.getAPI().getLogManager().warn("An error occurred while processing a packet of "
                        + user.getName() + " (state: " + user.getDecoderState() + ", serverVersion: "
                        + PacketEvents.getAPI().getServerManager().getVersion().getReleaseName() + ")", throwable);
            } else {
                PacketEvents.getAPI().getLogManager().warn(throwable.getMessage());
            }
        }
        if (!settings.isKickOnPacketExceptionEnabled()) return;
        PacketWrapper<?> packet = switch (user.getEncoderState()) {
            case LOGIN -> new WrapperLoginServerDisconnect(INVALID_PACKET);
            case CONFIGURATION -> new WrapperConfigServerDisconnect(INVALID_PACKET);
            case PLAY -> new WrapperPlayServerDisconnect(INVALID_PACKET);
            default -> null;
        };
        if (packet != null) user.sendPacket(packet);
        channel.connection().disconnect();
        if (user.getName() != null) {
            PacketEvents.getAPI().getLogManager().warn("Disconnected " + user.getName() + " due to an invalid packet!");
        }
    }

    static void runTasksAfterSend(@Nullable PacketSendEvent event) {
        if (event == null || !event.hasTasksAfterSend()) return;
        for (Runnable task : event.getTasksAfterSend()) {
            try {
                task.run();
            } catch (Throwable throwable) {
                MinecraftServer.getExceptionManager().handleException(throwable);
            }
        }
    }

    static boolean untouched(@Nullable PacketSendEvent event) {
        return event == null || event.getLastUsedWrapper() == null;
    }

    enum Verdict {
        DROP,
        KEEP,
        REWRITE
    }
}
