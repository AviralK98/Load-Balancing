package balancer;
public class ServerNode {
    private final String host;
    private final int port;
    private final int weight;
    private volatile boolean healthy = true;
    private Process process;

    public ServerNode(String host, int port, int weight) {
        this.host = host;
        this.port = port;
        this.weight = weight;
    }

    public void setProcess(Process process) {
        this.process = process;
    }

    public Process getProcess() {
        return process;
    }

    public boolean isRunningLocally() {
        return process != null && process.isAlive();
    }
    
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isHealthy() {
        return healthy;
    }
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    @Override
    public String toString() {
        return host + ":" + port + "(weight" + weight + ")" + (healthy ? " (healthy)" : " (unhealthy)");
    }
}
