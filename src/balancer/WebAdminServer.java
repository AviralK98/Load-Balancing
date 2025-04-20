package balancer;

import com.sun.net.httpserver.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class WebAdminServer {
    private final ServerManager serverManager;

    public WebAdminServer(ServerManager manager) {
        this.serverManager = manager;
    }

    public void start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(7070), 0);

        httpServer.createContext("/status", this::handleStatus);
        httpServer.createContext("/add", this::handleAdd);
        httpServer.createContext("/remove", this::handleRemove);

        httpServer.setExecutor(null); // default executor
        httpServer.start();

        System.out.println("[WebAdmin] Management API running on http://localhost:7070");
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String json = "[" + serverManager.getAllServers().stream()
            .map(s -> String.format(
                "{\"host\":\"%s\",\"port\":%d,\"weight\":%d,\"healthy\":%b}",
                s.getHost(), s.getPort(), s.getWeight(), s.isHealthy()))
            .collect(Collectors.joining(",")) + "]";

        sendJson(exchange, json);
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
        String body = scanner.hasNext() ? scanner.next() : "";

        String[] parts = body.trim().split(":");
        if (parts.length < 3) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        serverManager.addServer(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        sendJson(exchange, "{\"status\":\"added\"}");
    }

    private void handleRemove(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A");
        String body = scanner.hasNext() ? scanner.next() : "";

        serverManager.removeServerByKey(body.trim());
        sendJson(exchange, "{\"status\":\"removed\"}");
    }

    private void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
