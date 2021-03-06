

import Pull_Request.AnalyzingPRs;
import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
    static String user, pwd, myUrl, token;
    static String current_dir = System.getProperty("user.dir");

    public static void main(String[] args) {
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String current_OS = System.getProperty("os.name").toLowerCase();
        String myDriver = "com.mysql.jdbc.Driver";


        IO_Process io = new IO_Process();
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            token = io.readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        HashMap<String, Integer> url_id = new HashMap<>();

        /**  insert duplicate pr info  **/
////            String[] dupPR = io.readResult("/Users/shuruiz/Box Sync/ForkData/dupPR/only_in_label.txt").split("\n");
//            String[] dupPR = io.readResult("/Users/shuruiz/Box Sync/ForkData/dupPR/in_label_title_description.txt").split("\n");
//            for (int i = 1; i < dupPR.length; i++) {
//                String[] fork = dupPR[i].split(" ");
//                String url = fork[0].trim();
//                String pr = fork[1].trim();
//                String updateQuery = "UPDATE repo_PR as pr, repository as repo " +
//                        "SET pr.dupPR_comment_and_label = TRUE " +
//                        "WHERE pr.projectID = repo.id and repo.repoURL=?  AND pull_request_ID = ?";
//X
//                preparedStmt = conn.prepareStatement(updateQuery);
//                preparedStmt.setString(1, url);
//                preparedStmt.setInt(2, Integer.parseInt(pr));
//                System.out.println(preparedStmt.toString());
//                System.out.println("affect " + preparedStmt.executeUpdate());
//            }


        /**  insert code change loc  **/
//            String[] ownCode_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/ownCode.csv").split("\n");
//            for (int i = 1; i < ownCode_array.length; i++) {
//
//                String repo = ownCode_array[i];
//                if (repo.contains("FALSE")) {
//                    String[] repoInfo = repo.split(",");
//                    String insertQuery = "UPDATE fork.Final SET fork.Final.activeFork_no_ownCode = ? WHERE repoURL = ?";
//
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setString(1, repoInfo[4]);
//                    preparedStmt.setString(2, repoInfo[1].replace("\"", ""));
//                    System.out.println(preparedStmt.toString());
//                    preparedStmt.execute();
//                }
//            }
        /**  insert code change loc  **/
////            // codechange_mean
////            String[] loc_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/codechange_mean.csv").split("\n");
////            String[] loc_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/codechange_median.csv").split("\n");
////            String[] loc_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/file_codechange_mean.csv").split("\n");
//            String[] loc_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/file_codechange_median.csv").split("\n");
//            for (int i =2;i< loc_array.length;i++) {
//                String repo = loc_array[i];
//                String[] repoInfo = repo.split(",");
//                if(!repoInfo[2].equals("NA")) {
////                    String insertQuery = "UPDATE fork.Final SET fork.Final.code_changes_size_mean_loc = ? WHERE repoID = ?";
////                    String insertQuery = "UPDATE fork.Final SET fork.Final.code_changes_size_median_loc = ? WHERE repoID = ?";
////                    String insertQuery = "UPDATE fork.Final SET fork.Final.code_changes_size_mean_file = ? WHERE repoID = ?";
//                    String insertQuery = "UPDATE fork.Final SET fork.Final.code_changes_size_median_file = ? WHERE repoID = ?";
//
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setString(1, repoInfo[2]);
//                    preparedStmt.setInt(2, Integer.parseInt(repoInfo[1]));
//                    System.out.println(preparedStmt.toString());
//
//                    preparedStmt.execute();
//                }
//
//            }


        /**  update pr communication result  **/
//            String[] mergedPR = io.readResult("/Users/shuruiz/Work/ForkData/pre_comm.txt").split("\n");
//            int count = 0;
////            String query = "UPDATE fork.PR_TO_ISSUE\n" +
////                    "SET pre_communication = ?, same_owner =? \n" +
////                    "WHERE repoID = ? AND issue_id = ? AND pull_request_id = ?";
//
//            String query = "UPDATE fork.crossReference\n" +
//                    "SET pre_communication = ?, same_owner = ?\n" +
//                    "WHERE projectID = ? AND issue1_id = ? AND issue1_type = 'issue'\n" +
//                    "      AND issue2_id = ? AND issue2_type = 'pr'\n" +
//                    "      AND ref_projectID = ?";
//            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
//                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(query)) {
//                conn1.setAutoCommit(false);
//                for (int i = 1; i < mergedPR.length; i++) {
//                    System.out.println(mergedPR[i]);
//                    String[] prinfo = mergedPR[i].split(",");
//                    int projectID = Integer.parseInt(prinfo[0]);
//                    int issue_id = Integer.parseInt(prinfo[1]);
//                    int pr_id = Integer.parseInt(prinfo[2]);
//                    boolean pre_com = prinfo[3].equals("true") ? true : false;
//                    boolean sameOwner = prinfo[4].equals("true") ? true : false;
//
//                    preparedStmt_1.setBoolean(1, pre_com);
//                    preparedStmt_1.setBoolean(2, sameOwner);
//                    preparedStmt_1.setInt(3, projectID);
//                    preparedStmt_1.setInt(4, issue_id);
//                    preparedStmt_1.setInt(5, pr_id);
//                    preparedStmt_1.setInt(6, projectID);
//                    preparedStmt_1.addBatch();
//                    System.out.print(count + " count ");
//
//                    if (++count % 100 == 0) {
//                        io.executeQuery(preparedStmt_1);
//                        conn1.commit();
//                    }
//
//                }
//                io.executeQuery(preparedStmt_1);
//                conn1.commit();
//            }

        /**  insert duplicate comment**/
//        HashMap<String, Integer> url_id = new HashMap<>();
//        String[] array = new String[0];
//        try {
////            modularity_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/csvFile/PR_Status.csv").split("\n");
//            array = io.readResult("/Users/shuruiz/Work/ForkData/duplicateComment0729.txt").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        String updatequery = "UPDATE fork.Pull_Request SET dupPR_comment =TRUE , dup_related_comment = ? WHERE projectID = ? AND pull_request_ID = ?";
//        int count = 0;
//
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(updatequery)) {
//            conn.setAutoCommit(false);
//            for (String line : array) {
//                String[] repoInfo = line.split(",");
//                String repourl = repoInfo[0];
//                int repoID;
//                if (url_id.get(repourl) == null) {
//                    repoID = io.getRepoId(repourl);
//                    url_id.put(repourl, repoID);
//                } else {
//                    repoID = url_id.get(repourl);
//                }
//
//
//                preparedStmt.setString(1, io.normalize(repoInfo[2]));
//                preparedStmt.setInt(2, repoID);
//                preparedStmt.setInt(3, Integer.parseInt(repoInfo[1]));
////                preparedStmt.addBatch();
//                System.out.println(preparedStmt.toString());
//                System.out.println(preparedStmt.executeUpdate());
//
////                if (++count % 100 == 0) {
////                    io.executeQuery(preparedStmt);
////                    conn.commit();
////                }
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
        /**  insert ratio of merged pr  **/
//        String[] modularity_array = new String[0];
//        try {
////            modularity_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/csvFile/PR_Status.csv").split("\n");
//            modularity_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/csvFile/PR_Status_alltype_0726.csv").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        String insertQuery = "UPDATE fork.Final_PR SET num_merged_PR_tmp = ? , ratio_merged_PR_tmp = ? WHERE projectID = ?";
////        String insertQuery = "UPDATE fork.Final_PR SET num_merged_PR_allType = ? , ratio_merged_PR_allType = ? WHERE projectID = ?";
////        String insertQuery = "UPDATE fork.Final_PR SET num_rejected_PR = ?  ,ratio_rejected_PR = ? WHERE projectID = ?";
////        String insertQuery = "UPDATE fork.Final_PR SET num_open_PR = ? ,ratio_open_PR = ? WHERE projectID = ?";
//        int count = 0;
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//            conn.setAutoCommit(false);
//            for (String repo : modularity_array) {
//                if (repo.contains("Merged")) {
////                if (repo.contains("Rejected")) {
////                if (repo.contains("Open")) {
//                    String[] repoInfo = repo.split(",");
//
//                    preparedStmt.setString(1, repoInfo[3]);
//                    preparedStmt.setString(2, repoInfo[4]);
//                    preparedStmt.setString(3, repoInfo[1].replace("\"", ""));
//                    System.out.println(preparedStmt.toString());
//                    System.out.println(preparedStmt.executeUpdate());
//                    System.out.print(count + " count ");
//                    if (++count % 100 == 0) {
//                        io.executeQuery(preparedStmt);
//                        conn.commit();
//                    }
//                }
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
        /**  insert ratio of merged pr - ALL TYPE MERGE **/
//            String[] modularity_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/PR_Status_alltype_0726.csv").split("\n");
//            for (String repo : modularity_array) {
////                if (repo.contains("Merged")) {
//                if (repo.contains("Rejected")) {
//                    String[] repoInfo = repo.split(",");
////                    String insertQuery = "UPDATE fork.Final SET ratio_mergedPR_AllType = ? WHERE repoID = ?";
//                    String insertQuery = "UPDATE fork.Final SET ratio_rejectedPR_AllType = ? WHERE repoID = ?";
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
//            String[] modularity_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/commitHistory/10_repo_ECI_all_file.csv").split("\n");
//            for (String repo : modularity_array) {
//                if (!repo.contains("NaN")) {
//                    String[] repoInfo = repo.split(",");
//
//                    String insertQuery = "UPDATE fork.Final SET  repoURL = ?,modularity_threshold_10_all_files =? WHERE repoURL = ?";
//
//                    preparedStmt = conn.prepareStatement(insertQuery);
//                    preparedStmt.setString(1, repoInfo[0]);
//                    preparedStmt.setDouble(2, Double.parseDouble(repoInfo[1]));
//                    preparedStmt.setString(3, repoInfo[0]);
//                    preparedStmt.execute();
//                    System.out.println(preparedStmt.toString());
//                }
//            }


        /**  update  modularity in FINAL table **/
//            String[] mod_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/0424/modularity.csv").split("\n");
//            for (int i = 1; i < mod_array.length; i++) {
//
//                String[] line = mod_array[i].split(",");
//                int projectID = Integer.parseInt(line[2]);
//                double modularity = Double.parseDouble(line[3]);
//                System.out.println(projectID);
//                String insertQuery;
//                if (!line[0].equals("")) {
//                    insertQuery = "UPDATE fork.Final SET  mean_modularity= ? WHERE repoID = ? ";
//                } else {
//                    continue;
//                }
//                preparedStmt = conn.prepareStatement(insertQuery);
//                preparedStmt.setDouble(1, modularity);
//                preparedStmt.setInt(2, projectID);
//                System.out.println("update " + preparedStmt.executeUpdate() + " rows");
//
//            }


        /**  update Boolean issue_first result in PR_ISSUE table **/
//            String[] issueFirst_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/pr_issue_raw.csv").split("\n");
//            for (int i = 1; i < issueFirst_array.length; i++) {
//
//                String repo = issueFirst_array[i];
//                System.out.println(repo);
//                String insertQuery = "";
//                repo = repo.replace("\"", "");
//
//                String[] repoInfo = repo.split(",");
//                if (repo.contains(",TRUE") || repo.contains(",FALSE")) {
//                    insertQuery = "UPDATE fork.PR_TO_ISSUE SET  issue_before_PR = ? WHERE repoID = ? AND  pull_request_id = ? AND issue_id = ?";
//                } else {
//                    continue;
//                }
//                preparedStmt = conn.prepareStatement(insertQuery);
//                preparedStmt.setBoolean(1, repoInfo[4].endsWith("TRUE") ? true : false);
//                preparedStmt.setInt(2, Integer.valueOf(repoInfo[1]));
//                preparedStmt.setInt(3, Integer.valueOf(repoInfo[2]));
//                preparedStmt.setInt(4, Integer.valueOf(repoInfo[3]));
//                preparedStmt.execute();
//
//            }

        /**  update percentage of issue_first result in Final table **/
//            String[] issueFirst_array = io.readResult("/Users/shuruiz/Box Sync/ForkData/pr_issue.csv").split("\n");
//            for (int i = 1; i < issueFirst_array.length; i++) {
//
//                String repo = issueFirst_array[i];
//                String insertQuery = "";
//                repo = repo.replace("\"", "");
//
//                String[] repoInfo = repo.split(",");
//
//                if (repo.contains(",TRUE,")) {
//                    insertQuery = "UPDATE fork.Final SET  issue_before_PR = ? WHERE repoID = ?";
//                } else if (repo.contains(",FALSE,")) {
//                    insertQuery = "UPDATE fork.Final SET  issue_after_PR = ? WHERE repoID = ?";
//                }else{
//                    continue;
//                }
//                preparedStmt = conn.prepareStatement(insertQuery);
//                preparedStmt.setInt(1, Integer.valueOf(repoInfo[3]));
//                preparedStmt.setInt(2, Integer.valueOf(repoInfo[1]));
//                preparedStmt.execute();
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
//                String query = " update Repository.Fork.repository " +
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
//            String selectSQL = " SELECT repoURL FROM Repository.Fork.repository";
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
//                    String selectForkID = "SELECT id from Repository.Fork.repository where repoURL = ?";
//                    preparedStmt = conn.prepareStatement(selectForkID);
//                    preparedStmt.setString(1, forkurl);
//                    ResultSet rs = preparedStmt.executeQuery();
//                    int forkID=-1;
//                    if(rs.next()) {
//                         forkID = rs.getInt("id");
//                    }
//
//                    if(forkID != -1) {
//                        String query = "  INSERT into Repository.Fork.repo_fork_commit_graph ( forkID, only_F, only_U, F2U, U2F, only_F_commit_list, F2U_commit_list, data_update_at) " +
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

        /**  insert repo info left over**/
//
//        String[] repos = new String[0];
//        try {
//            repos = io.readResult("/Users/shuruiz/Work/ForkData/repoInfo.txt").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        String insertQuery = "UPDATE fork.Final_fork SET num_star = ? , num_watcher = ? ,has_issue = ? ,network_count= ?, subscribers_count = ? WHERE projectID = ?";
//        int count = 0;
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//            conn.setAutoCommit(false);
//            for (String repo : repos) {
//                String[] repoInfo = repo.split(",");
//                String url = repoInfo[0];
//                int projectID;
//                if (url_id.get(url) != null) {
//                    projectID = url_id.get(url);
//                } else {
//                    projectID = io.getRepoId(url);
//                    url_id.put(url, projectID);
//                }
//                preparedStmt.setInt(1, Integer.parseInt(repoInfo[1]));
//                preparedStmt.setInt(2, Integer.parseInt(repoInfo[2]));
//                preparedStmt.setString(3, repoInfo[3]);
//                preparedStmt.setInt(4, Integer.parseInt(repoInfo[4]));
//                preparedStmt.setInt(5, Integer.parseInt(repoInfo[5]));
//
//                preparedStmt.setInt(6, projectID);
//                preparedStmt.addBatch();
//                System.out.print(count + " count ");
//                if (++count % 100 == 0) {
//                    io.executeQuery(preparedStmt);
//                    conn.commit();
//                }
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }


        /**  insert repo info left over**/
