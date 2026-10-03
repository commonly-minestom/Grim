package ac.grim.grimac.platform.minestom.command;

import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import ac.grim.grimac.utils.anticheat.LogUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomCommandManager extends CommandManager<Sender> implements CommandRegistrationHandler<Sender> {
    private final Set<String> registered = ConcurrentHashMap.newKeySet();
    private final MinestomSenderFactory senderFactory;

    public MinestomCommandManager(MinestomSenderFactory senderFactory) {
        super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        this.senderFactory = senderFactory;
        commandRegistrationHandler(this);
        registerDefaultExceptionHandlers(
                message -> message.first().sender().sendMessage(Component.text(
                        message.first().formatCaption(message.second(), message.third()), NamedTextColor.RED)),
                error -> LogUtil.error(error.first(), error.second())
        );
    }

    @Override
    public boolean hasPermission(Sender sender, String permission) {
        return sender.hasPermission(permission);
    }

    @Override
    public boolean registerCommand(Command<Sender> command) {
        CommandComponent<Sender> root = command.rootComponent();
        if (!registered.add(root.name())) return false;
        String[] aliases = root.alternativeAliases().toArray(new String[0]);
        MinecraftServer.getCommandManager().register(new MinestomCloudCommand(root.name(), aliases, this, senderFactory));
        return true;
    }
}
