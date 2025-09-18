package voting;

/**
 * Entry point of the Voting System application.
 */
public class Main {
    public static void main(String[] args) {
        VotingSystem system = VotingSystem.loadData();
        system.mainMenu();
    }
}
