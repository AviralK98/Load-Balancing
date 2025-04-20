package balancer;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public class HealthMonitor {
    private final List<ServerNode> servers;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient client;

    public HealthMonitor(List<ServerNode> servers) {
        this.servers = servers;
        this.client = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(1))
                                .version(HttpClient.Version.HTTP_1_1)
                                .build();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkServers, 0, 5, TimeUnit.SECONDS);
    }

    private void checkServers() {
        for (ServerNode server : servers) {
            pingServer(server).thenAccept(isHealthy -> {
                server.setHealthy(isHealthy);
                System.out.println("[HealthCheck] " + server + " is " + (isHealthy ? "HEALTHY" : "DOWN"));
            });
        }
    }

    private CompletableFuture<Boolean> pingServer(ServerNode server) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + server.getHost() + ":" + server.getPort() + "/"))
                .timeout(Duration.ofSeconds(1))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 400)
                .exceptionally(ex -> false);
    }

    public void stop() {
        scheduler.shutdown();
    }
}
