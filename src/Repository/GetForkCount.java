package Repository;

import Util.IO_Process;
import Util.JsonUtility;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by shuruiz on 10/31/17.
 */
public class GetForkCount {
    static String github_api_repo = "https://api.github.com/repos/";
    static String token;

    static String mostForkFile = "mostForkedRepo.csv";
    static String dir = "/Users/shuruiz/Box Sync/GithubScript-New/result/GithubData/";

    /**
     * This function gets a list of forks of a repository
     * input: repo_url
     * output: fork list
     *
     */
    public void getActiveForkList() {
        StringBuilder sb = new StringBuilder();
//        sb.append("repo_url, isfork ,1st level fork, all fork counts, %\n");
        IO_Process io = new IO_Process();
//        io.rewriteFile(sb.toString(), dir + "forkResult.csv");


        try {
            token = io.readResult("/Users/shuruiz/Box Sync/GithubScript-New/result/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] repoArray = {};
        try {
            repoArray = io.readResult(dir + mostForkFile).split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (String repoInfo : repoArray) {
            sb = new StringBuilder();
            String[] tmp = repoInfo.split(",");
            String repo_url = tmp[1] + "/" + tmp[2];
            int first_level_fork = Integer.parseInt(tmp[0]);

            String url = github_api_repo + repo_url + "?access_token=" + token;
            JsonUtility jsonUtility = new JsonUtility();

            ArrayList<String> repo_json = null;
            try {
                repo_json = jsonUtility.readUrl(url);
                if(repo_json==null){
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            JSONObject fork_list_jsonObj = new JSONObject(repo_json.get(0));
            int all_forks_count = (int) fork_list_jsonObj.get("forks_count");

            boolean isfork = (boolean) fork_list_jsonObj.get("fork");

            sb.append(repo_url + ","+ isfork+","+ first_level_fork + ","+all_forks_count + "," + (float)first_level_fork/all_forks_count +"\n");
            io.writeTofile(sb.toString(), dir + "forkResult.csv");
        }

    }


    public static void main(String[] args) {
        GetForkCount getForkCount = new GetForkCount();

        getForkCount.getActiveForkList();

    }
}
