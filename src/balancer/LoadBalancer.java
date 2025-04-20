package balancer;

import java.io.*;
import java.net.*;
import java.util.*;

import io.github.cdimascio.dotenv.Dotenv;

public class LoadBalancer {

    private static ServerSocket loadBalancerSocket;

    public static void main(String[] args) {
        // Load .env file using dotenv-java
        Dotenv dotenv = Dotenv.configure().directory(".").ignoreIfMissing().load();

        List<ServerNode> serverList;
        ServerManager serverManager;

        try {
            serverList = loadServers("servers.txt");
            serverManager = new ServerManager(serverList);
        } catch (IOException e) {
            System.err.println("Failed to load server list: " + e.getMessage());
            return;
        }

        // Start health checks
        HealthMonitor healthMonitor = new HealthMonitor(serverList);
        healthMonitor.start();

        // Start web admin
        WebAdminServer admin = new WebAdminServer(serverManager);
        try {
            admin.start();
        } catch (IOException e) {
            System.err.println("Failed to start WebAdminServer: " + e.getMessage());
        }

        // Start load balancer socket
        try {
            loadBalancerSocket = new ServerSocket(8080);
            System.out.println("Load balancer listening on port 8080...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (loadBalancerSocket != null && !loadBalancerSocket.isClosed()) {
                        loadBalancerSocket.close();
                        System.out.println("Load balancer socket closed.");
                    }
                } catch (IOException e) {
                    System.err.println("Error closing socket on shutdown: " + e.getMessage());
                }
            }));

            // Handle client requests
            while (true) {
                Socket clientSocket = loadBalancerSocket.accept();
                ServerNode targetServer = serverManager.getNextServer();
                serverManager.recordRequest(targetServer);
                if (targetServer == null) {
                    System.err.println("No healthy backend servers available.");
                    clientSocket.close();
                    continue;
                }
                System.out.println("Forwarding to: " + targetServer);
                new Thread(new ClientHandler(clientSocket, targetServer)).start();
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            try {
                if (loadBalancerSocket != null && !loadBalancerSocket.isClosed()) {
                    loadBalancerSocket.close();
                    System.out.println("Socket closed in finally block.");
                }
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    private static List<ServerNode> loadServers(String filePath) throws IOException {
        List<ServerNode> servers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                int weight = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                servers.add(new ServerNode(host, port, weight));
            }
        }
        return servers;
    }
}
