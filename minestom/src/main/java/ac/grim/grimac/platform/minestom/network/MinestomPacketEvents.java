package ac.grim.grimac.platform.minestom.network;

import ac.grim.grimac.api.plugin.GrimPlugin;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.InternalPacketListener;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.PEVersions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.player.PlayerSpawnEvent;

@Getter
@RequiredArgsConstructor
public final class MinestomPacketEvents extends PacketEventsAPI<GrimPlugin> {
    private static final String IDENTIFIER = "minestom";

    private final GrimPlugin plugin;
    private final ServerManager serverManager = new MinestomServerManager();
    private final ProtocolManager protocolManager = new MinestomProtocolManager();
    private final PlayerManager playerManager = new MinestomPlayerManager();
    private final NettyManager nettyManager = new MinestomNettyManager();
    private final ChannelInjector injector = new MinestomChannelInjector();
    @Getter(AccessLevel.NONE)
    private final EventListener<PlayerSpawnEvent> loginListener = EventListener.of(PlayerSpawnEvent.class, this::onSpawn);
    private boolean loaded;
    private boolean initialized;
    private boolean terminated;

    @Override
    public void load() {
        if (loaded) return;
        PacketEvents.IDENTIFIER = "pe-" + IDENTIFIER;
        PacketEvents.ENCODER_NAME = "pe-encoder-" + IDENTIFIER;
        PacketEvents.DECODER_NAME = "pe-decoder-" + IDENTIFIER;
        PacketEvents.CONNECTION_HANDLER_NAME = "pe-connection-handler-" + IDENTIFIER;
        PacketEvents.SERVER_CHANNEL_HANDLER_NAME = "pe-connection-initializer-" + IDENTIFIER;
        PacketEvents.TIMEOUT_HANDLER_NAME = "pe-timeout-handler-" + IDENTIFIER;
        super.load();
        injector.inject();
        MinecraftServer.getGlobalEventHandler().addListener(loginListener);
        loaded = true;
        getLogManager().info("Loaded packetevents v" + PEVersions.RAW + " for Minestom");
    }

    @Override
    public void init() {
        load();
        if (initialized) return;
        if (getSettings().shouldCheckForUpdates()) getUpdateChecker().handleUpdateCheck();
        PacketType.Play.Client.load();
        PacketType.Play.Server.load();
        initialized = true;
    }

    @Override
    public void terminate() {
        if (!initialized) return;
        MinecraftServer.getGlobalEventHandler().removeListener(loginListener);
        super.terminate();
        initialized = false;
        terminated = true;
    }

    @Override
    protected void registerInternalListener() {
        getEventManager().registerListener(new InternalPacketListener());
        getEventManager().registerListener(new InternalPacketListener(PacketListenerPriority.LOWEST, true));
    }

    private void onSpawn(PlayerSpawnEvent event) {
        if (!event.isFirstSpawn()) return;
        User user = playerManager.getUser(event.getPlayer());
        if (user != null) getEventManager().callEvent(new UserLoginEvent(user, event.getPlayer()));
    }
}
