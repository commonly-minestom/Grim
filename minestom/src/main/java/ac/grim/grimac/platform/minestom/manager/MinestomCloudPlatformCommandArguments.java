package ac.grim.grimac.platform.minestom.manager;

import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.manager.cloud.CloudPlatformCommandArguments;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.minestom.command.MinestomPlayerSelectorParser;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MinestomCloudPlatformCommandArguments implements CloudPlatformCommandArguments {
    private final SuggestionProvider<Sender> onlinePlayers = (context, input) -> CompletableFuture.completedFuture(onlinePlayerNames());
    private final ParserDescriptor<Sender, PlayerSelector> playerSelector;

    public MinestomCloudPlatformCommandArguments(MinestomSenderFactory senderFactory) {
        this.playerSelector = ParserDescriptor.of(new MinestomPlayerSelectorParser(senderFactory, onlinePlayers), PlayerSelector.class);
    }

    @Override
    public ParserDescriptor<Sender, PlayerSelector> singlePlayerSelectorParser() {
        return playerSelector;
    }

    @Override
    public SuggestionProvider<Sender> onlinePlayerSuggestions() {
        return onlinePlayers;
    }

    private static List<Suggestion> onlinePlayerNames() {
        Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        List<Suggestion> suggestions = new ArrayList<>(players.size());
        for (Player player : players) {
            suggestions.add(Suggestion.suggestion(player.getUsername()));
        }
        return suggestions;
    }
}
