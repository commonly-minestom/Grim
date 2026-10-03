package ac.grim.grimac.platform.minestom;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.plugin.BasicGrimPlugin;
import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.command.CloudCommandService;
import ac.grim.grimac.manager.init.Initable;
import ac.grim.grimac.manager.init.stop.StoppableInitable;
import ac.grim.grimac.platform.api.PlatformLoader;
import ac.grim.grimac.platform.api.command.CommandService;
import ac.grim.grimac.platform.api.manager.ItemResetHandler;
import ac.grim.grimac.platform.api.manager.MessagePlaceHolderManager;
import ac.grim.grimac.platform.api.manager.PlatformPluginManager;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.player.PlatformPlayerFactory;
import ac.grim.grimac.platform.minestom.command.MinestomCommandManager;
import ac.grim.grimac.platform.minestom.initables.MinestomLuckPermsInitable;
import ac.grim.grimac.platform.minestom.initables.MinestomTickEndEvent;
import ac.grim.grimac.platform.minestom.initables.MinestomTickMonitor;
import ac.grim.grimac.platform.minestom.manager.MinestomCloudPlatformCommandArguments;
import ac.grim.grimac.platform.minestom.manager.MinestomItemResetHandler;
import ac.grim.grimac.platform.minestom.manager.MinestomMessagePlaceHolderManager;
import ac.grim.grimac.platform.minestom.manager.MinestomPermissionRegistrationManager;
import ac.grim.grimac.platform.minestom.manager.MinestomPlatformPluginManager;
import ac.grim.grimac.platform.minestom.network.MinestomPacketEvents;
import ac.grim.grimac.platform.minestom.permission.PermissionProvider;
import ac.grim.grimac.platform.minestom.player.MinestomPlatformPlayerFactory;
import ac.grim.grimac.platform.minestom.scheduler.MinestomPlatformScheduler;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import ac.grim.grimac.platform.minestom.utils.Slf4jLogger;
import ac.grim.grimac.utils.anticheat.LogUtil;
import ac.grim.grimac.utils.common.PropertiesUtil;
import ac.grim.grimac.utils.lazy.LazyHolder;
import com.github.retrooper.packetevents.PacketEventsAPI;
import lombok.AccessLevel;
import lombok.Getter;

import java.nio.file.Path;
import java.util.List;

@Getter
public final class GrimACMinestomLoader implements PlatformLoader {
    private static final String PLUGIN_NAME = "GrimAC";
    private static final String DESCRIPTION = "Libre simulation anticheat powered by PacketEvents";
    private static final String UNKNOWN_VERSION = "unknown";

    private final GrimPlugin plugin;
    private final MinestomPermissionRegistrationManager permissionManager;
    private final MinestomSenderFactory senderFactory;
    private final MinestomPlatformScheduler scheduler = new MinestomPlatformScheduler();
    private final PlatformPlayerFactory platformPlayerFactory;
    private final PlatformPluginManager pluginManager = new MinestomPlatformPluginManager();
    private final MinestomPlatformServer platformServer;
    private final MessagePlaceHolderManager messagePlaceHolderManager = new MinestomMessagePlaceHolderManager();
    private final ItemResetHandler itemResetHandler = new MinestomItemResetHandler();
    private final PacketEventsAPI<?> packetEvents;
    @Getter(AccessLevel.NONE)
    private final LazyHolder<CommandService> commandService = LazyHolder.simple(this::createCommandService);

    GrimACMinestomLoader(Path dataFolder, PermissionProvider permissions) {
        this.plugin = new BasicGrimPlugin(
                new Slf4jLogger(PLUGIN_NAME),
                dataFolder.toFile(),
                PropertiesUtil.readProperties(GrimAPI.class, "grimac.properties").getProperty("build.version", UNKNOWN_VERSION),
                DESCRIPTION,
                List.of(PLUGIN_NAME)
        );
        this.permissionManager = new MinestomPermissionRegistrationManager(permissions);
        this.senderFactory = new MinestomSenderFactory(permissionManager);
        this.platformPlayerFactory = new MinestomPlatformPlayerFactory(senderFactory);
        this.platformServer = new MinestomPlatformServer(senderFactory);
        this.packetEvents = new MinestomPacketEvents(plugin);
        GrimAPI.INSTANCE.getExtensionManager().registerResolver(this::resolvePlugin);
    }

    Initable[] initables() {
        return new Initable[] {
                new MinestomTickMonitor(platformServer),
                new MinestomTickEndEvent(),
                new MinestomLuckPermsInitable(),
                (StoppableInitable) scheduler::shutdown
        };
    }

    @Override
    public CommandService getCommandService() {
        return commandService.get();
    }

    @Override
    public void registerAPIService() {
        GrimAPIProvider.init(GrimAPI.INSTANCE.getExternalAPI());
    }

    private GrimPlugin resolvePlugin(Object context) {
        return context instanceof String name && name.equalsIgnoreCase(PLUGIN_NAME) ? plugin : null;
    }

    private CommandService createCommandService() {
        try {
            CloudPlatformCommandArguments arguments = new MinestomCloudPlatformCommandArguments(senderFactory);
            MinestomCommandManager manager = new MinestomCommandManager(senderFactory);
            return new CloudCommandService(() -> manager, arguments);
        } catch (Throwable throwable) {
            LogUtil.warn("Failed to initialize the command framework, Grim will run without commands", throwable);
            return () -> {};
        }
    }
}
