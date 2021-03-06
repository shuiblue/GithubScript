package Repository;

import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AnalyzeRepository {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd, token;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;
    static boolean isAnalyzingHardfork = true;

    AnalyzeRepository() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        AnalyzeRepository analyzeRepository = new AnalyzeRepository();
        /**   insert project_url to database ***/
//        analyzeRepository.insertProject();
        IO_Process io = new IO_Process();

        Set<String> repoSet = new HashSet<>();
        if (isAnalyzingHardfork) {
            repoSet = getHardforkRelatedRepos();
        } else {
            String[] projectList = null;

            try {
                projectList = io.readResult(current_dir + "/input/repoList.txt").split("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            repoSet = new HashSet(Arrays.asList(projectList));

        }


        for (String projectUrl : repoSet) {
            if (!isChecked(projectUrl)) {

                System.out.println(projectUrl);
                /**     get repo info from api*/
                GithubRepository repo = new GithubRepository().getRepoInfo(projectUrl, "");
                analyzeRepository.updateRepoInfo(repo);
            }
        }


    }

    public static Set<String> getHardforkRelatedRepos() {
        //list all the hard fork url
        String query = "SELECT hardfork_url,upstream_url FROM fork.HardFork";
        Set<String> urlSet = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
            System.out.println(query);
            while (rs.next()) {
                urlSet.add(rs.getString("hardfork_url"));
                urlSet.add(rs.getString("upstream_url"));

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return urlSet;
    }

    private static boolean isChecked(String projectUrl) {
//        String query = "\n" +
//                "select TIMESTAMPDIFF(DAY, STR_TO_DATE(data_update_at, '%Y-%m-%dT%H:%i:%s'), CURDATE()) as diff\n" +
//                "from repository\n" +
//                "where repoURL = '" + projectUrl + "'";

        String query = "\n" +
                "select pushed_at\n" +
                "from repository\n" +
                "where repoURL = '" + projectUrl + "'";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
            System.out.println(query);
            if (rs.next()) {
                String result = (String) rs.getObject("pushed_at");
                if (result == null ||result.equals("") ) {
                    return false;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }


    public void updateRepoInfo(GithubRepository repoObj) {
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();

        String updateRepoInfo_query = "UPDATE fork.repository " +
                "SET isFork = ?, upstreamID = ?,projectID = ?, num_of_forks = ?, created_at = ?, pushed_at = ?, " +
                "size = ?, language = ?, loginID = ?, public_gists=?, public_repos = ?, followers = ?, following = ?," +
                "sign_up_time = ?, user_type = ? ,data_update_at = ? " +
                "WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(updateRepoInfo_query)) {

            // isFork = ?,
            preparedStmt.setBoolean(1, repoObj.isFork);
            // upstreamID = ?,
            preparedStmt.setInt(2, repoObj.getUpstreamID());
            // projectID = ?,
            preparedStmt.setInt(3, repoObj.getProjectID());
            // num_of_forks = ?,
            preparedStmt.setInt(4, repoObj.getNum_forks());
            // created_at = ?
            preparedStmt.setString(5, repoObj.getCreated_at());
            //pushed_at = ?, " +
            preparedStmt.setString(6, repoObj.getPushed_at());
            //"size = ?,
            preparedStmt.setInt(7, repoObj.getSize());
            // language = ?,
            preparedStmt.setString(8, repoObj.getLanguage());
            // loginID = ?,
            preparedStmt.setString(9, repoObj.getLoginID());
            // public_gists=?,
            preparedStmt.setInt(10, repoObj.getPublic_gists());
            // public_repos = ?,
            preparedStmt.setInt(11, repoObj.getPublic_repos());
            //followers = ?,
            preparedStmt.setInt(12, repoObj.getFollowers());
            // following = ?," +
            preparedStmt.setInt(13, repoObj.getFollowing());
            //"sign_up_time = ?,
            preparedStmt.setString(14, repoObj.getSign_up_date());
            // user_type = ? ,

            preparedStmt.setString(15, repoObj.getType());
            // data_update_at = ?
            preparedStmt.setString(16, String.valueOf(now));

            // repoID = ?
            String repoURL = repoObj.getRepoUrl();
            int repoID = io.getRepoId(repoURL);
            preparedStmt.setInt(17, repoID);
            System.out.println("added repo:" + repoURL + ", id :" + repoID + " to batch. affect: " + preparedStmt.executeUpdate() + " row");
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }


}
