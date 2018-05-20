import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

public class GithubRepository {
    int num_forks;
    int public_repos;
    int public_gists;
    int followers;
    int following;
    int size = 0;

    public int getUpstreamID() {
        return upstreamID;
    }

    public void setUpstreamID(int upstreamID) {
        this.upstreamID = upstreamID;
    }

    int upstreamID = 0;


    int projectID;
    String created_at = "";
    String pushed_at = "";
    String sign_up_date;
    String language;
    String loginID;
    String type;
    String repoUrl;

    static String token;
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_api_user = "https://api.github.com/users/";
    static String github_api_search = "https://api.github.com/search/";
    static String result_dir;

    public GithubRepository getForkInfo(String repoURL, String upstreamURL) {
        GithubRepository repo = new GithubRepository();
        String current_dir = System.getProperty("user.dir");
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        repoURL = "thinkyhead/Marlin";
        repo.setRepoUrl(repoURL);
        String api_url = github_api_repo + repoURL + "?access_token=" + token;
        JsonUtility jsonUtility = new JsonUtility();
        IO_Process io_process = new IO_Process();
        ArrayList<String> fork_info_json = null;
        try {
            fork_info_json = jsonUtility.readUrl(api_url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (fork_info_json.size() > 0) {
            JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));
            repo.setNum_forks((int) fork_jsonObj.get("forks_count"));
            repo.setCreated_at(String.valueOf(fork_jsonObj.get("created_at")));
            repo.setPushed_at(String.valueOf(fork_jsonObj.get("pushed_at")));
            repo.setSize((int) fork_jsonObj.get("size"));
            repo.setLanguage(String.valueOf(fork_jsonObj.get("language")));

            if(fork_jsonObj.get("parent")!=null){
                String parentUrl = String.valueOf(new JSONObject( fork_jsonObj.get("parent")).get("full_name"));
                repo.setUpstreamID(io_process.getRepoId(parentUrl));
            }else{
                repo.setUpstreamID(0);
            }


            String owner_url = (String) ((JSONObject) fork_jsonObj.get("owner")).get("url");
            ArrayList<String> owner_info_json = null;
            try {
                owner_info_json = jsonUtility.readUrl(owner_url + "?access_token=" + token);
            } catch (Exception e) {
                e.printStackTrace();
            }

            JSONObject owner_jsonObj = new JSONObject(owner_info_json.get(0));
            repo.setLoginID(String.valueOf(owner_jsonObj.get("login")));
            repo.setPublic_repos((int) owner_jsonObj.get("public_repos"));
            repo.setPublic_gists((int) owner_jsonObj.get("public_gists"));
            repo.setFollowers((int) owner_jsonObj.get("followers"));
            repo.setFollowing((int) (owner_jsonObj.get("following")));
            repo.setSign_up_date(String.valueOf(owner_jsonObj.get("created_at")));
            repo.setType(String.valueOf(owner_jsonObj.get("type")));
            if (upstreamURL.equals("")) {
                repo.setProjectID(0);
            } else {
                repo.setProjectID(io_process.getRepoId(upstreamURL));
            }
        }


        return repo;
    }

    public int getNum_forks() {
        return num_forks;
    }

    public void setNum_forks(int num_forks) {
        this.num_forks = num_forks;
    }

    public int getPublic_repos() {
        return public_repos;
    }

    public void setPublic_repos(int public_repos) {
        this.public_repos = public_repos;
    }

    public int getPublic_gists() {
        return public_gists;
    }

    public void setPublic_gists(int public_gists) {
        this.public_gists = public_gists;
    }

    public int getFollowers() {
        return followers;
    }

    public void setFollowers(int followers) {
        this.followers = followers;
    }

    public int getFollowing() {
        return following;
    }

    public void setFollowing(int following) {
        this.following = following;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    public String getPushed_at() {
        return pushed_at;
    }

    public void setPushed_at(String pushed_at) {
        this.pushed_at = pushed_at;
    }

    public String getSign_up_date() {
        return sign_up_date;
    }

    public void setSign_up_date(String sign_up_date) {
        this.sign_up_date = sign_up_date;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getLoginID() {
        return loginID;
    }

    public void setLoginID(String loginID) {
        this.loginID = loginID;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static String getToken() {
        return token;
    }

    public static void setToken(String token) {
        GithubRepository.token = token;
    }

    public static String getGithub_api_repo() {
        return github_api_repo;
    }

    public static void setGithub_api_repo(String github_api_repo) {
        GithubRepository.github_api_repo = github_api_repo;
    }

    public static String getGithub_api_user() {
        return github_api_user;
    }

    public static void setGithub_api_user(String github_api_user) {
        GithubRepository.github_api_user = github_api_user;
    }

    public static String getGithub_api_search() {
        return github_api_search;
    }

    public static void setGithub_api_search(String github_api_search) {
        GithubRepository.github_api_search = github_api_search;
    }

    public static String getResult_dir() {
        return result_dir;
    }

    public static void setResult_dir(String result_dir) {
        GithubRepository.result_dir = result_dir;
    }

    public int getProjectID() {
        return projectID;
    }

    public void setProjectID(int projectID) {
        this.projectID = projectID;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
}
