package balancer;

import com.sun.net.httpserver.*;
import io.github.cdimascio.dotenv.Dotenv;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class WebAdminServer {
    private final ServerManager serverManager;
    private final String USERNAME;
    private final String PASSWORD;
    private static final String SESSION_COOKIE = "LB_SESSION";
    private final Map<String, String> sessions = new HashMap<>();

    public WebAdminServer(ServerManager manager) {
        this.serverManager = manager;
        System.out.println("CWD: " + System.getProperty("user.dir"));
        Dotenv dotenv = Dotenv.configure()
                .directory("Load-Balancing/src/main/resources")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        this.USERNAME = dotenv.get("LB_ADMIN_USER", "admin");
        this.PASSWORD = dotenv.get("LB_ADMIN_PASS", "password123");

        System.out.println("Loaded USERNAME: " + USERNAME);
        System.out.println("Loaded PASSWORD: " + PASSWORD);

        if (USERNAME.isEmpty() || PASSWORD.isEmpty()) {
            System.err.println("LB_ADMIN_USER and LB_ADMIN_PASS must be set in .env");
            System.exit(1);
        }
    }

    public void start() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(7070), 0);

        httpServer.createContext("/login", this::handleLogin);
        httpServer.createContext("/logout", this::handleLogout);

        httpServer.createContext("/status", exchange -> requireAuth(this::handleStatus).handle(exchange));
        httpServer.createContext("/add", exchange -> requireAuth(this::handleAdd).handle(exchange));
        httpServer.createContext("/remove", exchange -> requireAuth(this::handleRemove).handle(exchange));
        httpServer.createContext("/start", exchange -> requireAuth(this::handleStartServer).handle(exchange));
        httpServer.createContext("/stop", exchange -> requireAuth(this::handleStopServer).handle(exchange));
        httpServer.createContext("/metrics", exchange -> requireAuth(this::handleMetrics).handle(exchange));

        httpServer.createContext("/", this::handleDashboard);

        httpServer.setExecutor(null);
        httpServer.start();
        System.out.println("[WebAdmin] Management API running on http://localhost:7070");
    }

    private HttpHandler requireAuth(HttpHandler handler) {
        return exchange -> {
            if (!isAuthenticated(exchange)) {
                exchange.getResponseHeaders().add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            handler.handle(exchange);
        };
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Map<String, Integer> perServer = serverManager.getRequestCounts();
        int total = serverManager.getTotalRequests();

        String json = String.format("{\"total\":%d,\"perServer\":%s}", total, new Gson().toJson(perServer));
        sendJson(exchange, json);
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                for (String pair : cookie.split(";")) {
                    String[] kv = pair.trim().split("=");
                    if (kv.length == 2 && kv[0].equals(SESSION_COOKIE)) {
                        return sessions.containsKey(kv[1]);
                    }
                }
            }
        }
        return false;
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("webapp/login.html");
            if (inputStream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] html = inputStream.readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html);
            }
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            String body;
            try (Scanner scanner = new Scanner(exchange.getRequestBody()).useDelimiter("\\A")) {
                body = scanner.hasNext() ? scanner.next() : "";
            }

            String[] parts = body.split("&");
            String user = parts[0].split("=")[1];
            String pass = parts[1].split("=")[1];

            if (USERNAME.equals(user) && PASSWORD.equals(pass)) {
                String sessionId = UUID.randomUUID().toString();
                sessions.put(sessionId, user);
                exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + sessionId + "; Path=/");
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(403, -1);
            }
        }
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!isAuthenticated(exchange)) {
            exchange.getResponseHeaders().add("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("webapp/admin.html");
        if (inputStream == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] html = inputStream.readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "text/html");
        exchange.sendResponseHeaders(200, html.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(html);
        }
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

    private void handleLogout(HttpExchange exchange) throws IOException {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                for (String pair : cookie.split(";")) {
                    String[] kv = pair.trim().split("=");
                    if (kv.length == 2 && kv[0].equals(SESSION_COOKIE)) {
                        sessions.remove(kv[1]); // remove the session
                    }
                }
            }
        }

        // Clear the session cookie by setting expiry in the past
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; Max-Age=0");
        exchange.getResponseHeaders().add("Location", "/login");
        exchange.sendResponseHeaders(302, -1);
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
