package ac.grim.grimac.platform.minestom.sender;

import ac.grim.grimac.platform.minestom.network.MinestomConnection;
import ac.grim.grimac.utils.anticheat.MessageUtil;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.entity.Player;

@UtilityClass
public class MinestomMessenger {
    public static void send(CommandSender sender, Component message) {
        if (sender instanceof Player player) {
            send(player, message);
            return;
        }
        sender.sendMessage(MessageUtil.stripColor(LegacyComponentSerializer.legacySection().serialize(message)));
    }

    public static void send(Player player, Component message) {
        if (player.getPlayerConnection() instanceof MinestomConnection connection) {
            connection.user().sendMessage(message);
            return;
        }
        player.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
    }
}
