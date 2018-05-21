import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class AnalyzeRepository {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd, token;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;


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
        String[] projectList = null;

        try {
            projectList = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        ArrayList<GithubRepository> repoObj_List = new ArrayList<>();

        int count = 0;
        while (count <= 100)
            for (String projectUrl : projectList) {
                System.out.println(projectUrl);
                /**     get repo info from api*/
                GithubRepository repo = new GithubRepository().getRepoInfo(projectUrl, "");
                analyzeRepository.updateRepoInfo(repo);
            }


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
