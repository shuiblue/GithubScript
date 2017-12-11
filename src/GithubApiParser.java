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
    static String result_dir ;


    GithubApiParser(){
        final String current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);
        result_dir = current_dir+"/result/";

        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void getForkInfo(String forkInfo) {

        String forkUrl = github_api_repo + forkInfo + "?access_token=" + token ;
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
                JSONObject fork_list_jsonObj = new JSONObject(fork_info_json);
                String name = (String) fork_list_jsonObj.get("full_name");

                if ((int) fork_list_jsonObj.get("forks") > 0) {
                    forks_has_forks.add(name);
                }


                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                String created_at = (String) fork_list_jsonObj.get("created_at");
                String pushed_at = (String) fork_list_jsonObj.get("pushed_at");

                try {
                    Date created_time = formatter.parse(created_at.replaceAll("Z$", "+0000"));
                    Date pushed_time = formatter.parse(pushed_at.replaceAll("Z$", "+0000"));
                    if (created_time.before(pushed_time)) {
                        if (name.length() > 0) {
                            sb.append(name + "," + forkUrl + "," + created_at + "\n");
                        }
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }

        }

    }
}
