package voting;

/**
 * Simple data model for a Candidate in the voting system.
 */
public class Candidate {
    private final int id;
    private final String name;
    private final int voteCount;

    public Candidate(int id, String name, int voteCount) {
        this.id = id;
        this.name = name;
        this.voteCount = voteCount;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getVoteCount() {
        return voteCount;
    }
}
