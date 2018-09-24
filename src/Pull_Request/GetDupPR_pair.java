package Pull_Request;

import Pull_Request.Merge_PR_Status.GetMergedPR;
import Util.IO_Process;

import java.io.IOException;
import java.sql.*;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetDupPR_pair {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir;
    static String myUrl, user, pwd;

    GetDupPR_pair() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            timeline_dir = output_dir + "shurui_crossRef.cache_pass/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new GetDupPR_pair();
        detectDuplicatePRid();
//        insertDupPRidToDB();

//        detectDuplicatePR_relatedCommit();
//        insertDupPR_CommitToDB();
    }

    private static void detectDuplicatePRid() {
        String query = "SELECT projectID, pull_request_ID, dup_related_comment\n" +
                "FROM Pull_Request\n" +
                "WHERE Pull_Request.dupPR_comment = TRUE AND merged = 'false'";
        IO_Process io = new IO_Process();
        final StringBuilder[] sb = {new StringBuilder()};

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int projectID = rs.getInt(1);
                int prid = rs.getInt(2);
                String dup_comment = rs.getString(3);

                HashSet<Integer> dupPRSet = getPotentialDupPRid(dup_comment);

                for (int dupPR : dupPRSet) {
                    System.out.println(projectID + ", pr" + prid + " dup pr:  " + dupPR);
                    io.writeTofile(projectID + "," + prid + "," + dupPR + "\n", output_dir + "DupPRpair.txt");

                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void detectDuplicatePR_relatedCommit() {
        String query = "SELECT projectID, pull_request_ID, dup_related_comment\n" +
                "FROM Pull_Request\n" +
                "WHERE Pull_Request.dupPR_comment = TRUE AND merged = 'false'";
        IO_Process io = new IO_Process();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int projectID = rs.getInt(1);
                int prid = rs.getInt(2);
                String dup_comment = rs.getString(3);
                GetMergedPR getMergedPR = new GetMergedPR();
                String sha = getMergedPR.containsPotentialSHA(dup_comment);

                if(!sha.trim().equals("")) {
                    System.out.println(projectID + ", pr" + prid + " dup commit: " + sha.trim());
                    io.writeTofile(projectID + "," + prid + "," + sha + "\n", output_dir + "DupPRpair_sha.txt");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

//

    public static void insertDupPRidToDB() {
        new GetDupPR_pair();
        IO_Process io = new IO_Process();
        String[] arr = {};
        try {
            arr = io.readResult(output_dir + "DupPRpair.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "INSERT Dup_PR_pairs (projectID, PR1, PR2)  VALUES (?,?,?)";
        int count = 0;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);


            for (String line : arr) {
                String[] info = line.split(",");
                int projectID = Integer.parseInt(info[0]);
                int prid = Integer.parseInt(info[1]);
                int dup_prID = Integer.parseInt(info[2]);


                preparedStmt.setInt(1, projectID);
                preparedStmt.setInt(2, prid);
                preparedStmt.setInt(3, dup_prID);

                preparedStmt.addBatch();

                int batchSize = 100;
                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }
            }

            if (count > 0) {
                io.executeQuery(preparedStmt);
                conn.commit();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    public static void insertDupPR_CommitToDB() {
        new GetDupPR_pair();
        IO_Process io = new IO_Process();
        String[] arr = {};
        try {
            arr = io.readResult(output_dir + "DupPRpair_sha.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "UPDATE Pull_Request " +
                "set dup_with_commit_sha = ? WHERE projectID = ? and pull_request_ID = ?";
        int count = 0;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);


            for (String line : arr) {
                System.out.println(line);
                String[] info = line.split(",");
                int projectID = Integer.parseInt(info[0]);
                int prid = Integer.parseInt(info[1]);
                String dup_pr_sha = info[2].trim();

                preparedStmt.setString(1, dup_pr_sha);
                preparedStmt.setInt(2, projectID);
                preparedStmt.setInt(3, prid);


                preparedStmt.addBatch();

                int batchSize = 100;
                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }
            }

            if (count > 0) {
                io.executeQuery(preparedStmt);
                conn.commit();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }


    private static HashSet<Integer> getPotentialDupPRid(String comment) {

        HashSet<Integer> idSet = new HashSet<>();
        Pattern p1 = Pattern.compile("(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)");
        Matcher m1 = p1.matcher(comment);
        while (m1.find()) {
            idSet.add(Integer.valueOf(m1.group().replaceAll("(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)", "")));
        }

        return idSet;


    }
}
