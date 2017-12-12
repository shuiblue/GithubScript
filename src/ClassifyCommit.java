import com.jcraft.jsch.HASH;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;

/**
 * Created by shuruiz on 11/23/17.
 */
public class ClassifyCommit {


    /**
     * 1. repo_url  --> forkList
     * 2. git clone repo
     * 3. git clone fork
     * 4. repo_url -> a) list of commit in upstream, b) list of merge commit c) repo_url-> branchList  <-- Parent_one(c),
     * 5. fork_url -> a) list of commit in fork , b) list of merge commit  c) fork_url-> branchList <--  Parent_one(c)
     * 6. for each 4/5.b) list of merge commit --> S(mc) = S(p1(mc))+S(p2(mc))+mc
     * 7.  ForkToUpstream(mc) (for each branch) = S(mc)-S(p1(mc))
     * 8.  UpstreamToFork(mc) (for each branch) = S(mc)-S(p1(mc))
     */
    static String tmpDirPath;
    static int maxAnalyzedForkNum = 50;
    static int randomAnalyzedForkNum = 5;
    static HashMap<String, HashSet<String>> reachable_map = new HashMap<>();
    static String current_dir;

    ClassifyCommit() {
        current_dir = System.getProperty("user.dir");
        tmpDirPath = current_dir + "/cloneRepos/";
    }

    public static void main(String[] args) {
        ClassifyCommit cc = new ClassifyCommit();
        IO_Process io = new IO_Process();
        String[] repoList = {};
        TrackCommitHistory trackCommitHistory = new TrackCommitHistory();

        current_dir = System.getProperty("user.dir");

        tmpDirPath = current_dir + "/cloneRepos/";

        /** get repo list **/
        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String repoUrl : repoList) {
            if (repoUrl.trim().length() > 0) {
                String repoName = repoUrl.split("/")[0];
                /** get active fork list for given repository **/
                System.out.println("get all active forks...");
//                String all_activeForkList = trackCommitHistory.getActiveForkList(repoUrl);
//                if (all_activeForkList.split("\n").length<= maxAnalyzedForkNum) {
//                    io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl + "/ActiveForklist.txt");
//                } else {
//                    io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl +"/all_ActiveForklist.txt");
//                    System.out.println("randomly pick " + randomAnalyzedForkNum + " active forks...");
//                    trackCommitHistory.getRamdomForks(repoUrl, randomAnalyzedForkNum);
//                }

                /** analyze commit history **/
                String[] activeForkList = {};
                try {
                    activeForkList = io.readResult(current_dir + "/result/" + repoUrl + "/ActiveForklist.txt").split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                StringBuilder sb_result = new StringBuilder();
                sb_result.append("fork,upstream，only_F,only_U,only_U_including_PRs,F->U,U->F,U->F_including_PRs,sync_with_U,only_F_commits,only_U_commits,F->U_commits,U->F_commits\n");

                io.rewriteFile(sb_result.toString(), current_dir + "/result/" + repoUrl + "/graph_result.csv");
                for (String forkInfo : activeForkList) {
                    cc.analyzeCommitHistory(forkInfo, true, repoUrl);
                }
            }

        }

    }


    /***
     * 4. repo_url -> a) list of commit in upstream, b) list of merge commit c) repo_url-> branchList  <-- Parent_one(c),
     * 5. fork_url -> a) list of commit in fork , b) list of merge commit  c) fork_url-> branchList <--  Parent_one(c)
     * 6. for each 4/5.b) list of merge commit --> S(mc) = S(p1(mc))+S(p2(mc))+mc
     * 7.  ForkToUpstream(mc) (for each branch) = S(mc)-S(p1(mc))
     * 8.  UpstreamToFork(mc) (for each branch) = S(mc)-S(p1(mc))
     * @param forkInfo   [forkUrl, upstreamUrl]
     * @param compareForkWithUpstream   if = false, compare branches within a fork
     */