//
//        String[] repos = new String[0];
//        try {
//            repos = io.readResult("/Users/shuruiz/Work/ForkData/stat/median_mergeExpr.csv").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        String insertQuery = "UPDATE fork.Final_PR SET median_mergePR_exprience = ?   WHERE projectID = ?";
//        int count = 0;
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//            conn.setAutoCommit(false);
//            for (int i =1;i< repos.length;i++) {
//                String repo = repos[i];
//                String[] repoInfo = repo.split(",");
//                int projectID = Integer.parseInt(repoInfo[1].replace("\"",""));
//                double median = Double.parseDouble(repoInfo[2]);
//
//                preparedStmt.setDouble(1, median);
//                preparedStmt.setInt(2, projectID);
//                preparedStmt.addBatch();
//                System.out.print(count + " count ");
//                if (++count % 100 == 0) {
//                    io.executeQuery(preparedStmt);
//                    conn.commit();
//                }
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }


        /**  insert repo number of fork from GHT r**/
////
//        String[] repos = new String[0];
//        try {
//            repos = io.readResult("/Users/shuruiz/Work/ForkData/subjectProject.txt").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        String insertQuery = "UPDATE fork.Final  SET num_fork_ght = ?   WHERE repoURL = ?";
//        int count = 0;
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//            conn.setAutoCommit(false);
//            for (int i = 1; i < repos.length; i++) {
//                String repo = repos[i];
//                String[] repoInfo = repo.split(",");
//                int num_forks = Integer.parseInt(repoInfo[0]);
//                String repoURL = repoInfo[1]  ;
//
//                preparedStmt.setInt(1, num_forks);
//                preparedStmt.setString(2, repoURL);
//                preparedStmt.addBatch();
//                System.out.print(count + " count ");
//                if (++count % 100 == 0) {
//                    io.executeQuery(preparedStmt);
//                    conn.commit();
//                }
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }


        /**  insert social connection for each repo from GHT r**/
