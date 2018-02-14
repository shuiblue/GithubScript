import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by shuruiz on 2/12/18.
 */
public class Fork {
    String forkName;
    HashSet<String> mergedCommits = new HashSet<>();
    HashSet<String> rejectedCommits = new HashSet<>();

    public HashSet<String> getPendingCommits() {
        return pendingCommits;
    }

    HashSet<String> pendingCommits = new HashSet<>();
    HashSet<String> all_PR_commits = new HashSet<>();
    int num_pr;

    public String getForkName() {
        return forkName;
    }

    public HashSet<String> getMergedCommits() {
        return mergedCommits;
    }

    public HashSet<String> getRejectedCommits() {
        return rejectedCommits;
    }

    public HashSet<String> getAll_PR_commits() {
        return all_PR_commits;
    }

    public int getNum_pr() {
        return num_pr;
    }

    public ArrayList<Integer> getPr_size_in_commit() {
        return pr_size_in_commit;
    }

    ArrayList<Integer> pr_size_in_commit = new ArrayList<>();


    public Fork(String forkName) {
        this.forkName = forkName;
    }

    public void setMergedCommits(HashSet<String> mergedCommits) {
        this.mergedCommits = mergedCommits;
    }

    public void setRejectedCommits(HashSet<String> rejectedCommits) {
        this.rejectedCommits = rejectedCommits;
    }

    public void setPendingCommits(HashSet<String> pendingCommits) {
        this.pendingCommits = pendingCommits;
    }
}
