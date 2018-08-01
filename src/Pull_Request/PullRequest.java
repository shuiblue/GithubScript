package Pull_Request;

import java.util.HashSet;
import java.util.List;

/**
 * Created by shuruiz on 2/12/18.
 */
public class PullRequest {

    String pr_id;
    String forkName;
    String author;
    String created_at;
    String closed_at;
    String closed;
    String merged;


    String forkURL;
    HashSet<String> commitSet = new HashSet<>();

    public PullRequest(String pr_id, String forkName, String author, String forkURL, String created_at, String closed_at, String closed, String merged, HashSet<String> commitSet) {
        this.pr_id = pr_id;
        this.forkName = forkName;
        this.author = author;
        this.created_at = created_at;
        this.closed_at = closed_at;
        this.closed = closed;
        this.merged = merged;
        this.forkURL = forkURL;
        this.commitSet.addAll(commitSet);
    }

    public String getForkURL() {
        return forkURL;
    }

    public String getPr_id() {
        return pr_id;
    }

    public String getForkName() {
        return forkName;
    }


    public String getAuthor() {
        return author;
    }


    public String getCreated_at() {
        return created_at;
    }


    public String getClosed_at() {
        return closed_at;
    }


    public String getClosed() {
        return closed;
    }


    public String getMerged() {
        return merged;
    }


    public void setCommitSet(HashSet<String> commitSet) {
        this.commitSet.addAll(commitSet);
    }
}
