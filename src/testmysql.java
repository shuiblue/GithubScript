

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * A Java MySQL PreparedStatement INSERT example.
 * Demonstrates the use of a SQL INSERT statement against a
 * MySQL database, called from a Java program, using a
 * Java PreparedStatement.
 * <p>
 * Created by Alvin Alexander, http://alvinalexander.com
 */
public class testmysql {

    public static void main(String[] args) {
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
         String current_OS = System.getProperty("os.name").toLowerCase();
         String myUrl,user;
        try {
            String myDriver = "com.mysql.jdbc.Driver";

            if (current_OS.indexOf("mac") >= 0) {
                myUrl = "jdbc:mysql://localhost:3307/fork";
                user = "shuruiz";
            } else {
//            output_dir = "/home/feature/shuruiz/ForkData";
                myUrl = "jdbc:mysql://localhost:3306/fork";
                user = "shuruiz";
            }


            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            PreparedStatement preparedStmt = null;

            // create a mysql database connection
            Class.forName(myDriver);
            IO_Process io = new IO_Process();


            /**  insert code change loc  **/
//            String[] ownCode_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/noSync.csv").split("\n");
//            for (int i =1;i< ownCode_array.length;i++) {
//
//                String repo = ownCode_array[i];
//                if(repo.contains("TRUE")) {
//                    String[] repoInfo = repo.split(",");
//                    String insertQuery = "UPDATE fork.Final SET fork.Final.activeFork_no_sync = ? WHERE repoURL = ?";
//
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setString(1, repoInfo[4]);
//                    preparedStmt.setString(2, repoInfo[1].replace("\"", ""));
//                    System.out.println(preparedStmt.toString());
//                    preparedStmt.execute();
//                }
//            }
            /**  insert code change loc  **/
//            String[] loc_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/file_codechange_median.csv").split("\n");
//            for (int i =1;i< loc_array.length;i++) {
//                String repo = loc_array[i];
//                String[] repoInfo = repo.split(",");
//                    String insertQuery = "UPDATE fork.Final SET fork.Final.code_changes_size_median_file = ? WHERE repoURL = ?";
//
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setString(1, repoInfo[2]);
//                    preparedStmt.setString(2, repoInfo[1].replace("\"",""));
//                    System.out.println(preparedStmt.toString());
//                    preparedStmt.execute();
//
//            }
            /**  easiness **/
            String[] easiness_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/easiness.csv").split("\n");
            for (int i =1;i< easiness_array.length;i++) {
                String repo = easiness_array[i];
                String[] repoInfo = repo.split(",");
                    String insertQuery = "UPDATE fork.Final SET Easiness_mergePR_mean = ? WHERE repoURL = ?";

                    preparedStmt = conn.prepareStatement(insertQuery);
                    preparedStmt.setString(1, repoInfo[2]);
                    preparedStmt.setString(2, repoInfo[1].replace("\"",""));
                    System.out.println(preparedStmt.toString());
                    preparedStmt.execute();

            }
            /**  insert ratio of merged pr  **/
//            String[] modularity_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/PR_Status.csv").split("\n");
//            for (String repo : modularity_array) {
//                if (repo.contains("Merged")) {
//                    String[] repoInfo = repo.split(",");
//                    String insertQuery = "UPDATE fork.Final SET ratio_mergedPR = ? WHERE repoURL = ?";
//
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setString(1, repoInfo[4]);
//                    preparedStmt.setString(2, repoInfo[1].replace("\"",""));
//                    System.out.println(preparedStmt.toString());
//                    System.out.println(preparedStmt.executeUpdate());
//
//                }
//            }
            /**  insert modularity of project  **/
//            String[] modularity_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/commitHistory/10_repo_ECI.csv").split("\n");
//            for (String repo : modularity_array) {
//                String[] repoInfo = repo.split(",");
//                String insertQuery = "UPDATE Fork.Final SET  repoURL = ?,modularity_threshold_10_files =? WHERE repoURL = ?";
//
//                preparedStmt = conn.prepareStatement(insertQuery);
//                preparedStmt.setString(1, repoInfo[0]);
//                preparedStmt.setDouble(2, Double.parseDouble(repoInfo[1]));
//                preparedStmt.setString(3, repoInfo[0]);
//                preparedStmt.execute();
//                System.out.println(preparedStmt.toString());
//            }

            /**  update percentage of issue_first result  **/
//            String[] issueFirst_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/pr_issue.csv").split("\n");
//            for (int i =1;i< issueFirst_array.length;i++) {
//
//                String repo = issueFirst_array[i];
//                if(repo.contains(",TRUE,")) {
//                    repo = repo.replace("\"", "");
//
//                    String[] repoInfo = repo.split(",");
//                    String insertQuery = "UPDATE fork.Final SET  central_management_index = ? WHERE repoID = ?";
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setDouble(1, Double.parseDouble(repoInfo[4]));
//                    preparedStmt.setInt(2, Integer.parseInt(repoInfo[1]));
//                    preparedStmt.execute();
//                }
//            }


            /*** insert repoList to repository table ***/
//            String[] repos = io.readResult("/Users/shuruiz/Box Sync/SPL_Research/ForkBasedDev/statistics/try/repoList.txt").split("\n");
//
//            for (int i = 1; i <= repos.length; i++) {
//
//                String repourl = repos[i - 1];
//                System.out.println(repourl);
//                String[] upstream_INFO = io.readResult("/Users/shuruiz/Box Sync/ForkData/result/" + repourl + "/upstreamInfo.csv").split("\n")[1].split(",");
//
//
//                String query = " update Fork.repository " +
//                        " set   num_of_forks = ?,created_at = ?, pushed_at = ? , size = ?, language = ?, ownerID = ? ,public_repos = ?" +
//                        ",public_gists = ?, followers = ?, following = ?, sign_up_time = ?,user_type = ?, belongToRepo = ?, isFork=? " +
//                        " where repoURL = ?";
//
//                preparedStmt = conn.prepareStatement(query);
//                preparedStmt.setInt(1, Integer.parseInt(upstream_INFO[1]));
//                preparedStmt.setString(2, upstream_INFO[2]);
//                preparedStmt.setString(3, upstream_INFO[3]);
//                preparedStmt.setString(4, upstream_INFO[4]);
//                preparedStmt.setString(5, upstream_INFO[5]);
//                preparedStmt.setString(6, upstream_INFO[6]);
//                preparedStmt.setString(7, upstream_INFO[7]);
//                preparedStmt.setString(8, upstream_INFO[8]);
//                preparedStmt.setString(9, upstream_INFO[9]);
//                preparedStmt.setString(10, upstream_INFO[10]);
//                preparedStmt.setString(11, upstream_INFO[11]);
//                preparedStmt.setString(12, upstream_INFO[12]);
//                preparedStmt.setString(13, repourl);
//                preparedStmt.setBoolean(14, false);
//                preparedStmt.setString(15, upstream_INFO[0]);
//                preparedStmt.execute();
//            }


            /*** insert pr info to  repo_PR table***/
//            String selectSQL = " SELECT repoURL FROM Fork.repository";
//            preparedStmt = conn.prepareStatement(selectSQL);
//
//            //Execute select SQL stetement
//            for (String repoURL : repos) {
//                String[] forkListInfo = {};
//                try {
//                    forkListInfo = io.readResult("/Users/shuruiz/Box Sync/ForkData/result/" + repoURL + "/graph_result.txt").split("\n");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                for (int i = 1; i < forkListInfo.length; i++) {
//                    String[] columns = forkListInfo[i].split(",");
//                    HashSet<String> codeChangeCommits = new HashSet<>();
//                    String forkurl = columns[0];
//                    System.out.println(forkurl);
//
//                    int onlyF = Integer.parseInt(columns[2]);
//                    int onlyU = Integer.parseInt(columns[3]);
//                    int F2U = Integer.parseInt(columns[4]);
//                    int U2F = Integer.parseInt(columns[5]);
//
//                    List<String> commits_onlyF = Arrays.asList(io.removeBrackets(columns[6]).split("/ "));
//                    List<String> commits_onlyU = Arrays.asList(io.removeBrackets(columns[7]).split("/ "));
//                    List<String> commits_F2U = Arrays.asList(io.removeBrackets(columns[8]).split("/ "));
//                    List<String> commits_U2F = Arrays.asList(io.removeBrackets(columns[9]).split("/ "));
//
//                    String selectForkID = "SELECT id from Fork.repository where repoURL = ?";
//                    preparedStmt = conn.prepareStatement(selectForkID);
//                    preparedStmt.setString(1, forkurl);
//                    ResultSet rs = preparedStmt.executeQuery();
//                    int forkID=-1;
//                    if(rs.next()) {
//                         forkID = rs.getInt("id");
//                    }
//
//                    if(forkID != -1) {
//                        String query = "  INSERT into Fork.repo_fork_commit_graph ( forkID, only_F, only_U, F2U, U2F, only_F_commit_list, F2U_commit_list, data_update_at) " +
//                                "VALUES (?,?,?,?,?,?,?,?)";
//                        preparedStmt = conn.prepareStatement(query);
//
//
//                        preparedStmt.setInt(1, forkID);
//                        preparedStmt.setInt(2, onlyF);
//                        preparedStmt.setInt(3, onlyU);
//                        preparedStmt.setInt(4, F2U);
//                        preparedStmt.setInt(5, U2F);
//                        preparedStmt.setString(6, commits_onlyF.toString());
//                        preparedStmt.setString(7, commits_F2U.toString());
//                        preparedStmt.setString(8, String.valueOf(now));
//
//                        preparedStmt.execute();
//                    }
//
//                }
//
//            }


            preparedStmt.close();
            conn.close();


            conn.close();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
