import Djikstra.DijkstraAlgorithm;
import Djikstra.Graph;
import Djikstra.Vertex;
import org.apache.solr.common.util.Hash;
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


public class GraphBasedAnalyzer {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;
    static int maxAnalyzedForkNum = 100;

    HashMap<String, ArrayList<String>> all_HistoryMap = new HashMap<>();
    HashSet<String> upstream_firstCommit_set = new HashSet<>();
    HashSet<String> fork_firstCommit_set = new HashSet<>();


    GraphBasedAnalyzer() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            graph_dir = output_dir + "ClassifyCommit/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GraphBasedAnalyzer graphBasedAnalyzer = new GraphBasedAnalyzer();

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
            String classifyCommit_file = graph_dir +projectUrl.replace("/", ".") + "_graph_result.csv";
            String repoName = projectUrl.split("/")[0];
            /** get active fork list for given repository **/
            System.out.println("get all active forks of repo: " + repoName);

//            List<String> activeForkList = io.getActiveForksFromDatabase(projectUrl);
            List<String> activeForkList = io.getActiveForksFromPRresult(projectUrl);
            System.out.println(activeForkList.size()+ " forks has submitted PR..");

            HashSet<String> select_activeForkList = new HashSet<>();

                if (activeForkList.size() > maxAnalyzedForkNum) {
                    while(select_activeForkList.size()<maxAnalyzedForkNum) {
                        Random rand = new Random();
                        int i = rand.nextInt(activeForkList.size()-1) + 1;
                        select_activeForkList.add(activeForkList.get(i));
                    }

                } else {
                    select_activeForkList.addAll(activeForkList);
                }
            System.out.println("randomly sample :" +select_activeForkList.size()+ " forks...");



            /**   classify commits **/
            /**  by graph  **/
            System.out.println("graph-based...");
            StringBuilder sb_result = new StringBuilder();
            sb_result.append("fork,upstream,only_F,only_U,F->U,U->F\n");
            io.rewriteFile(sb_result.toString(), classifyCommit_file);

            /** clone project **/
            new JgitUtility().cloneRepo_cmd(select_activeForkList, projectUrl);

            for (String forkURL : select_activeForkList) {
                System.out.println("FORK: " + forkURL);
                graphBasedAnalyzer.analyzeCommitHistory(forkURL, projectUrl);
            }
        }
    }




    public void analyzeCommitHistory(String forkUrl, String projectURL) {
        all_HistoryMap=new HashMap<>();
        upstream_firstCommit_set=new HashSet<>();
        fork_firstCommit_set=new HashSet<>();
        IO_Process io = new IO_Process();
        String classifyCommit_file = graph_dir + projectURL.replace("/", ".") + "_graph_result.csv";

        /** result**/
        HashSet<String> onlyFork = new HashSet<>();
        HashSet<String> onlyUpstream = new HashSet<>();
        HashSet<String> fork2Upstream = new HashSet<>();
        HashSet<String> upstream2Fork = new HashSet<>();


        /** get commit in branch and get commit history */
        upstream_firstCommit_set = getCommitInBranch(projectURL, projectURL);
        fork_firstCommit_set = getCommitInBranch(forkUrl, projectURL);


        HashSet<String> allCommits = new HashSet<>();
        allCommits.addAll(all_HistoryMap.keySet());


        /** Dijkstra ---  calculate shortest path  **/

        Graph graph = new Graph(all_HistoryMap);
        System.out.println("distance to updatream");
        HashMap<String, Integer> distance2Upstream_map = getShortestPath_Dij(upstream_firstCommit_set, graph, allCommits);
        System.out.println("distance to fork");
        HashMap<String, Integer> distance2Fork_map = getShortestPath_Dij(fork_firstCommit_set, graph, allCommits);

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


        io.writeTofile(forkUrl + "," + projectURL + "," + onlyFork.size() + "," + onlyUpstream.size() + "," +
                        fork2Upstream.size() + "," + upstream2Fork.size() + "," +
                        onlyFork.toString().replace(",", "/") + "," +
                        onlyUpstream.toString().replace(",", "/") + "," +
                        fork2Upstream.toString().replace(",", "/") + "," +
                        upstream2Fork.toString().replace(",", "/") + "\n"
                , classifyCommit_file);


    }


    public HashSet<String> getCommitInBranch(String forkUrl, String projectUrl) {

        IO_Process io = new IO_Process();
        HashSet<String> firstCommit_set = new HashSet<>();
        String branch = "origin";
        if (!projectUrl.equals(forkUrl)) {
            branch = forkUrl.split("/")[0];
        }

        String cmd_getUpstreamBranch = "git branch --all --list "+branch+"*";
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
                }
            }
        }

        return firstCommit_set;

    }

    private HashMap<String, Integer> getShortestPath_Dij(HashSet<String> latestCommits, Graph graph, HashSet<String> allCommits) {
        HashMap<String, Integer> distance_map = new HashMap<>();
        System.out.println("set all commits distance as 999");
        for (String commit : allCommits) {
            distance_map.put(commit, 999);
        }

        DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);

        System.out.println(latestCommits.size() + " loops");
        for (String target : latestCommits) {
            System.out.println("target: " + target);
            dijkstra.execute(new Vertex(target));
            HashMap<Vertex, Integer> tmp_distance_map = (HashMap<Vertex, Integer>) dijkstra.getDistance();
//            System.out.println("get " + tmp_distance_map.keySet().size() + " reacheable vertex, get min distance");
            for (String c : allCommits) {

                int old = distance_map.get(c);
                if (tmp_distance_map.get(new Vertex(c)) != null) {
                    int updated = tmp_distance_map.get(new Vertex(c));
                    int newdist = old <= updated ? old : updated;
                    /**remove merge commit*/
                    if(all_HistoryMap.get(c).size()==1) {
                        distance_map.put(c, newdist);
                    }
                }

            }
        }
        return distance_map;
    }


}
