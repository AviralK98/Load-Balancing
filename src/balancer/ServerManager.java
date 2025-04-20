package balancer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerManager {
    private final List<ServerNode> servers;
    private final List<ServerNode>  weightedList = new ArrayList<>();
    private final AtomicInteger index = new AtomicInteger(0);

    public ServerManager(List<ServerNode> servers) {
        this.servers = servers;
        rebuildWeightedList();
    }

    private void rebuildWeightedList() {
        synchronized(weightedList){
            weightedList.clear();
        for (ServerNode server : servers) {
            for (int i = 0; i < server.getWeight(); i++) {
                weightedList.add(server);
            }
        }
        }
    }

    public synchronized ServerNode getNextServer() {
        int maxTries = weightedList.size();
        for(int i =0; i< maxTries ; i++) {
            int iNext = index.getAndUpdate(j -> (j + 1) % weightedList.size());
            ServerNode candidate = weightedList.get(iNext);
            // Check if the candidate server is healthy
            if(candidate.isHealthy()) {
                return candidate;
            }
            // If the candidate is not healthy, we need to skip it
        }
        return null; // No healthy server found
    }

    public synchronized List<ServerNode> getAllServers() {
        return servers;
    }
    
    public synchronized void addServer(String host, int port, int weight) {
        ServerNode node = new ServerNode(host, port, weight);
        servers.add(node);
        rebuildWeightedList();
    }
    
    public synchronized void removeServerByKey(String key) {
        servers.removeIf(s -> (s.getHost() + ":" + s.getPort()).equals(key));
        rebuildWeightedList();
    }
    
}
