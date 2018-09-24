package Repository;

import Util.GithubApiParser;
import Util.IO_Process;

import java.io.File;
import java.io.IOException;

/**
 * Created by shuruiz on 3/5/18.
 */
public class GetActiveForksStat {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, token, result_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;
    static int maxAnalyzedForkNum = 100;

    public GetActiveForksStat() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            result_dir = output_dir + "result0821/";
            clone_dir = output_dir + "clones/";
            graph_dir = output_dir + "Commit.Commit.ClassifyCommit/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        new GetActiveForksStat();

        String[] repoList = {};
        /** get repo list **/
        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String repoUrl : repoList) {
            System.out.println(repoUrl);
            /**  get active fork num**/
            int activeForkCount = 0;
            String filePath = output_dir +"result/"+ repoUrl + "/all_ActiveForklist.txt";
            try {
                if (new File(filePath).exists()) {
                    String[] activeForks = io.readResult(filePath).split("\n");
                    activeForkCount = activeForks.length;
                    System.out.println(repoUrl + "," + activeForkCount  );
                    io.writeTofile(repoUrl + "," + activeForkCount + "\n", output_dir + "activeForkCount.csv");
                } else {
                    io.writeTofile(repoUrl + "\n", output_dir + "activeListMiss.txt");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }



        }

    }
}
