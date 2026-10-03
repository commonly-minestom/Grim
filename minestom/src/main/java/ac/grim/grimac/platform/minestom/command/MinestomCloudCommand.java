package ac.grim.grimac.platform.minestom.command;

import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import io.leangen.geantyref.GenericTypeReflector;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.CompletionException;

final class MinestomCloudCommand extends Command {
    private static final String PLACEHOLDER = "\0";

    private final CommandManager<Sender> manager;
    private final MinestomSenderFactory senderFactory;

    MinestomCloudCommand(String name, String[] aliases, CommandManager<Sender> manager, MinestomSenderFactory senderFactory) {
        super(name, aliases);
        this.manager = manager;
        this.senderFactory = senderFactory;
        ArgumentStringArray arguments = ArgumentType.StringArray("arguments");
        arguments.setSuggestionCallback(this::suggest);
        setCondition(this::canUse);
        setDefaultExecutor(this::execute);
        addSyntax(this::execute, arguments);
    }

    private void execute(CommandSender sender, CommandContext context) {
        manager.commandExecutor().executeCommand(senderFactory.map(sender), context.getInput());
    }

    private void suggest(CommandSender sender, CommandContext context, Suggestion suggestion) {
        String input = suggestion.getInput();
        if (input.endsWith(PLACEHOLDER)) input = input.substring(0, input.length() - PLACEHOLDER.length());
        try {
            for (org.incendo.cloud.suggestion.Suggestion entry : manager.suggestionFactory().suggestImmediately(senderFactory.map(sender), input).list()) {
                suggestion.addEntry(new SuggestionEntry(entry.suggestion()));
            }
        } catch (CompletionException ignored) {
        }
    }

    private boolean canUse(CommandSender sender, @Nullable String command) {
        if (command != null) return true;
        CommandNode<Sender> node = manager.commandTree().getNamedNode(getName());
        if (node == null) return false;
        Map<Type, Permission> access = node.nodeMeta().getOrDefault(CommandNode.META_KEY_ACCESS, Map.of());
        Sender mapped = senderFactory.map(sender);
        for (Map.Entry<Type, Permission> entry : access.entrySet()) {
            if (GenericTypeReflector.isSuperType(entry.getKey(), mapped.getClass())
                    && manager.testPermission(mapped, entry.getValue()).allowed()) {
                return true;
            }
        }
        return false;
    }
}
