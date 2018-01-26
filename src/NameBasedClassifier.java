import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by shuruiz on 10/8/17.
 */
public class NameBasedClassifier {
    static String token;
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String result_dir;

    static String tmpDirPath;

    NameBasedClassifier() {
        final String current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);
        result_dir = current_dir + "/result/";
        tmpDirPath = current_dir + "/cloneRepos/";
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
    public String getActiveForkList(String repo_url, boolean hasTimeConstraint) {

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

                    try {
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                        String created_at = (String) fork_list_jsonObj.get("created_at");
                        Date created_time = formatter.parse(created_at.replaceAll("Z$", "+0000"));

                        if (hasTimeConstraint) {
                            String pushed_at = (String) fork_list_jsonObj.get("pushed_at");
                            Date pushed_time = formatter.parse(pushed_at.replaceAll("Z$", "+0000"));
                            if (pushed_time.before(created_time) || pushed_time.equals(created_time)) {
                                //todo: previous approach
//                            if (created_time.before(pushed_time)) {
                                if (name.length() > 0) {
                                    sb.append(name + "," + repo_url + "," + created_at + "\n");
                                }
                            }

                        } else {
                            sb.append(name + "," + repo_url + "," + created_at + "\n");
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
            sb.append(getActiveForkList(fork, hasTimeConstraint));
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

            if (three_month_ago.equals("")) {
                continue;
            }

            /** get email from user profile */
            getForkOwnerInfo(forkName);


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
        if (fork_array_json != null & fork_array_json.size() > 0) {

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
        } else {
            return "";
        }
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
            if (!fork_contact_info.equals("")) {
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
            ArrayList<String> email_list = new ArrayList<>();
            if (forkid_email.get(fork_owner_id) != null) {
                email_list = forkid_email.get(fork_owner_id);
            }
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

            sb.append(updated_commit_in_fork_set.size() - commit_in_upstream_set.size() + "," + commit_in_upstream_set.size()
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


        JSONArray parents_json = (JSONArray) commit_jsonObj.get("parents");
        if (parents_json.length() > 0) {
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
                } else {
                    return CommitType.other;
                }
            }
        }
        return CommitType.other;
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
                System.out.println(fork);
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
                            int page = num_issue / 30 + 1;

                            for (int p = 1; p <= page; p++) {
                                try {
                                    issue_array_json = jsonUtility.readUrl(issueUrl + "&page=" + page);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                if (issue_array_json.size() > 0) {
                                    for (String iss : issue_array_json) {
                                        JSONObject iss_jsonObj = new JSONObject(issue);
                                        JSONArray items_json = iss_jsonObj.getJSONArray("items");
                                        for (int index = 0; index < items_json.toList().size(); index++) {
                                            ;
                                            JSONObject pr_json = (JSONObject) ((JSONObject) items_json.get(index)).get("pull_request");
                                            String pr_url = (String) pr_json.get("url");

                                            String merge_state = getPRstate(pr_url);
                                            sb.append(pr_url + "," + merge_state + ",");

                                            /** get commit of this PR **/
                                            ArrayList<String> pr_commitList = getPRcommits(pr_url);
                                            sb.append(pr_commitList.toString() + "\n");
                                        }
                                    }
                                }
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
        String[] commits_in_fork = null;
        String[] commits_in_PR = null;
        StringBuilder sb = new StringBuilder();
        try {
            commits_in_fork = io.readResult(result_dir + upstream_url + "/fork_contribution.csv").split("\n");
            commits_in_PR = io.readResult(result_dir + upstream_url + "/pr_fork.txt").split("fork:");
        } catch (IOException e) {
            e.printStackTrace();
        }
        commits_in_fork[0] += ", #commits of unmerged PRs,mergedCommits\n";
        sb.append(commits_in_fork[0]);
        for (int i = 1; i < commits_in_fork.length; i++) {
            List<String> commits_only_in_fork = Arrays.asList(removeBrackets(commits_in_fork[i].split(",")[5].split(",")[0]).split("/ "));
            List<String> commits_in_PR_from_fork = new ArrayList<>();
            HashSet<String> mergedPR = new HashSet<>();
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
                    } else {
                        mergedPR.addAll(Arrays.asList(pr_info[1].replace("]", "").split(", ")));
                    }
                }
            }

            sb.append(commits_in_fork[i] + "," + commits_in_PR_from_fork.size() + "," + mergedPR.toString().replace(",", "/") + "\n");
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


    public void classifyCommitsByAuthor(String upstream_url) {
        NameBasedClassifier tch = new NameBasedClassifier();

        System.out.println("get fork member contact information...");
        /** analyze each fork owner's emails  **/
        tch.getForkMemberContactInfo(upstream_url);

        System.out.println("analyzing commit history...");
        /** analyze each fork commit history **/
        tch.analyzeCommitHistory(upstream_url);


        System.out.println("get pull request information...");
        /** analyze each fork issue and PR history **/
        tch.getForkRelatedIssue(upstream_url);

        System.out.println("analyzing contribution of each fork..");
        tch.analyzeContribution(upstream_url);

    }

    public void getPRofEachFORK(String upstream_url) {
        NameBasedClassifier tch = new NameBasedClassifier();


        System.out.println("get pull request information...");
        /** analyze each fork issue and PR history **/
        tch.getForkRelatedIssue(upstream_url);


        System.out.println("analyzing contribution of each fork..");
        IO_Process io = new IO_Process();
//            String[] commits_in_fork = null;
        String[] commits_in_PR = null;
        StringBuilder sb = new StringBuilder();
        try {
//                commits_in_fork = io.readResult(result_dir + upstream_url + "/fork_contribution.csv").split("\n");
            commits_in_PR = io.readResult(result_dir + upstream_url + "/pr_fork.txt").split("fork:");
        } catch (IOException e) {
            e.printStackTrace();
        }
//            commits_in_fork[0] += ", #commits of unmerged PRs,mergedCommits\n";
//            sb.append(commits_in_fork[0]);
        for (int i = 1; i < commits_in_PR.length; i++) {
//                List<String> commits_only_in_fork = Arrays.asList(removeBrackets(commits_in_fork[i].split(",")[5].split(",")[0]).split("/ "));
            List<String> commits_in_PR_from_fork = new ArrayList<>();
            HashSet<String> mergedPR = new HashSet<>();
            String[] pr_array = {};
            pr_array = commits_in_PR[i].split("\n");

            if (pr_array.length > 1) {
                for (int j = 1; j < pr_array.length; j++) {
                    String[] pr_info = pr_array[j].split("\\[");
                    String pr_state = pr_info[0].split(",")[1];
                    if (!pr_state.equals("merged")) {
                        String[] rejected_commits = removeBrackets(pr_info[1]).split(", ");
                        for (String rej_commit : rejected_commits) {
//                                if (commits_only_in_fork.contains(rej_commit)) {
                            commits_in_PR_from_fork.add(rej_commit);
//                                }
                        }
                    } else {
                        mergedPR.addAll(Arrays.asList(pr_info[1].replace("]", "").split(", ")));
                    }
                }
            }

            sb.append(commits_in_PR_from_fork.toString().replace(",", "/") + "," + mergedPR.toString().replace(",", "/") + "\n");
//                sb.append(commits_in_fork[i] + "," + commits_in_PR_from_fork.size() + "," + mergedPR.toString().replace(",", "/") + "\n");
        }

        io.rewriteFile(sb.toString(), result_dir + upstream_url + "/PR_result.csv");


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

        if (profile_array_json != null) {
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
        } else {
            return null;
        }
    }

    public void getPRs(String upstream_url) {
        NameBasedClassifier tch = new NameBasedClassifier();

        System.out.println("get pull request information...");
        /** analyze each fork issue and PR history **/
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();

        //https://api.github.com/repos/MarlinFirmware/Marlin/pulls
        String PRUrl = github_api_repo + upstream_url + "/pulls?access_token=" + token + "&state=all&page=";


        JsonUtility jsonUtility = new JsonUtility();
        io.rewriteFile(sb.toString(), result_dir + upstream_url + "/pr_list.txt");
        sb.append("fork,pr_id,state,num_commits,commitsList\n");

        for (int page = 1; page <= 300; page++) {
            ArrayList<String> pr_array_json = null;
            try {
                pr_array_json = jsonUtility.readUrl(PRUrl + page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (pr_array_json.size() > 0) {
                for (String pr : pr_array_json) {
                    JSONObject pr_jsonObj = new JSONObject(pr);
                    int pr_id = (int) pr_jsonObj.get("number");
                    String fork = ((JSONObject) pr_jsonObj.get("user")).get("login").toString();
                    String state = (String) pr_jsonObj.get("state");
                    String created_at = (String) pr_jsonObj.get("created_at");
                    String closed_at = "";

                    if (state.equals("closed")) {
                        closed_at = (String) pr_jsonObj.get("closed_at");
                        boolean merged = pr_jsonObj.get("merged_at").equals(null) ? false : true;
                        if (merged) {
                            state = "merged";
                        }
                    }
                    String PR_commit_Url = github_api_repo + upstream_url + "/pulls/" + pr_id + "/commits" + "?access_token=" + token + "&page=";

                    ArrayList<String> commitsList = new ArrayList<>();
                    for (int cp = 1; cp <= 30; cp++) {
                        ArrayList<String> commits_array_json = null;
                        try {
                            commits_array_json = jsonUtility.readUrl(PR_commit_Url + cp);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (commits_array_json.size() == 0) {
                            break;
                        }
                        for (String str : commits_array_json) {
                            commitsList.add((String) new JSONObject(str).get("sha"));
                        }
                    }
                    sb.append(fork + "," + pr_id + "," + state + "," + created_at + "," + closed_at + "," + commitsList.size() + "," + commitsList.toString().replace(",", "/") + "\n");

                }
            } else {
                break;
            }

        }
        io.writeTofile(sb.toString(), result_dir + upstream_url + "/pr_list.txt");


    }

    public void getPRsOfEachFork(String repoUrl) {

        IO_Process io = new IO_Process();
        io.rewriteFile("fork,upstream,Only_F,Only_U,F2U,U2F,pr_commit,merged_commit,rejected_commit\n", result_dir + repoUrl + "/graph_pr.csv");

        HashMap<String, ArrayList<String>> fork_PR = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        String[] prs = null;
        try {
            prs = io.readResult(result_dir + repoUrl + "/pr_list.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 1; i < prs.length; i++) {
            String line = prs[i];
            String fork = line.split(",")[0];
            ArrayList<String> prList = new ArrayList<>();
            if (fork_PR.get(fork) == null) {
                prList.add(line);
            } else {
                prList = fork_PR.get(fork);
                prList.add(line);

            }
            fork_PR.put(fork, prList);
        }

        List<List<String>> graph_approach_result = io.readCSV(result_dir + repoUrl + "/graph_result.txt");
        for (int i = 1; i < graph_approach_result.size(); i++) {
            List<String> result = graph_approach_result.get(i);
            String forkUrl = result.get(0);
            String upstreamUrl = result.get(1);
            String fork = forkUrl.split("/")[0];

            ArrayList<String> onlyF_commits = new ArrayList<>();
            String of = io.removeBrackets(result.get(6));
            if (!of.equals("")) {
                onlyF_commits.addAll(Arrays.asList(of.split("/ ")));
            }

            ArrayList<String> onlyU_commits = new ArrayList<>();
            String ou = io.removeBrackets(result.get(7));
            if (!ou.equals("")) {
                onlyU_commits.addAll(Arrays.asList(ou.split("/")));
            }

            ArrayList<String> F2U_commits = new ArrayList<>();
            String f2u = io.removeBrackets(result.get(8));
            if (!f2u.equals("")) {
                F2U_commits.addAll(Arrays.asList(f2u.split("/")));
            }

            ArrayList<String> U2F_commits = new ArrayList<>();
            String u2f = io.removeBrackets(result.get(9));
            if (!f2u.equals("")) {
                U2F_commits.addAll(Arrays.asList(u2f.split("/")));
            }


            ArrayList<String> prList = fork_PR.get(fork);
            if (prList != null) {

                HashSet<String> pr_commit_set = new HashSet<>();
                HashSet<String> pr_commit_merged = new HashSet<>();
                HashSet<String> pr_commit_rejected = new HashSet<>();

                for (String pr : prList) {
                    String[] prInfo = pr.split(",");
                    String status = prInfo[2];
                    List<String> pr_commit = Arrays.asList(io.removeBrackets(prInfo[3]).split("/ "));
                    for (String c : pr_commit) {
                        boolean hasTwoParents = isMergeCommit(c, forkUrl);
                        if (!hasTwoParents) {
                            pr_commit_set.add(c);
                            if (status.equals("merged")) {
                                pr_commit_merged.add(c);
                                if (!F2U_commits.contains(c)) {
                                    F2U_commits.add(c);
                                }
                            } else if (status.equals("closed")) {
                                pr_commit_rejected.add(c);
                                if (!onlyF_commits.contains(c)) {
                                    onlyF_commits.add(c);
                                }
                            }
                        }

                    }
                }
                sb.append(forkUrl + "," + upstreamUrl + "," + onlyF_commits.size() + "," + onlyU_commits.size() + "," + F2U_commits.size() + "," + U2F_commits.size()
                        + "," + pr_commit_set.size() + "," + pr_commit_merged.size() + "," + pr_commit_rejected.size() + "\n");

            } else {
                sb.append(forkUrl + "," + upstreamUrl + "," + onlyF_commits.size() + "," + onlyU_commits.size() + "," + F2U_commits.size() + "," + U2F_commits.size()
                        + ",,,\n");
                continue;
            }


        }
        io.writeTofile(sb.toString(), result_dir + repoUrl + "/graph_pr.csv");

    }

    private boolean isMergeCommit(String sha, String forkURL) {
        String commitURL = github_api_repo + forkURL + "/commit/" + sha;
        JsonUtility jsonUtility = new JsonUtility();
        JSONObject commit_jsonObj = getJsonObject(sha, forkURL, jsonUtility);
        JSONArray parents = (JSONArray) commit_jsonObj.get("parents");
        if (parents.length() == 2) {
            return true;
        }
        return false;

    }

    public void getPRofForks(String repoUrl) {
        IO_Process io = new IO_Process();
        HashMap<String, ArrayList<String>> fork_PR = new HashMap<>();

        String[] prs = null;
        try {
            prs = io.readResult(result_dir + repoUrl + "/pr_list.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 1; i < prs.length; i++) {
            String line = prs[i];
            String fork = line.split(",")[0];
            ArrayList<String> prList = new ArrayList<>();
            if (fork_PR.get(fork) == null) {
                prList.add(line);
            } else {
                prList = fork_PR.get(fork);
                prList.add(line);

            }
            fork_PR.put(fork, prList);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("fork,pr_commit_set,pr_commit_merged,pr_commit_rejected\n");
        fork_PR.forEach((k, v) -> {
            HashSet<String> pr_commit_set = new HashSet<>();
            HashSet<String> pr_commit_merged = new HashSet<>();
            HashSet<String> pr_commit_rejected = new HashSet<>();

            for (String pr : v) {
                String[] prInfo = pr.split(",");
                String status = prInfo[2];
                List<String> pr_commit = Arrays.asList(io.removeBrackets(prInfo[6]).split("/ "));
                for (String c : pr_commit) {
                    pr_commit_set.add(c);
                    if (status.equals("merged")) {
                        pr_commit_merged.add(c);
                    } else if (status.equals("closed")) {
                        pr_commit_rejected.add(c);
                    }

                }
            }

            pr_commit_rejected.removeAll(pr_commit_merged);
            sb.append(k + "," + pr_commit_set.size() + "," + pr_commit_merged.size() + "," + pr_commit_rejected.size() + "\n");
        });


        io.rewriteFile(sb.toString(), result_dir + repoUrl + "/pr_result.txt");

    }

    public void getPRbyresuletable() {
        final String current_dir = System.getProperty("user.dir");

        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String[] forkArray = {};
        try {
            String forkList = io.readResult(current_dir + "/input/result.csv");
            forkArray = forkList.split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 1; i < forkArray.length; i++) {
            HashSet<String> allPR = new HashSet<>();
            HashSet<String> mergedPR = new HashSet<>();
            HashSet<String> rejectedPR = new HashSet<>();
            String line = forkArray[i];
            sb.append(line);
            String[] arr = line.split(",");
            String fork = arr[1];
            String upstreamName = arr[2].split("/")[0].trim();
            String forkName = fork.split("/")[0].replace("+", "%2B");
            //https://api.github.com/search/issues?q=author%3AJobLeonard+user%3Atimscaffidi+type%3Aissue
            String issueUrl = github_api_search + "issues?q=author%3A" + forkName + "+user%3A" + upstreamName + "+type%3Apr&access_token=" + token;
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
                        int page = num_issue / 30 + 1;

                        for (int p = 1; p <= page; p++) {
                            try {
                                issue_array_json = jsonUtility.readUrl(issueUrl + "&page=" + page);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            if (issue_array_json.size() > 0) {
                                for (String iss : issue_array_json) {
                                    JSONObject iss_jsonObj = new JSONObject(issue);
                                    JSONArray items_json = iss_jsonObj.getJSONArray("items");
                                    for (int index = 0; index < items_json.toList().size(); index++) {
                                        ;
                                        JSONObject pr_json = (JSONObject) ((JSONObject) items_json.get(index)).get("pull_request");
                                        String pr_url = (String) pr_json.get("url");

                                        String merge_state = getPRstate(pr_url);
//                                        sb.append(pr_url + "," + merge_state + ",");

                                        /** get commit of this PR **/
                                        ArrayList<String> pr_commitList = getPRcommits(pr_url);
                                        allPR.addAll(pr_commitList);
                                        if (merge_state.equals("merged")) {
                                            mergedPR.addAll(pr_commitList);
                                        } else if (merge_state.equals("closed")) {
                                            rejectedPR.addAll(pr_commitList);

                                        }
                                    }
                                }
                            }
                        }


                    } else {
                        break;
                    }

                }

            }
            mergedPR.removeAll(rejectedPR);
            sb.append(","+allPR.size()+","+mergedPR.size()+","+rejectedPR.size()+"\n");
            io.writeTofile(sb.toString(), result_dir+"new_result.csv");
            sb = new StringBuilder();
        }
    }
}
