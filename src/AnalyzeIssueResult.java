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


    static public void main(String[] args) {
        AnalyzeCoChangedFile acc = new AnalyzeCoChangedFile();

        current_dir = System.getProperty("user.dir");

        tmpDirPath = output_dir + "/cloneRepos/";
        historyDirPath = output_dir + "/commitHistory/";
        resultDirPath = output_dir + "/result/";
        IO_Process io = new IO_Process();
        String[] repoList = {};
        current_dir = System.getProperty("user.dir");
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/

        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String repo : repoList) {
            String myDriver = "com.mysql.jdbc.Driver";
            String myUrl = "jdbc:mysql://localhost:3306/Fork";
            try {
                Connection conn = DriverManager.getConnection(myUrl, "root", "shuruiz");
                PreparedStatement preparedStmt;

                String selectRepoID = "SELECT id from Fork.repository where repoURL = ?";
                preparedStmt = conn.prepareStatement(selectRepoID);
                preparedStmt.setString(1, repo);
                ResultSet rs = preparedStmt.executeQuery();
                int RepoID = -1;
                if (rs.next()) {
                    RepoID = rs.getInt("id");
                }
                if (RepoID != -1) {

                    String issue_dir = "/Users/shuruiz/Box Sync/queryGithub/" + repo + "/linked_issue_info.txt";
                    String pr_issue_dir = "/Users/shuruiz/Box Sync/queryGithub/" + repo + "/pr_comments.txt";

                    // create a mysql database connection
                    Class.forName(myDriver);

                    File f = new File(issue_dir);
                    if (f.exists()) {
                        // do something

//                        System.out.println(repo);
                        String[] issueList = io.readResult(issue_dir).split("\n");
                        for (String is : issueList) {
                            if (!is.contains("null") && is.contains("[{")) {
//                                String query = "  INSERT into Fork.ISSUE (issue_id, author, closed, created_at, updated_at, closed_at, title,repoID) " +
//                                        "VALUES (?,?,?,?,?,?,?,?)";

                                String query = "  INSERT into Fork.ISSUE (issue_id, author, closed, created_at, updated_at, closed_at, title,repoID) " +

                                        " SELECT * FROM (SELECT ? as a,? as b,? as c,? as d,? as e,? as f, ? as ds,? as de) AS tmp" +
                                        " WHERE NOT EXISTS (" +
                                        " SELECT * FROM  Fork.ISSUE WHERE repoID = ? AND issue_id = ?" +
                                        ") LIMIT 1";




                                preparedStmt = conn.prepareStatement(query);
                                System.out.println(is);
                                String[] issue_arr = is.split("----")[1].replace("[{", "").replace("}]", "").split(", \"");

                                int issueID = Integer.parseInt(issue_arr[3].replace("number", "").replace("\":", "").replace("\"", "").trim());
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
                                preparedStmt.execute();
//                        System.out.println("insert to issue " + is);
                            }
                        }

//                        System.out.println("start pr_issue...");
                        String[] pr_issue_List = io.readResult(pr_issue_dir).split("\n");
                        HashSet<Integer> checkedIssue = new HashSet<>();
                        for (String pr : pr_issue_List) {
                            if (pr.contains("#")) {
                                int pr_num = Integer.parseInt(pr.trim().split("----")[0]);


                                String[] issueLinkedList = pr.split("----")[1].split(",");
                                for (String issue : issueLinkedList) {
                                    int issue_num = Integer.parseInt(issue.replace("#", ""));
                                    if (!checkedIssue.contains(issue_num)) {
                                        checkedIssue.add(issue_num);


                                        String query = "  INSERT into Fork.PR_TO_ISSUE (repoID, pull_request_id, issue_id) " +
                                                "VALUES (?,?,?)";
                                        preparedStmt = conn.prepareStatement(query);
                                        preparedStmt.setInt(1, RepoID);
                                        preparedStmt.setInt(2, pr_num);
                                        preparedStmt.setInt(3, issue_num);
                                        preparedStmt.execute();
//                                        System.out.println("insert to prTOissue " + issue);

                                    }
                                }
                            }
                        }
                    }
                    else {
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
}
