package ac.grim.grimac.platform.minestom;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.player.GrimPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.client.play.ClientCommandChatPacket;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.packet.server.status.ResponsePacket;
import net.minestom.server.utils.PacketViewableUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class GrimMinestomIntegrationTest {
    private static final String SESSION_URL_PROPERTY = "minestom.auth.url";
    private static final Pos SPAWN = new Pos(0.5, 40, 0.5);
    private static final int OPERATOR_LEVEL = 4;
    private static final long AWAIT_SECONDS = 20;
    private static final long POLL_MILLIS = 25;
    private static final float NEIGHBOUR_YAW = 90;
    private static final float SETBACK_YAW = 45;
    private static final String BROADCAST = "x".repeat(1024);

    private final AtomicInteger flags = new AtomicInteger();
    private final Auth auth;
    private TestSessionServer sessionServer;
    private InetSocketAddress address;
    private TestClient client;

    GrimMinestomIntegrationTest(Auth auth) {
        this.auth = auth;
    }

    @BeforeAll
    void startServer(@TempDir Path dataFolder) throws Exception {
        sessionServer = TestSessionServer.start();
        System.setProperty(SESSION_URL_PROPERTY, sessionServer.url());

        MinecraftServer server = MinecraftServer.init(auth);
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, SPAWN.blockY(), Block.STONE));
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            event.getPlayer().setRespawnPoint(SPAWN);
            event.getPlayer().setPermissionLevel(OPERATOR_LEVEL);
        });

        GrimMinestom.builder().dataFolder(dataFolder).enable();
        GrimAPI.INSTANCE.getEventBus().subscribe(GrimAPI.INSTANCE.getGrimPlugin(), FlagEvent.class, event -> flags.incrementAndGet());

        server.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        await(() -> !MinecraftServer.getServer().isOpen());
        address = new InetSocketAddress(InetAddress.getLoopbackAddress(), MinecraftServer.getServer().getPort());
    }

    @AfterAll
    void stopServer() throws IOException {
        if (client != null) client.close();
        MinecraftServer.stopCleanly();
        sessionServer.close();
    }

    @Test
    @Order(1)
    void answersStatusRequests() throws Exception {
        try (TestClient status = connect(() -> TestClient.status(address))) {
            ResponsePacket response = status.await(ResponsePacket.class, AWAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(response);
            assertTrue(response.jsonResponse().contains(MinecraftServer.VERSION_NAME));
        }
    }

    @Test
    @Order(2)
    void tracksJoiningPlayers() throws Exception {
        client = connect(() -> TestClient.login(address, "GrimBot"));
        assertNotNull(client.await(JoinGamePacket.class, AWAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(auth instanceof Auth.Online, client.encrypted());
        await(() -> player() != null && player().platformPlayer != null);
        assertEquals(client.uuid(), player().platformPlayer.getUniqueId());
    }

    @Test
    @Order(3)
    void exchangesTransactions() throws Exception {
        int received = player().lastTransactionReceived.get();
        await(() -> player().lastTransactionReceived.get() > received + 5 && client.pongs() > 5);
    }

    @Test
    @Order(4)
    void acceptsLegitimateMovement() throws Exception {
        await(() -> client.position() != null);
        int teleports = client.teleports();
        int flagged = flags.get();
        TimeUnit.SECONDS.sleep(3);
        assertEquals(flagged, flags.get());
        assertEquals(teleports, client.teleports());
    }

    @Test
    @Order(5)
    void executesCommands() throws Exception {
        client.clearReceived();
        client.send(new ClientCommandChatPacket("grim verbose"));
        assertNotNull(client.await(SystemChatPacket.class, AWAIT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    @Order(6)
    void relaysViewablePackets() throws Exception {
        try (TestClient neighbour = connect(() -> TestClient.login(address, "GrimNeighbour"))) {
            await(() -> neighbour.position() != null && online(neighbour) != null && online(neighbour).isViewer(online(client)));
            Player player = online(neighbour);
            neighbour.rotate(NEIGHBOUR_YAW, 0);
            assertNotNull(client.await(EntityPositionSyncPacket.class,
                    packet -> packet.entityId() == player.getEntityId() && packet.yaw() == NEIGHBOUR_YAW,
                    AWAIT_SECONDS, TimeUnit.SECONDS));

            PacketViewableUtils.prepareViewablePacket(player.getChunk(), new SystemChatPacket(Component.text(BROADCAST), false));
            assertNotNull(client.await(SystemChatPacket.class,
                    packet -> packet.message() instanceof TextComponent text && BROADCAST.equals(text.content()),
                    AWAIT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    @Order(7)
    void setsBackIllegalMovement() throws Exception {
        int teleports = client.teleports();
        int flagged = flags.get();
        client.move(client.position().add(0, 5, 0), true);
        await(() -> flags.get() > flagged && client.teleports() > teleports);
        assertEquals(SPAWN.y(), client.position().y(), 0.01);
        assertEquals(SPAWN.y(), online(client).getPosition().y(), 0.01);

        client.rotate(SETBACK_YAW, 0);
        await(() -> online(client).getPosition().yaw() == SETBACK_YAW);
    }

    @Test
    @Order(8)
    void forgetsDisconnectedPlayers() throws Exception {
        client.close();
        await(() -> player() == null);
        client = null;
    }

    private GrimPlayer player() {
        return GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(client.uuid());
    }

    private static Player online(TestClient client) {
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(client.uuid());
    }

    private TestClient connect(ClientFactory factory) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (true) {
            try {
                return factory.connect();
            } catch (IOException exception) {
                if (System.nanoTime() > deadline) throw exception;
                TimeUnit.MILLISECONDS.sleep(POLL_MILLIS);
            }
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "condition not met in time");
            TimeUnit.MILLISECONDS.sleep(POLL_MILLIS);
        }
    }

    @FunctionalInterface
    private interface ClientFactory {
        TestClient connect() throws IOException;
    }
}
