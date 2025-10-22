package voting;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * VotingGUI - The main graphical interface (Swing) for the college voting system.
 * Contains all the logic for admin and student operations, adapted from the
 * previous command-line interface (CLI) to use Swing components.
 */
public class VotingGUI extends JFrame {

    // Security constants
    private static final String ADMIN_PASSWORD = "admin";
    private static final String STUDENT_PASSWORD_HASH = "2a908d16f5b9f464010e6a8e38a207f2ca6b3aced46b07c819133a82440b8f0a"; // SHA-256 for "LBSCEK"

    // Time formatting constants
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // UI components
    private final JPanel mainPanel;

    public VotingGUI() {
        setTitle("College Voting System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        add(mainPanel);
        // Show the main menu when the GUI is created
        showMainMenu();
    }

    /**
     * Initializes and displays the main JFrame.
     */
    public void createAndShowGUI() {
        pack();
        setLocationRelativeTo(null); // Center the window
        setVisible(true);
    }

    // --- Utility Methods ---

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    private void showMainMenu() {
        mainPanel.removeAll();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("=== College Voting System ===");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(15));

        // Create buttons for main menu options
        String[] options = {"Admin Login", "Student Login", "View Results", "Exit"};
        for (int i = 0; i < options.length; i++) {
            JButton button = new JButton((i + 1) + ". " + options[i]);
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.addActionListener(e -> handleMainMenuChoice(e));
            mainPanel.add(button);
            mainPanel.add(Box.createVerticalStrut(10));
        }

        pack();
        revalidate();
        repaint();
    }

    private void handleMainMenuChoice(ActionEvent event) {
        String command = event.getActionCommand();
        if (command.contains("1. Admin Login")) {
            adminLogin();
        } else if (command.contains("2. Student Login")) {
            studentLogin();
        } else if (command.contains("3. View Results")) {
            viewResultsMain();
        } else if (command.contains("4. Exit")) {
            System.exit(0);
        }
    }

    // --- Admin Functions ---

    private void adminLogin() {
        String password = JOptionPane.showInputDialog(this, "Enter admin password:", "Admin Login", JOptionPane.PLAIN_MESSAGE);
        if (password == null) return; // User cancelled

        if (ADMIN_PASSWORD.equals(password)) {
            showAdminMenu();
        } else {
            showMessage("Incorrect admin password.");
        }
    }

    private void showAdminMenu() {
        mainPanel.removeAll();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("--- Admin Menu ---");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(15));

        String[] options = {"Add Candidate", "Bulk Register Students", "Set Voting Time", "Publish Results", "Back"};
        for (int i = 0; i < options.length; i++) {
            JButton button = new JButton((i + 1) + ". " + options[i]);
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.addActionListener(e -> handleAdminMenuChoice(e));
            mainPanel.add(button);
            mainPanel.add(Box.createVerticalStrut(10));
        }

