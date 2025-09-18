package voting;

import java.io.Serializable;

/**
 * Candidate POJO. Implements Serializable (per requirements).
 */
public class Candidate implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private int voteCount;

    public Candidate(int id, String name, int voteCount) {
        this.id = id;
        this.name = name;
        this.voteCount = voteCount;
    }

    public Candidate(String name) {
        this(0, name, 0);
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

    @Override
    public String toString() {
        return name + " (Votes: " + voteCount + ")";
    }
}
