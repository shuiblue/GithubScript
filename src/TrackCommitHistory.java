import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by shuruiz on 10/8/17.
 */
public class TrackCommitHistory {
    static String token;
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String result_dir ;

    TrackCommitHistory() {
        final String current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);
        result_dir = current_dir+"/result/";

        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This function gets a list of forks of a repository
     * input: repo_url
     * output: fork list
     *
     * @param repo_url e.g. 'shuiblue/INFOX'
     */
    public String getActiveForkList(String repo_url) {

        String forkUrl = github_api_repo + repo_url + "/forks?access_token=" + token + "&page=";
        JsonUtility jsonUtility = new JsonUtility();

        StringBuilder sb = new StringBuilder();
        ArrayList<String> forks_has_forks = new ArrayList<>();

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

                    if ((int) fork_list_jsonObj.get("forks") > 0) {
                        forks_has_forks.add(name);
                    }


                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    String created_at = (String) fork_list_jsonObj.get("created_at");
                    String pushed_at = (String) fork_list_jsonObj.get("pushed_at");

                    try {
                        Date created_time = formatter.parse(created_at.replaceAll("Z$", "+0000"));
                        Date pushed_time = formatter.parse(pushed_at.replaceAll("Z$", "+0000"));
                        if (created_time.before(pushed_time)) {
                            if (name.length() > 0) {
                                sb.append(name + "," + repo_url + "," + created_at + "\n");
                            }
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                }
            } else {
                break;
            }
        }

        for (String fork : forks_has_forks) {
            sb.append(getActiveForkList(fork));
        }

