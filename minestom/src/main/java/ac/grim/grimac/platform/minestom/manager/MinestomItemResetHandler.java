package ac.grim.grimac.platform.minestom.manager;

import ac.grim.grimac.platform.api.manager.ItemResetHandler;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.minestom.utils.MinestomConversionUtil;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.thread.Acquirable;
import org.jetbrains.annotations.Nullable;

public final class MinestomItemResetHandler implements ItemResetHandler {
    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player) {
        if (player == null) return;
        Acquirable<? extends Player> acquirable = ((Player) player.getNative()).acquirable();
        // Until the player is ticked there is no item use to reset, and nothing but the login thread may lock it
        if (acquirable.assignedThread() == null) return;
        acquirable.sync(MinestomItemResetHandler::stopUsingItem);
    }

    @Override
    public @Nullable InteractionHand getItemUsageHand(@Nullable PlatformPlayer player) {
        return player == null ? null : MinestomConversionUtil.fromMinestomHand(((Player) player.getNative()).getItemUseHand());
    }

    @Override
    public boolean isUsingItem(@Nullable PlatformPlayer player) {
        return player != null && ((Player) player.getNative()).isUsingItem();
    }

    private static void stopUsingItem(Player player) {
        if (!player.isUsingItem()) return;
        LivingEntityMeta meta = player.getLivingEntityMeta();
        if (meta != null) meta.setHandActive(false);
        player.clearItemUse();
    }
}
