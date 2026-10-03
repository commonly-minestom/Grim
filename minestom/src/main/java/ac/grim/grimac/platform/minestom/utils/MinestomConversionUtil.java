package ac.grim.grimac.platform.minestom.utils;

import ac.grim.grimac.utils.anticheat.LogUtil;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.experimental.UtilityClass;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class MinestomConversionUtil {
    public static ItemStack fromMinestomItemStack(net.minestom.server.item.ItemStack itemStack) {
        if (itemStack.isAir()) return ItemStack.EMPTY;
        try {
            byte[] bytes = NetworkBuffer.makeArray(net.minestom.server.item.ItemStack.NETWORK_TYPE, itemStack, MinecraftServer.getRegistries());
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            return PacketWrapper.createUniversalPacketWrapper(buffer).readItemStack();
        } catch (RuntimeException exception) {
            LogUtil.error("Failed to encode ItemStack: " + itemStack, exception);
            return ItemStack.EMPTY;
        }
    }

    public static GameMode fromMinestomGameMode(net.minestom.server.entity.GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }

    public static net.minestom.server.entity.GameMode toMinestomGameMode(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> net.minestom.server.entity.GameMode.SURVIVAL;
            case CREATIVE -> net.minestom.server.entity.GameMode.CREATIVE;
            case ADVENTURE -> net.minestom.server.entity.GameMode.ADVENTURE;
            case SPECTATOR -> net.minestom.server.entity.GameMode.SPECTATOR;
        };
    }

    public static @Nullable InteractionHand fromMinestomHand(@Nullable PlayerHand hand) {
        if (hand == null) return null;
        return hand == PlayerHand.MAIN ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static Pos toMinestomPos(ac.grim.grimac.utils.math.Location location) {
        return new Pos(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
}
