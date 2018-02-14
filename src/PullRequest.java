import java.util.HashSet;
import java.util.List;

/**
 * Created by shuruiz on 2/12/18.
 */
public class PullRequest {
    String forkName, author , created_at ,closed_at, closed ,merged ;
    HashSet<String> commitSet=new HashSet<>();

    public PullRequest(String forkName, String author, String created_at, String closed_at, String closed, String merged) {
        this.forkName = forkName;
        this.author = author;
        this.created_at = created_at;
        this.closed_at = closed_at;
        this.closed = closed;
        this.merged = merged;
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
