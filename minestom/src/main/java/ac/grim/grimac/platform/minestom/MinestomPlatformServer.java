package ac.grim.grimac.platform.minestom;

import ac.grim.grimac.platform.api.PlatformServer;
import ac.grim.grimac.platform.api.sender.Sender;
import ac.grim.grimac.platform.minestom.sender.MinestomSenderFactory;
import lombok.RequiredArgsConstructor;
import net.minestom.server.Git;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.event.server.ServerTickMonitorEvent;

@RequiredArgsConstructor
public final class MinestomPlatformServer implements PlatformServer {
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final double SMOOTHING = 0.05;

    private final MinestomSenderFactory senderFactory;
    private volatile double tickTime;

    @Override
    public String getPlatformImplementationString() {
        return MinecraftServer.getBrandName() + " " + Git.version() + " (MC: " + MinecraftServer.VERSION_NAME + ")";
    }

    @Override
    public void dispatchCommand(Sender sender, String command) {
        MinecraftServer.getCommandManager().execute(senderFactory.reverse(sender), command);
    }

    @Override
    public Sender getConsoleSender() {
        return senderFactory.map(MinecraftServer.getCommandManager().getConsoleSender());
    }

    @Override
    public void registerOutgoingPluginChannel(String name) {
    }

    @Override
    public double getTPS() {
        double tickTime = this.tickTime;
        if (tickTime <= 0) return ServerFlag.SERVER_TICKS_PER_SECOND;
        return Math.min(ServerFlag.SERVER_TICKS_PER_SECOND, MILLIS_PER_SECOND / tickTime);
    }

    public void recordTick(ServerTickMonitorEvent event) {
        double sample = event.getTickMonitor().getTickTime();
        double tickTime = this.tickTime;
        this.tickTime = tickTime <= 0 ? sample : tickTime + (sample - tickTime) * SMOOTHING;
    }
}
