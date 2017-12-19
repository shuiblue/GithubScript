import java.io.IOException;
import java.text.DateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by shuruiz on 12/8/17.
 */
public class main {
    static String current_dir;
    static int maxAnalyzedForkNum = 100;

    public static void main(String[] args) {
        ClassifyCommit cc = new ClassifyCommit();
        TrackCommitHistory trackCommitHistory = new TrackCommitHistory();

        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);

        String[] repoList = {};
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
//                System.out.println("get all active forks of repo: " + repoName);
//                String all_activeForkList = trackCommitHistory.getActiveForkList(repoUrl, activeForkNum);
////                io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl + "/ActiveForklist.txt");
//

                String all_activeForkList = trackCommitHistory.getActiveForkList(repoUrl);
                if (all_activeForkList.split("\n").length<= maxAnalyzedForkNum) {
                    io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl + "/ActiveForklist.txt");
                } else {
                    io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl +"/all_ActiveForklist.txt");
                    System.out.println("randomly pick " + maxAnalyzedForkNum + " active forks...");
                    trackCommitHistory.getRamdomForks(repoUrl, maxAnalyzedForkNum);
                }


                /** analyze commit history **/
                String[] activeForkList = {};
                try {
                    activeForkList = io.readResult(current_dir + "/result/" + repoUrl + "/ActiveForklist.txt").split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }


                /**   classify commits **/
                /**  by graph  **/
                System.out.println("graph-based...");
                StringBuilder sb_result = new StringBuilder();
                sb_result.append("fork,upstream,only_F,only_U,only_U_including_PRs,F->U,U->F,U->F_including_PRs,sync_with_U,only_F_commits,only_U_commits,F->U_commits,U->F_commits\n");

                io.rewriteFile(sb_result.toString(), current_dir + "/result/" + repoUrl + "/graph_result.csv");
                for (String forkInfo : activeForkList) {
                    System.out.println("FORK: " + forkInfo);
                    cc.analyzeCommitHistory(forkInfo, true, repoUrl);
                }