        pack();
        revalidate();
        repaint();
    }

    private void handleAdminMenuChoice(ActionEvent event) {
        String command = event.getActionCommand();
        if (command.contains("1. Add Candidate")) {
            addCandidate();
        } else if (command.contains("2. Bulk Register Students")) {
            bulkRegisterStudentsMenu();
        } else if (command.contains("3. Set Voting Time")) {
            setVotingTime();
        } else if (command.contains("4. Publish Results")) {
            publishResults();
        } else if (command.contains("5. Back")) {
            showMainMenu();
        }
    }
    
    // Check if admin is allowed to add candidates/register students (before start time)
    private boolean adminAddAllowed() {
        LocalDateTime start = getStartTimeFromDB();
        if (start == null) return true; // If time isn't set, allow changes
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(start);
    }

    private void addCandidate() {
        if (!adminAddAllowed()) {
            showMessage("Cannot add candidates after voting has started.");
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Enter candidate name:", "Add Candidate", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            showMessage("Candidate addition aborted.");
            return;
        }
        name = name.trim();

        String sql = "INSERT INTO candidates(name) VALUES(?);";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
            showMessage("Candidate added: " + name);
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                showMessage("Error: Candidate '" + name + "' already exists.");
            } else {
                showMessage("Error adding candidate: " + e.getMessage());
            }
        }
    }

    private void bulkRegisterStudentsMenu() {
        if (!adminAddAllowed()) {
            showMessage("Cannot register students after voting has started.");
            return;
        }

        String[] departments = {"CS (KSD24CS)", "EEE (KSD24EEE)", "EC (KSD24EC)", "MECH (KSD24ME)"};
        String selectedDept = (String) JOptionPane.showInputDialog(
                this,
                "Select department to register 999 students (001-999):",
                "Bulk Registration",
                JOptionPane.PLAIN_MESSAGE,
                null,
                departments,
                departments[0]
        );

        if (selectedDept == null) return; // User cancelled

        String prefix = selectedDept.substring(selectedDept.indexOf('(') + 1, selectedDept.indexOf(')'));
        String deptName = selectedDept.substring(0, selectedDept.indexOf('(')).trim();

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Confirm registering 999 students for " + deptName + " (" + prefix + ")?",
                "Confirm Bulk Registration",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            registerStudentsBulk(prefix, deptName);
        }
    }

    private void registerStudentsBulk(String prefix, String deptName) {
        String sql = "INSERT OR IGNORE INTO students(regNo) VALUES(?)";
        int count = 0;

        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false); // Start transaction for speed
            for (int i = 1; i <= 999; i++) {
                String regNo = prefix + String.format("%03d", i);
                ps.setString(1, regNo);
                ps.addBatch();
                count++;
            }
            
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);

            showMessage("Successfully registered " + count + " potential students for " + deptName 
                      + " (" + prefix + "001 to " + prefix + "999).");

        } catch (SQLException e) {
            showMessage("Error during bulk registration: " + e.getMessage());
            try {
                DBHelper.getConnection().rollback();
            } catch (SQLException ex) {
                // Ignore rollback errors for simplicity
            }
        }
    }

    private void setVotingTime() {
        // Input fields for start and end time
        JTextField startField = new JTextField(20);
        JTextField endField = new JTextField(20);

        JPanel panel = new JPanel(new GridLayout(2, 2));
        panel.add(new JLabel("Start Time (yyyy-MM-dd HH:mm):"));
        panel.add(startField);
        panel.add(new JLabel("End Time (yyyy-MM-dd HH:mm):"));
        panel.add(endField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Set Voting Time", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String sstart = startField.getText().trim();
            String send = endField.getText().trim();

            try {
                LocalDateTime start = LocalDateTime.parse(sstart, INPUT_FORMAT);
                LocalDateTime end = LocalDateTime.parse(send, INPUT_FORMAT);

                if (!end.isAfter(start)) {
                    showMessage("End time must be after start time.");
                    return;
                }

                DBHelper.setSetting("startTime", start.toString());
                DBHelper.setSetting("endTime", end.toString());
                DBHelper.setSetting("resultsPublished", "false"); // Reset results status
                showMessage("Voting times saved: " + start.format(DISPLAY_FORMAT) + " to " + end.format(DISPLAY_FORMAT));
            } catch (DateTimeParseException e) {
                showMessage("Invalid date/time format. Use yyyy-MM-dd HH:mm");
            }
        }
    }

    private void publishResults() {
        LocalDateTime end = getEndTimeFromDB();
        if (end == null) {
            showMessage("Error: Voting end time is not set.");
            return;
        }

        if (LocalDateTime.now().isBefore(end)) {
            showMessage("Voting is still in progress. Cannot publish yet.\nEnds at: " + end.format(DISPLAY_FORMAT));
            return;
        }

        List<Candidate> results = fetchAllCandidatesSorted();
        if (results.isEmpty()) {
            showMessage("No candidates were registered.");
            return;
        }

        // Format results for display and file saving
        StringBuilder sb = new StringBuilder();
        sb.append("--- Final Voting Results ---\n");
        results.forEach(c -> sb.append(c.getName()).append(" : ").append(c.getVoteCount()).append("\n"));
        
        showMessage(sb.toString());

        // Save to results.txt
        saveResultsToFile(results);
        DBHelper.setSetting("resultsPublished", "true");
        showMessage("Results saved successfully to results.txt and published.");
    }

    private void saveResultsToFile(List<Candidate> results) {
        try (FileWriter fw = new FileWriter("results.txt")) {
            fw.write("--- Final Voting Results ---\n");
            fw.write("Published on: " + LocalDateTime.now().format(DISPLAY_FORMAT) + "\n\n");
            for (Candidate c : results) {
                fw.write(c.getName() + " : " + c.getVoteCount() + "\n");
            }
        } catch (IOException e) {
            showMessage("Error saving results file: " + e.getMessage());
        }
    }


    // --- Student Functions ---

    private void studentLogin() {
        String regNo = JOptionPane.showInputDialog(this, "Enter register number:", "Student Login", JOptionPane.PLAIN_MESSAGE);
        if (regNo == null || regNo.trim().isEmpty()) return;
        regNo = regNo.trim().toUpperCase();

        if (!isStudentRegistered(regNo)) {
            showMessage("Registration number not found. You are not registered to vote.");
            return;
        }

        JPasswordField passwordField = new JPasswordField(10);
        int option = JOptionPane.showConfirmDialog(this, passwordField, "Enter password (LBSCEK):", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) return;

        String pwd = new String(passwordField.getPassword());
        String hashed = sha256(pwd);

        if (!STUDENT_PASSWORD_HASH.equalsIgnoreCase(hashed)) {
            showMessage("Authentication failed: incorrect password.");
            return;
        }

        // Check voting time
        LocalDateTime start = getStartTimeFromDB();
        LocalDateTime end = getEndTimeFromDB();

        if (start == null || end == null) {
            showMessage("Voting time not set. Contact admin.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!(now.isAfter(start) && now.isBefore(end))) {
            showMessage("Voting is not active now.\nVoting window: " +
                start.format(DISPLAY_FORMAT) + " to " + end.format(DISPLAY_FORMAT));
            return;
        }

        if (hasStudentVoted(regNo)) {
            showMessage("You have already voted.");
            return;
        }

        // If all checks pass, allow voting
        castVote(regNo);
    }

    private void castVote(String regNo) {
        List<Candidate> list = fetchAllCandidates();
        if (list.isEmpty()) {
            showMessage("No candidates available.");
            return;
        }

        // Create options array for JOptionPane
        String[] candidateOptions = list.stream()
                                    .map(c -> c.getName())
                                    .toArray(String[]::new);

        String choiceName = (String) JOptionPane.showInputDialog(
                this,
                "Select a candidate to vote for:",
                "Cast Your Vote",
                JOptionPane.QUESTION_MESSAGE,
                null,
                candidateOptions,
                candidateOptions[0]
        );

        if (choiceName == null) { // User cancelled
            showMessage("Vote cancelled.");
            return;
        }

        // Find the selected candidate object
        Candidate sel = list.stream()
                            .filter(c -> c.getName().equals(choiceName))
                            .findFirst().orElse(null);

        if (sel == null) {
            showMessage("Invalid selection.");
            return;
        }
        
        // Execute atomic update
        String updateVote = "UPDATE candidates SET votes = votes + 1 WHERE id = ?;";
        String setVoted = "UPDATE students SET hasVoted = 1 WHERE regNo = ?;";
        
        try (Connection conn = DBHelper.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(updateVote);
                 PreparedStatement ps2 = conn.prepareStatement(setVoted)) {

                ps1.setInt(1, sel.getId());
                ps1.executeUpdate();

                ps2.setString(1, regNo);
                ps2.executeUpdate();

                conn.commit();
                showMessage("Vote recorded successfully for " + sel.getName() + ". Thank you!");
            } catch (SQLException ex) {
                conn.rollback();
                showMessage("Failed to record vote due to database error: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showMessage("Database connection error: " + e.getMessage());
        }
    }


    // --- Results Functions ---

    private void viewResultsMain() {
        LocalDateTime end = getEndTimeFromDB();
        if (end == null) {
            showMessage("Voting end time is not set yet.");
            return;
        }
        if (LocalDateTime.now().isBefore(end)) {
            showMessage("Voting is still in progress. Results are not yet final.");
            return;
        }
        
        List<Candidate> results = fetchAllCandidatesSorted();
        if (results.isEmpty()) {
            showMessage("No candidates were registered.");
            return;
        }
        
        StringBuilder sb = new StringBuilder("--- Voting Results ---\n\n");
        results.forEach(c -> sb.append(c.getName()).append(" : ").append(c.getVoteCount()).append("\n"));
        
        JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(sb.toString(), 10, 30)), 
                                      "Election Results", JOptionPane.PLAIN_MESSAGE);
    }

    // --- DB Helper Implementations ---

    private boolean isStudentRegistered(String regNo) {
        String sql = "SELECT 1 FROM students WHERE regNo = ?;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("DB error (isStudentRegistered): " + e.getMessage());
            return false;
        }
    }

    private boolean hasStudentVoted(String regNo) {
        String sql = "SELECT hasVoted FROM students WHERE regNo = ?;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("hasVoted") == 1;
                }
            }
        } catch (SQLException e) {
            System.err.println("DB error (hasStudentVoted): " + e.getMessage());
        }
        return false;
    }

    private LocalDateTime getStartTimeFromDB() {
        String s = DBHelper.getSetting("startTime");
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime getEndTimeFromDB() {
        String s = DBHelper.getSetting("endTime");
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
    
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
            System.err.println("DB error (fetchAllCandidates): " + e.getMessage());
        }
        return out;
    }

    private List<Candidate> fetchAllCandidatesSorted() {
        List<Candidate> out = new ArrayList<>();
        // Sort by votes DESC, then name ASC for tie-breaking
        String sql = "SELECT id, name, votes FROM candidates ORDER BY votes DESC, name ASC;";
        try (Connection conn = DBHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Candidate(rs.getInt("id"), rs.getString("name"), rs.getInt("votes")));
            }
        } catch (SQLException e) {
            System.err.println("DB error (fetchAllCandidatesSorted): " + e.getMessage());
        }
        return out;
    }


    // --- Security Helper ---

    /**
     * Generates the SHA-256 hash of the input string.
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x & 0xff));
            return sb.toString();
        } catch (Exception e) {
            // In a real app, handle this gracefully. Here, we throw a runtime exception.
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }

    /**
     * Helper to correctly size the main window components after a view change.
     */
    @Override
    public void pack() {
        super.pack();
        // Ensure minimum size for better appearance
        int width = Math.max(300, getWidth());
        int height = Math.max(250, getHeight());
        setSize(width, height);
    }
}
