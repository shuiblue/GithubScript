import java.io.IOException;
import java.util.List;

/**
 * Created by shuruiz on 12/8/17.
 */
public class main {
    static String current_dir;
    static int activeForkNum = 100;

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
//                String all_activeForkList = trackCommitHistory.getActiveForkList(repoUrl,activeForkNum);
//                io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl + "/ActiveForklist.txt");

                /** analyze commit history **/
                String[] activeForkList = {};
                try {
                    activeForkList = io.readResult(current_dir + "/result/" + repoUrl + "/ActiveForklist.txt").split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

//
//                /**   classify commits **/
//                        /**  by graph  **/
//                System.out.println("graph-based...");
//                StringBuilder sb_result = new StringBuilder();
//                sb_result.append("fork,upstream,only_F,only_U,only_U_including_PRs,F->U,U->F,U->F_including_PRs,sync_with_U,only_F_commits,only_U_commits,F->U_commits,U->F_commits\n");
//
//                io.rewriteFile(sb_result.toString(), current_dir + "/result/" + repoUrl + "/graph_result.csv");
//                for (String forkInfo : activeForkList) {
//                    System.out.println("FORK: "+forkInfo);
//                    cc.analyzeCommitHistory(forkInfo, true, repoUrl);
//                }

                         /**  by author id  **/
                System.out.println("authorID-based...");
                System.out.println("repo: "+repoUrl);
                trackCommitHistory.classifyCommitsByAuthor(repoUrl);


//                /** get fork info  **/
//                GithubApiParser githubApiParser = new GithubApiParser();
//                StringBuilder sb = new StringBuilder();
//                sb.append("forkUrl,fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type\n");
//                for (String forkInfo : activeForkList) {
//                    String forkURL = forkInfo.split(",")[0];
//                    System.out.println("get fork info: "+forkInfo);
//                   sb.append(forkURL+","+ githubApiParser. getForkInfo(forkURL));
//                }
//
//                io.rewriteFile(sb.toString(),current_dir+ "/result/" + repoUrl+"/forkInfo.csv" );

//                /**   combines results together **/
//                combineTwoApproaches(repoUrl);


            }

        }
    }



    private static void combineTwoApproaches(String repoUrl) {
        IO_Process io = new IO_Process();
        List<List<String>> author_approach_result = null;
        List<List<String>> graph_approach_result = null;

        author_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/authorName_result.csv");
        graph_approach_result = io.readCSV(current_dir + "/result/" + repoUrl + "/graph_result.csv");

        for(int i=1;i<author_approach_result.size();i++){

        }


        System.out.println();

    }


}
