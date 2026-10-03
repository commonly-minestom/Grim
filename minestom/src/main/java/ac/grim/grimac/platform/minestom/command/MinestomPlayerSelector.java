package ac.grim.grimac.platform.minestom.command;

import ac.grim.grimac.platform.api.command.PlayerSelector;
import ac.grim.grimac.platform.api.sender.Sender;

import java.util.Collection;
import java.util.List;

public record MinestomPlayerSelector(Sender player, String inputString) implements PlayerSelector {
    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Sender getSinglePlayer() {
        return player;
    }

    @Override
    public Collection<Sender> getPlayers() {
        return List.of(player);
    }
}
