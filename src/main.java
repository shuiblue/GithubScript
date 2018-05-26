import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by shuruiz on 12/8/17.
 */
public class main {
    static String current_dir, tmpDirPath;
    static int maxAnalyzedForkNum = 100;
    static boolean hasTimeConstraint = false;

    public static void main(String[] args) {
        GraphBasedClassifier graphBasedClassifier = new GraphBasedClassifier();
        NameBasedClassifier nameBasedClassifier = new NameBasedClassifier();

        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);


        String current_OS = System.getProperty("os.name").toLowerCase();
        current_dir = System.getProperty("user.dir");
//        tmpDirPath = current_dir + "/cloneRepos/";

        if (current_OS.indexOf("mac") >= 0) {
            tmpDirPath = "/Users/shuruiz/Box Sync/ForkData";
        } else {
            tmpDirPath = "/home/feature/shuruiz/ForkData";

        }

        boolean getActiveForksFromAPI = false;
        boolean getActiveForksFromDATABASE = true;


        String[] repoList = {};
        /** get repo list **/

        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }


        for (String projectUrl : repoList) {
            if (projectUrl.trim().length() > 0) {
                String repoName = projectUrl.split("/")[0];
                /** get active fork list for given repository **/
                System.out.println("get all active forks of repo: " + repoName);
                List<String> activeForkList = new ArrayList<>();
                List<String> select_activeForkList = new ArrayList<>();
//                String[] activeForkList = {};


                if (getActiveForksFromAPI) {
                    File all_activeFork = new File(tmpDirPath + "/result/" + projectUrl + "/all_ActiveForklist.txt");
                    File selected_activeFork = new File(tmpDirPath + "/result/" + projectUrl + "/ActiveForklist.txt");

                    if (!all_activeFork.exists()) {

                        /**  get active forks using github api **/
                        String all_activeForkList = nameBasedClassifier.getActiveForkList(projectUrl, hasTimeConstraint);
                        io.rewriteFile(all_activeForkList, tmpDirPath + "/result/" + projectUrl + "/ActiveForklist.txt");
                    }

                    String all_activeForkList = "";
                    /**  randomize forks from active_fork_list **/
                    try {
                        all_activeForkList = io.readResult(tmpDirPath + "/result/" + projectUrl + "/ActiveForklist.txt");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (all_activeForkList.split("\n").length <= maxAnalyzedForkNum) {
                        io.rewriteFile(all_activeForkList, tmpDirPath + "/result/" + projectUrl + "/ActiveForklist.txt");
                    } else {
                        io.rewriteFile(all_activeForkList, tmpDirPath + "/result/" + projectUrl + "/all_ActiveForklist.txt");
                        System.out.println("randomly pick " + maxAnalyzedForkNum + " active forks...");
                        nameBasedClassifier.getRamdomForks(projectUrl, maxAnalyzedForkNum);
                    }
                    /** analyze commit history **/

                    try {

                        activeForkList = Arrays.asList(io.readResult(tmpDirPath + "/result/" + projectUrl + "/ActiveForklist.txt").split("\n"));
                        System.out.println(activeForkList.size() + " forks");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (getActiveForksFromDATABASE) {
                    activeForkList = io.getActiveForksFromDatabase(projectUrl);
                    if (activeForkList.size() > maxAnalyzedForkNum) {
                        for (int i = 0; i < maxAnalyzedForkNum; i++) {
                            select_activeForkList.add(activeForkList.get(i));
                        }
                    } else {
                        select_activeForkList.addAll(activeForkList);
                    }
                }


                /**   classify commits **/
                /**  by graph  **/
                System.out.println("graph-based...");
                StringBuilder sb_result = new StringBuilder();
                sb_result.append("fork,upstream,only_F,only_U,F->U,U->F\n");
                io.rewriteFile(sb_result.toString(), tmpDirPath + "/result/" + projectUrl + "/graph_result.csv");
                io.rewriteFile(sb_result.toString(), tmpDirPath + "/result/" + projectUrl + "/graph_result.txt");
                for (String forkInfo : select_activeForkList) {
//                for (String forkInfo : activeForkList) {
                    System.out.println("FORK: " + forkInfo);
                    graphBasedClassifier.analyzeCommitHistory(forkInfo, projectUrl);
                }
//
////                /**  by author id  **/
//////                System.out.println("authorID-based...");
//////                System.out.println("repo: " + projectUrl);
//////                /** classify commit by name **/
////////                nameBasedClassifier.classifyCommitsByAuthor(projectUrl);
////
////
                /** get fork info  **/
//                GithubApiParser githubApiParser = new GithubApiParser();
//                StringBuilder sb = new StringBuilder();
//                sb.append("forkUrl,fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type\n");
//                io.rewriteFile(sb.toString(), current_dir + "/result/" + projectUrl + "/forkInfo.csv");
////                for (String forkInfo : activeForkList) {
////                    String forkURL = forkInfo.split(",")[0];
//                    System.out.println("get fork info: " + projectUrl);
////                    System.out.println("get fork info: " + forkInfo);
////                    sb.append(forkURL + "," + githubApiParser.getForkInfo(forkURL));
//                    sb.append(projectUrl + "," + githubApiParser.getForkInfo(projectUrl));
////                }
//                io.rewriteFile(sb.toString(), current_dir + "/result/" + projectUrl + "/upstreamInfo.csv");
////
//
//                System.out.println("generating final table.");
//                /**   combines two result graph and name together **/
////                combineTwoApproaches(projectUrl);
//                /**  combine graph, info and PsR result together**/
//                combineGraphWithInfo(projectUrl);


//                /**  get PR by forkid **/
//                nameBasedClassifier.getPRbyresuletable(projectUrl);


            }

        }
    }

    /**
     * combine graph, info and PR reuslt together
     *
     * @param repoUrl
     */
    private static void combineGraphWithInfo(String repoUrl) {
        IO_Process io = new IO_Process();
        List<List<String>> graph_approach_result, fork_info_result;

        StringBuilder sb = new StringBuilder();
        sb.append("repo,fork,upstream,only_F,only_U,F2U,U2F,"
//                +"pr_commit,merged_commit,reject_commit,"
                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age\n");

        int created_at_index = 2;
        int push_at_index = 3;

        graph_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/graph_result.csv");
        fork_info_result = io.readCSV(current_dir + "/result/" + repoUrl + "/forkInfo.csv");
        String graph_approach_commitList[] = {};


        for (int i = 1; i < graph_approach_result.size(); i++) {
            List<String> graph_result = graph_approach_result.get(i);
            System.out.println(i);
            if (!io.removeBrackets(graph_result.toString()).equals("") && fork_info_result.get(i).size() > 1) {
                String[] created = fork_info_result.get(i).get(created_at_index).split("T")[0].split("-");
                String[] push = fork_info_result.get(i).get(push_at_index).split("T")[0].split("-");
                LocalDate create_date = LocalDate.of(Integer.valueOf(created[0]), Integer.valueOf(created[1]), Integer.valueOf(created[2]));
                LocalDate push_date = LocalDate.of(Integer.valueOf(push[0]), Integer.valueOf(push[1]), Integer.valueOf(push[2]));
                LocalDate today = LocalDate.now();
                long fork_age = Duration.between(create_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option
                long lastCommit_age = Duration.between(push_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option

                String forkInfoStr = io.removeBrackets(fork_info_result.get(i).toString()).replace(graph_result.get(0) + ",", "") + "," + fork_age + "," + lastCommit_age;


                sb.append(repoUrl + "," + io.removeBrackets(graph_result.toString()) + "," + forkInfoStr + "\n"); //,U2F_list

            }
        }

        io.rewriteFile(sb.toString(), current_dir + "/result/" + repoUrl + "/graph_info.csv");


    }


    /**
     * combine graph, info and PR reuslt together
     *
     * @param repoUrl
     */
    private static void combineGraph_Info_PR(String repoUrl) {
        IO_Process io = new IO_Process();
        List<List<String>> graph_approach_result, fork_info_result;

        StringBuilder sb = new StringBuilder();
        sb.append("repo,fork,upstream,only_F,only_U,F2U,U2F,"
                + "attempt_pr_commit,mergedPR_commit,"
                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age\n");

        int created_at_index = 2;
        int push_at_index = 3;

        graph_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/graph_result.csv");
        fork_info_result = io.readCSV(current_dir + "/result/" + repoUrl + "/forkInfo.csv");
        String graph_approach_commitList[] = {}, PR_result[] = {};
        try {
            PR_result = io.readResult(current_dir + "/result/" + repoUrl + "/PR_result.csv").split("\n");


        } catch (IOException e) {
            e.printStackTrace();
        }


        for (int i = 1; i < graph_approach_result.size(); i++) {
            ArrayList<String> mergedCommit_pr = new ArrayList<>();
            mergedCommit_pr.addAll(Arrays.asList(io.removeBrackets(PR_result[i - 1].split(",")[1]).split("/ ")));
            mergedCommit_pr.remove("");
            ArrayList<String> rejectPR_Commit = new ArrayList<>();
            rejectPR_Commit.addAll(Arrays.asList(io.removeBrackets(PR_result[i - 1].split(",")[0]).split("/ ")));
            rejectPR_Commit.remove("");
            int attempt_commits = mergedCommit_pr.size() + rejectPR_Commit.size();


            List<String> graph_result = graph_approach_result.get(i);
            String[] created = fork_info_result.get(i).get(created_at_index).split("T")[0].split("-");
            String[] push = fork_info_result.get(i).get(push_at_index).split("T")[0].split("-");
            LocalDate create_date = LocalDate.of(Integer.valueOf(created[0]), Integer.valueOf(created[1]), Integer.valueOf(created[2]));
            LocalDate push_date = LocalDate.of(Integer.valueOf(push[0]), Integer.valueOf(push[1]), Integer.valueOf(push[2]));

            LocalDate today = LocalDate.now();
            long fork_age = Duration.between(create_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option
            long lastCommit_age = Duration.between(push_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option

            String forkInfoStr = io.removeBrackets(fork_info_result.get(i).toString()).replace(graph_result.get(0) + ",", "") + "," + fork_age + "," + lastCommit_age;


            sb.append(repoUrl + "," + io.removeBrackets(graph_result.toString()) + "," + attempt_commits + "," + mergedCommit_pr.size() + "," + forkInfoStr + "\n"); //,U2F_list

        }

        io.rewriteFile(sb.toString(), current_dir + "/result/" + repoUrl + "/final_result.csv");


    }


    private static void combineTwoApproaches(String repoUrl) {
        IO_Process io = new IO_Process();
        List<List<String>> author_approach_result, graph_approach_result, fork_info_result;

        StringBuilder sb = new StringBuilder();

//        sb.append("fork,upstream,only_F,only_U,only_U_with_PR,F2U,U2F,U2F_with_PR,sync_with_U,fail_to_merge_commits,only_F_list,only_U_list,F2U_list,U2F_list,"
//                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age\n");
//
        sb.append("fork,upstream,only_F,only_U,F2U,U2F,syncWithU,fail_to_merge_commits,only_F_list,only_U_list,F2U_list,U2F_list,"
                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age\n");


        int author_result_onlyF_index = 5;
        int author_result_F2U_index = 6;
        int author_pr_index = 8;

        int graph_result_onlyF_index = 6;

        int graph_result_F2U_index = 8;
        int graph_result_U2F_index = 9;
        int graph_result_onlyU_list_index = 7;

        int created_at_index = 2;
        int push_at_index = 3;

        author_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/authorName_result.csv");
        graph_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/graph_result.txt");
        fork_info_result = io.readCSV(current_dir + "/result/" + repoUrl + "/forkInfo.csv");


        for (int i = 1; i < author_approach_result.size(); i++) {

            System.out.println(i);
            List<String> author_result = author_approach_result.get(i);
            List<String> graph_result = graph_approach_result.get(i);

            Set<String> author_only_F_commit = getCommitResult(author_result_onlyF_index, author_result);
            Set<String> author_F2U_commit = getCommitResult(author_result_F2U_index, author_result);
            Set<String> author_PRcommit = getCommitResult(author_pr_index, author_result);


            Set<String> graph_only_F_commit = new HashSet<>();
            Set<String> graph_F2U_commit = new HashSet<>();
            Set<String> graph_U2F_commit = new HashSet<>();
            Set<String> graph_only_U_commit = new HashSet<>();
            if (graph_result.size() > 1) {
                graph_only_F_commit = getCommitResult(graph_result_onlyF_index, graph_result);
                graph_F2U_commit = getCommitResult(graph_result_F2U_index, graph_result);
                graph_U2F_commit = getCommitResult(graph_result_U2F_index, graph_result);
                graph_only_U_commit = getCommitResult(graph_result_onlyU_list_index, graph_result);

            }
            author_only_F_commit.addAll(graph_only_F_commit);
            author_F2U_commit.addAll(author_PRcommit);
            author_F2U_commit.addAll(graph_F2U_commit);
            author_F2U_commit.removeAll(graph_only_U_commit);

            author_only_F_commit.remove("");
            author_F2U_commit.remove("");
            graph_U2F_commit.removeAll(author_F2U_commit);

            String[] created = fork_info_result.get(i).get(created_at_index).split("T")[0].split("-");
            String[] push = fork_info_result.get(i).get(push_at_index).split("T")[0].split("-");
            LocalDate create_date = LocalDate.of(Integer.valueOf(created[0]), Integer.valueOf(created[1]), Integer.valueOf(created[2]));
            LocalDate push_date = LocalDate.of(Integer.valueOf(push[0]), Integer.valueOf(push[1]), Integer.valueOf(push[2]));

            LocalDate today = LocalDate.now();
            long fork_age = Duration.between(create_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option
            long lastCommit_age = Duration.between(push_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option

            String forkInfoStr = io.removeBrackets(fork_info_result.get(i).toString()).replace(author_result.get(0) + ",", "") + "," + fork_age + "," + lastCommit_age;


            if (graph_result.size() > 1) {

                // "fork,upstream,only_F,only_U, F2U,U2F ,fail_to_merge_commits,only_F_list,only_U_list,F2U_list,U2F_list\n");
                sb.append(author_result.get(0) + "," + author_result.get(1) + "," + author_only_F_commit.size() + ","  //fork,upstream,only_F,
                        + graph_only_U_commit.size() + "," + author_F2U_commit.size() + "," + graph_U2F_commit.size() + "," //only_U, F2U,U2F
                        + author_result.get(4) + "," + author_result.get(7) + "," + author_only_F_commit.toString().replace(",", "/") + ","   //sync_time, fail_to_merge_commits,only_F_list
                        + graph_result.get(graph_result_onlyU_list_index) + "," + author_F2U_commit.toString().replace(",", "/") + "," //only_U_list,F2U_list
                        + graph_U2F_commit.toString().replace(",", "/") + "," + forkInfoStr + "\n"); //,U2F_list

            } else {
                sb.append(author_result.get(0) + "," + author_result.get(1) + "," + author_only_F_commit.size() + ","
                        + "," + "," + author_F2U_commit.size() + ","
                        + "," + "," + ","
                        + author_result.get(7) + "," + author_only_F_commit.toString().replace(",", "/") + "," + ","
                        + author_F2U_commit.toString().replace(",", "/") + "," + ","
                        + forkInfoStr + "\n");
            }
        }

        io.rewriteFile(sb.toString(), current_dir + "/result/" + repoUrl + "/combine_result.csv");


    }

    private static Set<String> getCommitResult(int index, List<String> result) {
        IO_Process io = new IO_Process();
        Set<String> graph_only_U_commit;
        String only_u = io.removeBrackets(result.get(index));
        if (!only_u.equals("")) {
            graph_only_U_commit = new HashSet<String>(Arrays.asList(only_u.split("/ ")));
        } else {
            graph_only_U_commit = new HashSet<>();
        }
        return graph_only_U_commit;
    }


}
