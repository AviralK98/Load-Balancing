package balancer;
import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ServerNode backendServer;

    public ClientHandler(Socket clientSocket, ServerNode backendServer) {
        this.clientSocket = clientSocket;
        this.backendServer = backendServer;
    }

    @Override
    public void run() {
        try (
            Socket serverSocket = new Socket(backendServer.getHost(), backendServer.getPort());
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
            InputStream serverIn = serverSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream()
        ) {
            // Read HTTP request from client
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            int contentLength = 0;
            boolean isPost = false;

            while (!(line = clientReader.readLine()).isEmpty()) {
                requestBuilder.append(line).append("\r\n");
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
                if (line.startsWith("POST")) {
                    isPost = true;
                }
            }
            requestBuilder.append("\r\n");

            // Read POST body if applicable
            char[] body = new char[contentLength];
            if (isPost && contentLength > 0) {
                clientReader.read(body, 0, contentLength);
                requestBuilder.append(body);
            }

            // Send request to backend
            serverWriter.write(requestBuilder.toString());
            serverWriter.flush();

            // Pipe backend response back to client
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = serverIn.read(buffer)) != -1) {
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
