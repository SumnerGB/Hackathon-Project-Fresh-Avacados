import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Main {


//User
    static class User {
        String username;
        String password;
        String role;

        User(String username, String password, String role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    static List<User> users = new ArrayList<>();

    //Schedule System
    static class Slot {
        String id;
        String teacher;
        String time;
        String bookedBy; // student username (null if free)
        String createdBy;
        Slot(String id, String teacher, String time, String createdBy) {
            this.id = id;
            this.teacher = teacher;
            this.time = time;
            this.createdBy = createdBy;
            this.bookedBy = null;
        }
    }

    static List<Slot> slots = new ArrayList<>();
    static int slotIdCounter = 1;

    //Main
    public static void main(String[] args) throws Exception {
        users.add(new User("student1", "1234", "student"));
        users.add(new User("teacher1", "1234", "teacher"));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/register", Main::handleRegister);
        server.createContext("/api/login", Main::handleLogin);
        server.createContext("/api/users", Main::handleUsers);

        // ================= NEW API ROUTES =================
        server.createContext("/api/slots/create", Main::handleCreateSlot); // teacher only
        server.createContext("/api/slots/book", Main::handleBookSlot);     // student only
        server.createContext("/api/slots/list", Main::handleListSlots);    // everyone

        // Catch-all route for static files (HTML, images, CSS, JS, etc.)
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                serveFile(exchange, "index.html");
            } else {
                serveFile(exchange, path.substring(1)); // Remove leading slash
            }
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:8080");
    }

    //File Server
    static void serveFile(HttpExchange exchange, String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            sendText(exchange, 404, "File not found");
            return;
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        String contentType = getContentType(fileName);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);

        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    //Determine Content-Type based on file extension
    static String getContentType(String fileName) {
        if (fileName.endsWith(".html")) return "text/html; charset=UTF-8";
        if (fileName.endsWith(".css")) return "text/css";
        if (fileName.endsWith(".js")) return "text/javascript";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".gif")) return "image/gif";
        if (fileName.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    //Register
    static void handleRegister(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String username = getJsonValue(body, "username");
        String password = getJsonValue(body, "password");
        String role = getJsonValue(body, "role");

        for (User u : users) {
            if (u.username.equalsIgnoreCase(username)) {
                sendText(exchange, 400, "Username already exists");
                return;
            }
        }

        users.add(new User(username, password, role));
        sendText(exchange, 200, "User registered");
    }

    //Login
    static void handleLogin(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String username = getJsonValue(body, "username");
        String password = getJsonValue(body, "password");

        for (User u : users) {
            if (u.username.equals(username) && u.password.equals(password)) {
                String json = "{\"username\":\"" + u.username + "\",\"role\":\"" + u.role + "\"}";
                sendJson(exchange, 200, json);
                return;
            }
        }

        sendText(exchange, 401, "Invalid login");
    }

    //User
    static void handleUsers(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            json.append("{\"username\":\"").append(u.username)
               .append("\",\"password\":\"").append(u.password)
                .append("\",\"role\":\"").append(u.role).append("\"}");
            if (i < users.size() - 1) json.append(",");
        }
        json.append("]");

        sendJson(exchange, 200, json.toString());
    }

    //Teacher only slots
    static void handleCreateSlot(HttpExchange exchange) throws IOException {
        addCors(exchange);

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String teacher = getJsonValue(body, "teacher");
        String role = getJsonValue(body, "role");
        String time = getJsonValue(body, "time");

        if (!role.equals("teacher")) {
            sendText(exchange, 403, "Only teachers can create slots");
            return;
        }

        String id = String.valueOf(slotIdCounter++);
        String createdBy = getJsonValue(body, "createdBy");
        slots.add(new Slot(id, teacher, time, createdBy));

        sendText(exchange, 200, "Slot created with id " + id);
    }

    //Book Slot Student only 
    static void handleBookSlot(HttpExchange exchange) throws IOException {
        addCors(exchange);

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String slotId = getJsonValue(body, "slotId");
        String student = getJsonValue(body, "student");
        String role = getJsonValue(body, "role");

        if (!role.equals("student")) {
            sendText(exchange, 403, "Only students can book slots");
            return;
        }

        for (Slot s : slots) {
            if (s.id.equals(slotId)) {

                if (s.bookedBy != null && !s.bookedBy.equals(student)) {
                    sendText(exchange, 403, "You cannot modify another student's booking");
                return;
                }
                if (s.bookedBy != null && s.bookedBy.equals(student)) {
                    s.bookedBy = null;
                    sendText(exchange, 200, "Booking cancelled");
                return;
                }

                s.bookedBy = student;
                sendText(exchange, 200, "Booked successfully");
                return;
            }
        }

        sendText(exchange, 404, "Slot not found");
    }

    //list slots
    static void handleListSlots(HttpExchange exchange) throws IOException {
        addCors(exchange);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            json.append("{")
                .append("\"id\":\"").append(s.id).append("\",")
                .append("\"teacher\":\"").append(s.teacher).append("\",")
                .append("\"time\":\"").append(s.time).append("\",")
                .append("\"bookedBy\":\"").append(s.bookedBy).append("\"")
                .append("}");

            if (i < slots.size() - 1) json.append(",");
        }
        json.append("]");

        sendJson(exchange, 200, json.toString());
    }

    //helpers
    static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static void sendText(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static void sendJson(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static String getJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int i = json.indexOf(search);
        if (i == -1) return "";

        int c = json.indexOf(":", i);
        int q1 = json.indexOf("\"", c + 1);
        int q2 = json.indexOf("\"", q1 + 1);

        if (q1 == -1 || q2 == -1) return "";
        return json.substring(q1 + 1, q2);
    }
}