import java.io.IOException;

/**
 * Created by shuruiz on 12/8/17.
 */
public class main {

    public static void main(String[] args) {
        ClassifyCommit cc = new ClassifyCommit();
        TrackCommitHistory trackCommitHistory = new TrackCommitHistory();

        IO_Process io = new IO_Process();
        final String current_dir = System.getProperty("user.dir");
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
                System.out.println("get all active forks of repo: "+ repoName);
//                String all_activeForkList = trackCommitHistory.getActiveForkList(repoUrl);
//                io.rewriteFile(all_activeForkList, current_dir + "/result/" + repoUrl + "/ActiveForklist.txt");

                /** analyze commit history **/
                String[] activeForkList = {};
                try {
                    activeForkList = io.readResult(current_dir + "/result/" + repoUrl + "/ActiveForklist.txt").split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }


                /**   classify commits **/
                /**  by graph  **/
                StringBuilder sb_result = new StringBuilder();
                sb_result.append("fork,upstream,only F,only U,only U (including PRs),F -> U,U -> F,U -> F (including PRs), #Times sync with U\n");

                io.rewriteFile(sb_result.toString(), current_dir + "/result/" + repoUrl + "/graph_result.csv");
                for (String forkInfo : activeForkList) {
                    cc.analyzeCommitHistory(forkInfo, true, repoUrl);

                }

                /**  by author id  **/
//                trackCommitHistory.classifyCommitsByAuthor(repoUrl);


            }

        }
    }


}
