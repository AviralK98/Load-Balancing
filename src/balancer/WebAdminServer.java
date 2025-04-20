package balancer;

import com.sun.net.httpserver.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class WebAdminServer {
    private final ServerManager serverManager;

    public WebAdminServer(ServerManager manager) {
        this.serverManager = manager;
    }

    private void handleStartServer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
    
        String body;
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            body = scanner.hasNext() ? scanner.next() : "";
        }
    
        String[] parts = body.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
    
        for (ServerNode node : serverManager.getAllServers()) {
            if (node.getHost().equals(host) && node.getPort() == port) {
                if (node.getProcess() == null || !node.getProcess().isAlive()) {
                    System.out.println("[Start] Attempting to start: " + host + ":" + port);
    
                    ProcessBuilder pb = new ProcessBuilder("python3", "-m", "http.server", String.valueOf(port));
                    pb.redirectErrorStream(true);
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    
                    Process proc = pb.start();
                    node.setProcess(proc);
    
                    System.out.println("[Start] Started server on port " + port + " with PID: " + proc.pid());
                }
            }
        }
    
        sendJson(exchange, "{\"status\":\"started\"}");
    }

    private void handleStopServer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body;
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            body = scanner.hasNext() ? scanner.next() : "";
        }
        String[] parts = body.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        for (ServerNode node : serverManager.getAllServers()) {
            if (node.getHost().equals(host) && node.getPort() == port) {
                if (node.getProcess() != null && node.getProcess().isAlive()) {
                    node.getProcess().destroy();
                    node.setProcess(null);
                }
            }
        }

        sendJson(exchange, "{\"status\":\"stopped\"}");
    }

    public void start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(7070), 0);

        httpServer.createContext("/status", this::handleStatus);
        httpServer.createContext("/add", this::handleAdd);
        httpServer.createContext("/remove", this::handleRemove);
        httpServer.createContext("/start", this::handleStartServer);
        httpServer.createContext("/stop", this::handleStopServer);

        httpServer.setExecutor(null); // default executor
        httpServer.start();

        System.out.println("[WebAdmin] Management API running on http://localhost:7070");
        httpServer.createContext("/", exchange -> {
            byte[] content = Files.readAllBytes(Path.of("public/admin.html"));
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        });
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

        String body;
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            body = scanner.hasNext() ? scanner.next() : "";
        }

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

        String body;
        try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
            body = scanner.hasNext() ? scanner.next() : "";
        }

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
