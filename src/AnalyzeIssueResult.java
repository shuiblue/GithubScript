import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
                "VALUES(?,?,?,?,?,?,?,?)";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertIssueQuery);) {
            conn1.setAutoCommit(false);
            int count = 0;
            for (String repo : repos) {
                int projectID = io.getRepoId(repo);
                String issue_file = output_dir + "shurui.cache/get_issues." + repo.replace("/", ".") + ".csv";
                List<List<String>> issueList = io.readCSV(issue_file);
                for (List<String> issue : issueList) {
                    String author = issue.get(1);
                    String closed = issue.get(2);
                    String closed_at = issue.get(3);
                    String created_at = issue.get(4);
                    String issue_num = issue.get(5);
                    String title = issue.get(6);
                    String updated_at = issue.get(7);


                    // projectID,
                    preparedStmt_1.setInt(1, projectID);
                    //issue_id,
                    preparedStmt_1.setInt(2, Integer.parseInt(issue_num));

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
                    preparedStmt_1.setString(8, title);
                    preparedStmt_1.addBatch();

                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt_1);
                        conn1.commit();
                    }

                }
                System.out.println("inserting " + issueList.size() + " issue from " + repo);
                io.executeQuery(preparedStmt_1);
                conn1.commit();
            }



        } catch (SQLException e) {
            e.printStackTrace();
        }


    }
}