//        List<List<String>> repos = io.readCSV("/Users/shuruiz/Work/ForkData/stat/socialConn_external.csv");
//
//        String insertQuery = "UPDATE fork.Final_PR  SET ratio_social_connection_external = ?   WHERE fork.Final_PR.projectID = ?";
//        int count = 0;
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//            conn.setAutoCommit(false);
//            for (int i = 1; i < repos.size(); i++) {
//                List<String> repoInfo = repos.get(i);
//                int repoID = Integer.parseInt(repoInfo.get(1));
//                double ratio = Double.parseDouble(repoInfo.get(2));
//                System.out.println(repoID+" "+ratio);
//                preparedStmt.setDouble(1, ratio);
//                preparedStmt.setInt(2, repoID);
//                preparedStmt.addBatch();
//                System.out.println(count + " count ");
//                if (++count % 100 == 0) {
//                    io.executeQuery(preparedStmt);
//                    conn.commit();
//                }
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }


        /**  insert hotness for each repo from GHT r**/
//        List<List<String>> repos = io.readCSV("/Users/shuruiz/Work/ForkData/stat/gousios_hotness_median_external.csv");
        List<List<String>> repos = io.readCSV("/Users/shuruiz/Work/ForkData/stat/gousios_hotness_mean_external.csv");
