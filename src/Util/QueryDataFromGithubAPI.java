package Util;

import Util.IO_Process;
import Util.JsonUtility;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;

public class QueryDataFromGithubAPI {
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, token;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;
    static int maxAnalyzedForkNum = 100;

    public QueryDataFromGithubAPI() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
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

    /**
     * This function gets a list of forks of a repository
     * input: repo_url
     * output: fork list
     *
     * @param repo_url e.g. 'shuiblue/INFOX'
     */
    public String getActiveForkList(String repo_url, boolean hasTimeConstraint) {

        String forkUrl = github_api_repo + repo_url + "/forks?access_token=" + token + "&page=";
        JsonUtility jsonUtility = new JsonUtility();
        StringBuilder sb = new StringBuilder();
        ArrayList<String> forks_has_forks = new ArrayList<>();
        int totalForks = 0;
        for (int page = 1; page <= 600; page++) {
            ArrayList<String> fork_array_json = null;
            try {
                fork_array_json = jsonUtility.readUrl(forkUrl + page);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (fork_array_json.size() > 0) {
                for (String fork : fork_array_json) {
                    totalForks += 1;
                    JSONObject fork_list_jsonObj = new JSONObject(fork);
                    String name = (String) fork_list_jsonObj.get("full_name");

                    if ((int) fork_list_jsonObj.get("forks") > 0) {
                        forks_has_forks.add(name);
                    }

                    try {
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                        String created_at = (String) fork_list_jsonObj.get("created_at");
                        Date created_time = formatter.parse(created_at.replaceAll("Z$", "+0000"));

                        if (hasTimeConstraint) {
                            String pushed_at = (String) fork_list_jsonObj.get("pushed_at");
                            Date pushed_time = formatter.parse(pushed_at.replaceAll("Z$", "+0000"));
                            if (created_time.before(pushed_time)) {
                                if (name.length() > 0) {
                                    sb.append(name + "," + repo_url + "," + created_at + "\n");
                                }
                            }

                        } else {
                            sb.append(name + "," + repo_url + "," + created_at + "\n");
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                break;
            }
        }

        for (String fork : forks_has_forks) {
            sb.append(getActiveForkList(fork, hasTimeConstraint));
        }


        return sb.toString();
    }


    public void getRamdomForks(String repo_url, int randomNum) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String[] forkArray = {};
        try {
            forkArray = io.readResult(output_dir+"result/" + repo_url + "/all_ActiveForklist.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        HashSet<Integer> random_index = new HashSet<>();
        while (random_index.size() < randomNum) {
            int rnd = new Random().nextInt(forkArray.length);
            if (!random_index.contains(rnd)) {
                random_index.add(rnd);
                sb.append(forkArray[rnd] + "\n");
            }
        }

        io.rewriteFile(sb.toString(), output_dir+"result/"  + repo_url + "/ActiveForklist.txt");
    }


}
