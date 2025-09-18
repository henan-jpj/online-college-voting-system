package voting;

import java.sql.*;

/**
 * DBHelper - lightweight SQLite helper.
 * Creates DB file voting.db in working directory and creates tables if they don't exist.
 */
public class DBHelper {
    private static final String DB_URL = "jdbc:sqlite:voting.db";

    static {
        // Ensure tables exist on first access
        try {
            Class.forName("org.sqlite.JDBC"); // safe no-op if driver auto-loads
        } catch (ClassNotFoundException ignored) {}

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA foreign_keys = ON;");

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS candidates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE NOT NULL," +
                "votes INTEGER DEFAULT 0" +
                ");"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS students (" +
                "regNo TEXT PRIMARY KEY NOT NULL," +
                "hasVoted INTEGER DEFAULT 0" +
                ");"
            );

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS settings (" +
                "key TEXT PRIMARY KEY NOT NULL," +
                "value TEXT" +
                ");"
            );

        } catch (SQLException e) {
            System.err.println("DB initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Utility to read a setting by key (returns null if not set).
     */
    public static String getSetting(String key) {
        String sql = "SELECT value FROM settings WHERE key = ?;";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            System.err.println("getSetting error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Insert or replace a setting.
     */
    public static void setSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO settings(key, value) VALUES(?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("setSetting error: " + e.getMessage());
        }
    }
}
