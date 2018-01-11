import java.util.HashSet;
import java.util.Objects;

/**
 * Created by shuruiz on 11/20/17.
 */
public class ForkInfo {
    String ownerID;
    String full_name;
    HashSet<String> emails = new HashSet<>();

    ForkInfo() {

    }

    ForkInfo(String ownerID, String full_name, HashSet<String> emails) {
        this.ownerID = ownerID;
        this.full_name = full_name;
        this.emails.addAll(emails);
    }


    public String getFull_name() {
        return full_name;
    }

    public void setFull_name(String full_name) {
        this.full_name = full_name;
    }


    public String getOwnerID() {
        return ownerID;
    }

    public void setOwnerID(String ownerID) {
        this.ownerID = ownerID;
    }

    public HashSet<String> getEmails() {
        return emails;
    }

    public void setEmails(HashSet<String> emails) {
        this.emails = emails;
    }

    public boolean isSameAuthor(ForkInfo forkInfo_1, ForkInfo forkInfo_2) {
            boolean hasCommonEmail = false;
        for (String email : forkInfo_1.emails) {
            hasCommonEmail = forkInfo_2.emails.contains(email);
            if (hasCommonEmail) break;
        }

        return forkInfo_1.ownerID.equals(forkInfo_2.ownerID) || forkInfo_1.full_name.equals(forkInfo_2.full_name)||hasCommonEmail;
    }
}
