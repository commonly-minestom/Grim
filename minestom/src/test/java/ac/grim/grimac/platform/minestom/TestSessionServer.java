package ac.grim.grimac.platform.minestom;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class TestSessionServer implements AutoCloseable {
    private static final String PATH = "/session/minecraft/hasJoined";
    private static final String USERNAME_PARAMETER = "username=";

    private final HttpServer server;

    private TestSessionServer(HttpServer server) {
        this.server = server;
    }

    static TestSessionServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(PATH, TestSessionServer::authenticate);
        server.start();
        return new TestSessionServer(server);
    }

    static UUID uuid(String username) {
        return UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8));
    }

    String url() {
        InetSocketAddress address = server.getAddress();
        return "http://" + address.getHostString() + ":" + address.getPort() + PATH;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void authenticate(HttpExchange exchange) throws IOException {
        String username = username(exchange.getRequestURI().getQuery());
        JsonObject profile = new JsonObject();
        profile.addProperty("id", uuid(username).toString().replace("-", ""));
        profile.addProperty("name", username);
        profile.add("properties", new JsonArray());
        byte[] body = profile.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, body.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(body);
        }
    }

    private static String username(String query) {
        for (String parameter : query.split("&")) {
            if (parameter.startsWith(USERNAME_PARAMETER)) return parameter.substring(USERNAME_PARAMETER.length());
        }
        throw new IllegalArgumentException("Missing username: " + query);
    }
}
