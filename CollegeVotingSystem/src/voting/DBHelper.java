package voting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles all direct interactions with the SQLite database.
 */
public class DBHelper {

    // SQLite connection string
    private static final String URL = "jdbc:sqlite:voting.db";

    // Static initializer to ensure tables are created when the class is loaded
    static {
        try {
            Class.forName("org.sqlite.JDBC"); // Load SQLite driver
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC driver not found: " + e.getMessage());
        }
        createTables();
    }

    /**
     * Establishes and returns a database connection.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /**
     * Creates the necessary tables if they don't exist.
     */
    private static void createTables() {
        // Table for students
        String studentSql = "CREATE TABLE IF NOT EXISTS students ("
                + "regNo TEXT PRIMARY KEY,"
                + "hasVoted INTEGER DEFAULT 0"
                + ");";

        // Table for candidates
        String candidateSql = "CREATE TABLE IF NOT EXISTS candidates ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL UNIQUE,"
                + "votes INTEGER DEFAULT 0"
                + ");";

        // Table for application settings (e.g., voting time)
        String settingsSql = "CREATE TABLE IF NOT EXISTS settings ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(studentSql);
            stmt.execute(candidateSql);
            stmt.execute(settingsSql);
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
        }
    }

    /**
     * Retrieves an application setting by key.
     */
    public static String getSetting(String key) {
        String sql = "SELECT value FROM settings WHERE key = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error reading setting: " + e.getMessage());
        }
        return null;
    }

    /**
     * Saves or updates an application setting.
     */
    public static void setSetting(String key, String value) {
        // Use REPLACE INTO to insert or update the value
        String sql = "REPLACE INTO settings(key, value) VALUES(?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving setting: " + e.getMessage());
        }
    }
}
