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

    public void getForkInfo(String forkURL) {

        String forkUrl = github_api_repo + forkURL + "?access_token=" + token;
        JsonUtility jsonUtility = new JsonUtility();

        StringBuilder sb = new StringBuilder();
        ArrayList<String> forks_has_forks = new ArrayList<>();
        ArrayList<String> fork_info_json = null;
        try {
            fork_info_json = jsonUtility.readUrl(forkUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (fork_info_json.size() > 0) {
            JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));
            int fork_num = (int) fork_jsonObj.get("forks");
            String created_at = (String) fork_jsonObj.get("created_at");
            String pushed_at = (String) fork_jsonObj.get("pushed_at");

        }

    }
}
