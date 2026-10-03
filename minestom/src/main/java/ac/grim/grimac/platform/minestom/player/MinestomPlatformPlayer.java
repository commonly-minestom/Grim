package ac.grim.grimac.platform.minestom.player;

import ac.grim.grimac.platform.api.entity.GrimEntity;
import ac.grim.grimac.platform.api.player.BlockTranslator;
import ac.grim.grimac.platform.api.player.PlatformInventory;
import ac.grim.grimac.platform.api.player.PlatformPlayer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.minestom.entity.MinestomGrimEntity;
import ac.grim.grimac.platform.minestom.sender.MinestomMessenger;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import ac.grim.grimac.platform.minestom.utils.MinestomConversionUtil;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.util.Vector3d;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MinestomPlatformPlayer extends MinestomGrimEntity implements PlatformPlayer {
    private static final String LEGACY_BUNGEE_CHANNEL = "BungeeCord";
    private static final String BUNGEE_CHANNEL = "bungeecord:main";

    private final Player player;
    private final MinestomSenderFactory senderFactory;
    @Getter
    private final PlatformInventory inventory;

    public MinestomPlatformPlayer(Player player, MinestomSenderFactory senderFactory) {
        super(player);
        this.player = player;
        this.senderFactory = senderFactory;
        this.inventory = new MinestomPlatformInventory(player);
    }

    @Override
    public void kickPlayer(String textReason) {
        player.kick(textReason);
    }

    @Override
    public void resyncSharedFlags() {
        player.sendPacket(player.getMetadataPacket());
    }

    @Override
    public boolean hasPermission(String permission) {
        return getSender().hasPermission(permission);
    }

    @Override
    public boolean hasPermission(String permission, boolean defaultIfUnset) {
        return getSender().hasPermission(permission, defaultIfUnset);
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    @Override
    public void sendMessage(Component message) {
        MinestomMessenger.send(player, message);
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public void updateInventory() {
        player.getInventory().update();
    }

    @Override
    public Vector3d getPosition() {
        Pos position = player.getPosition();
        return new Vector3d(position.x(), position.y(), position.z());
    }

    @Override
    public @Nullable GrimEntity getVehicle() {
        Entity vehicle = player.getVehicle();
        return vehicle == null ? null : new MinestomGrimEntity(vehicle);
    }

    @Override
    public GameMode getGameMode() {
        return MinestomConversionUtil.fromMinestomGameMode(player.getGameMode());
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        player.setGameMode(MinestomConversionUtil.toMinestomGameMode(gameMode));
    }

    @Override
    public boolean isExternalPlayer() {
        return false;
    }

    @Override
    public void sendPluginMessage(String channelName, byte[] byteArray) {
        player.sendPluginMessage(channelName.equals(LEGACY_BUNGEE_CHANNEL) ? BUNGEE_CHANNEL : channelName, byteArray);
    }

    @Override
    public Sender getSender() {
        return senderFactory.map(player);
    }

    @Override
    public BlockTranslator getBlockTranslator() {
        return BlockTranslator.IDENTITY;
    }

    @Override
    public @NotNull Player getNative() {
        return player;
    }
}
