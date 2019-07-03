package Commit;

import Djikstra.DijkstraAlgorithm;
import Djikstra.Graph;
import Djikstra.Vertex;
import Repository.GithubRepository;
import Util.IO_Process;
import Util.JgitUtility;
import Util.QueryDataFromGithubAPI;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


public class GraphBasedAnalyzer {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, result_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;
    static int maxAnalyzedForkNum = 100;

    HashMap<String, ArrayList<String>> all_HistoryMap = new HashMap<>();
    HashSet<String> upstream_firstCommit_set = new HashSet<>();
    HashSet<String> fork_firstCommit_set = new HashSet<>();
    static boolean getActiveForksFromAPI = false;
    static boolean hasTimeConstraint = true;


    public GraphBasedAnalyzer() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            result_dir = output_dir + "result0821/";

            clone_dir = output_dir + "clones/";
            graph_dir = output_dir + "ClassifyCommit_new/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GraphBasedAnalyzer graphBasedAnalyzer = new GraphBasedAnalyzer();

        QueryDataFromGithubAPI queryDataFromGithubAPI = new QueryDataFromGithubAPI();
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");

        String[] repoList = {};
        /** get repo list **/

        try {
            repoList = io.readResult(current_dir + "/input/graph_repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (String projectUrl : repoList) {
            HashSet<String> select_activeForkList = new HashSet<>();
            String classifyCommit_file;
            if (getActiveForksFromAPI) {
                classifyCommit_file = graph_dir + projectUrl.replace("/", ".") + "_graph_result_allFork.csv";
            } else {
                classifyCommit_file = graph_dir + projectUrl.replace("/", ".") + "_graph_result_2019.csv";

            }

            String repoName = projectUrl.split("/")[0];
            /** get active fork list for given repository **/
            System.out.println("get all active forks of repo: " + repoName);

            HashSet<String> activeForkList;
            if (getActiveForksFromAPI) {
                String allActiveList = result_dir + projectUrl + "/all_ActiveForklist.txt";
                File all_activeFork = new File(allActiveList);

                try {
                    if (!all_activeFork.exists() || (all_activeFork.exists() && !io.readResult(allActiveList).trim().equals(""))) {
                        /**  get active forks using github api **/
                        queryDataFromGithubAPI.getActiveForkList(projectUrl, hasTimeConstraint, projectUrl);

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String all_activeForkList = "";
                /**  randomize forks from active_fork_list **/
                try {
                    all_activeForkList = io.readResult(result_dir + projectUrl + "/all_ActiveForklist.txt");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (all_activeForkList.split("\n").length <= maxAnalyzedForkNum) {
                    io.rewriteFile(all_activeForkList, result_dir + projectUrl + "/ActiveForklist.txt");
                } else {
//                        io.rewriteFile(all_activeForkList, result_dir + projectUrl + "/all_ActiveForklist.txt");
                    System.out.println("randomly pick " + maxAnalyzedForkNum + " active forks...");
                    queryDataFromGithubAPI.getRamdomForks(projectUrl, maxAnalyzedForkNum);
                }

                try {
                    select_activeForkList.addAll(Arrays.asList(io.readResult(result_dir + projectUrl + "/ActiveForklist.txt").split("\n")));
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                activeForkList = io.getActiveForksFromPRresult(projectUrl);
                if (activeForkList.size() == 0) {
                    System.out.println("no fork in database yet !");
                    io.writeTofile(projectUrl + "\n", output_dir + "no_fork_inDB.txt");
                    continue;
                }
                System.out.println(activeForkList.size() + " forks has submitted PR..");
                if (activeForkList.size() > maxAnalyzedForkNum) {
                    for (String fork : activeForkList) {
                        select_activeForkList.add(fork);
                        if (select_activeForkList.size() == maxAnalyzedForkNum)
                            break;
                    }
                } else {
                    select_activeForkList.addAll(activeForkList);
                }
            }

            if (select_activeForkList.size() > 0) {
                System.out.println("randomly sample :" + select_activeForkList.size() + " forks...");


                /**   classify commits **/
                /**  by graph  **/
                System.out.println("graph-based...");
//                StringBuilder sb_result = new StringBuilder();
//                sb_result.append("fork,upstream,only_F,only_U,F->U,U->F,commitsBeforeForking\n");
//                io.rewriteFile(sb_result.toString(), classifyCommit_file);

                /** clone project **/
                new JgitUtility().cloneRepo_cmd(select_activeForkList, projectUrl, getActiveForksFromAPI);

                for (String forkURL : select_activeForkList) {
                    if (!forkURL.trim().equals("")) {

                        System.out.println("FORK: " + forkURL);
                        graphBasedAnalyzer.analyzeCommitHistory(forkURL, projectUrl, getActiveForksFromAPI);
                    }
                }
            } else {
                io.writeTofile(projectUrl + "\n", output_dir + "forkListNull.txt");
            }

            try {
                io.deleteDir(new File(clone_dir + projectUrl));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }


    public String analyzeCommitHistory(String forkUrl, String projectURL, boolean getActiveForksFromAPI) {

        Date forkingPoint = new GithubRepository().getRepoCreatedDate(forkUrl);
        if (forkingPoint == null) {
            new IO_Process().writeTofile(forkUrl, "/DATA/shurui/ForkData/hardfork/token-broken-commitEvolExp.txt");
            return "403";

        }
        String fork = "", upstream = "";
        if (getActiveForksFromAPI) {
            fork = forkUrl.split(",")[0];
            upstream = forkUrl.split(",")[1];
        } else {
            fork = forkUrl;
            upstream = projectURL;
        }
        all_HistoryMap = new HashMap<>();
        upstream_firstCommit_set = new HashSet<>();
        fork_firstCommit_set = new HashSet<>();
        IO_Process io = new IO_Process();

        String classifyCommit_file = "";
        if (getActiveForksFromAPI) {
            classifyCommit_file = graph_dir + projectURL.replace("/", ".") + "_graph_result_allFork.csv";
        } else {
//            classifyCommit_file = graph_dir + projectURL.replace("/", ".") + "_graph_result.csv";
            classifyCommit_file = graph_dir + projectURL.replace("/", ".") + "_graph_result_2019.csv";
        }

        /** result**/
        HashSet<String> onlyFork = new HashSet<>();
        HashSet<String> onlyUpstream = new HashSet<>();
        HashSet<String> fork2Upstream = new HashSet<>();
        HashSet<String> upstream2Fork = new HashSet<>();
        HashSet<String> commitsBeforeForkingpoint = new HashSet<>();


        /** get commit in branch and get commit history 3*/
//        upstream_firstCommit_set = getCommitInBranch(projectURL, projectURL);
//        fork_firstCommit_set = getCommitInBranch(forkUrl, projectURL);
        upstream_firstCommit_set = getCommitInBranch(upstream, upstream, projectURL);
        fork_firstCommit_set = getCommitInBranch(fork, upstream, projectURL);


        HashSet<String> allCommits = new HashSet<>();
        allCommits.addAll(all_HistoryMap.keySet());


        /** Dijkstra ---  calculate shortest path  **/

        Graph graph = new Graph(all_HistoryMap);
        System.out.println("distance to updatream");
        HashMap<String, Integer> distance2Upstream_map = getShortestPath_Dij(upstream_firstCommit_set, graph, allCommits);
        System.out.println("distance to fork");
        HashMap<String, Integer> distance2Fork_map = getShortestPath_Dij(fork_firstCommit_set, graph, allCommits);

        HashSet<String> mergeCommits = new HashSet<>();
        for (String commit : distance2Fork_map.keySet()) {
            if (all_HistoryMap.get(commit).size() > 1) {
                mergeCommits.add(commit);
            }
        }


        distance2Fork_map.forEach((fork_origin_commit, d) -> {
            if (!mergeCommits.contains(fork_origin_commit)) {

                int distance2Upstream = distance2Upstream_map.get(fork_origin_commit) != null ? distance2Upstream_map.get(fork_origin_commit) : 0;
                if (distance2Upstream == 999) {
                    onlyFork.add(fork_origin_commit);
                } else if (d == 999) {
                    onlyUpstream.add(fork_origin_commit);
                } else if (d < distance2Upstream) {
                    fork2Upstream.add(fork_origin_commit);
                } else if (d > distance2Upstream) {
                    upstream2Fork.add(fork_origin_commit);
                } else {  // before forking point
                    commitsBeforeForkingpoint.add(fork_origin_commit);
                }
            }
        });
        System.out.println(onlyFork.size() + " , " + onlyUpstream.size() + " , " + fork2Upstream.size() + " , " + upstream2Fork.size() + " , " + commitsBeforeForkingpoint.size());

        io.writeTofile(fork + "," + upstream + "," + onlyFork.size() + "," + onlyUpstream.size() + "," +
                        fork2Upstream.size() + "," + upstream2Fork.size() + "," + commitsBeforeForkingpoint.size()
//
//                do not print commit sha
//                           + "," +
//                        onlyFork.toString().replace(",", "/") + "," +
////                        onlyUpstream.toString().replace(",", "/") +
//                        "," +
//                        fork2Upstream.toString().replace(",", "/") + "," +
//                        upstream2Fork.toString().replace(",", "/")+ "," +
//                        commitsBeforeForkingpoint.toString().replace(",", "/")
                        + "\n"
                , classifyCommit_file);


        /**  PRINT commits: sha, date, category **/

        distance2Fork_map.forEach((sha, d) -> {
            if (!mergeCommits.contains(sha)) {

                String cmd = "git show -s --format=\'%cr~%cI\' " + sha;
                String commitInfo = io.exeCmd(cmd.split(" "), clone_dir + projectURL).replace("\'", "").replace(",", " ").replace("~", ",");
                if (commitInfo.equals("noRepo")) {
                    System.out.println("noRepo");
                    return;
                }
                String commitDate = commitInfo.split(",")[1].split("T")[0];
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");

                Date commit_date_format = null;
                try {
                    commit_date_format = sdf.parse(commitDate);
                } catch (ParseException e) {
                    e.printStackTrace();
                }


                String category = "";
                if (onlyFork.contains(sha)) {
                    category = "OnlyF";
                } else if (onlyUpstream.contains(sha)) {
                    category = "OnlyU";
                } else if (fork2Upstream.contains(sha)) {
                    category = "F2U";
                } else if (upstream2Fork.contains(sha)) {
                    category = "U2F";
                } else if (commitsBeforeForkingpoint.contains(sha)) {
                    category = "beforeForking";
                }

                if (!category.equals("beforeForking") && commit_date_format.before(forkingPoint)) {
//                    System.out.println(category + " is wrong, early than forking point");
                    category = "beforeForking";
                }

//                System.out.println(sha + "," + category + "," + commitInfo);
                io.writeTofile(sha + "," + category + "," + commitInfo, graph_dir + forkUrl.replace("/", ".") + "_commit_date_category.csv");

            }
        });
        return "success";
    }


    public HashSet<String> getCommitInBranch(String fork, String upstream, String projectUrl) {

        if (!upstream.equals(projectUrl)) {
            System.out.println();
        }
        IO_Process io = new IO_Process();
        HashSet<String> firstCommit_set = new HashSet<>();
        String branch = "origin";
        if (!projectUrl.equals(fork) || !projectUrl.equals(upstream)) {
            branch = fork.split("/")[0];
        }

        String cmd_getUpstreamBranch = "git branch --all --list " + branch + "*";
        String[] upstream_branchList = io.exeCmd(cmd_getUpstreamBranch.split(" "), clone_dir + projectUrl).split("\n");
        for (String br : upstream_branchList) {
            if (!br.contains("HEAD")) {
                String cmd_getCommit = "git log " + br.trim() + " --pretty=format:\'%H:%P\'";
                String[] commitList = io.exeCmd(cmd_getCommit.split(" "), clone_dir + projectUrl).split("\n");
                firstCommit_set.add(commitList[0].split(":")[0].replace("\'", ""));
                for (String commit : commitList) {
                    commit = commit.replace("\'", "");
                    String[] arr = commit.split(":");
                    if (arr.length > 1) {
                        String[] parentArr = arr[1].split(" ");
                        ArrayList<String> parents = new ArrayList<>();
                        for (String p : parentArr) {
                            parents.add(p);
                        }
                        all_HistoryMap.put(arr[0], parents);
                    }

                    if (arr.length == 1) {
                        // 1st commit
                        all_HistoryMap.put(arr[0], new ArrayList<>());
                    }
                }
            }
        }

        return firstCommit_set;

    }

    private HashMap<String, Integer> getShortestPath_Dij(HashSet<String> latestCommits, Graph graph, HashSet<String> allCommits) {
        HashMap<String, Integer> distance_map = new HashMap<>();
        for (String commit : allCommits) {
            distance_map.put(commit, 999);
        }

        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        System.out.println(latestCommits.size() + " loops");
        for (String target : latestCommits) {
//            System.out.println("target: " + target);
            dijkstra.execute(new Vertex(target));
            HashMap<Vertex, Integer> tmp_distance_map = (HashMap<Vertex, Integer>) dijkstra.getDistance();
//            System.out.println("get " + tmp_distance_map.keySet().size() + " reacheable vertex, get min distance");
            for (String c : allCommits) {

                int old = distance_map.get(c);
                if (tmp_distance_map.get(new Vertex(c)) != null) {
                    int updated = tmp_distance_map.get(new Vertex(c));
                    int newdist = old <= updated ? old : updated;
                    /**remove merge commit*/
                    if (all_HistoryMap.get(c).size() == 1) {
                        distance_map.put(c, newdist);
                    }
                }

            }
        }
        return distance_map;
    }


}
