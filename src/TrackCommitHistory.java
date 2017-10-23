import jdk.nashorn.internal.parser.JSONParser;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
            ArrayList<String> fork_array_json = null;
            try {
                fork_array_json = jsonUtility.readUrl(forkUrl + page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (fork_array_json.size() > 0) {
                for (String fork : fork_array_json) {
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
                                sb.append(name + "\n");
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

    private ArrayList<String> getBranches(String forkUrl) {
        ArrayList<String> branchList = new ArrayList<>();
        String branches_Url = github_api_repo + forkUrl + "/branches?access_token=" + token + "&page=";
        JsonUtility jsonUtility = new JsonUtility();

        StringBuilder sb = new StringBuilder();
        for (int page = 1; page <= 50; page++) {
            ArrayList<String> branch_array_json = null;
            try {
                branch_array_json = jsonUtility.readUrl(branches_Url + page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (branch_array_json.size() > 0) {
                for (String branch : branch_array_json) {
                    JSONObject branch_list_jsonObj = new JSONObject(branch);
                    branchList.add((String) branch_list_jsonObj.get("name"));
                }
            } else {
                break;
            }
        }
        return branchList;
    }


    /**
     * This function collect fork owners's email from profile and commit history
     *
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
            HashSet<String> email_set = new HashSet<>();
            String fork_url = fork.split(",")[0];
            String forkName = fork_url.split("/")[0];
            String email;


            JsonUtility jsonUtility = new JsonUtility();
            /* get email from user profile */
            String profile_url = github_api_user + forkName + "?access_token=" + token;
            ArrayList<String> profile_array_json = null;
            try {
                profile_array_json = jsonUtility.readUrl(profile_url);
            } catch (Exception e) {
                e.printStackTrace();
            }

            String profile = profile_array_json.get(0);
            JSONObject profile_jsonObj = new JSONObject(profile);
            if (!profile.contains("\"email\":null")) {
                email = profile_jsonObj.getString("email").toString();
                if (email.contains("@")) {
                    email_set.add(email);
                }
            }
            /*  get email from commit history */


            String commit_Url = github_api_repo + fork_url + "/commits?access_token=" + token + "&author=" + forkName + "&page=";
            String commitList = null;

            for (int page = 1; page <= 300; page++) {

                ArrayList<String> commit_array_json = null;
                try {
                    commit_array_json = jsonUtility.readUrl(commit_Url + page);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (commit_array_json.size() > 0) {
                    for (String commit : commit_array_json) {


                        JSONObject commit_jsonObj = new JSONObject(commit);
                        JSONObject commit_block = (JSONObject) commit_jsonObj.get("commit");

                        JSONObject author = commit_block.getJSONObject("author");
                        email = (String) author.get("email");
                        if (email.contains("@")) {
                            email_set.add(email);
                        }
                    }
                } else {
                    break;
                }
            }

            sb.append(forkName + "," + email_set.toString() + "\n");
        }
        io.rewriteFile(sb.toString(), result_dir + repo_url + "/EmailList.txt");

    }

    public HashMap<String, ArrayList<String>> getForkIdMap(String repo_url) {
        HashMap<String, ArrayList<String>> forkid_email = new HashMap<>();
        IO_Process io = new IO_Process();
        String[] forkArray = null;
        String[] forkowner_Array = null;
        try {
            forkowner_Array = io.readResult(result_dir + repo_url + "/EmailList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String fork_contact_info : forkowner_Array) {
            ArrayList<String> email_arrayList = new ArrayList<>();
            String owner_id = fork_contact_info.split(",\\[")[0];
            String[] emailarray = fork_contact_info.split(",\\[")[1].replace("]", "").split(",");
            for (String email : emailarray) {
                email_arrayList.add(email.trim());
            }
            forkid_email.put(owner_id, email_arrayList);
        }
        return forkid_email;
    }

    /**
     * @param repo_url
     */
    public void analyzeCommitHistory(String repo_url) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        sb.append("fork_id, commits in fork, commit in upstream\n");
        HashMap<String, ArrayList<String>> forkid_email = getForkIdMap(repo_url);
        String[] forkArray = null;

        try {
            forkArray = io.readResult(result_dir + repo_url + "/ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String fork : forkArray) {
            sb.append(fork + ",");
            String fork_url = fork.split(",")[0];
            String fork_owner_id = fork_url.split("/")[0];
            ArrayList<String> email_list = forkid_email.get(fork_owner_id);
            email_list.add(fork_owner_id);

            HashMap<String, HashSet<String>> commit_in_fork_map = getCommitsOfForkOwner(fork_url, email_list, getBranches(fork_url), repo_url);
            HashMap<String, HashSet<String>> commit_in_upstream_map = getCommitsOfForkOwner(repo_url, email_list, getBranches(repo_url), repo_url);

            final int[] num_Commit_in_fork = {0};
            commit_in_fork_map.forEach((k,v)->
                    num_Commit_in_fork[0] +=v.size());
            final int[] num_Commit_in_upstream = {0};
            commit_in_upstream_map.forEach((k,v)->
                    num_Commit_in_upstream[0] +=v.size());
            sb.append(num_Commit_in_fork[0] + "," + num_Commit_in_upstream[0] + "\n");


        }
        io.rewriteFile(sb.toString(), result_dir + repo_url + "/fork_contribution.csv");
    }


    /**
     * This function get commits of the fork owner from a repository
     *
     * @param repo_url   repository url
     * @param email_list email list of the fork owner
     * @return a set of commit SHA
     */

    private HashMap<String, HashSet<String>> getCommitsOfForkOwner(String repo_url, ArrayList<String> email_list, ArrayList<String> branchList, String upstream_url) {
        HashSet<String> commit_in_repo_set = new HashSet<>();
        HashMap<String, HashSet<String>> branch_commitList_map = new HashMap<>();

        for (String author : email_list) {
            JsonUtility jsonUtility = new JsonUtility();
            ArrayList<String> commit_in_repo_json = new ArrayList<>();
            HashMap<String, Boolean> branch_checkList = new HashMap<>();

            for (String branch : branchList) {
                for (int page = 1; page <= 300; page++) {
                    if (branch_checkList.size() < branchList.size()) {
                        if (branch_checkList.get(branch) == null) {

                            String commit_in_repo_url = github_api_repo + repo_url + "/commits?access_token=" + token + "&sha=" + branch + "&author=" + author + "&page=";

                            try {
                                commit_in_repo_json = jsonUtility.readUrl(commit_in_repo_url + page);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (commit_in_repo_json.size() > 0) {
                                for (String commit : commit_in_repo_json) {
                                    String sha = (String) new JSONObject(commit).get("sha");
                                    commit_in_repo_set.add(sha);
                                    HashSet<String> commitList = branch_commitList_map.get(branch);
                                    if (commitList == null) {
                                        commitList = new HashSet<>();
                                    }
                                    commitList.add(sha);
                                    branch_commitList_map.put(branch, commitList);
                                }
                            } else {
                                branch_checkList.put(branch, true);

                            }
                        }else{
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }
        return branch_commitList_map;
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
        tch.getActiveForkList(repo_url);
        /** analyze each fork owner's emails  **/
        tch.getForkMemberContactInfo(repo_url);

        /** analyze each fork commit history **/
        tch.analyzeCommitHistory(repo_url);


    }


}
