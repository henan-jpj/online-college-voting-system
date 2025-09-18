package voting;

import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * VotingSystem - main app logic using SQLite.
 *
 * Admin password: "admin"
 * Student password (plain) : "LBSCEK" (we never store it; we compare SHA-256)
 * Provided SHA-256 hash for "LBSCEK":
 * 2a908d16f5b9f464010e6a8e38a207f2ca6b3aced46b07c819133a82440b8f0a
 */
public class VotingSystem {
    private final Scanner sc;
    private static final String ADMIN_PASSWORD = "admin";
    private static final String STUDENT_PASSWORD_HASH = 
        "2a908d16f5b9f464010e6a8e38a207f2ca6b3aced46b07c819133a82440b8f0a";
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public VotingSystem(Scanner sc) {
        this.sc = sc;
    }

    // ---------- Main Menu ----------
    public void mainMenu() {
        while (true) {
            System.out.println("\n=== College Voting System ===");
            System.out.println("1. Admin Login");
            System.out.println("2. Student Login");
            System.out.println("3. View Results");
            System.out.println("4. Exit");
            System.out.print("Choose: ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": adminLogin(); break;
                case "2": studentLogin(); break;
                case "3": viewResultsMain(); break;
                case "4": System.out.println("Goodbye!"); return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    // ---------- Admin ----------
    private void adminLogin() {
        System.out.print("Enter admin password: ");
        String pass = sc.nextLine().trim();
        if (!ADMIN_PASSWORD.equals(pass)) {
            System.out.println("Incorrect admin password.");
            return;
        }
        adminMenu();
    }

    private void adminMenu() {
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. Add Candidate");
            System.out.println("2. Register Student");
            System.out.println("3. Set Voting Time");
            System.out.println("4. Publish Results");
            System.out.println("5. Back");
            System.out.print("Choose: ");
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1": addCandidate(); break;
                case "2": registerStudent(); break;
                case "3": setVotingTime(); break;
                case "4": publishResults(); break;
                case "5": return;
                default: System.out.println("Invalid choice.");
            }
        }
    }

    private LocalDateTime getStartTimeFromDB() {
        String s = DBHelper.getSetting("startTime");
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime getEndTimeFromDB() {
        String s = DBHelper.getSetting("endTime");
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean adminAddAllowed() {
        LocalDateTime start = getStartTimeFromDB();
        if (start == null) return true;
        // Disallow adding candidates or registering students once voting has started (start reached)
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(start);
    }

    private void addCandidate() {
        if (!adminAddAllowed()) {
            System.out.println("Cannot add candidates after voting has started.");
            return;
        }
        System.out.print("Enter candidate name: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("Aborted.");
            return;
        }
        String sql = "INSERT INTO candidates(name) VALUES(?);";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
            System.out.println("Candidate added: " + name);
        } catch (SQLException e) {
            System.out.println("Error adding candidate: " + e.getMessage());
        }
    }

    private void registerStudent() {
        if (!adminAddAllowed()) {
            System.out.println("Cannot register students after voting has started.");
            return;
        }
        System.out.print("Enter student register number: ");
        String reg = sc.nextLine().trim();
        if (reg.isEmpty()) {
            System.out.println("Aborted.");
            return;
        }
        String sql = "INSERT INTO students(regNo) VALUES(?);";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reg);
            ps.executeUpdate();
            System.out.println("Student registered: " + reg);
        } catch (SQLException e) {
            System.out.println("Error registering student: " + e.getMessage());
        }
    }

    private void setVotingTime() {
        try {
            System.out.print("Enter start time (yyyy-MM-dd HH:mm): ");
            String sstart = sc.nextLine().trim();
            LocalDateTime start = LocalDateTime.parse(sstart, INPUT_FORMAT);

            System.out.print("Enter end time (yyyy-MM-dd HH:mm): ");
            String send = sc.nextLine().trim();
            LocalDateTime end = LocalDateTime.parse(send, INPUT_FORMAT);

            if (!end.isAfter(start)) {
                System.out.println("End time must be after start time.");
                return;
            }

            DBHelper.setSetting("startTime", start.toString());
            DBHelper.setSetting("endTime", end.toString());
            DBHelper.setSetting("resultsPublished", "false");
            System.out.println("Voting times saved: " + start.format(DISPLAY_FORMAT) + " to " + end.format(DISPLAY_FORMAT));
        } catch (Exception e) {
            System.out.println("Invalid date/time format. Use yyyy-MM-dd HH:mm");
        }
    }

    private void publishResults() {
        LocalDateTime end = getEndTimeFromDB();
        if (end == null) {
            System.out.println("End time not set. Set voting time first.");
            return;
        }
        if (LocalDateTime.now().isBefore(end)) {
            System.out.println("Voting still in progress. Cannot publish yet. Ends at: " + end.format(DISPLAY_FORMAT));
            return;
        }

        List<Candidate> results = fetchAllCandidatesSorted();
        if (results.isEmpty()) {
            System.out.println("No candidates to publish.");
            return;
        }

        System.out.println("\n--- Final Results ---");
        results.forEach(c -> System.out.println(c.getName() + " : " + c.getVoteCount()));

        // Save to results.txt
        saveResultsToFile(results);
        DBHelper.setSetting("resultsPublished", "true");
        System.out.println("Results saved to results.txt");
    }

