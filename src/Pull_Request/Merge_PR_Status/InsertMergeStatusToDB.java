package Pull_Request.Merge_PR_Status;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;

public class InsertMergeStatusToDB {

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

        /**  update merged pr type 2 - type 4  **/
        HashMap<String, Integer> url_id = new HashMap<>();
        String[] mergedPR = new String[0];
        try {
//            mergedPR = io.readResult("/home/feature/shuruiz/ForkData/update_mergedPR0806.txt").split("\n");
            mergedPR = io.readResult("/Users/shuruiz/Work/ForkData/update_mergedPR0806.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        int count = 0;
        String insertQuery = "UPDATE fork.Pull_Request " +
                "SET new_merge_1 = ? ,new_merge_2 = ?, " +
                "new_merge_2ref =? ," +
                "new_merge_3 =? ,new_merge_4 =?,new_merge_5 = ?  ," +
                " merge_ref_commit = ?  " +
                "WHERE projectID = ? AND pull_request_ID = ?";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn1.prepareStatement(insertQuery)) {
            conn1.setAutoCommit(false);
            for (int i = 0; i < mergedPR.length; i++) {
                String[] prinfo = mergedPR[i].split(",");
                String url = prinfo[0];
                int projectID;
                if (url_id.get(url) != null) {
                    projectID = url_id.get(url);
                } else {
                    projectID = io.getRepoId(url);
                    url_id.put(url, projectID);
                }
                int pr_id = Integer.parseInt(prinfo[1]);
                String type = prinfo[2];

                boolean merge_1 = false;
                boolean merge_2 = false;
                boolean merge_2Ref = false;
                boolean merge_3 = false;
                boolean merge_4 = false;
                boolean merge_5 = false;
                String sha = "";

                if (type.equals("merged-1")) {
                    merge_1 = true;
                } else if (type.equals("merged-2")) {
                    merge_2 = true;
                } else if (type.contains("merged-2ref")) {
                    merge_2Ref = true;
                     sha =  prinfo[3].trim();
                     System.out.println("merge_2Ref "+ sha);
                } else if (type.equals("merged-3")) {
                    merge_3 = true;
                } else if (type.equals("merged-4")) {
                    merge_4 = true;
                } else if (type.equals("merged-5")) {
                    merge_5 = true;
                }

                preparedStmt.setBoolean(1, merge_1);
                preparedStmt.setBoolean(2, merge_2);
                preparedStmt.setBoolean(3, merge_2Ref);
                preparedStmt.setBoolean(4, merge_3);
                preparedStmt.setBoolean(5, merge_4);
                preparedStmt.setBoolean(6, merge_5);
                preparedStmt.setString(7, sha);
                preparedStmt.setInt(8, projectID);
                preparedStmt.setInt(9, pr_id);
                preparedStmt.addBatch();
                System.out.print(count + " count ");
                System.out.println(projectID + "," + pr_id + "," + merge_1 + "," + merge_2 + "," + merge_2Ref + "," + merge_3 + "," + merge_4 + ", " + merge_5);
                if (++count % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    conn1.commit();
                }

            }
            io.executeQuery(preparedStmt);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        /**  update rejected PR status  **/
//        String[] mergedPR = new String[0];
//        try {
////            mergedPR = io.readResult("/Users/shuruiz/Work/ForkData/noComments_rejectedPR-1120pm.txt").split("\n");
//            mergedPR = io.readResult("/Users/shuruiz/Work/ForkData/check_type3_mergedPR-1120pm.txt").split("\n");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        int count = 0;
//            String currentProject = "";
        int currentProject_id = -1;
//            String insertQuery = "UPDATE fork.Pull_Request " +
//                    "SET merge_2Ref =?    " +
////                    "SET merge_2 = ? ,merge_2Ref =? ,merge_3 =? ,merge_4 =?  " +
//                    "WHERE projectID = ? AND pull_request_ID = ? ";
//            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
//                 PreparedStatement preparedStmt = conn1.prepareStatement(insertQuery)) {
//                conn1.setAutoCommit(false);
//                for (int i = 1; i < mergedPR.length; i++) {
//                    String[] prinfo = mergedPR[i].split(",");
//                    String projectURL = prinfo[0];
//                    if (!currentProject.equals(projectURL)) {
//                        currentProject=projectURL;
//                        currentProject_id =io.getRepoId(projectURL);
//                        System.out.println("new "+projectURL+","+currentProject_id);
//                    }
//                    int projectID = currentProject_id;
//                    int pr_id = Integer.parseInt(prinfo[1].trim());
////
////                    preparedStmt.setBoolean(1, false);
////                    preparedStmt.setBoolean(2, false);
////                    preparedStmt.setBoolean(3, false);
////                    preparedStmt.setBoolean(4, false);
////                    preparedStmt.setInt(5, projectID);
////                    preparedStmt.setInt(6, pr_id);
//
//                    preparedStmt.setBoolean(1, false);
//                    preparedStmt.setInt(2, projectID);
//                    preparedStmt.setInt(3, pr_id);
//                    preparedStmt.addBatch();
//                    System.out.print(count + " count ");
//                    System.out.println(mergedPR[i]);
//                    if (++count % 100 == 0) {
//                        io.executeQuery(preparedStmt);
//                        conn1.commit();
//                    }
//
//                }
//                io.executeQuery(preparedStmt);
//                conn1.commit();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }

    }
}
