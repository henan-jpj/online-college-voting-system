package voting;

import java.util.Scanner;

/**
 * Main entry for the voting app.
 */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        VotingSystem vs = new VotingSystem(sc);
        vs.mainMenu();
        sc.close();
    }
}
