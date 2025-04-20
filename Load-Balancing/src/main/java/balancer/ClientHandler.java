package balancer;
import java.io.*;
import java.net.*;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ServerNode backendServer;

    public ClientHandler(Socket clientSocket, ServerNode backendServer) {
        this.clientSocket = clientSocket;
        this.backendServer = backendServer;
    }
    
    private String getHeaderIgnoreCase(Map<String, String> headers, String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void run() {
        try (
            Socket serverSocket = new Socket(backendServer.getHost(), backendServer.getPort());
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            OutputStream clientOut = clientSocket.getOutputStream()
        ) {
            // Read HTTP request from client
            StringBuilder request = new StringBuilder();
            String line;
            int contentLength = 0;
            boolean isPost = false;

            while (!(line = clientReader.readLine()).isEmpty()) {
                request.append(line).append("\r\n");
            }
            request.append("\r\n");

            // Forward Request To Backend
            serverWriter.write(request.toString());
            serverWriter.flush();
            
            // Read Status Line from Backend
            String statusLine = serverReader.readLine();
            if (statusLine == null) throw new IOException("Empty from backend server");
            clientOut.write((statusLine + "\r\n").getBytes());

            // Read Headers from Backend
            Map<String, String> headers = new LinkedHashMap<>();
            while(!(line = serverReader.readLine()).isEmpty()) {
                int colon = line.indexOf(':');
                if (colon != -1){
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    headers.put(key, value);
                }
            }
            
            // Log backend response headers
            System.out.println("← " + backendServer + " responded: " + statusLine);
            System.out.println("   Content-Type: " + getHeaderIgnoreCase(headers, "Content-Type"));
            System.out.println("   Content-Length: " + headers.get("Content-Length"));

            // Inject custom header
            headers.put("X-Load-Balancer", "MyLoadBalancer JavaLB");

            // Forward headers to client
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String headerLine = entry.getKey() + ": " + entry.getValue() + "\r\n";
                clientOut.write(headerLine.getBytes());
            }
            clientOut.write("\r\n".getBytes());

            // Forward body
            InputStream backendIn = serverSocket.getInputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = backendIn.read(buffer)) != -1) {
                clientOut.write(buffer, 0, bytesRead);
            }

            clientOut.flush();

        } catch (IOException e) {
            System.err.println("Error handling client request: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
