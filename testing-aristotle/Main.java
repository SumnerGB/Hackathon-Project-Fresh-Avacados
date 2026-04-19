import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
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
    static final File APP_DIR = resolveAppDir();
    static final File USERS_FILE = new File(APP_DIR, "users.txt");

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

    //Attendance tracking
    static class Attendance {
        String blockId;
        List<String> students; // list of student usernames attending
        
        Attendance(String blockId) {
            this.blockId = blockId;
            this.students = new ArrayList<>();
        }
    }
    
    static List<Attendance> attendanceRecords = new ArrayList<>();

    //Main
    public static void main(String[] args) throws Exception {
        loadUsers();

        if (users.isEmpty()) {
            users.add(new User("student1", "1234", "student"));
            users.add(new User("teacher1", "1234", "teacher"));
            saveUsers();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/register", Main::handleRegister);
        server.createContext("/api/login", Main::handleLogin);
        server.createContext("/api/users", Main::handleUsers);
        server.createContext("/api/account/delete", Main::handleDeleteAccount);

        // ================= NEW API ROUTES =================
        server.createContext("/api/slots/create", Main::handleCreateSlot); // teacher only
        server.createContext("/api/slots/book", Main::handleBookSlot);     // student only
        server.createContext("/api/slots/list", Main::handleListSlots);    // everyone
        
        // Attendance routes
        server.createContext("/api/attendance/toggle", Main::handleToggleAttendance); // student only
        server.createContext("/api/attendance/get", Main::handleGetAttendance);       // everyone

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
        File file = resolveStaticFile(fileName);
        if (!file.exists() || !file.isFile()) {
            sendText(exchange, 404, "File not found");
            return;
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        String contentType = getContentType(file.getName());
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
        saveUsers();
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

    static void handleDeleteAccount(HttpExchange exchange) throws IOException {
        addCors(exchange);

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String username = getJsonValue(body, "username");

        if (username.isEmpty()) {
            sendText(exchange, 400, "Missing username");
            return;
        }

        int userIndex = -1;
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).username.equals(username)) {
                userIndex = i;
                break;
            }
        }

        if (userIndex == -1) {
            sendText(exchange, 404, "Account not found");
            return;
        }

        users.remove(userIndex);
        removeUserData(username);
        saveUsers();
        sendText(exchange, 200, "Account deleted");
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

    //Toggle attendance for a student on an office hours block
    static void handleToggleAttendance(HttpExchange exchange) throws IOException {
        addCors(exchange);

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String blockId = getJsonValue(body, "blockId");
        String username = getJsonValue(body, "username");

        if (blockId.isEmpty() || username.isEmpty()) {
            sendText(exchange, 400, "Missing blockId or username");
            return;
        }

        // Find or create attendance record for this block
        Attendance attendanceRecord = null;
        for (Attendance a : attendanceRecords) {
            if (a.blockId.equals(blockId)) {
                attendanceRecord = a;
                break;
            }
        }

        if (attendanceRecord == null) {
            attendanceRecord = new Attendance(blockId);
            attendanceRecords.add(attendanceRecord);
        }

        // Toggle attendance
        int studentIndex = attendanceRecord.students.indexOf(username);
        if (studentIndex > -1) {
            // Remove student
            attendanceRecord.students.remove(studentIndex);
            sendText(exchange, 200, "Attendance removed");
        } else {
            // Add student
            attendanceRecord.students.add(username);
            sendText(exchange, 200, "Attendance added");
        }
    }

    //Get attendance count and list for a block
    static void handleGetAttendance(HttpExchange exchange) throws IOException {
        addCors(exchange);

        String query = exchange.getRequestURI().getQuery();
        String blockId = "";

        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("blockId=")) {
                    blockId = URLDecoder.decode(param.substring(8), StandardCharsets.UTF_8);
                    break;
                }
            }
        }

        if (blockId.isEmpty()) {
            sendText(exchange, 400, "Missing blockId parameter");
            return;
        }

        // Find attendance record
        Attendance attendanceRecord = null;
        for (Attendance a : attendanceRecords) {
            if (a.blockId.equals(blockId)) {
                attendanceRecord = a;
                break;
            }
        }

        // Build response
        StringBuilder json = new StringBuilder("{");
        if (attendanceRecord != null) {
            json.append("\"count\":").append(attendanceRecord.students.size()).append(",");
            json.append("\"students\":[");
            for (int i = 0; i < attendanceRecord.students.size(); i++) {
                json.append("\"").append(attendanceRecord.students.get(i)).append("\"");
                if (i < attendanceRecord.students.size() - 1) json.append(",");
            }
            json.append("]");
        } else {
            json.append("\"count\":0,\"students\":[]");
        }
        json.append("}");

        sendJson(exchange, 200, json.toString());
    }

    static File resolveAppDir() {
        File projectSubdir = new File("testing-aristotle");
        if (projectSubdir.isDirectory()) {
            return projectSubdir;
        }

        File currentDir = new File(".");
        if (new File(currentDir, "MaristHours.html").exists()) {
            return currentDir;
        }

        return currentDir;
    }

    static File resolveStaticFile(String fileName) {
        String normalized = fileName == null ? "" : fileName.replace("\\", "/");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.startsWith("testing-aristotle/")) {
            normalized = normalized.substring("testing-aristotle/".length());
        }

        if (normalized.isEmpty()) {
            normalized = "index.html";
        }

        return new File(APP_DIR, normalized);
    }

    static void loadUsers() throws IOException {
        users.clear();

        File file = USERS_FILE;
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split("\t", -1);
                if (parts.length != 3) continue;

                String username = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String password = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                String role = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);

                users.add(new User(username, password, role));
            }
        }
    }

    static void saveUsers() throws IOException {
        File file = USERS_FILE;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            for (User u : users) {
                String line =
                    URLEncoder.encode(u.username, StandardCharsets.UTF_8) + "\t" +
                    URLEncoder.encode(u.password, StandardCharsets.UTF_8) + "\t" +
                    URLEncoder.encode(u.role, StandardCharsets.UTF_8);
                writer.write(line);
                writer.newLine();
            }
        }
    }

    static void removeUserData(String username) {
        for (int i = slots.size() - 1; i >= 0; i--) {
            Slot slot = slots.get(i);
            if (username.equals(slot.createdBy)) {
                slots.remove(i);
            } else if (username.equals(slot.bookedBy)) {
                slot.bookedBy = null;
            }
        }

        for (int i = attendanceRecords.size() - 1; i >= 0; i--) {
            Attendance attendance = attendanceRecords.get(i);
            attendance.students.remove(username);
            if (attendance.students.isEmpty()) {
                attendanceRecords.remove(i);
            }
        }
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
