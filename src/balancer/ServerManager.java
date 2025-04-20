package balancer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerManager {
    private List<ServerNode> servers;
    private AtomicInteger currentIndex = new AtomicInteger(0);

    public ServerManager(List<ServerNode> servers) {
        this.servers = servers;
    }

    public ServerNode getNextServer() {
        int index = currentIndex.getAndUpdate(i -> (i + 1) % servers.size());
        return servers.get(index);
    }
}
