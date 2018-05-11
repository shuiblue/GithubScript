import Djikstra.DijkstraAlgorithm;
import Djikstra.Edge;
import Djikstra.Graph;
import Djikstra.Vertex;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 12/29/17.
 */
public class GraphBasedClassifier {

    static String tmpDirPath;
    static String current_dir;
    HashMap<String, ArrayList<String>> fork_HistoryMap, upstream_HistoryMap;
    HashSet<String> fork_mergeCommitSet, upstream_mergeCommitSet;
    String forkPointCommitSHA = "";

    ArrayList<String> checkedCommits;
    HashSet<String> checkedCommitsBeforeForking;

    GraphBasedClassifier() {
         String current_OS = System.getProperty("os.name").toLowerCase();
        current_dir = System.getProperty("user.dir");
//        tmpDirPath = current_dir + "/cloneRepos/";

        if (current_OS.indexOf("mac") >= 0) {
            tmpDirPath = "/Users/shuruiz/Box Sync/ForkData/cloneRepos/";

        } else {
            tmpDirPath = "/home/feature/shuruiz/ForkData/cloneRepos/";

        }





    }


    public void analyzeCommitHistory(String forkInfo, String repoURL) {
        JgitUtility jg = new JgitUtility();
        IO_Process io = new IO_Process();
        forkPointCommitSHA = "";
        checkedCommits = new ArrayList<>();
        checkedCommitsBeforeForking = new HashSet<>();
        HashSet<String> fork_ignoreBranches, upstream_ignoreBranches;

        /** result**/
        HashSet<String> onlyFork = new HashSet<>();
        HashSet<String> onlyUpstream = new HashSet<>();
        HashSet<String> fork2Upstream = new HashSet<>();
        HashSet<String> upstream2Fork = new HashSet<>();

        String forkUrl = forkInfo.split(",")[0];
        String upstreamUrl = forkInfo.split(",")[1];
        String forkpointDate = forkInfo.split(",")[2];

        /**clone fork to local*/
        System.out.println("git clone " + forkUrl);
        jg.cloneRepo(forkUrl);
        System.out.println("git clone " + upstreamUrl);
        jg.cloneRepo(upstreamUrl);

        /** get commit history in each branch of fork and upstream**/
        /**----   in fork **/
        System.out.println("getting Commits In all Branches of fork: " + forkUrl);
        fork_HistoryMap = getCommitInBranch(forkUrl, forkpointDate, "");
        /**----  in upstream **/
        System.out.println("getting Commits In all Branches of upstream: " + upstreamUrl);
        upstream_HistoryMap = getCommitInBranch(upstreamUrl, "", forkUrl);


        HashMap<String, List<String>> fork_branch_History_map, upstream_branch_History_map;
        String[] upstream_withMeged_branchArray;

        try {
            System.out.println("getting fork branch commit list..");
            String[] fork_ignoreBranchesArray = io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_ignoreBranches.txt").split("\n");
            fork_ignoreBranches = new HashSet<>();
            for (String br : fork_ignoreBranchesArray) {
                fork_ignoreBranches.add(br.split(",")[0]);
            }

            String fork_branchStr = io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_branch_commitList.txt");
            if (fork_branchStr.equals("")) {
                StringBuilder sb_result = new StringBuilder();
                sb_result.append(forkUrl + "," + upstreamUrl + ",0,0,0,0,[],[],[],[]\n");
                StringBuilder sb_result_csv = new StringBuilder();
                sb_result_csv.append(forkUrl + "," + upstreamUrl + ",0,0,0,0,\n");
                io.writeTofile(sb_result_csv.toString(), current_dir + "/result/" + repoURL + "/graph_result.csv");
                io.writeTofile(sb_result.toString(), current_dir + "/result/" + repoURL + "/graph_result.txt");
                return;

            }


            System.out.println("getting fork commit history map..");
            fork_branch_History_map = getHistory(io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_CommitHistory.txt").split("\n"));
            String fork_mergeCommitStr = io.removeBrackets(io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_all_mergedCommit.txt"));
            fork_mergeCommitSet = new HashSet<>(Arrays.asList(fork_mergeCommitStr.split(", ")));


            System.out.println("getting upstream branch commit list..");
            String[] upstream_ignoreBranchesArray = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_ignoreBranches.txt").split("\n");
            upstream_ignoreBranches = new HashSet<>();
            for (String br : upstream_ignoreBranchesArray) {
                upstream_ignoreBranches.add(br.split(",")[0]);
            }

            System.out.println("get merged commits");

            upstream_withMeged_branchArray = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_CommitHistory.txt").split("\n");
            upstream_branch_History_map = getHistory(upstream_withMeged_branchArray);
            String upstream_mergeCommitStr = io.removeBrackets(io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_all_mergedCommit.txt"));
            upstream_mergeCommitSet = new HashSet<>(Arrays.asList(upstream_mergeCommitStr.split(", ")));


            /**  calculate shortest path  **/
            System.out.println("start to calculate short distance");

            HashSet<String> allCommits = new HashSet<>();
            HashSet<String> forkLatestCommits = new HashSet<>();
            HashSet<String> upstreamLatestCommits = new HashSet<>();

            HashSet<String> finalFork_ignoreBranches = fork_ignoreBranches;
            fork_branch_History_map.forEach((k, v) -> {
                if (!finalFork_ignoreBranches.contains(k)) {
                    forkLatestCommits.add(v.get(0));
                    allCommits.addAll(v);
                }

            });
            HashSet<String> finalUpstream_ignoreBranches = upstream_ignoreBranches;
            upstream_branch_History_map.forEach((k, v) -> {
                if (!finalUpstream_ignoreBranches.contains(k)) {
                    upstreamLatestCommits.add(v.get(0));
                    allCommits.addAll(v);
                }

            });

            allCommits.removeAll(upstream_mergeCommitSet);
            allCommits.removeAll(fork_mergeCommitSet);
            System.out.println(allCommits.size() + " commits");

            /** Dijkstra ---  calculate shortest path  **/


            initializeGraph();
            Graph graph = new Graph(all_historyMap);
            System.out.println("distance to updatream");
            HashMap<String, Integer> distance2Upstream_map = getShortestPath_Dij(upstreamLatestCommits, graph, allCommits);
            System.out.println("distance to fork");
            HashMap<String, Integer> distance2Fork_map = getShortestPath_Dij(forkLatestCommits, graph, allCommits);

            distance2Fork_map.forEach((fork_origin_commit, d) -> {
                int distance2Upstream = distance2Upstream_map.get(fork_origin_commit) != null ? distance2Upstream_map.get(fork_origin_commit) : 0;
                if (distance2Upstream == 999) {
                    onlyFork.add(fork_origin_commit);
                } else if (d == 999) {
                    onlyUpstream.add(fork_origin_commit);
                } else if (d < distance2Upstream) {
                    fork2Upstream.add(fork_origin_commit);
                } else if (d > distance2Upstream) {
                    upstream2Fork.add(fork_origin_commit);
                }
            });
            System.out.println(onlyFork.size() + " , " + onlyUpstream.size() + " , " + fork2Upstream.size() + " , " + upstream2Fork.size());
        } catch (IOException e) {
            e.printStackTrace();
        }

        io.writeTofile(forkUrl + "," + upstreamUrl + "," + onlyFork.size() + "," + onlyUpstream.size() + "," +
                fork2Upstream.size() + "," + upstream2Fork.size() + "," + "\n", current_dir + "/result/" + repoURL + "/graph_result.csv");

        io.writeTofile(forkUrl + "," + upstreamUrl + "," + onlyFork.size() + "," + onlyUpstream.size() + "," +
                        fork2Upstream.size() + "," + upstream2Fork.size() + "," +
                        onlyFork.toString().replace(",", "/") + "," +
                        onlyUpstream.toString().replace(",", "/") + "," +
                        fork2Upstream.toString().replace(",", "/") + "," +
                        upstream2Fork.toString().replace(",", "/") + "\n"
                , current_dir + "/result/" + repoURL + "/graph_result.txt");

        /** remove local copy of fork**/
        try {
            io.deleteDir(new File(current_dir + "/cloneRepos/" + forkUrl));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private HashMap<String, Integer> getShortestPath_Dij(HashSet<String> latestCommits, Graph graph, HashSet<String> allCommits) {
        HashMap<String, Integer> distance_map = new HashMap<>();
        System.out.println("set all commits distance as 999");
        for (String commit : allCommits) {
            distance_map.put(commit, 999);
        }


        System.out.println("init graph");
        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        System.out.println(latestCommits.size() + " loops");
        for (String target : latestCommits) {
            long start = System.nanoTime();
            System.out.println("target: " + target);
            dijkstra.execute(new Vertex(target));
                HashMap<Vertex, Integer> tmp_distance_map = (HashMap<Vertex, Integer>) dijkstra.getDistance();

                long end = System.nanoTime();
                long used = end - start;
                System.out.println("dijkstra :" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");
                System.out.println("get " + tmp_distance_map.keySet().size() + " reacheable vertex, get min distance");
                for (String c : allCommits) {

                    int old = distance_map.get(c);
                    if (tmp_distance_map.get(new Vertex(c)) != null) {
                        int updated = tmp_distance_map.get(new Vertex(c));
                        int newdist = old <= updated ? old : updated;
                        distance_map.put(c, newdist);
//                        if (c.equals("1dec645383385e5728e47db9ae836b104701a66b")) {
//                            System.out.println(target + "  ,  " + newdist);
//                        }
                    }

                }
        }
        return distance_map;
    }


    HashSet<Vertex> nodeSet = new HashSet<>();
    List<Vertex> nodes = new ArrayList<Vertex>();
    List<Edge> edges = new ArrayList<Edge>();


    private void initializeGraph() {

        generatingGraphForDijkstra(fork_HistoryMap);
        generatingGraphForDijkstra(upstream_HistoryMap);
        nodes.addAll(nodeSet);
    }


    HashMap<String, ArrayList<String>> all_historyMap = new HashMap<>();

    private void generatingGraphForDijkstra(HashMap<String, ArrayList<String>> historyMap) {

        historyMap.forEach((k, v) -> {
            all_historyMap.put(k, v);
            Vertex v_k = new Vertex(k);
            nodeSet.add(v_k);
            if (v.size() > 0) {
                String p1 = v.get(0);
                Vertex v_p1 = new Vertex(p1);
                nodeSet.add(v_p1);
                addEdge(v_k, v_p1, 0);

                if (v.size() >= 2) {
                    for (int i = 1; i < v.size() - 1; i++) {
                        String p2 = v.get(i);
                        Vertex v_p2 = new Vertex(p2);
                        nodeSet.add(v_p2);
                        addEdge(v_k, v_p2, 1);
                    }


                }
            }
        });
    }

    private void addEdge(Vertex source, Vertex dest, int weight) {
        Edge edge = new Edge(source, dest, weight);
        edges.add(edge);
    }

    public ArrayList<String> getPath(String source, String target) {

        ArrayList<String> parents;
        if (fork_HistoryMap.get(target) != null) {
            parents = fork_HistoryMap.get(target);
        } else {
            parents = upstream_HistoryMap.get(target);
        }
        ArrayList<String> path = new ArrayList<>();
        if (parents.size() > 0) {
            /**  dealing with parent_1 **/
            String parent_1 = parents.get(0);

            if (parents.contains(source)) {
                path.add(target);
                path.add(source);
            } else {
                /**  recursion **/
                path.addAll(exploreParents(source, target, parent_1));
                if (path.contains(source)) {
                    return path;
                }

                String parent_2 ;
                if (parents.size() >= 2) {
                    for (int i = 1; i < parents.size(); i++) {
                        parent_2 = parents.get(i);
                        if (!parent_2.equals("")) {
                            path.addAll(exploreParents(source, target, parent_2));
                        }
                        if (path.contains(source)) {
                            return path;
                        }
                    }
                }


                /** non-recursion  **/

            }

        }
        return path;


    }


    private ArrayList<String> exploreParents(String source, String target, String parent) {
        ArrayList<String> path = new ArrayList<>();
        if (!checkedCommits.contains(parent)) {
            checkedCommits.add(parent);

            ArrayList<String> tmpPath = getPath(source, parent);
            if (tmpPath.size() > 0) {
                path.add(target);
                path.addAll(tmpPath);
            }
        }
        return path;
    }


    /**
     * This function gets commits in each branch, removes stale branches
     */

    public HashMap<String, ArrayList<String>> getCommitInBranch(String repo_uri, String forkpointDate, String forkUrl) {
        HashMap<String, ArrayList<String>> forkHistoryMap = new HashMap<>();

        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb_commitHistory = new StringBuilder();
        StringBuilder sb_ignoreBranches = new StringBuilder();
        StringBuilder sb_update_fork_ignoreBranches = new StringBuilder();
        StringBuilder sb_multipleParentsSHA = new StringBuilder();

        String pathname = tmpDirPath + repo_uri + "/";
        String repoName = repo_uri.split("/")[0];

        String update_fork_pathname = tmpDirPath + forkUrl + "/";
        String update_forkName = forkUrl.split("/")[0];

        String[] branchArray = {};
        try {
            branchArray = io.readResult(pathname + repoName + "_branchList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        /** the pure commit list is [3,2,4,1]**/
        HashSet<String> all_mergeCommitList = new HashSet<>();


        /**  no_merge [1, 2, 4, 3, 7 ] **/
        ArrayList<String> no_merge_CommitList = new ArrayList<>();

        /** currentBr_merge [6, 5] **/
        ArrayList<String> currentBr_mergeCommitList = new ArrayList<>();

        HashSet<String> update_fork_ignoreBranches = new HashSet<>();
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
            /**  no_merge [1 ,2 , 3, 6] **/
            ArrayList<String> firstParent_CommitList = new ArrayList<>();


            /** origin [1, 2, 3 ]**/
            ArrayList<String> originCommitList = new ArrayList<>();

            /** get all commits merged from other branches **/
            //https://stackoverflow.com/questions/21623699/is-it-possible-to-get-a-list-of-merges-into-a-branch-from-the-github-website-or
            String[] getMergeCommitCMD = {"git", "log", "--merges", "--first-parent", branch, "--pretty=format:\"%H\""};
            String[] get_origin_CommitCMD = {"git", "log", "--no-merges", "--first-parent", branch, "--pretty=format:\"%H\""};
            String[] get_firstParent_CommitCMD = {"git", "log", "--first-parent", branch, "--pretty=format:\"%H\""};
            String[] get_No_Merge_CommitCMD = {"git", "log", "--no-merges", branch, "--pretty=format:\"%H\""};

            try {
                repository = new FileRepository(pathname + ".git");
                Git git = new Git(repository);

                List<Ref> refList = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
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


                String[] get_latest_CommitCMD = {"git", "log", branch, "--pretty=format:\"%H %aI\""};
                String[] commitDate_result = io.exeCmd(get_latest_CommitCMD, pathname + ".git").split("\n");
                String[] latestCommit = commitDate_result[0].split(" ");
                String latestCommitSHA = latestCommit[0];
                String latestCommitDate = latestCommit[1];
                if(!latestCommitDate.startsWith("20")){
                    System.out.println("latestCommitDate:" + latestCommitDate);
                    sb_ignoreBranches.append(branch + "," + latestCommitSHA + "\n");
                    continue;
                }

                ZonedDateTime latestCommitDated_time = ZonedDateTime.parse(latestCommitDate);
                System.out.println("latestCommitDated_time:" + latestCommitDated_time);


                if (!forkpointDate.equals("") && branchArray.length > 1) {
                    ZonedDateTime forkpointDate_time = ZonedDateTime.parse(forkpointDate);
                    System.out.println("forkpointDate_time:" + forkpointDate_time);
                    if (latestCommitDated_time.isBefore(forkpointDate_time)) {
                        System.out.println("ignore branch:" + branch);
                        sb_ignoreBranches.append(branch + "," + latestCommitSHA + "\n");
                    }
                }

                if (!forkUrl.equals("")) {
                    String[] fork_ignoreBranchesArray = io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_ignoreBranches.txt").split("\n");
                    HashSet<String> fork_ignoreBranches = new HashSet<>(Arrays.asList(fork_ignoreBranchesArray));
                    boolean sameNameFork = false;
                    String br = "";

                    update_fork_ignoreBranches.addAll(fork_ignoreBranches);

                    for (String fork_br : fork_ignoreBranches) {
                        br = fork_br;
                        sameNameFork = fork_br.contains(branch + ",");
                        if (sameNameFork) break;
                    }


                    String currentUpstreamBranch = branch + "," + latestCommitSHA;
                    if (sameNameFork) {
                        if (fork_ignoreBranches.contains(currentUpstreamBranch)) {
                            sb_ignoreBranches.append(currentUpstreamBranch + "\n");

                        } else {
                            update_fork_ignoreBranches.remove(br);
                        }
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
                /**   get firstParent_CommitList   **/
                String[] firstParent_result = io.exeCmd(get_firstParent_CommitCMD, pathname + ".git").split("\n");
                Collections.addAll(firstParent_CommitList, firstParent_result);


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
                        if (rev.getParentCount() == 1) {
                            forkHistoryMap.get(sha).add(rev.getParent(0).getName());
                        } else if (rev.getParentCount() >= 2) {

                            all_mergeCommitList.add(sha);

                            for (int i = 0; i < rev.getParentCount(); i++) {
                                String p = rev.getParent(i).getName();
                                forkHistoryMap.get(sha).add(p);
                            }

                            if (rev.getParentCount() > 2) {
                                sb_multipleParentsSHA.append(sha + " " + rev.getParentCount() + " parents" + "\n");
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
        }

        for (String forkignore : update_fork_ignoreBranches) {
            sb_update_fork_ignoreBranches.append(forkignore + "\n");
        }
        io.rewriteFile(sb.toString(), pathname + repoName + "_branch_commitList.txt");
        io.rewriteFile(sb_commitHistory.toString(), pathname + repoName + "_CommitHistory.txt");
        io.rewriteFile(all_mergeCommitList.toString(), pathname + repoName + "_all_mergedCommit.txt");
        io.rewriteFile(sb_ignoreBranches.toString(), pathname + repoName + "_ignoreBranches.txt");
        io.rewriteFile(sb_update_fork_ignoreBranches.toString(), update_fork_pathname + update_forkName + "_ignoreBranches.txt");
        io.rewriteFile(sb_multipleParentsSHA.toString(), pathname + update_forkName + "_abnormalCommits.txt");

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
