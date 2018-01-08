import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Created by shuruiz on 12/29/17.
 */
public class GraphBasedClassifier {

    static String tmpDirPath;
    static String current_dir;
    HashMap<String, ArrayList<String>> fork_HistoryMap, upstream_HistoryMap;
    HashSet<String> fork_originCommitSet, fork_mergeCommitSet;
    HashSet<String> upstream_originCommitSet, upstream_mergeCommitSet;
    String forkPointCommitSHA = "";
    HashMap<String, Integer> distance2Fork_map = new HashMap<>();
    HashMap<String, Integer> distance2Upstream_map = new HashMap<>();

    ArrayList<String> checkedCommits = new ArrayList<>();
    HashSet<String> commitsBeforeForking;

    GraphBasedClassifier() {
        current_dir = System.getProperty("user.dir");
        tmpDirPath = current_dir + "/cloneRepos/";
    }

    public void analyzeCommitHistory(String forkInfo, String repoURL) {
        distance2Fork_map = new HashMap<>();
        distance2Upstream_map = new HashMap<>();
        forkPointCommitSHA = "";
        checkedCommits = new ArrayList<>();
        /** result**/
        HashSet<String> onlyFork = new HashSet<>();
        HashSet<String> onlyUpstream = new HashSet<>();
        HashSet<String> fork2Upstream = new HashSet<>();
        HashSet<String> upstream2Fork = new HashSet<>();

        JgitUtility jg = new JgitUtility();
        String forkUrl = forkInfo.split(",")[0];

        String upstreamUrl = forkInfo.split(",")[1];
        String forkpointDate = forkInfo.split(",")[2];

        /**clone fork to local*/
        System.out.println("git clone " + forkUrl);
        jg.cloneRepo(forkUrl);
        System.out.println("git clone " + upstreamUrl);
        jg.cloneRepo(upstreamUrl);

        /** get commit in branch**/
        /**----   in fork **/
        System.out.println("getting Commits In all Branches of fork: " + forkUrl);
        fork_HistoryMap = getCommitInBranch(forkUrl, forkpointDate);
        /**----  in upstream **/
        System.out.println("getting Commits In all Branches of upstream: " + upstreamUrl);
        upstream_HistoryMap = getCommitInBranch(upstreamUrl, "");


        IO_Process io = new IO_Process();
        HashMap<String, List<String>> fork_branch_History_map, upstream_branch_History_map;
        HashMap<String, List<String>> fork_origin_History_map, upstream_origin_History_map;
        String[] upstream_withMeged_branchArray;

        try {
            System.out.println("getting fork branch commit list..");
            String fork_branchStr = io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_branch_commitList.txt");
            if (fork_branchStr.equals("")) {
                StringBuilder sb_result = new StringBuilder();
                sb_result.append(forkUrl + "," + upstreamUrl + ",0,0,0,0,[],[],[],[]\n");
                io.writeTofile(sb_result.toString(), current_dir + "/result/" + repoURL + "/graph_result.csv");
                return;

            }


            System.out.println("getting fork commit history map..");
            fork_origin_History_map = getHistory(fork_branchStr.split("\n"));
            fork_branch_History_map = getHistory(io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_CommitHistory.txt").split("\n"));
            String fork_mergeCommitStr = io.removeBrackets(io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_all_mergedCommit.txt"));
            fork_mergeCommitSet = new HashSet<>(Arrays.asList(fork_mergeCommitStr.split(", ")));



            System.out.println("getting upstream branch commit list..");

            String upstream_branchStr = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_branch_commitList.txt");
            upstream_origin_History_map = getHistory(upstream_branchStr.split("\n"));

            upstream_withMeged_branchArray = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_CommitHistory.txt").split("\n");
            upstream_branch_History_map = getHistory(upstream_withMeged_branchArray);
            String upstream_mergeCommitStr = io.removeBrackets(io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_all_mergedCommit.txt"));
            upstream_mergeCommitSet = new HashSet<>(Arrays.asList(upstream_mergeCommitStr.split(", ")));


            /**   get forkPointCommitSHA **/
            System.out.println("getting fork point...");
            int index;
            String sha = "";

            ArrayList<Integer> min_index = new ArrayList<>();
            ArrayList<String> min_sha = new ArrayList<>();
            fork_originCommitSet = new HashSet<>();
            upstream_originCommitSet = new HashSet<>();
            for (List<String> fork_br_1 : fork_origin_History_map.values()) {
                fork_originCommitSet.addAll(fork_br_1);
                for (List<String> upstream_br_1 : upstream_origin_History_map.values()) {
                    upstream_originCommitSet.addAll(upstream_br_1);
                    int length;

                    length = fork_br_1.size() >= upstream_br_1.size() ? upstream_br_1.size() : fork_br_1.size();
                    index = length;

                    for (int i =  1; i <= length; i++) {
                        if (!fork_br_1.get(fork_br_1.size()-i).equals(upstream_br_1.get(upstream_br_1.size()-i))) {
                            break;
                        }
                        index = i;
                        sha = fork_br_1.get(fork_br_1.size()-i);

                    }
                    min_index.add(index);
                    min_sha.add(sha);
                }
            }


            int min = min_index.get(0);
            for (int i : min_index) {
                min = min < i ? min : i;
            }

            commitsBeforeForking = new HashSet<>();
            forkPointCommitSHA = min_sha.get(min_index.indexOf(min));
            System.out.println("forkPointCommitSHA is "+forkPointCommitSHA);

            System.out.println("getting commitsBeforeForking ... ");

            for (List<String> fork_br_1 : fork_origin_History_map.values()) {
                for (int i = fork_br_1.size() - 1; i > fork_br_1.indexOf(forkPointCommitSHA); i--) {
                    System.out.print(".");
                    commitsBeforeForking.add(fork_br_1.get(i));
                }
                break;
            }

            System.out.println("\nadding commits not in any branch into commitsBeforeForking ... ");

            HashSet<String> missingCommits = new HashSet<>();
            missingCommits.addAll(getAllAncestor(forkPointCommitSHA, fork_HistoryMap));
            commitsBeforeForking.addAll(missingCommits);
            commitsBeforeForking.add(forkPointCommitSHA);

            System.out.println("there are "+commitsBeforeForking.size()+" commitsBeforeForking ... ");


            System.out.println("initialize distance to fork/upstream ... ");

            for (String c : fork_originCommitSet) {
                distance2Fork_map.put(c, 0);

            }

            for (String c : upstream_originCommitSet) {
                distance2Upstream_map.put(c, 0);
            }
            fork_originCommitSet.removeAll(commitsBeforeForking);
            upstream_originCommitSet.removeAll(commitsBeforeForking);
            HashSet<String> duplicateCommits = new HashSet<>();
            for (String c : fork_originCommitSet) {
                if (upstream_originCommitSet.contains(c)) {
                    duplicateCommits.add(c);
                }
            }
            fork_originCommitSet.removeAll(duplicateCommits);
            upstream_originCommitSet.removeAll(duplicateCommits);

//            String upstream_originCommitStr = io.removeBrackets(io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_originCommit.txt"));
//            upstream_originCommitSet = new HashSet<>(Arrays.asList(upstream_originCommitStr.split(", ")));


            HashSet<String> upstream_CommitHistorySet = new HashSet<>();

            for (List<String> upstream_br_commitHistory : upstream_branch_History_map.values()) {
                upstream_CommitHistorySet.addAll(upstream_br_commitHistory);
            }
            upstream_CommitHistorySet.removeAll(commitsBeforeForking);

            System.out.println("start to calculate distance from fork to upstream, in total:  "+ fork_originCommitSet.size()+"original commits in fork");
            for (String forkOriginCommit : fork_originCommitSet) {
                if (!fork_mergeCommitSet.contains(forkOriginCommit)) {
                    System.out.println(forkOriginCommit);
                    getDistance(forkOriginCommit, upstream_branch_History_map, "upstream");
                }
            }

            System.out.println("start to calculate distance from upstream  to fork, in total:  "+ upstream_CommitHistorySet.size()+" all commits (including origin and merged commits) in upstream");
            checkedCommits = new ArrayList<>();
            for (String upstream_commit : upstream_CommitHistorySet) {
                System.out.println(upstream_commit);
                if (!upstream_mergeCommitSet.contains(upstream_commit)) {
                    getDistance(upstream_commit, fork_branch_History_map, "fork");
                }

            }


            distance2Fork_map.forEach((fork_origin_commit, d) -> {
                int distance2Upstream = distance2Upstream_map.get(fork_origin_commit) != null ? distance2Upstream_map.get(fork_origin_commit) : 0;
                if (d == 0 && distance2Upstream == 1) {
                    fork2Upstream.add(fork_origin_commit);
                } else if (d == 0 && distance2Upstream == -1) {
                    onlyFork.add(fork_origin_commit);
                } else if (d == 1 && distance2Upstream == 0) {
                    upstream2Fork.add(fork_origin_commit);
                } else if (d == -1 && distance2Upstream == 0) {
                    onlyUpstream.add(fork_origin_commit);
                }
            });

            distance2Upstream_map.forEach((upstream_commit, d) -> {
                int distance2Fork = distance2Fork_map.get(upstream_commit) != null ? distance2Fork_map.get(upstream_commit) : 0;
                if (d == 0 && distance2Fork == 1) {
                    upstream2Fork.add(upstream_commit);
                } else if (d == 0 && distance2Fork == -1) {
                    onlyUpstream.add(upstream_commit);
                } else if (d == 1 && distance2Fork == 0) {
                    fork2Upstream.add(upstream_commit);
                } else if (d == -1 && distance2Fork == 0) {
                    onlyFork.add(upstream_commit);
                }
            });


        } catch (IOException e) {
            e.printStackTrace();
        }

//        sb_result.append("fork,upstream,only_F,only_U,F->U,U->F,only_F_commits,only_U_commits,F->U_commits,U->F_commits\n");

        io.writeTofile(forkUrl + "," + upstreamUrl + "," + onlyFork.size() + "," + onlyUpstream.size() + "," +
                        fork2Upstream.size() + "," + upstream2Fork.size() + "," +
                        onlyFork.toString().replace(",", "/") + "," +
                        onlyUpstream.toString().replace(",", "/") + "," +
                        fork2Upstream.toString().replace(",", "/") + "," +
                        upstream2Fork.toString().replace(",", "/") + "\n"
                , current_dir + "/result/" + repoURL + "/graph_result.csv");

    }


    public HashSet<String> getAllAncestor(String commit, HashMap<String, ArrayList<String>> historyMap) {
        HashSet<String> ancestor = new HashSet<>();

        ArrayList<String> parents = historyMap.get(commit);
        for (String p : parents) {
            if (!commitsBeforeForking.contains(p) && !fork_mergeCommitSet.contains(p) && !upstream_mergeCommitSet.contains(p)) {
                ancestor.add(p);
            }
            ancestor.addAll(getAllAncestor(p, historyMap));
        }
        return ancestor;

    }


    /**
     * calculate reachability, from commit to forkpoint
     *
     * @param source
     * @param branch_History_map
     * @param targetName         fork or upstream
     */
    public void getDistance(String source, HashMap<String, List<String>> branch_History_map, String targetName) {


        branch_History_map.forEach((br, historyList) -> {
            String target = historyList.get(0);
            int distance = -2;
            if (!target.equals(forkPointCommitSHA)) {
                checkedCommits = new ArrayList<>();

                if (targetName.equals("fork")) {
                    if (distance2Fork_map.get(source) != null) {
                        distance = distance2Fork_map.get(source);
                    }
                } else {
                    if (distance2Upstream_map.get(source) != null) {
                        distance = distance2Upstream_map.get(source);
                    }
                }

                if (distance != 1) {
                    ArrayList<String> path = getPath(source, target, targetName);
                    System.out.println(source + " , " + target + " , " + targetName);
                    if (path.contains(source)) {
                        path.removeAll(fork_originCommitSet);
                        path.removeAll(fork_mergeCommitSet);
                        if (path.size() > 0) {
                            distance = 1;
                            System.out.println("find a bridge, start to set all parents' distance to "+targetName+ " as 1");
                            setParentsDistanceAsOne(source, targetName, distance);
                        }
                    } else {
                        distance = -1;
                    }

                    if (targetName.equals("fork")) {
                        if (distance2Fork_map.get(source) != null) {
                            if (distance2Fork_map.get(source) < distance) {
                                distance2Fork_map.put(source, distance);
                            }
                        } else {
                            distance2Fork_map.put(source, distance);
                        }
                    } else {
                        if (distance2Upstream_map.get(source) != null) {
                            if (distance2Upstream_map.get(source) < distance) {
                                distance2Upstream_map.put(source, distance);
                            }
                        } else {
                            distance2Upstream_map.put(source, distance);
                        }
                    }
                }
            }

        });

    }

    private void setParentsDistanceAsOne(String source, String targetName, int distance) {
        ArrayList<String> parents;
        String parent_1 = "";
        if (targetName.equals("fork")) {
            parents = upstream_HistoryMap.get(source);
        } else {
            parents = fork_HistoryMap.get(source);
        }
        if (parents.size() > 0) {
            parent_1 = parents.get(0);
            if (targetName.equals("fork")) {
                if (distance2Fork_map.get(parent_1) == null) {
                    if (!fork_mergeCommitSet.contains(parent_1)) {
                        distance2Fork_map.put(parent_1, distance);
                    }
                } else {
                    if (distance2Fork_map.get(parent_1) > distance) {
                        distance2Fork_map.put(parent_1, distance);
                    }
                }
            } else {
                if (distance2Upstream_map.get(parent_1) == null) {
                    if (!upstream_mergeCommitSet.contains(parent_1)) {
                        distance2Upstream_map.put(parent_1, distance);
                    }
                } else {
                    if (distance2Upstream_map.get(parent_1) > distance) {
                        distance2Upstream_map.put(parent_1, distance);
                    }
                }
            }
            if (!parent_1.equals("") && !parent_1.equals(forkPointCommitSHA)) {
                setParentsDistanceAsOne(parent_1, targetName, distance);
            } else {
                return;
            }
        } else {
            return;
        }


    }

    /**
     * @param source
     * @param target
     * @param targetName fork or upstream
     * @return
     */

    public ArrayList<String> getPath(String source, String target, String targetName) {
        System.out.print(" "+target);
        ArrayList<String> path = new ArrayList<>();
        ArrayList<String> parents;
        if (targetName.equals("fork")) {
            parents = fork_HistoryMap.get(target);
        } else {
            parents = upstream_HistoryMap.get(target);
        }

        if (parents != null && parents.size() > 0) {
            /**  dealing with parent_1 **/
            String parent_1 = parents.get(0);
            String parent_2 = "";
            if (parents.size() == 2) {
                parent_2 = parents.get(1);
            }
            if (parent_1.equals(source) || parent_2.equals(source)) {
                path.add(target);
                path.add(source);
            } else if (parent_1.equals(forkPointCommitSHA) || parent_2.equals(forkPointCommitSHA)) {
                return path;
            } else {
                path.addAll(exploreParents(source, target, targetName, parent_1));
                if (path.contains(source)) {
                    return path;
                }
                path.addAll(exploreParents(source, target, targetName, parent_2));
            }

        } else {
            return path;
        }
        return path;
    }

    private ArrayList<String> exploreParents(String source, String target, String targetName, String parent) {
        ArrayList<String> path = new ArrayList<>();
        if (!checkedCommits.contains(parent)) {
            checkedCommits.add(parent);

            ArrayList<String> tmpPath = getPath(source, parent, targetName);
            if (tmpPath.size() > 0) {
                path.add(target);
                path.addAll(tmpPath);
            }
        }
        return path;
    }


    /**
     * This function get commits in each branch, removing merged commits
     */

    public HashMap<String, ArrayList<String>> getCommitInBranch(String repo_uri, String forkpointDate) {
        HashMap<String, ArrayList<String>> forkHistoryMap = new HashMap<>();

        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb_parentTwo = new StringBuilder();
        StringBuilder sb_commitHistory = new StringBuilder();
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
        HashSet<String> all_mergeCommitList = new HashSet<>();


        HashSet<String> fork_originCommitSet = new HashSet<>();


        /**  no_merge [1, 2, 4, 3, 7 ] **/
        ArrayList<String> no_merge_CommitList = new ArrayList<>();

        /** currentBr_merge [6, 5] **/
        ArrayList<String> currentBr_mergeCommitList = new ArrayList<>();


        for (String branch : branchArray) {
            System.out.println("analyzing branch: " + branch);
            Repository repository;

            /**Pure commit list only contains Parent1-commits, remove merge-commit from history
             * 1----> 2--------> 3 -------->6
             *        |                    ^
             *        |                    |
             *        --- 4 ------> 5 -----
             *            |         ^
             *            |         |
             *            ---- 7 ---
             */

            /** commit history   [6, 5, 7, 4, 3, 2, 1] **/
            ArrayList<String> commitHistory = new ArrayList<>();
            /**  contribution [4, 7 ] =  no_merge - origin **/
            ArrayList<String> contributionCommitList = new ArrayList<>();

            /** origin [1, 2, 3 ]**/
            ArrayList<String> originCommitList = new ArrayList<>();

            /** get all commits merged from other branches **/
            //https://stackoverflow.com/questions/21623699/is-it-possible-to-get-a-list-of-merges-into-a-branch-from-the-github-website-or
            String[] getMergeCommitCMD = {"git", "log", "--merges", "--first-parent", branch, "--pretty=format:\"%H\""};
            String[] get_origin_CommitCMD = {"git", "log", "--no-merges", "--first-parent", branch, "--pretty=format:\"%H\""};
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

                if (!forkpointDate.equals("") && branchArray.length > 1) {
                    /**   get Merge Commit **/
                    String[] get_latest_CommitCMD = {"git", "log", branch, "--pretty=format:\"%aI\""};
                    String[] commitDate_result = io.exeCmd(get_latest_CommitCMD, pathname + ".git").split("\n");
                    String latestCommitDate = commitDate_result[0];

                    ZonedDateTime forkpointDate_time = ZonedDateTime.parse(forkpointDate);
                    ZonedDateTime latestCommitDated_time = ZonedDateTime.parse(latestCommitDate);

                    System.out.println("forkpointDate_time:" + forkpointDate_time);
                    System.out.println("latestCommitDated_time:" + latestCommitDated_time);

                    if (latestCommitDated_time.isBefore(forkpointDate_time)) {
                        System.out.println("ignore branch:" + branch);
                        continue;
                    }

                }

                /**   get Merge Commit **/
                String[] mergedIn_result = io.exeCmd(getMergeCommitCMD, pathname + ".git").split("\n");
                Collections.addAll(currentBr_mergeCommitList, mergedIn_result);

                /**   get No_Merge first parent Commit  **/
                String[] no_Merge_first_parent_result = io.exeCmd(get_origin_CommitCMD, pathname + ".git").split("\n");
                Collections.addAll(originCommitList, no_Merge_first_parent_result);
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
                    commitHistory.add(sha);
                    if (forkHistoryMap.get(sha) == null) {
                        forkHistoryMap.put(sha, new ArrayList<>());
                        if (rev.getParentCount() == 2) {

                            all_mergeCommitList.add(sha);
                            String p1 = rev.getParent(0).getName();
                            String p2 = rev.getParent(1).getName();

                            forkHistoryMap.get(sha).add(p1);
                            forkHistoryMap.get(sha).add(p2);

                            sb_parentTwo.append(sha + "," + p1 + "," + p2 + "\n");
                            parentTwoCommitMap.put(sha, p1 + "," + p2);
                        } else {
                            if (rev.getParentCount() == 1) {
                                forkHistoryMap.get(sha).add(rev.getParent(0).getName());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoHeadException e) {
                e.printStackTrace();
            } catch (GitAPIException e) {
                e.printStackTrace();
            }

            sb_commitHistory.append(branch + "," + commitHistory + "\n");
            /**  contribution =  no_merge - origin **/
            for (String c : no_merge_CommitList) {
                if (!originCommitList.contains(c)) {
                    contributionCommitList.add(c);
                }
            }

            sb.append(branch + "," + originCommitList + "\n");
//            sb.append(branch + "," + originCommitList + "," + contributionCommitList + "\n");

            fork_originCommitSet.addAll(originCommitList);
        }

        io.rewriteFile(sb.toString(), pathname + repoName + "_branch_commitList.txt");
        io.rewriteFile(sb_parentTwo.toString(), pathname + repoName + "_twoParentCommit.txt");
        io.rewriteFile(sb_commitHistory.toString(), pathname + repoName + "_CommitHistory.txt");
        io.rewriteFile(all_mergeCommitList.toString(), pathname + repoName + "_all_mergedCommit.txt");
        io.rewriteFile(fork_originCommitSet.toString(), pathname + repoName + "_originCommit.txt");

        return forkHistoryMap;
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
}
