package Repository;

import Util.IO_Process;
import Util.JsonUtility;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by shuruiz on 11/20/17.
 */
public class ForkInfo {
    String ownerID;
    String full_name;
    HashSet<String> emails = new HashSet<>();
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, passResult_dir, apiResult_dir;
    static String myUrl, user, pwd;
    static String github_api_repo = "https://api.github.com/repos/";
    static String token;
    public ForkInfo(){
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            apiResult_dir = output_dir + "shurui_crossRef.cache/";
            passResult_dir = output_dir + "shurui_crossRef.cache_pass/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public ForkInfo(String ownerID, String full_name, HashSet<String> emails) {
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

        return forkInfo_1.ownerID.equals(forkInfo_2.ownerID) || forkInfo_1.full_name.equals(forkInfo_2.full_name) || hasCommonEmail;
    }


    public static void main(String[] args) {
        new ForkInfo();
        IO_Process io = new IO_Process();
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/mod_repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String repoUrl : repos) {
            StringBuilder sb = getForkInfo(repoUrl);
            io.writeTofile(sb.toString(), output_dir + "repoInfo.txt");
        }
    }

    public static StringBuilder getForkInfo(String forkURL) {
        StringBuilder sb = new StringBuilder();

        String forkUrl = github_api_repo + forkURL + "?access_token=" + token;
        JsonUtility jsonUtility = new JsonUtility();

        ArrayList<String> fork_info_json = null;
        try {
            fork_info_json = jsonUtility.readUrl(forkUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (fork_info_json.size() > 0) {
            JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));
            sb.append(forkURL+ ",");
            sb.append(fork_jsonObj.get("stargazers_count") + ",");
            sb.append(fork_jsonObj.get("watchers_count") + ",");
            sb.append(fork_jsonObj.get("has_issues") + ",");
            sb.append(fork_jsonObj.get("network_count") + ",");
            sb.append(fork_jsonObj.get("subscribers_count") );
            sb.append("\n");
        }


        return sb;
    }
}
