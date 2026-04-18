import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public @WebServlet("/login")
public class SignupServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("uname");
        String password = request.getParameter("psw");

        try {
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/userdb", "root", "password");

            String sql = "SELECT password FROM users WHERE username=?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);

            var rs = stmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password");

                if (BCrypt.checkpw(password, storedHash)) {
                    response.getWriter().println("Login successful!");
                } else {
                    response.getWriter().println("Invalid password.");
                }
            } else {
                response.getWriter().println("User not found.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 
