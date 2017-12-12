import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by shuruiz on 12/10/17.
 */
public class GithubApiParser {
    static String token;
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String result_dir;


    GithubApiParser() {
        final String current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);
        result_dir = current_dir + "/result/";

        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StringBuilder getForkInfo(String forkURL) {
        StringBuilder sb = new StringBuilder();

        String forkUrl = github_api_repo + forkURL + "?access_token=" + token;
        JsonUtility jsonUtility = new JsonUtility();

        ArrayList<String> fork_info_json = null;
        try {
            fork_info_json = jsonUtility.readUrl(forkUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));
        sb.append(fork_jsonObj.get("forks_count") + ",");
        sb.append(fork_jsonObj.get("created_at") + ",");
        sb.append(fork_jsonObj.get("pushed_at") + ",");
        sb.append(fork_jsonObj.get("size") + ",");
        sb.append(fork_jsonObj.get("language") + ",");


        String owner_url = (String) ((JSONObject) fork_jsonObj.get("owner")).get("url");
        ArrayList<String> owner_info_json = null;
        try {
            owner_info_json = jsonUtility.readUrl(owner_url);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject owner_jsonObj = new JSONObject(owner_info_json.get(0));
        sb.append(owner_jsonObj.get("login") + ",");
        sb.append(owner_jsonObj.get("public_repos") + ",");
        sb.append(owner_jsonObj.get("public_gists") + ",");
        sb.append(owner_jsonObj.get("followers") + ",");
        sb.append(owner_jsonObj.get("following") + ",");
        sb.append(owner_jsonObj.get("created_at") + ",");
        sb.append(owner_jsonObj.get("type") + ",");

        sb.append("\n");

        return sb;
    }
}
