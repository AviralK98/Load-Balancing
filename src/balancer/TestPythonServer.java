package balancer;

public class TestPythonServer {
    public static void main(String[] args) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "-m", "http.server", "9001");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();
            System.out.println("Started python HTTP server with PID: " + process.pid());

            // Wait so the process doesn't exit immediately (optional)
            Thread.sleep(10000); // wait 10 seconds

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