    public void analyzeCommitHistory(String forkInfo, boolean compareForkWithUpstream, String repoURL) {
        HashSet<String> fork2Upstream = new HashSet<>();
        HashSet<String> upstream2Fork = new HashSet<>();
        HashSet<String> upstream2Fork_includePRMerge = new HashSet<>();
        HashSet<String> parent2Set = new HashSet<>();


        JgitUtility jg = new JgitUtility();
        ClassifyCommit cc = new ClassifyCommit();
        String forkUrl = forkInfo.split(",")[0];
        String forkName = forkUrl.split("/")[0];

        String upstreamUrl = forkInfo.split(",")[1];
        String forkpointDate = forkInfo.split(",")[2];

        /**clone fork to local*/
//        System.out.println("git clone " + forkUrl);
//        jg.cloneRepo(forkUrl);
//
//        if (compareForkWithUpstream) {
//            System.out.println("git clone " + upstreamUrl);
//            jg.cloneRepo(upstreamUrl);
//        }
//
//        /** get commit in branch**/
//        /**----   in fork **/
//        System.out.println("getting Commits In all Branches of fork: " + forkUrl);
//        cc.getCommitInBranch(forkUrl, forkpointDate);
//        /**----  in upstream **/
//        if (compareForkWithUpstream) {
//            System.out.println("getting Commits In all Branches of upstream: " + upstreamUrl);
//            cc.getCommitInBranch(upstreamUrl, "");
//        }


        IO_Process io = new IO_Process();
        String[] fork_branchArray = {};
        HashMap<String, List<String>> fork_branch_History_map = new HashMap<>();
        HashMap<String, List<String>> upstream_branch_History_map = new HashMap<>();
        HashSet<String> allCommitsInUpstream = new HashSet<>();
        String[] upstream_branchArray = {};
        String[] upstream_withMeged_branchArray = {};

        try {
            System.out.println("getting fork branch commit list..");
            String fork_branchStr = io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_branch_commitList.txt");
            if (fork_branchStr.equals("")) {
                StringBuilder sb_result = new StringBuilder();
                sb_result.append(forkUrl + ",\n");
                io.writeTofile(sb_result.toString(), current_dir + "/result/" + repoURL + "/graph_result.csv");
                return;

            }
            fork_branch_History_map = getHistory(io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_CommitHistory.txt").split("\n"));
            fork_branchArray = fork_branchStr.split("\n");

            if (compareForkWithUpstream) {
                System.out.println("getting upstream branch commit list..");
                String upstream_branchStr = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_branch_commitList.txt");
                upstream_withMeged_branchArray = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_CommitHistory.txt").split("\n");
                upstream_branch_History_map = getHistory(upstream_withMeged_branchArray);
                upstream_branchArray = upstream_branchStr.split("\n");

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        upstream_branch_History_map.forEach((k, v) -> {
            allCommitsInUpstream.addAll(v);
        });


        HashSet<String> uselessBranches = null;
        if (fork_branch_History_map.keySet().size() >= 2) {
            uselessBranches = RemoveBranchesBeforeForkPoint(fork_branch_History_map, forkInfo);
        }

        /** comparing branches **/
        io.rewriteFile("", tmpDirPath + forkName + "_commitClassification.txt");

        if (compareForkWithUpstream) {
            System.out.println("comparing branches of " + forkUrl + " with upstream " + upstreamUrl);
            for (String fork_br : fork_branchArray) {
                String fork_branchName = fork_br.split(",\\[")[0];

                if ((uselessBranches != null && !uselessBranches.contains(fork_branchName)) || uselessBranches == null) {
                    for (int i = 0; i < upstream_branchArray.length; i++) {

                        String up_nomerge_br = upstream_branchArray[i];

                        String up_withMerged_br = "";
                        if (i < upstream_branchArray.length - 1) {
                            up_withMerged_br = upstream_withMeged_branchArray[i];
                        }

                        System.out.println(fork_branchName);
                        System.out.println(" vs ");
                        System.out.println(up_nomerge_br.split(",\\[")[0]);

                        ArrayList<HashSet<String>> result_1 = cc.compareHistory(forkUrl, fork_br.split(",\\["), upstreamUrl, up_nomerge_br.split(",\\["), forkUrl.split("/")[0]);
                        ArrayList<HashSet<String>> result_2 = null;
                        if (i < upstream_branchArray.length - 1) {
                            result_2 = cc.compareHistory(forkUrl, fork_br.split(",\\["), upstreamUrl, up_withMerged_br.split(",\\["), forkUrl.split("/")[0]);
                        }
                        upstream2Fork.addAll(result_1.get(0));
                        fork2Upstream.addAll(result_1.get(1));
                        parent2Set.addAll(result_1.get(2));
                        if (i < upstream_branchArray.length - 1) {
                            upstream2Fork_includePRMerge.addAll(result_2.get(0));
                        }
                    }
                }
            }
        } else {

            /**b3  -- b1**/
            ArrayList<HashSet<String>> result_1 = cc.compareHistory(forkUrl, fork_branchArray[0].split(",\\["), upstreamUrl, fork_branchArray[1].split(",\\["), forkUrl.split("/")[0]);
            HashSet<String> b3_b1 = result_1.get(0);
            if (b3_b1.size() == 1
                    && b3_b1.contains("02c740dde6715ebbadce1cbf5a6b9115c2c5ab83")) {
                System.out.println("b1-->b3 is 1");
            }

            HashSet<String> b1_b3 = result_1.get(1);
            if (b1_b3.size() == 0) {
                System.out.println("b1-->b3 is 0");
            }

            /** b1 -- master**/
            ArrayList<HashSet<String>> result_2 = cc.compareHistory(forkUrl, fork_branchArray[0].split(",\\["), upstreamUrl, fork_branchArray[3].split(",\\["), forkUrl.split("/")[0]);
            HashSet<String> m_b1 = result_2.get(0);

            if (m_b1.size() == 2
                    && m_b1.contains("037cc5b70f52a6a01039e380297dcfb739f30a47")
                    && m_b1.contains("b7d97eb3526bcc8b128d1f194dff967b09481d43")) {
                System.out.println("master-->b1 is 2");
            }

            HashSet<String> b1_m = result_2.get(1);
            if (b1_m.size() == 1 && b1_m.contains("63c54d3e1750d7d0fa686995e3e694d76cb06fda")) {
                System.out.println("b1-->master is 1");
            }

            /** b1 -- deleteBranch**/
            ArrayList<HashSet<String>> result_3 = cc.compareHistory(forkUrl, fork_branchArray[0].split(",\\["), upstreamUrl, fork_branchArray[4].split(",\\["), forkUrl.split("/")[0]);
            HashSet<String> b1_deletedBranch = result_3.get(0);

            if (b1_deletedBranch.size() == 2
                    && m_b1.contains("037cc5b70f52a6a01039e380297dcfb739f30a47")
                    && m_b1.contains("b7d97eb3526bcc8b128d1f194dff967b09481d43")) {
                System.out.println("master-->b1 is 2");
            }

            HashSet<String> deleteBranch_b1 = result_3.get(1);
            if (deleteBranch_b1.size() == 1 && deleteBranch_b1.contains("63c54d3e1750d7d0fa686995e3e694d76cb06fda")) {
                System.out.println("b1-->master is 1");
            }

        }


        HashSet<String> copy_fork2Up = new HashSet<>();
        copy_fork2Up.addAll(fork2Upstream);
        for (String commit : copy_fork2Up) {
            if (upstream2Fork.contains(commit) && fork2Upstream.contains(commit)) {
                upstream2Fork.remove(commit);
                fork2Upstream.remove(commit);
            }
        }


        if (compareForkWithUpstream) {
            /** Get  commits   in fork**/
            HashSet<String> commits_in_fork = new HashSet<>();
            HashSet<String> merge_points_in_fork = new HashSet<>();
            for (String fork_br : fork_branchArray) {
                String[] branch = fork_br.split(",\\[");
                String branch1_name = branch[0];
                if ((uselessBranches != null && !uselessBranches.contains(branch1_name)) || uselessBranches == null) {
                    List<String> branch1_commitList = Arrays.asList(io.removeBrackets(branch[1]).split(", "));
                    commits_in_fork.addAll(branch1_commitList);
                    merge_points_in_fork.addAll(Arrays.asList(io.removeBrackets(branch[2]).split(", ")));
                }
            }

            /** Get  commits   in master**/
            HashSet<String> commits_in_upstream = new HashSet<>();
            HashSet<String> merge_points_in_upstream = new HashSet<>();
            for (String up_br : upstream_branchArray) {
                String[] branch = up_br.split(",\\[");
                String branch1_name = branch[0];
                List<String> branch1_commitList = Arrays.asList(io.removeBrackets(branch[1]).split(", "));
                commits_in_upstream.addAll(branch1_commitList);
                merge_points_in_upstream.addAll(Arrays.asList(io.removeBrackets(branch[2]).split(", ")));
            }

            /**Get commits before forking point */
            HashSet<String> commits_before_fork_point = new HashSet<>();
            for (String c : commits_in_fork) {
                if (commits_in_fork.contains(c) && commits_in_upstream.contains(c)) {
                    commits_before_fork_point.add(c);
                }
            }

            commits_in_fork.removeAll(commits_before_fork_point);
            commits_in_fork.removeAll(merge_points_in_fork);
            commits_in_fork.removeAll(fork2Upstream);


            commits_in_upstream.removeAll(commits_before_fork_point);
            commits_in_upstream.removeAll(merge_points_in_upstream);
            commits_in_upstream.removeAll(upstream2Fork);


            /**all the commits existing upstream**/
            allCommitsInUpstream.removeAll(commits_before_fork_point);
            allCommitsInUpstream.removeAll(merge_points_in_upstream);
            allCommitsInUpstream.removeAll(upstream2Fork);
            allCommitsInUpstream.removeAll(upstream2Fork_includePRMerge);

            parent2Set.removeAll(upstream2Fork_includePRMerge);
            parent2Set.removeAll(allCommitsInUpstream);

            StringBuilder sb = new StringBuilder();
            sb.append("only in fork: " + commits_in_fork.size() + " commits, " + commits_in_fork + "\n");
            sb.append("only in upstream: " + commits_in_upstream.size() + " commits, " + commits_in_upstream + "\n");
            sb.append("only in upstream (with PR merge): " + allCommitsInUpstream.size() + " commits, " + allCommitsInUpstream + "\n");
            sb.append("fork --> upstream: " + fork2Upstream.size() + " commits, " + fork2Upstream + "\n");
            sb.append("upstream --> fork: " + upstream2Fork.size() + " commits, " + upstream2Fork + "\n");
            sb.append("upstream --> fork (with PR merge): " + upstream2Fork_includePRMerge.size() + " commits, " + upstream2Fork_includePRMerge + "\n");
            sb.append("sync with upstream (times) " + parent2Set.size() + " commits, " + parent2Set + "\n");
            io.rewriteFile(sb.toString(), current_dir + "/result/" + repoURL + "/" + forkName + "_result.txt");


            StringBuilder sb_result = new StringBuilder();
            sb_result.append(forkUrl + "," + upstreamUrl + "," + commits_in_fork.size() + "," + commits_in_upstream.size() + ","
                    + allCommitsInUpstream.size() + "," + fork2Upstream.size() + "," + upstream2Fork.size() + ","
                    + upstream2Fork_includePRMerge.size() + "," + parent2Set.size() + ","
                    + commits_in_fork.toString().replace(",", "/") + "," + commits_in_upstream.toString().replace(",", "/") + ","
                    + fork2Upstream.toString().replace(",", "/") + "," + upstream2Fork.toString().replace(",", "/") + "\n");
            io.writeTofile(sb_result.toString(), current_dir + "/result/" + repoURL + "/graph_result.csv");


        }

        /** remove local copy of fork**/
        String localDirPath = "/Users/shuruiz/Box Sync/GithubScript-New/cloneRepos/";
        try {
            if (compareForkWithUpstream) {
                io.deleteDir(new File(localDirPath + upstreamUrl));
            }
            io.deleteDir(new File(localDirPath + forkUrl));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private HashSet<String> RemoveBranchesBeforeForkPoint(HashMap<String, List<String>> fork_branch_history_map, String forkInfo) {

        String forkPointTime = forkInfo.split(",")[2];
        String forkurl = forkInfo.split(",")[0];

        HashSet<String> uselessBranches = new HashSet<>();
        HashSet<List<String>> combination = getAllPairs(fork_branch_history_map.keySet());

        /**  comparing one branch is a subset of another **/
        for (List<String> comb : combination) {
            String b1 = comb.get(0);
            String b2 = comb.get(1);

            int size_1 = fork_branch_history_map.get(b1).size();
            int size_2 = fork_branch_history_map.get(b2).size();
            if (size_1 > size_2) {
                if (hasMerged(fork_branch_history_map.get(b1), fork_branch_history_map.get(b2)))
                    uselessBranches.add(b2);
            } else {
                if (hasMerged(fork_branch_history_map.get(b2), fork_branch_history_map.get(b1)))
                    uselessBranches.add(b1);
            }
        }


        return uselessBranches;
    }


    private boolean hasMerged(List<String> longer_br, List<String> shorter_br) {
        HashSet<String> shortBrCommitSet = new HashSet<>();
        shortBrCommitSet.addAll(shorter_br);
        for (String commit : shorter_br) {
            if (longer_br.contains(commit)) {
                shortBrCommitSet.remove(commit);
            }
        }
        if (shortBrCommitSet.size() == 0) {
            return true;
        } else {
            return false;
        }

    }

    private HashMap<String, List<String>> getHistory(String[] branchArray) {
        HashMap<String, List<String>> result = new HashMap<>();
        IO_Process io = new IO_Process();
        for (String str : branchArray) {
            String[] branchHistory = str.split(",\\[");
            String branch = branchHistory[0];

            List<String> branch1_commitList = Arrays.asList(io.removeBrackets(branchHistory[1]).split(", "));
            result.put(branch, branch1_commitList);
        }


        return result;


    }

    /**
     * This function generates all the pairs of the set
     *
     * @param forkBranchSet
     * @return
     */
    public HashSet<List<String>> getAllPairs(Set<String> forkBranchSet) {

        Set<String> set = new HashSet<>();
        set.addAll(forkBranchSet);
        HashSet<List<String>> allPairs = new HashSet<>();
        List<String> list = new ArrayList<>(set);
        String first = list.remove(0);
        for (String node : list) {
            List<String> currentPair = new ArrayList<>();
            currentPair.add(first);
            currentPair.add(node);
            allPairs.add(currentPair);

        }


        if (set.size() > 2) {
            set.remove(first);
            getAllPairs(set);
        } else {
            List<String> tmp = new ArrayList<String>(set);
            allPairs.add(tmp);
        }

        return allPairs;
    }


    private ArrayList<HashSet<String>> compareHistory(String forkURL, String[] branch1, String upstreamURL, String[] branch2, String forkName) {

        ArrayList<HashSet<String>> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        HashSet<String> b1_to_b2 = new HashSet<>();
        HashSet<String> b2_to_b1 = new HashSet<>();

        System.out.println("get branch1 info...");
        IO_Process io = new IO_Process();
        String branch1_name = branch1[0];
        List<String> branch1_commitList = Arrays.asList(io.removeBrackets(branch1[1]).split(", "));
        String[] branch1_merged_commitList = io.removeBrackets(branch1[2]).split(", ");

        System.out.println("get branch2 info...");
        String branch2_name = branch2[0];
        List<String> branch2_commitList = Arrays.asList(io.removeBrackets(branch2[1]).split(", "));
        String[] branch2_merged_commitList = io.removeBrackets(branch2[2]).split(", ");


        System.out.println("get fork merge commits info..");
        HashMap<String, String> fork_commitTwoParentMap = getParents(forkURL);

        System.out.println("get upstream merge commits info..");
        HashMap<String, String> upstream_commitTwoParentMap = getParents(upstreamURL);

        System.out.println(" get B1 To B2 Contribution..");
        ArrayList<HashSet<String>> contribution = getB1ToB2ContributionSet(branch1_merged_commitList, fork_commitTwoParentMap, branch1_commitList, branch2_commitList, branch2_merged_commitList);
        b2_to_b1 = contribution.get(0);
        HashSet<String> parent2Set = contribution.get(1);

        System.out.println(" get B2 To B1 Contribution..");
        contribution = getB1ToB2ContributionSet(branch2_merged_commitList, upstream_commitTwoParentMap, branch2_commitList, branch1_commitList, branch1_merged_commitList);
        b1_to_b2 = contribution.get(0);


        result.add(b2_to_b1);
        result.add(b1_to_b2);
        result.add(parent2Set);

        sb.append(branch2_name + " --> " + branch1_name + ": " + b2_to_b1.size() + " commits, " + b2_to_b1 + "\n");
        sb.append(branch1_name + " --> " + branch2_name + ": " + b1_to_b2.size() + " commits. " + b1_to_b2 + "\n");

        io.writeTofile(sb.toString(), tmpDirPath + forkName + "_commitClassification.txt");

        return result;


    }

    private ArrayList<HashSet<String>> getB1ToB2ContributionSet(String[] branch1_merged_commitList, HashMap<String, String> commitTwoParentMap, List<String> branch1_commitList, List<String> branch2_commitList, String[] branch2_merged_commitList) {
        ArrayList<HashSet<String>> result = new ArrayList<>();

        HashSet<String> b2_to_b1 = new HashSet<>();
        HashSet<String> parent2Set = new HashSet<>();
        Set<String> branch2MergeCommits = new HashSet<String>(Arrays.asList(branch2_merged_commitList));
        for (String mergedCommit : branch1_merged_commitList) {
            if (!mergedCommit.equals("")) {
                getReachableCommits(mergedCommit, commitTwoParentMap, branch1_commitList, branch2_commitList);
                String[] parents = commitTwoParentMap.get(mergedCommit).split(",");
                String parent1 = parents[0];
                String parent2 = parents[1];
                HashSet<String> mergeCommmitSet = new HashSet<>();


                if (commitTwoParentMap.keySet().contains(parent2)) {
                    parent2Set.add(parent2);
                }
                while (commitTwoParentMap.keySet().contains(parent2)) {
                    mergeCommmitSet.add(parent2);
                    parent2 = commitTwoParentMap.get(parent2).split(",")[0];

                }

                if (branch2_commitList.contains(parent2)) {
                    b2_to_b1.addAll(getB1ToB2Contribution(mergedCommit, parent1, mergeCommmitSet));
                }
            }
        }


        result.add(b2_to_b1);
        result.add(parent2Set);
        return result;
    }

    private HashSet<String> getB1ToB2Contribution(String mergedCommit, String parent1, HashSet<String> mergeCommmitSet) {
        HashSet<String> b1_to_b2 = new HashSet<>();
        b1_to_b2.addAll(reachable_map.get(mergedCommit));
        b1_to_b2.removeAll(reachable_map.get(parent1));
        b1_to_b2.remove(mergedCommit);
        b1_to_b2.removeAll(mergeCommmitSet);
        return b1_to_b2;

    }

    private HashMap<String, String> getParents(String repo_url) {
        HashMap<String, String> result = new HashMap<>();
        IO_Process io = new IO_Process();
        String[] commitParentList = {};
        try {
            commitParentList = io.readResult(tmpDirPath + repo_url + "/" + repo_url.split("/")[0] + "_twoParentCommit.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String commitINFO : commitParentList) {
            String[] info = commitINFO.split(",");
            result.put(info[0], info[1] + "," + info[2]);

        }
        return result;

    }


    /**
     * This function get commits in each branch, removing merged commits
     */

    public void getCommitInBranch(String repo_uri, String forkpointDate) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb_parentTwo = new StringBuilder();
        StringBuilder sb_commitHistory = new StringBuilder();
        StringBuilder sb_branchCommitDate = new StringBuilder();
        HashMap<String, String> parentTwoCommitMap = new HashMap<>();
        String pathname = tmpDirPath + repo_uri + "/";
        String repoName = repo_uri.split("/")[0];
        String[] branchArray = {};
        try {
            branchArray = io.readResult(pathname + repoName + "_branchList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        /** the pure commit list is [3,2,4,1]**/
//        HashSet<String> branchedRemoved_CommitSet = new HashSet<>();
        HashSet<String> noMergeFirstParent_CommitSet = new HashSet<>();


        for (String branch : branchArray) {
            System.out.println("analyzing branch: " + branch);
            Repository repository;

            /**Pure commit list only contains Parent1-commits, remove merge-commit from history
             * 1----> 2-----> 3
             * |      ^
             * |     |
             * 4----->
             */

            /** the pure commit list is [3, 4,1]**/
            ArrayList<String> pureCommitList = new ArrayList<>();

            /**  [2 ]**/
            ArrayList<String> all_mergeCommitList = new ArrayList<>();

            /**  [4]**/
            ArrayList<String> mergeCommmits_into_branch_List = new ArrayList<>();

            /**  [1, 3 ]**/
            ArrayList<String> no_merge_first_parent_CommitList = new ArrayList<>();

            /** [1, 4, 3] **/
            ArrayList<String> no_merge_CommitList = new ArrayList<>();


            /** get all commits merged from other branches **/
            //https://stackoverflow.com/questions/21623699/is-it-possible-to-get-a-list-of-merges-into-a-branch-from-the-github-website-or
            String[] getMergeCommitCMD = {"git", "log", "--merges", "--first-parent", branch, "--pretty=format:\"%H\""};
            String[] get_No_Merge_first_parent_CommitCMD = {"git", "log", "--no-merges", "--first-parent", branch, "--pretty=format:\"%H\""};
            String[] get_No_Merge_CommitCMD = {"git", "log", "--no-merges", branch, "--pretty=format:\"%H\""};


            try {
                repository = new FileRepository(pathname + ".git");
                Git git = new Git(repository);

                List<Ref> refList = git.branchList().call();
                List<String> existBranches = new ArrayList<>();
                for (Ref ref : refList) {
                    existBranches.add(ref.toString().split("=")[0].replace("Ref[refs/heads/", ""));
                }


                /**only checkout remote branch**/
                if (!existBranches.contains(branch)) {
                    //https://stackoverflow.com/questions/12927163/jgit-checkout-a-remote-branch
                    System.out.println("check out branch: " + branch);
                    git.checkout().
                            setCreateBranch(true).
                            setName(branch).
                            setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).
                            setStartPoint("origin/" + branch).
                            call();
                }

                if (!forkpointDate.equals("")) {
                    /**   get Merge Commit **/
                    String[] get_latest_CommitCMD = {"git", "log", branch, "--pretty=format:\"%aI\""};
                    String[] commitDate_result = io.exeCmd(get_latest_CommitCMD, pathname + ".git").split("\n");
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    String latestCommitDate = commitDate_result[0];
                    System.out.println("forkpointDate ...:" + forkpointDate);
                    System.out.println("latestCommitDated..." + latestCommitDate);


//                        DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
//                        TemporalAccessor accessor = timeFormatter.parse(latestCommitDate);
//                        Date latestCommitDated_time = Date.from(Instant.from(accessor));
//                        Date forkpointDate_time = formatter.parse(forkpointDate.replaceAll("Z$", "+0000"));

                    ZonedDateTime forkpointDate_time = ZonedDateTime.parse(forkpointDate);
                    ZonedDateTime latestCommitDated_time = ZonedDateTime.parse(latestCommitDate);


                    System.out.println("forkpointDate_time:" + forkpointDate_time);
                    System.out.println("latestCommitDated_time:" + latestCommitDated_time);

//                        if (latestCommitDated_time.before(forkpointDate_time)) {
                    if (latestCommitDated_time.isBefore(forkpointDate_time)) {

                        System.out.println("ignore branch:" + branch);
                        continue;
                    }

                }

                /**   get Merge Commit **/
                String[] mergedIn_result = io.exeCmd(getMergeCommitCMD, pathname + ".git").split("\n");
                Collections.addAll(mergeCommmits_into_branch_List, mergedIn_result);

                /**   get No_Merge first parent Commit  **/
                String[] no_Merge_first_parent_result = io.exeCmd(get_No_Merge_first_parent_CommitCMD, pathname + ".git").split("\n");
                Collections.addAll(no_merge_first_parent_CommitList, no_Merge_first_parent_result);

                /**   get No_Merge Commit  **/
                String[] noMerge_result = io.exeCmd(get_No_Merge_CommitCMD, pathname + ".git").split("\n");
                Collections.addAll(no_merge_CommitList, noMerge_result);


                /** get commit history of a branch **/
                Iterable<RevCommit> logs = new Git(repository)
                        .log()
                        .add(repository.resolve("refs/remotes/origin/" + branch))
                        .call();

                for (RevCommit rev : logs) {
                    String sha = rev.getName();
                    pureCommitList.add(sha);
                    if (rev.getParentCount() == 2) {
                        all_mergeCommitList.add(sha);

                        sb_parentTwo.append(sha + "," + rev.getParent(0).getName() + "," + rev.getParent(1).getName() + "\n");
                        parentTwoCommitMap.put(sha, rev.getParent(0).getName() + "," + rev.getParent(1).getName());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoHeadException e) {
                e.printStackTrace();
            } catch (GitAPIException e) {
                e.printStackTrace();
            }


            pureCommitList.removeAll(mergeCommmits_into_branch_List);

            ArrayList<String> tmp_List = new ArrayList<>();
//            tmp_List.addAll(no_merge_CommitList);
//            tmp_List.removeAll(no_merge_first_parent_CommitList);
//            branchedRemoved_CommitSet.addAll(no_merge_CommitList);

            noMergeFirstParent_CommitSet.addAll(no_merge_first_parent_CommitList);


            sb.append(branch + "," + no_merge_first_parent_CommitList + "," + mergeCommmits_into_branch_List + "\n");
            sb_commitHistory.append(branch + "," + pureCommitList + "," + mergeCommmits_into_branch_List + "\n");


        }

//        branchedRemoved_CommitSet.removeAll(noMergeFirstParent_CommitSet);

        io.rewriteFile(sb.toString(), pathname + repoName + "_branch_commitList.txt");
        io.rewriteFile(sb_parentTwo.toString(), pathname + repoName + "_twoParentCommit.txt");
        io.rewriteFile(sb_commitHistory.toString(), pathname + repoName + "_CommitHistory.txt");
//        io.rewriteFile(branchedRemoved_CommitSet.toString(), pathname + repoName + "_removedBranchCommits.txt");
    }


    private HashSet<String> getReachableCommits(String mergedCommitSHA, HashMap<String, String> commitTwoParentMap, List<String> branch1_commitList, List<String> branch2_commitList) {
        HashSet<String> result = new HashSet<>();
        result.add(mergedCommitSHA);
        String[] parents = commitTwoParentMap.get(mergedCommitSHA).split(",");
        String parent1 = parents[0];
        String parent2 = parents[1];
//        result.add(parent1);
//        result.add(parent2);

        result.addAll(getParentReachableCommits(commitTwoParentMap, branch1_commitList, branch2_commitList, parent1));
        result.addAll(getParentReachableCommits(commitTwoParentMap, branch1_commitList, branch2_commitList, parent2));

        reachable_map.put(mergedCommitSHA, result);
        return result;
    }

    private HashSet<String> getParentReachableCommits(HashMap<String, String> commitTwoParentMap, List<String> branch1_commitList, List<String> branch2_commitList, String parent) {
        HashSet<String> result = new HashSet<>();

        if (commitTwoParentMap.keySet().contains(parent)) {
            result.addAll(getReachableCommits(parent, commitTwoParentMap, branch1_commitList, branch2_commitList));

        } else if (branch1_commitList.contains(parent)) {
            result.add(parent);
            result.addAll(getLinearReachableCommits(branch1_commitList, parent));

        } else if (branch2_commitList.contains(parent)) {
            result.addAll(getLinearReachableCommits(branch2_commitList, parent));
            result.add(parent);
        }

        reachable_map.put(parent, result);
        return result;

    }

    private HashSet<String> getLinearReachableCommits(List<String> branch1_commitList, String parent1) {
        HashSet<String> result = new HashSet<>();
        result.add(parent1);
        int indexOfParent1 = branch1_commitList.indexOf(parent1);
        for (int i = branch1_commitList.size() - 1; i > indexOfParent1; i--) {
            result.add(branch1_commitList.get(i));
        }
        reachable_map.put(parent1, result);
        return result;
    }

}
