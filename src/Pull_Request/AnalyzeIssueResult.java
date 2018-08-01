package Pull_Request;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class AnalyzeIssueResult {
    static String working_dir, pr_dir, output_dir, clone_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    static int batchSize = 100;

    AnalyzeIssueResult() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
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
        AnalyzeIssueResult analyzeIssueResult = new AnalyzeIssueResult();
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String insertIssueQuery = "INSERT INTO fork.ISSUE (projectID,issue_id,author,closed,closed_at,created_at,updated_at,title) \n" +
                "VALUES (?,?,?,?,?,?,?,?)";
//                "  SELECT *" +
//                "  FROM (SELECT" +
//                "          ? AS a,? AS b, ? AS c,? AS d, ? AS e,? AS f, ? AS g, ? AS h ) AS tmp" +
//                "  WHERE NOT EXISTS(" +
//                "      SELECT *" +
//                "      FROM fork.ISSUE AS ss" +
//                "      WHERE ss.projectID = ?" +
//                "      AND ss.issue_id= ?" +
//                "  )" +
//                "  LIMIT 1";

        HashMap<Integer, HashSet<Integer>> existing_issue = getExistingIssue();

        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertIssueQuery);) {
            conn1.setAutoCommit(false);
            int count = 0;
            for (String repo : repos) {
                System.out.println("repo --- " + repo);
                int projectID = io.getRepoId(repo);
                HashSet<Integer> existIssueSet = existing_issue.get(projectID);
                if (existIssueSet == null) {
                    continue;
                }
                String issue_file = output_dir + "shurui.cache/get_issues." + repo.replace("/", ".") + ".csv";
                if (new File(issue_file).exists()) {
                    List<List<String>> issueList = io.readCSV(issue_file);
                    for (int i = 1; i < issueList.size(); i++) {
                        List<String> issue = issueList.get(i);
                        int issue_num = Integer.parseInt(issue.get(5));
                        if (existIssueSet.contains(issue_num)) {
                            System.out.println("pass issue " + issue_num);
                            continue;
                        }

                        String author = issue.get(1);
                        String closed = issue.get(2);
                        String closed_at = issue.get(3);
                        String created_at = issue.get(4);

                        String title = issue.get(6);
                        String updated_at = issue.get(7);


                        // projectID,
                        preparedStmt_1.setInt(1, projectID);
                        //issue_id,
                        preparedStmt_1.setInt(2, issue_num);

                        //author,
                        preparedStmt_1.setString(3, author);
                        // closed,
                        preparedStmt_1.setString(4, closed);
                        // closed_at,
                        preparedStmt_1.setString(5, closed_at);
                        // created_at,
                        preparedStmt_1.setString(6, created_at);
                        // updated_at,
                        preparedStmt_1.setString(7, updated_at);
                        // title
                        preparedStmt_1.setString(8, io.normalize(title));
                        preparedStmt_1.addBatch();
                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_1);
                            conn1.commit();
                        }

                    }
                    if (count > 0) {
                        System.out.println("inserting " + count + " issue from " + repo);
                        io.executeQuery(preparedStmt_1);
                        conn1.commit();
                        System.out.println("done " + repo);
                    }
                } else {
                    io.writeTofile(repo + "\n", output_dir + "issueNotYet.txt");
                }
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    public static HashMap<Integer, HashSet<Integer>> getExistingIssue() {
        HashMap<Integer, HashSet<Integer>> existingIssue = new HashMap<>();

        String mergedCommitID_query = "SELECT projectID , issue_id\n" +
                "FROM fork.ISSUE;";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int projectID = rs.getInt(1);
                HashSet<Integer> issueSet = new HashSet<>();
                if (existingIssue.get(projectID) != null) {
                    issueSet = existingIssue.get(projectID);
                }
                issueSet.add(rs.getInt(2));
                existingIssue.put(projectID, issueSet);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return existingIssue;
    }
}
