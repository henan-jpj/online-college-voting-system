package voting;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Core Voting System class with Admin and Student features.
 */
public class VotingSystem implements Serializable {
    private static final long serialVersionUID = 1L;

    private ArrayList<Candidate> candidates = new ArrayList<>();
    private HashSet<String> eligibleStudents = new HashSet<>();
    private HashSet<String> votedStudents = new HashSet<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private static final String DATA_FILE = "votingData.ser";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String STUDENT_PASSWORD_HASH =
        "2a908d16f5b9f464010e6a8e38a207f2ca6b3aced46b07c819133a82440b8f0a";
    private static final DateTimeFormatter DISPLAY_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private transient Scanner sc = new Scanner(System.in);

    // --- Main Menu ---
    public void mainMenu() {
        while (true) {
            System.out.println("\n=== College Voting System ===");
            System.out.println("1. Admin Login");
            System.out.println("2. Student Login");
            System.out.println("3. View Results");
            System.out.println("4. Exit");
            System.out.print("Choose: ");
            String choice = sc.nextLine();

            switch (choice) {
                case "1" -> adminLogin();
                case "2" -> studentLogin();
                case "3" -> viewResults();
                case "4" -> {
                    System.out.println("Exiting system. Goodbye!");
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // --- Admin Login ---
    private void adminLogin() {
        System.out.print("Enter Admin Password: ");
        String pass = sc.nextLine();
        if (!ADMIN_PASSWORD.equals(pass)) {
            System.out.println("Incorrect password!");
            return;
        }
        adminMenu();
    }

    // --- Admin Menu ---
    private void adminMenu() {
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. Add Candidate");
            System.out.println("2. Register Student");
            System.out.println("3. Set Voting Time");
            System.out.println("4. Publish Results");
            System.out.println("5. Back");
            System.out.print("Choose: ");
            String choice = sc.nextLine();

            switch (choice) {
                case "1" -> addCandidate();
                case "2" -> registerStudent();
                case "3" -> setVotingTime();
                case "4" -> publishResultsView();
                case "5" -> { return; }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // --- Add Candidate ---
    private void addCandidate() {
        if (startTime != null && LocalDateTime.now().isAfter(startTime)) {
            System.out.println("Cannot add candidates after voting has started.");
            return;
        }
        System.out.print("Enter candidate name: ");
        String name = sc.nextLine();
        candidates.add(new Candidate(name));
        saveData();
        System.out.println("Candidate added successfully.");
    }

    // --- Register Student ---
    private void registerStudent() {
        if (startTime != null && LocalDateTime.now().isAfter(startTime)) {
            System.out.println("Cannot register students after voting has started.");
            return;
        }
        System.out.print("Enter Student Register Number: ");
        String regNo = sc.nextLine().trim();
        if (eligibleStudents.contains(regNo)) {
            System.out.println("Student already registered.");
        } else {
            eligibleStudents.add(regNo);
            saveData();
            System.out.println("Student registered successfully.");
        }
    }

    // --- Set Voting Time ---
    private void setVotingTime() {
        try {
            System.out.print("Enter Start Time (yyyy-MM-dd HH:mm): ");
            startTime = LocalDateTime.parse(sc.nextLine(), DISPLAY_FORMAT);
            System.out.print("Enter End Time (yyyy-MM-dd HH:mm): ");
            endTime = LocalDateTime.parse(sc.nextLine(), DISPLAY_FORMAT);
            saveData();
            System.out.println("Voting time set successfully.");
        } catch (Exception e) {
            System.out.println("Invalid format! Please try again.");
        }
    }

    // --- Publish Results (Admin only) ---
    private void publishResultsView() {
        if (endTime == null) {
            System.out.println("Voting end time not set.");
            return;
        }
        if (LocalDateTime.now().isBefore(endTime)) {
            System.out.println("Voting is still in progress. Ends at: " + endTime.format(DISPLAY_FORMAT));
            return;
        }
        if (candidates.isEmpty()) {
            System.out.println("No candidates were registered.");
            return;
        }

        System.out.println("\n--- Final Results ---");
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Integer.compare(b.getVoteCount(), a.getVoteCount()));

        for (Candidate c : sorted) {
            System.out.println(c.getName() + " : " + c.getVoteCount());
        }

        saveResultsToFile(sorted);
        System.out.println("Results saved to results.txt");
    }

    private void saveResultsToFile(List<Candidate> sorted) {
        try (FileWriter writer = new FileWriter("results.txt")) {
            writer.write("--- Final Voting Results ---\n");
            writer.write("Published on: " + LocalDateTime.now().format(DISPLAY_FORMAT) + "\n\n");
            for (Candidate c : sorted) {
                writer.write(c.getName() + " : " + c.getVoteCount() + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving results: " + e.getMessage());
        }
    }

    // --- Student Login ---
    private void studentLogin() {
        System.out.print("Enter Register Number: ");
        String regNo = sc.nextLine().trim();
        if (!eligibleStudents.contains(regNo)) {
            System.out.println("You are not registered to vote.");
            return;
        }

        System.out.print("Enter Password: ");
        String pass = sc.nextLine();
        if (!STUDENT_PASSWORD_HASH.equals(hashPassword(pass))) {
            System.out.println("Incorrect password.");
            return;
        }

        if (startTime == null || endTime == null) {
            System.out.println("Voting time not set by admin.");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime) || now.isAfter(endTime)) {
            System.out.println("Voting not active. Voting is allowed between " +
                               startTime.format(DISPLAY_FORMAT) + " and " + endTime.format(DISPLAY_FORMAT));
            return;
        }

        if (votedStudents.contains(regNo)) {
            System.out.println("You have already voted.");
            return;
        }

        vote(regNo);
    }

    // --- Voting Process ---
    private void vote(String regNo) {
        if (candidates.isEmpty()) {
            System.out.println("No candidates available to vote.");
            return;
        }

        System.out.println("\n--- Candidates ---");
        for (int i = 0; i < candidates.size(); i++) {
            System.out.println((i + 1) + ". " + candidates.get(i).getName());
        }
        System.out.print("Choose candidate number: ");
        try {
            int choice = Integer.parseInt(sc.nextLine());
            if (choice < 1 || choice > candidates.size()) {
                System.out.println("Invalid choice.");
                return;
            }
            candidates.get(choice - 1).incrementVote();
            votedStudents.add(regNo);
            saveData();
            System.out.println("Vote cast successfully!");
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    // --- Public Results View (for students) ---
    private void viewResults() {
        if (endTime == null || LocalDateTime.now().isBefore(endTime)) {
            System.out.println("Voting is still in progress.");
            return;
        }
        if (candidates.isEmpty()) {
            System.out.println("No candidates were registered.");
            return;
        }
        System.out.println("\n--- Voting Results ---");
        for (Candidate c : candidates) {
            System.out.println(c.getName() + " : " + c.getVoteCount());
        }
    }

    // --- Utility: Hash Password ---
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Persistence ---
    public void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(this);
        } catch (IOException e) {
            System.out.println("Error saving data: " + e.getMessage());
        }
    }

    public static VotingSystem loadData() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            return (VotingSystem) ois.readObject();
        } catch (FileNotFoundException e) {
            return new VotingSystem();
        } catch (Exception e) {
            System.out.println("Error loading data: " + e.getMessage());
            return new VotingSystem();
        }
    }
}