//        List<List<String>> repos = io.readCSV("/Users/shuruiz/Work/ForkData/stat/hotness_mean_external.csv");

//        String insertQuery = "UPDATE fork.Final_PR  SET hotness_median_external = ?   WHERE fork.Final_PR.projectID = ?";
//        String insertQuery = "UPDATE fork.Final_PR  SET hotness_median_external_correct = ?   WHERE fork.Final_PR.projectID = ?";
//        String insertQuery = "UPDATE fork.Final_PR  SET hotness_mean_external = ?   WHERE fork.Final_PR.projectID = ?";
        String insertQuery = "UPDATE fork.Final_PR  SET hotness_mean_external_correct = ?   WHERE fork.Final_PR.projectID = ?";
        int count = 0;
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
            conn.setAutoCommit(false);
            for (int i = 1; i < repos.size(); i++) {
                List<String> repoInfo = repos.get(i);
                int repoID = Integer.parseInt(repoInfo.get(1));
//                double median = Double.parseDouble(repoInfo.get(2));
                double mean = Double.parseDouble(repoInfo.get(3));
//                System.out.println(repoID+" "+median);
                System.out.println(repoID+" "+mean);
//                preparedStmt.setDouble(1, median);
                preparedStmt.setDouble(1, mean);
                preparedStmt.setInt(2, repoID);
                preparedStmt.addBatch();
                System.out.println(count + " count ");
                if (++count % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }
}
