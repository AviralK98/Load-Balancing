# Java Load Balancer

A fully functional load balancer implemented in Java with support for:

- ✅ Health Checks
- ⚖️ Weighted Round-Robin Load Balancing
- 🖊️ HTTP Header Manipulation
- 📃 Live Web Admin Dashboard
- ▶️ Remote Start/Stop of Backends

---

## ⚡ Requirements

- Java 11+ (tested with Java 21)
- Python 3 (for test backends)
- Curl (for manual API testing)
- VS Code or any IDE (optional)

---

## 📂 Project Structure

```bash
Load-Balancer/
├── public/
│   └── admin.html          # Web admin dashboard UI
├── servers.txt             # List of backend servers and weights
├── src/balancer/           # Java source files
│   ├── LoadBalancer.java
│   ├── ServerManager.java
│   ├── ServerNode.java
│   ├── ClientHandler.java
│   └── WebAdminServer.java
└── out/                    # Compiled class files
```

---

## ✅ How to Compile

```bash
# Compile all Java source files into 'out' directory
javac -d out src/balancer/*.java
```

---

## 🔄 How to Run the Load Balancer

```bash
# Run the main load balancer
java -cp out balancer.LoadBalancer
```

You should see:

```
[WebAdmin] Management API running on http://localhost:7070
Load balancer listening on port 8080...
```

---

## 🔎 How to Test Features

### 1. ✅ Health Checks

Backends in `servers.txt` are automatically health-checked every 5s:

**Example **``**:**

```
localhost:9001:3
localhost:9002:1
```

Run a test server:

```bash
python3 -m http.server 9001
```

### 2. ⚖️ Weighted Load Balancing

Weights are defined in `servers.txt`. Higher weight = more requests.

Run:

```bash
curl http://localhost:8080
```

Observe console:

```
Forwarding to: localhost:9001 (weight3)
```

### 3. 🖊️ HTTP Header Manipulation

- Adds `X-LoadBalancer: JavaLB` to all backend responses
- Logs `Content-Type`, `Content-Length`, etc.

### 4. 📃 Web Admin Dashboard

Open in browser:

```
http://localhost:7070
```

Features:

- View health and weight of servers
- Add or remove backends via forms

### 5. ▶️ Start/Stop Backends Remotely

Click "Start" or "Stop" in the dashboard to control:

```bash
python3 -m http.server <port>
```

Alternatively:

```bash
# Add a new backend
curl -X POST -d "localhost:9003:2" http://localhost:7070/add

# Start it
curl -X POST -d "localhost:9003" http://localhost:7070/start

# Stop it
curl -X POST -d "localhost:9003" http://localhost:7070/stop
```

---

## ✨ Coming Soon / Ideas

- 🔒 Admin login
- 📊 Request metrics + live charts
- 🔍 Logging & analytics

---

Built with Java&#x20;

