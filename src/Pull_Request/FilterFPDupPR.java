package Pull_Request;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FilterFPDupPR {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir;
    static String myUrl, user, pwd;

    FilterFPDupPR() {
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
        new FilterFPDupPR();
        HashMap<Integer, HashSet<String>> pr_commentMap = getDuplicatePRComment();

        checkAuthors(pr_commentMap);

//        2nd step, insert into db

//        insertFP_prToDB();

    }

    private static void insertFP_prToDB() {
        IO_Process io = new IO_Process();
        String[] arr = {};
        try {
            arr = io.readResult(output_dir + "FPdupPR.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "UPDATE Pull_Request\n" +
                "SET FP_Duplicate_PRid = ? , FalsePositive_DupPR =TRUE \n" +
                "WHERE projectID = ? AND pull_request_ID = ?";
        int count = 0;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);


            for (String line : arr) {
                String[] info = line.split(",");
                int projectID = Integer.parseInt(info[0]);
                int prid = Integer.parseInt(info[1]);
                int dup_prID = Integer.parseInt(info[2]);

                preparedStmt.setInt(1, dup_prID);
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

    private static void checkAuthors(HashMap<Integer, HashSet<String>> pr_commentMap) {
        IO_Process io = new IO_Process();
        final StringBuilder[] sb = {new StringBuilder()};
        pr_commentMap.forEach((projectID, prSet) -> {
            String projectURL = io.getRepoUrlByID(projectID);
            for (String prc : prSet) {
                int prid = Integer.parseInt(prc.split(",\\[")[0]);
                String dup_comment = prc.split(",\\[")[1];
                String commentAuthor = getCommentAuthor(projectURL, prid, dup_comment);

                if (!commentAuthor.equals("")) {
                    System.out.println(projectURL + ", pr" + prid + " dup comments by " + commentAuthor);
                    HashSet<Integer> dupPRSet = getPotentialDupPRid(dup_comment);

                    for (int dupPR : dupPRSet) {
                        String prAuthor = getPRAuthor(projectID, dupPR);
                        if (prAuthor.equals(commentAuthor)) {
                            System.out.println(projectURL + ", pr" + prid + " fp ! dup pr:  " + dupPR);

                            sb[0].append(projectID + "," + prid + "," + dupPR + "\n");
                        }
                    }


                } else {
                    System.out.println(projectURL + ", pr" + prid + " dup comments author is null");
                }

            }
            io.writeTofile(sb[0].toString(), output_dir + "FPdupPR.txt");
            sb[0] = new StringBuilder();
        });

    }



    private static String getCommentAuthor(String projectURL, int prid, String dup_comment) {
        String fileName = output_dir + "shurui.cache/get_pr_comments." + projectURL.replace("/", ".") + "_" + prid + ".csv";
        IO_Process io = new IO_Process();
        if (new File(fileName).exists()) {
            List<List<String>> rows = io.readCSV(fileName);
            for (List<String> r : rows) {
                if (dup_comment.contains(r.get(2))) {
                    return r.get(1);
                }
            }
        }
        return "";
    }

    private static String getPRAuthor(int projectid, int pr_id) {
        String query = "SELECT authorName\n" +
                "from Pull_Request\n" +
                "WHERE  projectID = " + projectid + " and pull_request_ID = " + pr_id + ";";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return "";
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

    public static HashMap<Integer, HashSet<String>> getDuplicatePRComment() {
        HashMap<Integer, HashSet<String>> pr_commentMap = new HashMap<>();
        String query = "SELECT projectID, pull_request_ID, dup_related_comment\n" +
                "FROM Pull_Request\n" +
                "WHERE dup_related_comment IS NOT NULL AND merged = 'false'";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {

                int projectID = rs.getInt(1);
                int prID = rs.getInt(2);
                String dupComment = rs.getString(3);
                HashSet<String> prc = new HashSet<>();
                if (pr_commentMap.get(projectID) != null) {
                    prc = pr_commentMap.get(projectID);
                }
                prc.add(prID + "," + dupComment);
                pr_commentMap.put(projectID, prc);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pr_commentMap;
    }
}
