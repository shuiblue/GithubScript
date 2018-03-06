import java.io.IOException;

/**
 * Created by shuruiz on 3/5/18.
 */
public class GetActiveForksStat {
    static String current_dir;

    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);
        io.rewriteFile("", current_dir + "/result/activeForkRatio.csv");


        String[] repoList = {};
        /** get repo list **/
        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        StringBuilder sb = new StringBuilder();
        for (String repoUrl : repoList) {
            /**   get all forks ***/
            GithubApiParser githubApiParser = new GithubApiParser();
            String forkINFO = githubApiParser.getForkInfo(repoUrl).toString();
            int all_fork_count = Integer.parseInt(forkINFO.split(",")[0]);

            /**  get active fork num**/
            int activeForkCount = 0;
            try {
                String[] activeForks = io.readResult(current_dir + "/result-MSR/" + repoUrl + "/all_ActiveForklist.txt").split("\n");
                activeForkCount = activeForks.length;
            } catch (IOException e) {
                e.printStackTrace();
            }


            io.writeTofile(repoUrl + "," + activeForkCount + "," + all_fork_count + "," + (float) activeForkCount / all_fork_count+"\n", current_dir + "/result/activeForkRatio.csv");
        }

    }
}
