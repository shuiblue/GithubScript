import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;

public class AnalyzeIssueResult {


    static String tmpDirPath;
    static String resultDirPath;
    static String current_dir;
    static String pathname, historyDirPath;
    static HashSet<String> stopFileSet = new HashSet<>();
    static String output_dir;
    static String myUrl, user, pwd;
    static String current_OS = System.getProperty("os.name").toLowerCase();


    static public void main(String[] args) {
        int batchSize = 100;
            AnalyzeChangedFile_ClassifyCommit acc = new AnalyzeChangedFile_ClassifyCommit();

        current_dir = System.getProperty("user.dir");
        IO_Process io = new IO_Process();
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            output_dir = paramList[0];
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }

        tmpDirPath = output_dir + "/cloneRepos/";
        historyDirPath = output_dir + "/commitHistory/";
        resultDirPath = output_dir + "/result/";
        String[] repoList = {};
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/

        try {
            repoList = io.readResult(current_dir + "/input/issueList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String repo : repoList) {
            String myDriver = "com.mysql.jdbc.Driver";
            try {
                Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                conn.setAutoCommit(false);
                int RepoID = io.getRepoId(repo);

                if (RepoID != -1) {

                    String issue_dir = output_dir + repo + "/linked_issue_info.txt";

                    // create a mysql database connection
                    Class.forName(myDriver);

                    File f = new File(issue_dir);
                    if (f.exists()) {
                        // do something

                        System.out.println("repo --- " + repo);
                        String[] issueList = io.readResult(issue_dir).split("\n");

                        String query = "  INSERT INTO fork.ISSUE (issue_id, author, closed, created_at, updated_at, closed_at, title,projectID) " +

                                " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d,? AS e,? AS f, ? AS ds,? AS de) AS tmp" +
                                " WHERE NOT EXISTS (" +
                                " SELECT * FROM  fork.ISSUE WHERE projectID = ? AND issue_id = ?" +
                                ") LIMIT 1";


                        PreparedStatement preparedStmt = conn.prepareStatement(query);
                        ;
                        int count = 0;
                        for (String is : issueList) {
                            if (!is.contains("null") && is.contains("[{")) {
//                                String query = "  INSERT into fork.ISSUE (issue_id, author, closed, created_at, updated_at, closed_at, title,repoID) " +
//                                        "VALUES (?,?,?,?,?,?,?,?)";


                                String[] issue_arr = is.split("----")[1].replace("[{", "").replace("}]", "").split(", \"");

                                int issueID = Integer.parseInt(issue_arr[3].replace("number", "").replace("\":", "").replace("\"", "").trim());
                                System.out.println(issueID);
                                preparedStmt.setInt(1, issueID);
                                preparedStmt.setString(2, issue_arr[0].replace("author", "").replace("\":", "").replace("\"", "").trim());
                                preparedStmt.setString(3, issue_arr[5].replace("closed", "").replace("\":", "").replace("\"", "").trim());
                                preparedStmt.setString(4, issue_arr[1].replace("created_at", "").replace("\":", "").replace("\"", "").trim());
                                preparedStmt.setString(5, issue_arr[4].replace("updated_at", "").replace("\":", "").replace("\"", "").trim());
                                preparedStmt.setString(6, issue_arr[6].replace("closed_at", "").replace("\":", "").replace("\"", "").trim());
                                preparedStmt.setString(7, issue_arr[2].replace("title", "").replace("\":", "").replace("\"", "").trim());
                                preparedStmt.setInt(8, RepoID);
                                preparedStmt.setInt(9, RepoID);
                                preparedStmt.setInt(10, issueID);
                                System.out.println(preparedStmt.toString());
                                preparedStmt.addBatch();

                                System.out.println("count" + count);
                                if (++count % batchSize == 0) {
                                    io.executeQuery(preparedStmt);
                                    conn.commit();

                                }
                            }
                        }
                        io.executeQuery(preparedStmt);
                        conn.commit();
                        preparedStmt.close();
                        conn.close();

                        System.out.println("start pr_issue...");


                        updatePr_issue_table(repo);
                    } else {
                        System.out.println(repo);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }


        }


    }

    private static void updatePr_issue_table(String repo) {
        int batchSize = 100;
        IO_Process io = new IO_Process();
        int repoID = io.getRepoId(repo);
        HashSet<Integer> checkedIssue = new HashSet<>();
        String pr_issue_dir = output_dir + repo + "/pr_comments.txt";
        String[] pr_issue_List = new String[0];
        Connection conn;
        String query = "  INSERT INTO fork.PR_TO_ISSUE (repoID, pull_request_id, issue_id) " +
//                                                "VALUES (?,?,?)";
                " SELECT * FROM (SELECT ? AS a,? AS b,? AS c) AS tmp" +
                " WHERE NOT EXISTS (" +
                "SELECT * FROM fork.PR_TO_ISSUE WHERE repoID = ? AND pull_request_id=? AND issue_id = ?" +
                ") LIMIT 1";
        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);
            pr_issue_List = io.readResult(pr_issue_dir).split("\n");
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            int count = 0;
            for (String pr : pr_issue_List) {
                if (pr.contains("#")) {
                    int pr_num = Integer.parseInt(pr.trim().split("----")[0]);


                    String[] issueLinkedList = pr.split("----")[1].split(",");
                    for (String issue : issueLinkedList) {
                        int issue_num = Integer.parseInt(issue.replace("#", ""));
                        if (!checkedIssue.contains(issue_num)) {
                            checkedIssue.add(issue_num);
                            preparedStmt.setInt(1, repoID);
                            preparedStmt.setInt(2, pr_num);
                            preparedStmt.setInt(3, issue_num);
                            preparedStmt.setInt(4, repoID);
                            preparedStmt.setInt(5, pr_num);
                            preparedStmt.setInt(6, issue_num);
                            preparedStmt.addBatch();
                            System.out.println(preparedStmt.toString());

                            System.out.println("count" + count);
                            if (++count % batchSize == 0) {
                                io.executeQuery(preparedStmt);
                                conn.commit();

                            }

                        }
                    }
                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
            preparedStmt.close();
            conn.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }
}
