package Commit.Easiness;

import Util.IO_Process;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;

public class InsertEasinessToDB {
    static String user, pwd, myUrl, token;
    static String current_dir = System.getProperty("user.dir");

    public static void main(String[] args) {
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
        /**  easiness  of project **/
//        String[] easiness_array = new String[0];
//        try {
//            easiness_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/easiness/easiness_project_external.csv").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        String insertQuery = "UPDATE fork.Final_easiness_external SET  mean_external = ?,  median_external = ? , geoMean_external = ?," +
//                " filtered_mean_external = ?, filtered_median_external = ? , filtered_geoMean_external = ?" +
//                " WHERE repoID = ?";
//        int count = 0;
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//            conn.setAutoCommit(false);
//
//            for (int i = 1; i < easiness_array.length; i++) {
//                String repo = easiness_array[i];
//                String[] repoInfo = repo.split(",");
//
//                preparedStmt.setDouble(1, Double.parseDouble(repoInfo[2]));
//                preparedStmt.setDouble(2, Double.parseDouble(repoInfo[3]));
//                preparedStmt.setDouble(3, Double.parseDouble(repoInfo[4]));
//                preparedStmt.setDouble(4, Double.parseDouble(repoInfo[5]));
//                preparedStmt.setDouble(5, Double.parseDouble(repoInfo[6]));
//                preparedStmt.setDouble(6, Double.parseDouble(repoInfo[7]));
//                preparedStmt.setInt(7, Integer.parseInt(repoInfo[1]));
//                preparedStmt.addBatch();
//                System.out.print(count + " count ");
//                if (++count % 100 == 0) {
//                    io.executeQuery(preparedStmt);
//                    conn.commit();
//                }
//
//
//            }
//            io.executeQuery(preparedStmt);
//            conn.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }


        /**  easiness of merged commit  **/
//        String[] easinessMerged_array = new String[0];
//        try {
//            easinessMerged_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/easiness/easiness_mergedPR_ofProject_external.csv").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        String insertQuery = "UPDATE fork.Final_easiness_external SET  merged_mean_external = ?, merged_median_external = ? , merged_geoMean_external = ?," +
//                    "filtered_merged_mean_external = ?, filtered_merged_median_external = ? , filtered_merged_geoMean_external = ?" +
//                    " WHERE repoID = ?";
//            int count = 0;
//            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//                 PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//                conn.setAutoCommit(false);
//
//                for (int i = 1; i < easinessMerged_array.length; i++) {
//                    String repo = easinessMerged_array[i];
//                    String[] repoInfo = repo.split(",");
//
//                    preparedStmt.setDouble(1, Double.parseDouble(repoInfo[2]));
//                    preparedStmt.setDouble(2, Double.parseDouble(repoInfo[3]));
//                    preparedStmt.setDouble(3, Double.parseDouble(repoInfo[4]));
//                    preparedStmt.setDouble(4, Double.parseDouble(repoInfo[5]));
//                    preparedStmt.setDouble(5, Double.parseDouble(repoInfo[6]));
//                    preparedStmt.setDouble(6, Double.parseDouble(repoInfo[7]));
//                    preparedStmt.setInt(7, Integer.parseInt(repoInfo[1]));
//                    preparedStmt.addBatch();
//                    System.out.print(count + " count ");
//                    if (++count % 100 == 0) {
//                        io.executeQuery(preparedStmt);
//                        conn.commit();
//                    }
//
//                }
//                io.executeQuery(preparedStmt);
//                conn.commit();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }

        /**  easiness of rejected commit  **/
//        String[] easinessMerged_array = new String[0];
//        try {
//            easinessMerged_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/easiness/easiness_rejectPR_ofProject_external.csv").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        String insertQuery = "UPDATE fork.Final_easiness_external SET  rejected_mean_external = ?, rejected_median_external = ? , rejected_geoMean_external = ?," +
//                    "filtered_rejected_mean_external = ?, filtered_rejected_median_external = ? , filtered_rejected_geoMean_external = ?" +
//                    " WHERE repoID = ?";
//            int count = 0;
//            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//                 PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
//                conn.setAutoCommit(false);
//
//                for (int i = 1; i < easinessMerged_array.length; i++) {
//                    String repo = easinessMerged_array[i];
//                    String[] repoInfo = repo.split(",");
//
//                    preparedStmt.setDouble(1, Double.parseDouble(repoInfo[2]));
//                    preparedStmt.setDouble(2, Double.parseDouble(repoInfo[3]));
//                    preparedStmt.setDouble(3, Double.parseDouble(repoInfo[4]));
//                    preparedStmt.setDouble(4, Double.parseDouble(repoInfo[5]));
//                    preparedStmt.setDouble(5, Double.parseDouble(repoInfo[6]));
//                    preparedStmt.setDouble(6, Double.parseDouble(repoInfo[7]));
//                    preparedStmt.setInt(7, Integer.parseInt(repoInfo[1]));
//                    preparedStmt.addBatch();
//                    System.out.print(count + " count ");
//                    if (++count % 100 == 0) {
//                        io.executeQuery(preparedStmt);
//                        conn.commit();
//                    }
//
//
//                }
//                io.executeQuery(preparedStmt);
//                conn.commit();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//
//



        /**  easiness of PR loc  **/
        String[] easinessMerged_array = new String[0];
        try {
            easinessMerged_array = io.readResult("/Users/shuruiz/Work/ForkData/stat/easiness/easiness_PRloc_external.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String insertQuery = "UPDATE fork.Pull_Request SET  easiness_loc = ?" +
                    " WHERE projectID = ? AND pull_request_ID = ?";
            int count = 0;
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insertQuery)) {
                conn.setAutoCommit(false);

                for (int i = 1; i < easinessMerged_array.length; i++) {
                    String repo = easinessMerged_array[i];
                    if(repo.contains("NA")){
                        continue;
                    }
                    String[] repoInfo = repo.split(",");

                    preparedStmt.setDouble(1, Double.parseDouble(repoInfo[3]));
                    preparedStmt.setInt(2, Integer.valueOf(repoInfo[1]));
                    preparedStmt.setInt(3,  Integer.valueOf(repoInfo[2]));
                    preparedStmt.addBatch();
                    System.out.print(count + " count ");
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
