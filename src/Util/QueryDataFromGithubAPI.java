package Util;

import Util.IO_Process;
import Util.JsonUtility;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class QueryDataFromGithubAPI {
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, token, result_dir;
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

    /**
     * This function gets a list of forks of a repository
     * input: repo
     * output: fork list
     *
     * @param repo e.g. 'shuiblue/INFOX'
     */
    public void getActiveForkList(String repo, boolean hasTimeConstraint, String projectURL) {

        String forkUrl = github_api_repo + repo + "/forks?access_token=" + token + "&page=";
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
                                    sb.append(name + "," + repo + "," + created_at + "\n");
                                }
                            }

                        } else {
                            sb.append(name + "," + repo + "," + created_at + "\n");
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                break;
            }
            new IO_Process().writeTofile(sb.toString(), result_dir + projectURL + "/all_ActiveForklist.txt");

        }

        for (String fork : forks_has_forks) {
            getActiveForkList(fork, hasTimeConstraint, projectURL);
        }


    }


    public void getRamdomForks(String repo_url, int randomNum) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String[] forkArray = {};
        try {
            forkArray = io.readResult(result_dir + repo_url + "/all_ActiveForklist.txt").split("\n");
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

        io.rewriteFile(sb.toString(), result_dir + repo_url + "/ActiveForklist.txt");
    }


    public String getGithubLoginID(String sha, String projectURL) {
        JsonUtility jsonReader = new JsonUtility();
        String commitURL = github_api_repo + projectURL + "/commits/" + sha + "?access_token=" + token;
        ArrayList<String> commitJson = jsonReader.readUrl(commitURL);


        JSONObject fork_jsonObj = new JSONObject(commitJson.get(0));
        if (!fork_jsonObj.get("author").toString().equals("null")) {
            return (String) ((JSONObject) fork_jsonObj.get("author")).get("login");
        } else {
            return "authorIsNull";
        }


    }


    public String getLanguage(String projectURL) {
        JsonUtility jsonReader = new JsonUtility();
        String commitURL = github_api_repo + projectURL + "?access_token=" + token;
        ArrayList<String> commitJson = jsonReader.readUrl(commitURL);
        if(commitJson.size()==0) return "deleted";
        JSONObject fork_jsonObj = new JSONObject(commitJson.get(0));
        if (!fork_jsonObj.get("language").toString().equals("null")) {
            return (String) fork_jsonObj.get("language");
        } else {
            return "null";
        }


    }

    public String getUpdatedAt(String projectURL) {
        JsonUtility jsonReader = new JsonUtility();
        String commitURL = github_api_repo + projectURL + "?access_token=" + token;
        ArrayList<String> commitJson = jsonReader.readUrl(commitURL);
        if(commitJson.size()==0) return "deleted";
        JSONObject fork_jsonObj = new JSONObject(commitJson.get(0));
        if (!fork_jsonObj.get("updated_at").toString().equals("null")) {
            return (String) fork_jsonObj.get("updated_at");
        } else {
            return "null";
        }


    }

    public static void main(String[] args) {
        QueryDataFromGithubAPI queryDataFromGithubAPI = new QueryDataFromGithubAPI();
        IO_Process io = new IO_Process();
        LineIterator it = null;
        String hardfork_dir = io.hardfork_dir;
        int count = 0;
        try {
//            System.out.println(hardfork_dir + "hardfork_language_left.txt");
            System.out.println(hardfork_dir + "updateAt_fork_upstream.csv");
//            it = FileUtils.lineIterator(new File(hardfork_dir + "hardfork_language_left.txt"), "UTF-8");
            it = FileUtils.lineIterator(new File(hardfork_dir + "updateAt_fork_upstream.csv"), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (it.hasNext()) {
                System.out.println("count: " + count++);
                String line = it.nextLine();
                if (!line.contains("hardfork_url")) {
                    String fork = line.split(",")[0];
                    String upstream = line.split(",")[1];
                    String fork_updatedAt = queryDataFromGithubAPI.getUpdatedAt(fork);
                    String upstream_updatedAt = queryDataFromGithubAPI.getUpdatedAt(upstream);
                    System.out.println(line+"，"+fork_updatedAt+","+upstream_updatedAt);
                    io.writeTofile(fork+","+upstream + ","  +fork_updatedAt+","+upstream_updatedAt + "\n", hardfork_dir + "updateAt_ghapi.txt");
                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());

        } finally {
            LineIterator.closeQuietly(it);
        }


    }
}