//
                /**  by author id  **/
                System.out.println("authorID-based...");
                System.out.println("repo: " + repoUrl);
                trackCommitHistory.classifyCommitsByAuthor(repoUrl);


                /** get fork info  **/
                GithubApiParser githubApiParser = new GithubApiParser();
                StringBuilder sb = new StringBuilder();
                sb.append("forkUrl,fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type\n");
                io.rewriteFile(sb.toString(), current_dir + "/result/" + repoUrl + "/forkInfo.csv");
                for (String forkInfo : activeForkList) {
                    String forkURL = forkInfo.split(",")[0];
                    System.out.println("get fork info: " + forkInfo);
                    sb.append(forkURL + "," + githubApiParser.getForkInfo(forkURL));
                   }
                io.rewriteFile(sb.toString(), current_dir + "/result/" + repoUrl + "/forkInfo.csv");


                /**   combines results together **/
                combineTwoApproaches(repoUrl);
            }

        }
    }


    private static void combineTwoApproaches(String repoUrl) {
        IO_Process io = new IO_Process();
        List<List<String>> author_approach_result = null;
        List<List<String>> graph_approach_result = null;
        List<List<String>> fork_info_result = null;

        StringBuilder sb = new StringBuilder();

        sb.append("fork,upstream,only_F,only_U,only_U_with_PR,F2U,U2F,U2F_with_PR,sync_with_U,fail_to_merge_commits,only_F_list,only_U_list,F2U_list,U2F_list,"
                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age\n");

        int author_result_onlyF_index = 5;
        int author_result_F2U_index = 6;
        int author_pr_index = 8;

        int graph_result_onlyF_index = 9;
        int graph_result_F2U_index = 11;

        int created_at_index = 2;
        int push_at_index = 3;

        author_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/authorName_result.csv");
        graph_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/graph_result.csv");
        fork_info_result = io.readCSV(current_dir + "/result/" + repoUrl + "/forkInfo.csv");


        for (int i = 1; i < author_approach_result.size(); i++) {

            System.out.println(i);
            List<String> author_result = author_approach_result.get(i);
            List<String> graph_result = graph_approach_result.get(i);

            Set<String> author_only_F_commit = new HashSet<String>(Arrays.asList(io.removeBrackets(author_result.get(author_result_onlyF_index)).split("/ ")));
            Set<String> author_F2U_commit = new HashSet<String>(Arrays.asList(io.removeBrackets(author_result.get(author_result_F2U_index)).split("/ ")));
            Set<String> author_PRcommit = new HashSet<String>(Arrays.asList(io.removeBrackets(author_result.get(author_pr_index)).split("/ ")));


            Set<String> graph_only_F_commit = new HashSet<>();
            Set<String> graph_F2U_commit = new HashSet<>();
            if (graph_result.size() > 1) {
                graph_only_F_commit = new HashSet<String>(Arrays.asList(io.removeBrackets(graph_result.get(graph_result_onlyF_index)).split("/ ")));
                graph_F2U_commit = new HashSet<String>(Arrays.asList(io.removeBrackets(graph_result.get(graph_result_F2U_index)).split("/ ")));
            }
            author_only_F_commit.addAll(graph_only_F_commit);
            author_F2U_commit.addAll(author_PRcommit);
            author_F2U_commit.addAll(graph_F2U_commit);
            author_only_F_commit.remove("");
            author_F2U_commit.remove("");


            String[] created = fork_info_result.get(i).get(created_at_index).split("T")[0].split("-");
            String[] push = fork_info_result.get(i).get(push_at_index).split("T")[0].split("-");
            LocalDate create_date = LocalDate.of(Integer.valueOf(created[0]), Integer.valueOf(created[1]), Integer.valueOf(created[2]));
            LocalDate push_date = LocalDate.of(Integer.valueOf(push[0]), Integer.valueOf(push[1]), Integer.valueOf(push[2]));

            LocalDate today = LocalDate.now();
            long fork_age = Duration.between(create_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option
            long lastCommit_age = Duration.between(push_date.atStartOfDay(), today.atStartOfDay()).toDays(); // another option

            String forkInfoStr = io.removeBrackets(fork_info_result.get(i).toString()).replace(author_result.get(0) + ",", "")+","+fork_age+","+lastCommit_age;


            if (graph_result.size() > 1) {


//     "fork,upstream,only_F,only_U,only_U_with_PR,F2U,U2F,U2F_with_PR,sync_with_U,fail_to_merge_commits,only_F_list,only_U_list,F2U_list,U2F_list\n");
                sb.append(author_result.get(0) + "," + author_result.get(1) + "," + author_only_F_commit.size() + ","
                        + graph_result.get(3) + "," + graph_result.get(4) + "," + author_F2U_commit.size() + ","
                        + graph_result.get(6) + "," + graph_result.get(7) + "," + graph_result.get(8) + ","
                        + author_result.get(7) + "," + author_only_F_commit.toString().replace(", ", "/") + "," + graph_result.get(10) + ","
                        + author_F2U_commit.toString().replace(", ", "/") + "," + graph_result.get(12) + ","
                        + forkInfoStr + "\n");
            } else {
                sb.append(author_result.get(0) + "," + author_result.get(1) + "," + author_only_F_commit.size() + ","
                        + "," + "," + author_F2U_commit.size() + ","
                        + "," + "," + ","
                        + author_result.get(7) + "," + author_only_F_commit.toString().replace(", ", "/") + "," + ","
                        + author_F2U_commit.toString().replace(", ", "/") + "," + ","
                        + forkInfoStr + "\n");
            }
        }

        io.rewriteFile(sb.toString(), current_dir + "/result/" + repoUrl + "/combine_result.csv");


    }


}
