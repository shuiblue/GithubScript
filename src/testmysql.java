

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

        try {
            String myDriver = "com.mysql.jdbc.Driver";
            String myUrl = "jdbc:mysql://localhost:3306/Fork";

            Connection conn = DriverManager.getConnection(myUrl, "root", "shuruiz");
            PreparedStatement preparedStmt = null;

            // create a mysql database connection
            Class.forName(myDriver);
            IO_Process io = new IO_Process();


            /**  insert modularity of project  **/
            String[] modularity_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/commitHistory/10_repo_ECI.csv").split("\n");
            for (String repo : modularity_array) {
                String[] repoInfo = repo.split(",");
                String insertQuery = "UPDATE Fork.Final SET  repoURL = ?,modularity_threshold_10_files =? WHERE repoURL = ?";
                preparedStmt = conn.prepareStatement(insertQuery);
                preparedStmt.setString(1, repoInfo[0]);
                preparedStmt.setDouble(2, Double.parseDouble(repoInfo[1]));
                preparedStmt.setString(3, repoInfo[0]);
                preparedStmt.execute();
            }


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
