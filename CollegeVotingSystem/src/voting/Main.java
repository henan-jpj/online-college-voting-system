package voting;

import javax.swing.SwingUtilities;

/**
 * Main entry point for the college voting application.
 * Initializes the database connection and launches the graphical interface (GUI).
 */
public class Main {
    public static void main(String[] args) {
        // --- Database Initialization ---
        try {
            // Ensure tables are created and connection is tested before GUI starts
            DBHelper.getConnection().close(); 
            System.out.println("Database 'voting.db' initialized successfully.");
        } catch (Exception e) {
            System.err.println("FATAL: Could not initialize database connection.");
            e.printStackTrace();
            return; 
        }

        // --- Application Start (GUI) ---
        // Swing applications MUST be run on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            new VotingGUI().createAndShowGUI();
        });
    }
}
