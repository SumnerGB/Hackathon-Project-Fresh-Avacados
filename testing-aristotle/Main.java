import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Main {

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

    public static void main(String[] args) throws Exception {
        users.add(new User("student1", "1234", "student"));
        users.add(new User("teacher1", "1234", "teacher"));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> serveFile(exchange, "index.html"));
        server.createContext("/register.html", exchange -> serveFile(exchange, "register.html"));
        server.createContext("/dashboard.html", exchange -> serveFile(exchange, "dashboard.html"));

        server.createContext("/api/register", Main::handleRegister);
        server.createContext("/api/login", Main::handleLogin);
        server.createContext("/api/users", Main::handleUsers);

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:8080");
    }

    static void serveFile(HttpExchange exchange, String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            String response = "File not found";
            exchange.sendResponseHeaders(404, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            return;
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static void handleRegister(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String username = getJsonValue(body, "username");
        String password = getJsonValue(body, "password");
        String role = getJsonValue(body, "role");

        if (username.isEmpty() || password.isEmpty() || role.isEmpty()) {
            sendText(exchange, 400, "All fields are required.");
            return;
        }

        if (!role.equals("student") && !role.equals("teacher")) {
            sendText(exchange, 400, "Role must be student or teacher.");
            return;
        }

        for (User user : users) {
            if (user.username.equalsIgnoreCase(username)) {
                sendText(exchange, 400, "Username already exists.");
                return;
            }
        }

        users.add(new User(username, password, role));
        sendText(exchange, 200, "User registered successfully.");
    }

    static void handleLogin(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String username = getJsonValue(body, "username");
        String password = getJsonValue(body, "password");

        for (User user : users) {
            if (user.username.equals(username) && user.password.equals(password)) {
                String json = "{"
                        + "\"username\":\"" + escape(user.username) + "\","
                        + "\"role\":\"" + escape(user.role) + "\""
                        + "}";
                sendJson(exchange, 200, json);
                return;
            }
        }

        sendText(exchange, 401, "Invalid username or password.");
    }

    static void handleUsers(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            json.append("{")
                .append("\"username\":\"").append(escape(user.username)).append("\",")
                .append("\"password\":\"").append(escape(user.password)).append("\",")
                .append("\"role\":\"").append(escape(user.role)).append("\"")
                .append("}");
            if (i < users.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        sendJson(exchange, 200, json.toString());
    }

    static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    static void sendText(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static void sendJson(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static String getJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIndex = json.indexOf(search);
        if (keyIndex == -1) return "";

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return "";

        int firstQuote = json.indexOf("\"", colonIndex + 1);
        if (firstQuote == -1) return "";

        int secondQuote = json.indexOf("\"", firstQuote + 1);
        if (secondQuote == -1) return "";

        return json.substring(firstQuote + 1, secondQuote);
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}