    private void saveResultsToFile(List<Candidate> results) {
        try (FileWriter fw = new FileWriter("results.txt")) {
            fw.write("--- Final Voting Results ---\n");
            fw.write("Published on: " + LocalDateTime.now().format(DISPLAY_FORMAT) + "\n\n");
            for (Candidate c : results) {
                fw.write(c.getName() + " : " + c.getVoteCount() + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving results file: " + e.getMessage());
        }
    }

    // ---------- Student ----------
    private void studentLogin() {
        System.out.print("Enter register number: ");
        String reg = sc.nextLine().trim();
        if (!isStudentRegistered(reg)) {
            System.out.println("You are not registered to vote.");
            return;
        }

        System.out.print("Enter password: ");
        String pwd = sc.nextLine();
        String hashed = sha256(pwd);
        if (!STUDENT_PASSWORD_HASH.equalsIgnoreCase(hashed)) {
            System.out.println("Authentication failed: incorrect password.");
            return;
        }

        LocalDateTime start = getStartTimeFromDB();
        LocalDateTime end = getEndTimeFromDB();
        if (start == null || end == null) {
            System.out.println("Voting time not set. Contact admin.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        // Students allowed only after start and before end (strict)
        if (!(now.isAfter(start) && now.isBefore(end))) {
            System.out.println("Voting is not active now. Voting window: " +
                start.format(DISPLAY_FORMAT) + " to " + end.format(DISPLAY_FORMAT));
            return;
        }

        if (hasStudentVoted(reg)) {
            System.out.println("You have already voted.");
            return;
        }

        castVote(reg);
    }

    private boolean isStudentRegistered(String reg) {
        String sql = "SELECT 1 FROM students WHERE regNo = ?;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println("DB error: " + e.getMessage());
            return false;
        }
    }

    private boolean hasStudentVoted(String reg) {
        String sql = "SELECT hasVoted FROM students WHERE regNo = ?;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reg);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("hasVoted") == 1;
                }
            }
        } catch (SQLException e) {
            System.out.println("DB error: " + e.getMessage());
        }
        return false;
    }

    private void castVote(String reg) {
        List<Candidate> list = fetchAllCandidates();
        if (list.isEmpty()) {
            System.out.println("No candidates available.");
            return;
        }

        System.out.println("\n--- Candidates ---");
        for (int i = 0; i < list.size(); i++) {
            Candidate c = list.get(i);
            System.out.println((i + 1) + ". " + c.getName());
        }
        System.out.print("Choose number to vote (or 0 to cancel): ");
        String input = sc.nextLine().trim();
        int choice;
        try {
            choice = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
            return;
        }
        if (choice == 0) {
            System.out.println("Cancelled.");
            return;
        }
        if (choice < 1 || choice > list.size()) {
            System.out.println("Invalid choice.");
            return;
        }

        Candidate sel = list.get(choice - 1);
        // atomic update
        String updateVote = "UPDATE candidates SET votes = votes + 1 WHERE id = ?;";
        String setVoted = "UPDATE students SET hasVoted = 1 WHERE regNo = ?;";
        try (Connection conn = DBHelper.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(updateVote);
                 PreparedStatement ps2 = conn.prepareStatement(setVoted)) {

                ps1.setInt(1, sel.getId());
                ps1.executeUpdate();

                ps2.setString(1, reg);
                ps2.executeUpdate();

                conn.commit();
                System.out.println("Vote recorded. Thank you!");
            } catch (SQLException ex) {
                conn.rollback();
                System.out.println("Failed to record vote: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("DB error: " + e.getMessage());
        }
    }

    // ---------- View Results (Main menu) ----------
    private void viewResultsMain() {
        LocalDateTime end = getEndTimeFromDB();
        if (end == null) {
            System.out.println("Voting end time not set yet.");
            return;
        }
        if (LocalDateTime.now().isBefore(end)) {
            System.out.println("Voting is still in progress.");
            return;
        }
        List<Candidate> results = fetchAllCandidatesSorted();
        if (results.isEmpty()) {
            System.out.println("No candidates were registered.");
            return;
        }
        System.out.println("\n--- Voting Results ---");
        results.forEach(c -> System.out.println(c.getName() + " : " + c.getVoteCount()));
    }

    // ---------- DB helpers for candidates ----------
    private List<Candidate> fetchAllCandidates() {
        List<Candidate> out = new ArrayList<>();
        String sql = "SELECT id, name, votes FROM candidates ORDER BY id ASC;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Candidate(rs.getInt("id"), rs.getString("name"), rs.getInt("votes")));
            }
        } catch (SQLException e) {
            System.out.println("DB error: " + e.getMessage());
        }
        return out;
    }

    private List<Candidate> fetchAllCandidatesSorted() {
        List<Candidate> out = new ArrayList<>();
        String sql = "SELECT id, name, votes FROM candidates ORDER BY votes DESC, name ASC;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Candidate(rs.getInt("id"), rs.getString("name"), rs.getInt("votes")));
            }
        } catch (SQLException e) {
            System.out.println("DB error: " + e.getMessage());
        }
        return out;
    }

    // ---------- Password hashing ----------
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // public alias used (keeps same naming as previous)
    public static String hashPassword(String p) {
        return sha256(p);
    }
}
