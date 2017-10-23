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
    static String github_api_search = "https://api.github.com/search/";
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
                if (!email.equals("")) {
                    email_arrayList.add(email.trim());
                }
            }
            forkid_email.put(owner_id, email_arrayList);
        }
        return forkid_email;
    }

    /**
     * @param upstream_url
     */
    public void analyzeCommitHistory(String upstream_url) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb_branch_list = new StringBuilder();
        sb.append("fork_id, commits in fork, commit in upstream,commits only in fork, commits only in upstream,merged commits\n");
        HashMap<String, ArrayList<String>> forkid_email = getForkIdMap(upstream_url);
        String[] forkArray = null;
        String upstreamName = upstream_url.split("/")[0];
        try {
            forkArray = io.readResult(result_dir + upstream_url + "/ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String fork : forkArray) {
            sb.append(fork + ",");
            String fork_url = fork.split(",")[0];
            String fork_owner_id = fork_url.split("/")[0];
            ArrayList<String> email_list = forkid_email.get(fork_owner_id);
            email_list.add(fork_owner_id);

            /** analyze commits in fork and in upstream from the fork owner  **/
            HashMap<String, HashSet<String>> commit_in_fork_map = getCommitsOfForkOwner(fork_url, email_list, getBranches(fork_url), upstream_url);
            HashMap<String, HashSet<String>> commit_in_upstream_map = getCommitsOfForkOwner(upstream_url, email_list, getBranches(upstream_url), upstream_url);


            HashSet<String> commit_in_fork_set = new HashSet<>();
            HashSet<String> copy_commit_in_fork_set = new HashSet<>();
            HashSet<String> merged_commit_set = new HashSet<>();

            sb_branch_list.append("commits in fork:" + fork_url + "\n");
            commit_in_fork_map.forEach((k, v) ->
            {
                commit_in_fork_set.addAll(v);
                copy_commit_in_fork_set.addAll(v);
                sb_branch_list.append("branch: " + k + "\n" + v.toString() + "\n");
            });

            HashSet<String> commit_in_upstream_set = new HashSet<>();
            HashSet<String> copy_commit_in_upstream_set = new HashSet<>();
            sb_branch_list.append("commits in upstream:" + upstream_url + "\n");
            commit_in_upstream_map.forEach((k, v) ->
            {
                commit_in_upstream_set.addAll(v);
                copy_commit_in_upstream_set.addAll(v);
                sb_branch_list.append("branch: " + k + "\n" + v.toString() + "\n");
            });

            for (String commit_in_fork : commit_in_fork_set) {
                if (copy_commit_in_upstream_set.contains(commit_in_fork)) merged_commit_set.add(commit_in_fork);
                copy_commit_in_upstream_set.remove(commit_in_fork);
            }
            for (String commit_in_upstream : commit_in_upstream_set) {
                if (copy_commit_in_fork_set.contains(commit_in_upstream)) merged_commit_set.add(commit_in_upstream);
                copy_commit_in_fork_set.remove(commit_in_upstream);
            }


            sb.append(commit_in_fork_set.size() + "," + commit_in_upstream_set.size() + "," + copy_commit_in_fork_set.toString().replace(",", "/") + "," + copy_commit_in_upstream_set.toString().replace(",", "/") + "," + merged_commit_set.toString().replace(",", "/") + "\n");


        }
        io.rewriteFile(sb.toString(), result_dir + upstream_url + "/fork_contribution.csv");
        io.rewriteFile(sb_branch_list.toString(), result_dir + upstream_url + "/commits_in_branches.txt");
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
                        } else {
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

    /**
     * This function gets all the issues and PRs related to each fork
     *
     * @param upstream_url upstream repo url
     */
    public void getForkRelatedIssue(String upstream_url) {
        String[] forkArray = null;
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String upstreamName = upstream_url.split("/")[0];
        try {
            forkArray = io.readResult(result_dir + upstream_url + "/ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

//        String[] issue_PR = {"issue", "pr"};
        String[] issue_PR = {"pr"};
        for (String str : issue_PR) {
            for (String fork : forkArray) {
                sb.append("fork: " + fork + "\n");
                String forkName = fork.split("/")[0];
                //https://api.github.com/search/issues?q=author%3AJobLeonard+user%3Atimscaffidi+type%3Aissue
                String issueUrl = github_api_search + "issues?q=author%3A" + forkName + "+user%3A" + upstreamName + "+type%3A" + str + "&access_token=" + token;
                JsonUtility jsonUtility = new JsonUtility();
                ArrayList<String> issue_array_json = null;
                try {
                    issue_array_json = jsonUtility.readUrl(issueUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (issue_array_json.size() > 0) {
                    for (String issue : issue_array_json) {
                        JSONObject issue_jsonObj = new JSONObject(issue);
                        int num_issue = (int) issue_jsonObj.get("total_count");
                        if (num_issue > 0) {
                            JSONObject pr_json = (JSONObject) issue_jsonObj.getJSONArray("items").getJSONObject(0).get("pull_request");
                            sb.append(pr_json.get("url") + "\n");
                            System.out.println();
                        } else {
                            break;
                        }
                    }
                }
            }
        }
        io.rewriteFile(sb.toString(), result_dir + upstream_url + "/pr_fork.txt");
    }


    public static void main(String[] args) {
        TrackCommitHistory tch = new TrackCommitHistory();
        IO_Process io = new IO_Process();
        String upstream_url = "timscaffidi/ofxVideoRecorder";

        try {
            token = io.readResult(result_dir + "/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        /** get active fork list for given repository **/
        tch.getActiveForkList(upstream_url);
        /** analyze each fork owner's emails  **/
        tch.getForkMemberContactInfo(upstream_url);

        /** analyze each fork commit history **/
        tch.analyzeCommitHistory(upstream_url);

        /** analyze each fork issue and PR history **/
        tch.getForkRelatedIssue(upstream_url);

    }


}
