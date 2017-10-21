import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;

/**
 * Created by shuruiz on 10/8/17.
 */
public class TrackCommitHistory {
    static String token;
    static String github_page = "https://github.com/";
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String result_dir = "/Users/shuruiz/Box Sync/GithubScript-New/result/";

    /**
     * This function gets a list of forks of a repository
     * input: repo_url
     * output: fork list
     *
     * @param repo_url e.g. 'shuiblue/INFOX'
     */
    public void getActiveForkList(String repo_url) {
        String repo = repo_url.split("/")[1];
        String forkUrl = github_api_repo + repo_url + "/forks?access_token=" + token + "&page=";
        JsonUtility jsonUtility = new JsonUtility();

        StringBuilder sb = new StringBuilder();
        for (int page = 1; page <= 300; page++) {
            String json = null;
            try {
                json = jsonUtility.readUrl(forkUrl + page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!json.equals("[]")) {
                for (String fork : json.split(",\\{")) {
                    if (!fork.startsWith("{")) {
                        fork = "{" + fork;
                    }
                    if (!fork.endsWith("}}")) {
                        fork = fork + "}}";
                    }
                    JSONObject fork_list_jsonObj = new JSONObject(fork);
                    String name = (String) fork_list_jsonObj.get("full_name");

                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    String created_at = (String) fork_list_jsonObj.get("created_at");
                    String pushed_at = (String) fork_list_jsonObj.get("pushed_at");

                    try {
                        Date created_time = formatter.parse(created_at.replaceAll("Z$", "+0000"));
                        Date pushed_time = formatter.parse(pushed_at.replaceAll("Z$", "+0000"));
                        if (created_time.before(pushed_time)) {
                            if (name.length() > 0) {
                                System.out.println(name);
                                sb.append(name + "," + created_at + "," + pushed_at + "\n");
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                }
            } else {
                break;
            }

            IO_Process io = new IO_Process();
          /*  active fork list:          fork, created_at, pushed_at    */
            io.rewriteFile(sb.toString(), result_dir + repo_url + "/ActiveForklist.txt");
        }
    }


    /**
     * This function collect fork owners's email from profile and commit history
     * @param repo_url repository url
     */
    private void getForkMemberContactInfo(String repo_url) {
        IO_Process io = new IO_Process();
        String[] forkArray = null;
        StringBuilder sb = new StringBuilder();
        try {
            forkArray = io.readResult(result_dir + repo_url + "/ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String fork : forkArray) {

            System.out.println(fork);
            HashSet<String> email_set = new HashSet<>();
            String fork_url = fork.split(",")[0];
            String forkName = fork_url.split("/")[0];
            String email;


            JsonUtility jsonUtility = new JsonUtility();
            /* get email from user profile */
            String profile_url = github_api_user + forkName + "?access_token=" + token;
            String profile = null;
            try {
                profile = jsonUtility.readUrl(profile_url);
            } catch (Exception e) {
                e.printStackTrace();
            }
            JSONObject profile_jsonObj = new JSONObject(profile);
            if(!profile.contains("\"email\":null")) {
                email = profile_jsonObj.getString("email").toString();
                if (email.contains("@")) {
                    email_set.add(email);
                }
            }
            /*  get email from commit history */
            String commit_Url = github_api_repo + fork_url + "/commits?access_token=" + token + "&author=" + forkName;
            String commit = null;
            try {
                commit = jsonUtility.readUrl(commit_Url);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!commit.equals("[]")) {
                JSONObject commit_jsonObj = new JSONObject(commit + "]}");
                JSONObject commit_block = (JSONObject) commit_jsonObj.get("commit");

                JSONObject author = commit_block.getJSONObject("author");
                email = (String) author.get("email");
                if(email.contains("@")){
                    email_set.add(email);
                }

            }

            sb.append(forkName + "," + email_set.toString() + "\n");
        }
        io.rewriteFile(sb.toString(), result_dir + repo_url + "/EmailList.txt");

    }


    public void analyzeCommitHistory(String fork_url) {
        String forkUrl = github_api_repo + fork_url + "/forks?access_token=" + token + "&page=";
        JsonUtility jsonUtility = new JsonUtility();

        StringBuilder sb = new StringBuilder();
        for (int page = 1; page <= 300; page++) {
            String json = null;
            try {
                json = jsonUtility.readUrl(forkUrl + page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!json.equals("[]")) {
                for (String commit : json.split(",\\{")) {
                    if (!commit.startsWith("{")) {
                        commit = "{" + commit;
                    }
                    if (!commit.endsWith("}}")) {
                        commit = commit + "}}";
                    }
                    JSONObject commit_jsonObj = new JSONObject(commit);
                    String committer = (String) commit_jsonObj.get("committer");
                    System.out.println();
                }
            } else {
                break;
            }
        }


    }

    public static void main(String[] args) {
        TrackCommitHistory tch = new TrackCommitHistory();
        IO_Process io = new IO_Process();
        String repo_url = "timscaffidi/ofxVideoRecorder";

        try {
            token = io.readResult(result_dir + "/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /** get active fork list for given repository **/
//        tch.getActiveForkList(repo_url);
        /** analyze each fork owner's emails  **/
//        tch.getForkMemberContactInfo(repo_url);

        /** analyze each fork commit history **/
//                String forkName= fork.split("/")[0];
//                tch.analyzeCommitHistory(forkName);


    }


}
