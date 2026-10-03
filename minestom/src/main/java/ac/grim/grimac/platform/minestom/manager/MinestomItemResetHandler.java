package ac.grim.grimac.platform.minestom.manager;

import ac.grim.grimac.platform.api.manager.ItemResetHandler;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.minestom.utils.MinestomConversionUtil;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import org.jetbrains.annotations.Nullable;

public final class MinestomItemResetHandler implements ItemResetHandler {
    @Override
    public void resetItemUsage(@Nullable PlatformPlayer player) {
        if (player == null) return;
        ((Player) player.getNative()).acquirable().sync(MinestomItemResetHandler::stopUsingItem);
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
