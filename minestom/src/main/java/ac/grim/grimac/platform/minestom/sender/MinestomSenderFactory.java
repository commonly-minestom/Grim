package ac.grim.grimac.platform.minestom.sender;

import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.api.sender.SenderFactory;
import ac.grim.grimac.platform.minestom.manager.MinestomPermissionRegistrationManager;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;
import org.incendo.cloud.SenderMapper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@RequiredArgsConstructor
public final class MinestomSenderFactory extends SenderFactory<CommandSender> implements SenderMapper<CommandSender, Sender> {
    private final MinestomPermissionRegistrationManager permissions;

    @Override
    protected UUID getUniqueId(CommandSender sender) {
        return sender instanceof Player player ? player.getUuid() : Sender.CONSOLE_UUID;
    }

    @Override
    protected String getName(CommandSender sender) {
        return sender instanceof Player player ? player.getUsername() : Sender.CONSOLE_NAME;
    }

    @Override
    protected void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    @Override
    protected void sendMessage(CommandSender sender, Component message) {
        MinestomMessenger.send(sender, message);
    }

    @Override
    protected boolean hasPermission(CommandSender sender, String node) {
        return permissions.hasPermission(sender, node);
    }

    @Override
    protected boolean hasPermission(CommandSender sender, String node, boolean defaultIfUnset) {
        return permissions.hasPermission(sender, node, defaultIfUnset);
    }

    @Override
    protected void performCommand(CommandSender sender, String command) {
        MinecraftServer.getCommandManager().execute(sender, command);
    }

    @Override
    protected boolean isConsole(CommandSender sender) {
        return !(sender instanceof Player);
    }

    @Override
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    @Override
    public @NotNull Sender map(@NotNull CommandSender base) {
        return wrap(base);
    }

    @Override
    public @NotNull CommandSender reverse(@NotNull Sender mapped) {
        return unwrap(mapped);
    }
}
