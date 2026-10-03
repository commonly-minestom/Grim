package ac.grim.grimac.platform.minestom.command;

import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import lombok.RequiredArgsConstructor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import net.minestom.server.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.SuggestionProvider;

@RequiredArgsConstructor
public final class MinestomPlayerSelectorParser implements ArgumentParser<Sender, PlayerSelector> {
    private final ArgumentEntity argument = new ArgumentEntity("player").onlyPlayers(true).singleEntity(true);
    private final MinestomSenderFactory senderFactory;
    private final SuggestionProvider<Sender> suggestions;

    @Override
    public ArgumentParseResult<PlayerSelector> parse(CommandContext<Sender> context, CommandInput input) {
        String selector = input.readString();
        CommandSender sender = senderFactory.reverse(context.sender());
        Player player;
        try {
            player = argument.parse(sender, selector).findFirstPlayer(sender);
        } catch (ArgumentSyntaxException exception) {
            return ArgumentParseResult.failure(new IllegalArgumentException("Invalid player selector '" + selector + "'"));
        }
        if (player == null) {
            return ArgumentParseResult.failure(new IllegalArgumentException("No player found for '" + selector + "'"));
        }
        return ArgumentParseResult.success(new MinestomPlayerSelector(senderFactory.map(player), selector));
    }

    @Override
    public SuggestionProvider<Sender> suggestionProvider() {
        return suggestions;
    }
}
