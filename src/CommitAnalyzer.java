import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by shuruiz on 11/20/17.
 */
public class CommitAnalyzer {
    static String token;
    static String github_page = "https://github.com/";
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String result_dir = "/Users/shuruiz/Box Sync/GithubScript-New/result/";

    public static void main(String[] args) {
        NameBasedClassifier tch = new NameBasedClassifier();
        IO_Process io = new IO_Process();
        String[] upstream_array = {"timscaffidi/ofxVideoRecorder"};
//        String[] upstream_array = {"timscaffidi/ofxVideoRecorder", "Smoothieware/Smoothieware", "MarlinFirmware/Marlin"};
        try {
            token = io.readResult(result_dir + "token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (String repo_url : upstream_array) {
            StringBuilder sb = new StringBuilder();
            io.rewriteFile("", result_dir + repo_url + "/commitAnalysisResult.txt");

//            /** get active fork list for given repository **/
//            System.out.println("get all active forks...");
//            String activeForkList = tch.getActiveForkList(repo_url);
//            io.rewriteFile(activeForkList, result_dir + repo_url + "/all_ActiveForklist.txt");
//
//            System.out.println("randomly pick 30 active forks...");
//            tch.getRamdomForks(repo_url);


            String[] forkArray = null;
            try {
                forkArray = io.readResult(result_dir + repo_url + "/ActiveForklist.txt").split("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            sb.append("fork_url,upstream_ur,commit_only_in_upstream,commit_only_in_fork,commit_sync_from_upstream,commit_merge_to_upstream\n");


            for (String fork_info : forkArray) {
                String upstream_url = fork_info.split(",")[1];
                String fork_url = fork_info.split(",")[0];

                sb.append(analyzingCommits(upstream_url, fork_url, repo_url) + "\n");
            }

            io.rewriteFile(sb.toString(), result_dir + repo_url + "/forkClassify.csv");
        }
    }

    private static String analyzingCommits(String upstream_url, String fork_url, String repo_url) {
        NameBasedClassifier trackCommitHistory = new NameBasedClassifier();
        String three_month_ago = trackCommitHistory.getThreeMonthBeforeForkTime(fork_url, new JsonUtility());

        HashSet<String> commit_only_in_upstream = new HashSet<>();
        HashSet<String> commit_only_in_fork = new HashSet<>();
        HashSet<String> commit_merge_to_upstream = new HashSet<>();
        HashSet<String> commit_sync_from_upstream = new HashSet<>();


        HashSet<String> commit_from_upstream = new HashSet<>();
        HashSet<String> commit_from_fork = new HashSet<>();

        IO_Process io = new IO_Process();
        getCommitList(repo_url, upstream_url);
        getCommitList(repo_url, fork_url);

        ArrayList<String> commitSet_in_upstream = null;
        try {
            commitSet_in_upstream = new ArrayList<String>(Arrays.asList(io.readResult(result_dir + repo_url + "/" + upstream_url.split("/")[0] + "_commitList.txt").split(",")));
        } catch (IOException e) {
            e.printStackTrace();
        }


        ArrayList<String> commitSet_in_fork = null;
        try {
            commitSet_in_fork = new ArrayList<String>(Arrays.asList(io.readResult(result_dir + repo_url + "/" + fork_url.split("/")[0] + "_commitList.txt").split(",")));
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (String c_up : commitSet_in_upstream) {
            if (c_up.trim().length() > 0) {
                JSONObject commit_jsonObj = trackCommitHistory.getJsonObject(c_up, upstream_url, new JsonUtility());
                JSONArray parents_json = (JSONArray) commit_jsonObj.get("parents");
                if (parents_json.length() == 2) {
                    String parent_sha_1 = (String) ((JSONObject) parents_json.get(0)).get("sha");
                    String parent_sha_2 = (String) ((JSONObject) parents_json.get(1)).get("sha");
                    if (((commitSet_in_fork.contains(parent_sha_1) && !commitSet_in_fork.contains(parent_sha_2)) || (!commitSet_in_fork.contains(parent_sha_1) && commitSet_in_fork.contains(parent_sha_2)))
                            ) {
                        commit_merge_to_upstream.add(c_up);
                    }
                } else {
                    commit_from_upstream.add(c_up);
                }
            }
        }

        for (String c_fork : commitSet_in_fork) {
            if (c_fork.trim().length() > 0) {
                JSONObject commit_jsonObj = trackCommitHistory.getJsonObject(c_fork, fork_url, new JsonUtility());
                JSONArray parents_json = (JSONArray) commit_jsonObj.get("parents");
                if (parents_json.length() == 2) {
                    String parent_sha_1 = (String) ((JSONObject) parents_json.get(0)).get("sha");
                    String parent_sha_2 = (String) ((JSONObject) parents_json.get(1)).get("sha");
                    if ((commitSet_in_upstream.contains(parent_sha_1) && !commitSet_in_upstream.contains(parent_sha_2))
                            || (!commitSet_in_upstream.contains(parent_sha_1) && commitSet_in_upstream.contains(parent_sha_2))
                            ) {
                        commit_sync_from_upstream.add(c_fork);
                    }
                } else {
                    commit_from_fork.add(c_fork);
                }
            }
        }

        for (String c_fork : commit_merge_to_upstream) {
            commit_from_fork.remove(c_fork);
        }
        commit_only_in_fork.addAll(commit_from_fork);

        for (String c_up : commit_sync_from_upstream) {
            commit_from_upstream.remove(c_up);
        }
        commit_only_in_upstream.addAll(commit_from_upstream);

        ArrayList<HashSet<String>> result_1 = removeCommonCommits(commit_only_in_fork, commit_only_in_upstream);
        commit_only_in_fork = new HashSet<>();
        commit_only_in_fork.addAll(result_1.get(0));
        commit_only_in_upstream = new HashSet<>();
        commit_only_in_upstream.addAll(result_1.get(1));

        ArrayList<HashSet<String>> result_2 = removeCommonCommits(commit_merge_to_upstream, commit_sync_from_upstream);
        commit_merge_to_upstream = new HashSet<>();
        commit_merge_to_upstream.addAll(result_2.get(0));
        commit_sync_from_upstream = new HashSet<>();
        commit_sync_from_upstream.addAll(result_2.get(1));

        io.writeTofile("fork: " + fork_url + "------\n", result_dir + repo_url + "/commitAnalysisResult.txt");
        printCommits("commit_only_in_upstream", commit_only_in_upstream, repo_url);
        printCommits("commit_only_in_fork", commit_only_in_fork, repo_url);
        printCommits("commit_sync_from_upstream", commit_sync_from_upstream, repo_url);
        printCommits("commit_merge_to_upstream", commit_merge_to_upstream, repo_url);

        return fork_url + "," + upstream_url + "," + commit_only_in_upstream.size() + "," + commit_only_in_fork.size() + "," + commit_sync_from_upstream.size() + "," + commit_merge_to_upstream.size();

    }

    private static ArrayList<HashSet<String>> removeCommonCommits(HashSet<String> commitSet_1, HashSet<String> commitSet_2) {

        HashSet<String> copy_1 = new HashSet<>();
        copy_1.addAll(commitSet_1);
        for (String c : copy_1) {
            if (commitSet_1.contains(c) && commitSet_2.contains(c)) {
                commitSet_1.remove(c);
                commitSet_2.remove(c);
            }
        }
        ArrayList<HashSet<String>> result = new ArrayList<>();
        result.add(commitSet_1);
        result.add(commitSet_2);
        return result;
    }

    private static void printCommits(String title, HashSet<String> commitSet, String repo_url) {

        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        sb.append(title + ":");
        for (String commit : commitSet) {
            sb.append(commit + ",");
        }
        sb.append("\n");
        io.writeTofile(sb.toString(), result_dir + repo_url + "/commitAnalysisResult.txt");

    }

    private static HashSet<String> getCommitList(String repo_url, String fork_url) {
        StringBuilder sb = new StringBuilder();
        String commit_Url = github_api_repo + fork_url + "/commits?access_token=" + token ;

        NameBasedClassifier tch = new NameBasedClassifier();
        ArrayList<String> branchList = tch.getBranches(fork_url);
        HashSet<String> commitList = new HashSet<>();

        JsonUtility jsonUtility = new JsonUtility();
        ArrayList<String> commit_in_repo_json = new ArrayList<>();
        HashMap<String, Boolean> branch_checkList = new HashMap<>();

        for (String branch : branchList) {
            for (int page = 1; page <= 300; page++) {
                if (branch_checkList.size() < branchList.size()) {
                    if (branch_checkList.get(branch) == null) {
                        String commit_in_repo_url = commit_Url + "&sha=" + branch + "&page=";
                        try {
                            commit_in_repo_json = jsonUtility.readUrl(commit_in_repo_url + page);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (commit_in_repo_json != null && commit_in_repo_json.size() > 0) {
                            for (String commit : commit_in_repo_json) {
                                String sha = (String) new JSONObject(commit).get("sha");

                                commitList.add(sha);
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
        new IO_Process().rewriteFile(tch.removeBrackets(commitList.toString()), result_dir + repo_url + "/" + fork_url.split("/")[0] + "_commitList.txt");
        return commitList;

    }


}