        return sb.toString();
    }


    public ArrayList<String> getBranches(String forkUrl) {
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
            if (branch_array_json != null & branch_array_json.size() > 0) {
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
            String fork_url = fork.split(",")[0].replace("+", "%2B");
            String forkName = fork_url.split("/")[0];
            String email, user_name = null, created_at;
            JsonUtility jsonUtility = new JsonUtility();

            /** get fork point date */

            String three_month_ago = getThreeMonthBeforeForkTime(fork_url, jsonUtility);

            /** get email from user profile */
            ForkInfo fork_owner_info = getForkOwnerInfo(forkName);


            /**  get email from commit history when author= forkname */
            String commit_Url = github_api_repo + fork_url + "/commits?access_token=" + token + "&since=" + three_month_ago;

            for (int page = 1; page <= 300; page++) {
                ArrayList<String> commit_array_json = null;
                try {
                    commit_array_json = jsonUtility.readUrl(commit_Url + "&author=" + forkName + "&page=" + page);
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
                    if (page == 1 && !sb.toString().contains(forkName + ",")) {
                        sb.append(forkName + ",");
                    }
                    break;
                }
            }


            /** get email from commit history without author constraint, check user name **/
            for (int page = 1; page <= 300; page++) {

                ArrayList<String> commit_array_json = null;
                try {
                    commit_array_json = jsonUtility.readUrl(commit_Url + "&page=" + page);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (commit_array_json.size() > 0) {
                    for (String commit : commit_array_json) {
                        JSONObject commit_jsonObj = new JSONObject(commit);
                        JSONObject commit_block = (JSONObject) commit_jsonObj.get("commit");
                        JSONObject author = commit_block.getJSONObject("author");

                        if (user_name != null && author.get("name").equals(user_name)) {
                            email_set.add((String) author.get("email"));
                        }
                    }
                } else {
                    if (page == 1 && !sb.toString().contains(forkName + ",")) {
                        sb.append(forkName + ",");
                    }
                    break;
                }
            }
            if (!sb.toString().contains(forkName + ",")) {
                sb.append(forkName + "," + email_set.toString() + "\n");
            } else {
                sb.append(email_set.toString() + "\n");

            }

        }
        io.rewriteFile(sb.toString(), result_dir + repo_url + "/EmailList.txt");

    }

    public String getThreeMonthBeforeForkTime(String fork_url, JsonUtility jsonUtility) {
        String created_at;
        ArrayList<String> fork_array_json = null;
        try {
            fork_array_json = jsonUtility.readUrl(github_api_repo + fork_url + "?access_token=" + token);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String fork_info = fork_array_json.get(0);
        JSONObject fork_jsonObj = new JSONObject(fork_info);
        created_at = (String) fork_jsonObj.get("created_at");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        String three_month_ago = "";
        try {
            Date created_time = sdf.parse(created_at.replaceAll("Z$", "+0000"));
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(created_time); // parsed date and setting to calendar
            calendar.add(Calendar.MONTH, -3);  // number of days to add
            three_month_ago = sdf.format(calendar.getTime());  // End date
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return three_month_ago;
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
            String[] emailarray = removeBrackets(fork_contact_info.split(",\\[")[1]).split(",");
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
        sb.append("forkURL,upstreamURL,only_F,F->U," +
                "U->F,Only_F_commits,F->U_commits\n");
        StringBuilder sb_commit_origin = new StringBuilder();
        StringBuilder sb_removed_commits = new StringBuilder();
        StringBuilder sb_branch_list = new StringBuilder();

        io.rewriteFile("", result_dir + upstream_url + "/removed_commits.txt");
        io.rewriteFile(sb.toString(), result_dir + upstream_url + "/fork_contribution.csv");
        sb = new StringBuilder();
        io.rewriteFile(sb_commit_origin.toString(), result_dir + upstream_url + "/commits_origin_info.txt");
        io.rewriteFile(sb_branch_list.toString(), result_dir + upstream_url + "/commits_in_branch.txt");


        HashMap<String, ArrayList<String>> forkid_email = getForkIdMap(upstream_url);
        String[] forkArray = null;
        String upstreamName = upstream_url.split("/")[0];
        try {
            forkArray = io.readResult(result_dir + upstream_url + "/ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String fork : forkArray) {
//        String fork = "danzeeeman/ofxVideoRecorder,timscaffidi/ofxVideoRecorder";
            sb = new StringBuilder();
            String fork_url = fork.split(",")[0].replace("+", "%2B");
            String parent_url = fork.split(",")[1];
            sb.append(fork_url + ",");
            sb.append(parent_url + ",");
            String parent_id = parent_url.split("/")[0];
            String fork_owner_id = fork_url.split("/")[0];
            ArrayList<String> email_list = forkid_email.get(fork_owner_id);
            email_list.add(fork_owner_id);

            /** analyze commits in fork and in upstream from the fork owner  **/
            HashMap<String, HashSet<String>> commit_in_fork_map = getCommitsOfForkOwner(fork_url, email_list, getBranches(fork_url), parent_url);
            HashMap<String, HashSet<String>> commit_in_upstream_map = getCommitsOfForkOwner(parent_url, email_list, getBranches(parent_url), parent_url);


            HashSet<String> commit_in_fork_set = new HashSet<>();
            HashSet<String> copy_commit_in_fork_set = new HashSet<>();
            HashSet<String> merged_commit_set = new HashSet<>();

            commit_in_fork_map.forEach((k, v) ->
            {
                commit_in_fork_set.addAll(v);
                copy_commit_in_fork_set.addAll(v);
            });

            /** remove commit that are not from this fork  */

            HashMap<CommitType, HashSet<String>> commit_map = new HashMap<>();
            CommitType[] commitTypes = {CommitType.origin, CommitType.sync_upstream, CommitType.merge_commit, CommitType.other};
            for (CommitType c : commitTypes) {
                commit_map.put(c, new HashSet<>());
            }
            for (String commit : commit_in_fork_set) {
                /** check whether this commit is a contribution, or sync from upstream  **/
//                HashSet<CommitType> commitType_set = checkCommitType(commit, fork_url, email_list, parent_id);
                CommitType commitType = checkCommitType(commit, fork_url, email_list, parent_id);
                sb_commit_origin.append(commit + "," + commitType + "\n");
                if (!commitType.equals(CommitType.origin)) {
                    copy_commit_in_fork_set.remove(commit);
                }

                HashSet<String> tmp_commits = commit_map.get(commitType);
                tmp_commits.add(commit);
                commit_map.put(commitType, tmp_commits);

            }
            HashSet<String> updated_commit_in_fork_set = new HashSet<>();
            updated_commit_in_fork_set.addAll(copy_commit_in_fork_set);


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
                if (copy_commit_in_upstream_set.contains(commit_in_fork))
                    merged_commit_set.add(commit_in_fork);
                copy_commit_in_upstream_set.remove(commit_in_fork);
            }
            for (String commit_in_upstream : commit_in_upstream_set) {
                if (copy_commit_in_fork_set.contains(commit_in_upstream))
                    merged_commit_set.add(commit_in_upstream);
                copy_commit_in_fork_set.remove(commit_in_upstream);
            }

//   "fork_id, upstream, #commits in fork, #commits merged to upstream,
//    #original commits, #sync_upstream commits, #branch_merge commits, #PullRequest commits, #other, " +
//    "commits only in fork, commits only in upstream,merged commits\n");

            sb.append(updated_commit_in_fork_set.size()-commit_in_upstream_set.size() + "," + commit_in_upstream_set.size()
                    + "," + commit_map.get(CommitType.sync_upstream).size() + ","
                    + copy_commit_in_fork_set.toString().replace(",", "/") + "," + merged_commit_set.toString().replace(",", "/") + "\n");

            sb_removed_commits.append(fork_url + "," + parent_url + "----\n");
            commit_map.forEach((k, v) ->
            {
                sb_removed_commits.append(k + " :");
                for (String commit : v) {
                    sb_removed_commits.append(commit + ",");
                }
                sb_removed_commits.append("\n");
            });
            io.writeTofile(sb_removed_commits.toString(), result_dir + upstream_url + "/removed_commits.txt");
            io.writeTofile(sb.toString(), result_dir + upstream_url + "/fork_contribution.csv");
            io.writeTofile(sb_commit_origin.toString(), result_dir + upstream_url + "/commits_origin_info.txt");

            io.writeTofile(sb_branch_list.toString(), result_dir + upstream_url + "/commits_in_branch.txt");
        }


    }


    /**
     * This function get commits of the fork owner from a repository
     *
     * @param repo_url   repository url
     * @param email_list email list of the fork owner
     * @return a set of commit SHA
     */

    private HashMap<String, HashSet<String>> getCommitsOfForkOwner(String repo_url, ArrayList<String> email_list, ArrayList<String> branchList, String upstream_url) {
        HashMap<String, HashSet<String>> branch_commitList_map = new HashMap<>();
        boolean isFork = repo_url.equals(upstream_url) ? false : true;

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
                            if (commit_in_repo_json != null && commit_in_repo_json.size() > 0) {
                                for (String commit : commit_in_repo_json) {
                                    String sha = (String) new JSONObject(commit).get("sha");


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
     * This function check whether this commit is from the repository orginally, or not
     *
     * @param sha      commit SHA number
     * @param repo_url repositosy url
     * @return 0, if it is an original commit;
     * 1, if it is a internal merge commit (branch merge)
     * 2, sync with upstream
     * 3, merge PR
     */

    public CommitType checkCommitType(String sha, String repo_url, ArrayList<String> contact_info, String upstream_id) {
        JsonUtility jsonUtility = new JsonUtility();

        String userID = repo_url.split("/")[0];

        JSONObject commit_jsonObj = getJsonObject(sha, repo_url, jsonUtility);
        String userid = getAuthorID(commit_jsonObj);

        JSONObject commit = (JSONObject) commit_jsonObj.get("commit");
        String email = (String) ((JSONObject) commit.get("author")).get("email");
        String author = (String) ((JSONObject) commit.get("author")).get("name");

        HashSet<String> email_set = new HashSet<>();
        email_set.add(email);
        ForkInfo fork_Owner = new ForkInfo(userID, author, email_set);
        ForkInfo upstream_Owner = getForkOwnerInfo(upstream_id);

        if (fork_Owner.isSameAuthor(fork_Owner, upstream_Owner)) {
            return CommitType.sync_upstream;
        }

        if (userid.equals(upstream_id)) {
            return CommitType.sync_upstream;
        }


        String commit_msg = (String) commit.get("message");

//        if (commit_msg.contains("Merge remote-tracking branch") || commit_msg.contains("Merge branch")) {
//            return CommitType.sync_upstream;
//        }
//
//        if (commit_msg.contains("Merge pull request")) {
//            return CommitType.PullRequest;
//        }
//
//        if (commit_msg.contains("Merge branch")) {
//            return CommitType.merge_commit;
//        }

        JSONArray parents_json = (JSONArray) commit_jsonObj.get("parents");
        if (parents_json.length() == 1) {
            if (contact_info.contains(userid) || contact_info.contains(author) || contact_info.contains(email)) {
                return CommitType.origin;
            } else {
                return CommitType.other;
            }

        } else {
            String parent_sha_1 = (String) ((JSONObject) parents_json.get(0)).get("sha");
            String parent_sha_2 = (String) ((JSONObject) parents_json.get(1)).get("sha");
            ForkInfo parent_1 = getParentInfo(parent_sha_1, repo_url);
            ForkInfo parent_2 = getParentInfo(parent_sha_2, repo_url);

            parent_1.setOwnerID(getAuthorID(getJsonObject(parent_sha_1, repo_url, jsonUtility)));
            parent_2.setOwnerID(getAuthorID(getJsonObject(parent_sha_2, repo_url, jsonUtility)));

            if (upstream_Owner.isSameAuthor(parent_1, upstream_Owner) || upstream_Owner.isSameAuthor(parent_2, upstream_Owner)) {
                return CommitType.sync_upstream;
            }
//            else if (upstream_Owner.isSameAuthor(parent_1, fork_Owner) && upstream_Owner.isSameAuthor(parent_2, fork_Owner)) {
//                return CommitType.merge_commit;
//            }
            else {
                return CommitType.other;
            }

//            CommitType parent1 = checkCommitType(parent_sha_1, repo_url, contact_info, upstream_id);
//            CommitType parent2 = checkCommitType(parent_sha_2, repo_url, contact_info, upstream_id);
//
//            if (parent1.equals(CommitType.origin) && parent2.equals(CommitType.origin)) {
//                return CommitType.merge_commit;
//            } else if (parent1.equals(CommitType.origin)) {
//                return parent2;
//            } else if (parent2.equals(CommitType.origin)) {
//                return parent1;
//            } else {
//                return CommitType.other;
//            }
        }
    }

    private ForkInfo getParentInfo(String parent_sha_2, String repo_url) {
        ForkInfo parentInfo = new ForkInfo();
        JSONObject commit_jsonObj = getJsonObject(parent_sha_2, repo_url, new JsonUtility());
        if (!((JSONObject) commit_jsonObj.get("commit")).get("author").equals("null")) {
            parentInfo.setOwnerID("");
            JSONObject author = (JSONObject) ((JSONObject) commit_jsonObj.get("commit")).get("author");
            parentInfo.setFull_name((String) author.get("name"));
            String email = (String) author.get("email");
            HashSet<String> emails = new HashSet<>();
            emails.add(email);
            parentInfo.setEmails(emails);
        }
        return parentInfo;
    }

    public JSONObject getJsonObject(String sha, String repo_url, JsonUtility jsonUtility) {
        String commit_json = "";

        String commit_url = github_api_repo + repo_url + "/commits/" + sha.trim() + "?access_token=" + token;

        try {
            commit_json = jsonUtility.readUrl(commit_url).get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new JSONObject(commit_json);
    }

    private String getAuthorID(JSONObject commit_jsonObj) {
        String userid = "";
        if (!commit_jsonObj.get("author").toString().equals("null")) {
            userid = (String) ((JSONObject) commit_jsonObj.get("author")).get("login");
        }
        return userid;
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
        io.rewriteFile("", result_dir + upstream_url + "/pr_fork.txt");
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
                String forkName = fork.split("/")[0].replace("+", "%2B");
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
                            for (int i = 0; i < num_issue; i++) {
                                JSONArray items_json = issue_jsonObj.getJSONArray("items");
                                JSONObject pr_json = (JSONObject) items_json.getJSONObject(i).get("pull_request");
                                String pr_url = (String) pr_json.get("url");

                                String merge_state = getPRstate(pr_url);
                                sb.append(pr_url + "," + merge_state + ",");

                                /** get commit of this PR **/
                                ArrayList<String> pr_commitList = getPRcommits(pr_url);
                                sb.append(pr_commitList.toString() + "\n");
                            }

                        } else {
                            break;
                        }

                    }
                    io.writeTofile(sb.toString(), result_dir + upstream_url + "/pr_fork.txt");
                    sb = new StringBuilder();
                }
            }
        }

    }

    /**
     * This function get PR state: open, close, merged
     *
     * @param pr_url
     * @return string-- pr state
     */
    private String getPRstate(String pr_url) {

        JsonUtility jsonUtility = new JsonUtility();
        ArrayList<String> pr_array_json = null;
        try {
            pr_array_json = jsonUtility.readUrl(pr_url + "?access_token=" + token);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject pr_json = new JSONObject(pr_array_json.get(0));
        String merged_at = pr_json.get("merged_at").toString();
        String closed_at = pr_json.get("closed_at").toString();

        if (closed_at.equals("null")) {
            return "open";
        } else {
            if (merged_at.equals("null")) {
                return "closed";
            } else {
                return "merged";
            }
        }

    }

    /**
     * This function get commits of a Pull Request
     *
     * @param pr_url Pull request url
     * @return a list of commit sha
     */
    private ArrayList<String> getPRcommits(String pr_url) {
        ArrayList<String> commitList = new ArrayList<>();

        String commitsUrl = pr_url + "/commits?" + "access_token=" + token;
        JsonUtility jsonUtility = new JsonUtility();
        ArrayList<String> commits_array_json = null;
        try {
            commits_array_json = jsonUtility.readUrl(commitsUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (String commit : commits_array_json) {
            JSONObject commit_jsonObj = new JSONObject(commit);
            commitList.add((String) commit_jsonObj.get("sha"));
        }
        return commitList;
    }

    private void analyzeContribution(String upstream_url) {
        IO_Process io = new IO_Process();
        String[] commits_in_fork = null, commits_in_PR = null;
        StringBuilder sb = new StringBuilder();
        try {
            commits_in_fork = io.readResult(result_dir + upstream_url + "/fork_contribution.csv").split("\n");
            commits_in_PR = io.readResult(result_dir + upstream_url + "/pr_fork.txt").split("fork:");
        } catch (IOException e) {
            e.printStackTrace();
        }
        commits_in_fork[0] += ", #commits of unmerged PRs\n";
        sb.append(commits_in_fork[0]);
        for (int i = 1; i < commits_in_fork.length; i++) {
            List<String> commits_only_in_fork = Arrays.asList(removeBrackets(commits_in_fork[i].split(",")[5].split(",")[0]).split("/ "));
            List<String> commits_in_PR_from_fork = new ArrayList<>();
            String[] pr_array = {};
            pr_array = commits_in_PR[i].split("\n");

            if (pr_array.length > 1) {
                for (int j = 1; j < pr_array.length; j++) {
                    String[] pr_info = pr_array[j].split("\\[");
                    String pr_state = pr_info[0].split(",")[1];
                    if (!pr_state.equals("merged")) {
                        String[] rejected_commits = removeBrackets(pr_info[1]).split(", ");
                        for (String rej_commit : rejected_commits) {
                            if (commits_only_in_fork.contains(rej_commit)) {
                                commits_in_PR_from_fork.add(rej_commit);
                            }
                        }
                    }
                }
            }

            sb.append(commits_in_fork[i] + "," + commits_in_PR_from_fork.size() + "\n");
        }

        io.rewriteFile(sb.toString(), result_dir + upstream_url + "/authorName_result.csv");
    }


    public void getRamdomForks(String repo_url, int randomNum) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String[] forkArray = {};
        try {
            forkArray = io.readResult(result_dir + repo_url + "/all_ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        HashSet<Integer> random_index = new HashSet<>();
        while (random_index.size() < randomNum) {
            int rnd = new Random().nextInt(forkArray.length);
            if (!random_index.contains(rnd)) {
                random_index.add(rnd);
                sb.append(forkArray[rnd] + "\n");
            }
        }

        io.rewriteFile(sb.toString(), result_dir + repo_url + "/ActiveForklist.txt");
    }


    public String removeBrackets(String str) {
        return str.replace("[", "").replace("]", "");
    }


    public void classifyCommitsByAuthor_local() {
        TrackCommitHistory tch = new TrackCommitHistory();
//        String[] upstream_array = {"traverseda/pycraft,Smoothieware/Smoothieware,MarlinFirmware/Marlin","timscaffidi/ofxVideoRecorder"};
//        String[] upstream_array = {};
        String[] upstream_array = {"timscaffidi/ofxVideoRecorder"};
        for (String upstream_url : upstream_array) {


//            /** get active fork list for given repository **/
//            System.out.println("get all active forks...");
//            String activeForkList = tch.getActiveForkList(upstream_url);
//            io.rewriteFile(activeForkList, result_dir + upstream_url + "/all_ActiveForklist.txt");
//
//            System.out.println("randomly pick 30 active forks...");
//            tch.getRamdomForks(upstream_url);

            System.out.println("get fork member contact information...");
            /** analyze each fork owner's emails  **/
            tch.getForkMemberContactInfo(upstream_url);

            System.out.println("analyzing commit history...");
            /** analyze each fork commit history **/
            tch.analyzeCommitHistory(upstream_url);


//            System.out.println("get pull request information...");
            /** analyze each fork issue and PR history **/
            tch.getForkRelatedIssue(upstream_url);

            System.out.println("analyzing contribution of each fork..");
            tch.analyzeContribution(upstream_url);
        }

    }


    public void classifyCommitsByAuthor(String upstream_url) {
        TrackCommitHistory tch = new TrackCommitHistory();

        System.out.println("get fork member contact information...");
        /** analyze each fork owner's emails  **/
        tch.getForkMemberContactInfo(upstream_url);

        System.out.println("analyzing commit history...");
        /** analyze each fork commit history **/
        tch.analyzeCommitHistory(upstream_url);


//            System.out.println("get pull request information...");
        /** analyze each fork issue and PR history **/
        tch.getForkRelatedIssue(upstream_url);

        System.out.println("analyzing contribution of each fork..");
        tch.analyzeContribution(upstream_url);

    }

    public static void main(String[] args) {
        TrackCommitHistory trackCommitHistory = new TrackCommitHistory();
        trackCommitHistory.classifyCommitsByAuthor_local();
    }


    public ForkInfo getForkOwnerInfo(String forkName) {
        HashSet<String> email_set = new HashSet<>();
        JsonUtility jsonUtility = new JsonUtility();
        String email, user_name = null;
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
        if (!profile_jsonObj.get("name").toString().equals("null")) {
            user_name = (String) profile_jsonObj.get("name");
        }

        return new ForkInfo(forkName, user_name, email_set);
    }
}
