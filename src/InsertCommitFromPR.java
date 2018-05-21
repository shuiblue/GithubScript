import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/12/18.
 */
public class InsertCommitFromPR {

    static String working_dir, pr_dir, output_dir, clone_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;

    InsertCommitFromPR() {
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
        IO_Process io = new IO_Process();
        InsertCommitFromPR analyzingPRs = new InsertCommitFromPR();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, Integer> finished_repos = new HashMap<>();
        try {
            String[] result = io.readResult(output_dir + "AnalyzePR/finish_PR_commit_analysis.txt").split("\n");
            for (String s : result) {
                if (!s.equals("")) {
                    String[] arr = s.split(",");
                    finished_repos.put(arr[0], Integer.valueOf(arr[1]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            int startPR = -1;
            if (finished_repos.size() > 0) {
                startPR = finished_repos.get(projectUrl);
            }

            String prNumList_filePath = output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt";
            List<String> prList = new ArrayList<>();
            int latestPRid = -1;
            if (new File(prNumList_filePath).exists()) {
                String prListString = "";
                try {
                    prListString = io.readResult(prNumList_filePath);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!prListString.trim().equals("")) {
                    prList = Arrays.asList(prListString.split("\n"));
                    String lastPR = prList.get(prList.size() - 1);
                    latestPRid = Integer.parseInt(lastPR);
                }
            }

            if (startPR <= latestPRid || !finished_repos.containsKey(projectUrl)) {
                System.out.println(projectUrl);
                int projectID = io.getRepoId(projectUrl);



                String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
                if (new File(csvFile_dir).exists()) {
                    System.out.println(projectUrl + " pr not exsit");
                    break;
                }
                int startIndex = 0;
                if (finished_repos.containsKey(projectUrl)) startIndex = prList.indexOf(startPR);
                System.out.println("start with project :" + projectUrl + " pr#: " + startPR + " index: " + startIndex);
                for (int i = startIndex; i < prList.size(); i++) {
                    String pr_id_str = prList.get(i);
                    int pr_id = Integer.parseInt(pr_id_str);
                    System.out.println(pr_id);
                    boolean fileExist = analyzingPRs.getCommitsInPR(projectUrl, projectID, pr_id);
                    if (fileExist) {
                        analyzingPRs.insertMap_Commits_PR(projectUrl, projectID, pr_id);
                    } else {
                        io.writeTofile(projectUrl + "," + pr_id + "\n", output_dir + "AnalyzePR/finish_PR_commit_analysis.txt");
                        return;
                    }
                }
                io.writeTofile(projectUrl + "," + latestPRid + "\n", output_dir + "AnalyzePR/finish_PR_commit_analysis.txt");
            } else {
                io.rewriteFile(projectUrl + "," + latestPRid + "\n", output_dir + "AnalyzePR/finish_PR_commit_analysis.txt");
            }

        }
    }
















    public boolean getCommitsInPR(String projectUrl, int projectID, int pr_id) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();
//        String cmd_getPR = "ls get_prs." + projectUrl.replace("/", ".") + "*";

        String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
        String csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
        String update_commit_query = " INSERT INTO fork.Commit  (commitSHA,loginID,author_name,email, projectID, data_update_at)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c, ? AS d,? AS c1, ? AS d1) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT commitSHA" +
                "      FROM fork.Commit AS cc" +
                "      WHERE cc.commitSHA = ?" +
                "  )" +
                "  LIMIT 1";


        String updatePR = "UPDATE fork.Pull_Request SET num_commit = ? WHERE pull_request_ID = ? AND projectID = ?";

        boolean csvFileExist = new File(csvFile_dir).exists();
        boolean csvFileAlter_Exist = new File(csvFile_dir_alternative).exists();


        if (csvFileAlter_Exist || csvFileExist) {

            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                 Connection conn3 = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt_updatePR = conn3.prepareStatement(updatePR);
                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(update_commit_query);) {
                conn1.setAutoCommit(false);
                List<List<String>> commits;
                if (csvFileExist) {
                    commits = io.readCSV(csvFile_dir);
                } else {
                    commits = io.readCSV(csvFile_dir_alternative);
                }

                preparedStmt_updatePR.setInt(1, commits.size());
                preparedStmt_updatePR.setInt(2, pr_id);
                preparedStmt_updatePR.setInt(3, projectID);
                System.out.println(preparedStmt_updatePR.executeUpdate() + " rows updated num_commits in PR" + pr_id);

                for (List<String> line : commits) {
                    if (!line.get(0).equals("")) {
                        /** put commit into commit_TABLE**/
                        int count = 0;
                        String sha = line.get(8);
                        //commitSHA
                        preparedStmt_1.setString(1, sha);
                        // loginID,
                        preparedStmt_1.setString(2, line.get(1));
                        //author_name
                        preparedStmt_1.setString(3, line.get(3));
                        //,email,
                        preparedStmt_1.setString(4, line.get(2));
                        // projectID,
                        preparedStmt_1.setInt(5, projectID);
                        //data_update_at
                        preparedStmt_1.setString(6, String.valueOf(now));
                        //sha
                        preparedStmt_1.setString(7, line.get(8));
                        preparedStmt_1.addBatch();


                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_1);
                            conn1.commit();
                        }
                    }
                    System.out.println("inserting " + commits.size() + " commits into database");
                    io.executeQuery(preparedStmt_1);
                    conn1.commit();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return true;
        } else {
            return false;
        }

    }

    public void insertMap_Commits_PR(String projectUrl, int projectID, int pr_id) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();
//        String cmd_getPR = "ls get_prs." + projectUrl.replace("/", ".") + "*";

        String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";

        String update_pr_commit_query = " INSERT INTO fork.PR_Commit (commitsha_id,projectID,pull_request_id)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c ) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.PR_Commit AS cc" +
                "      WHERE cc.commitsha_id = ?" +
                "      AND cc.projectID = ?" +
                "      AND cc.pull_request_id = ?" +
                "  )" +
                "  LIMIT 1";


        if (new File(csvFile_dir).exists()) {
            try (
                    Connection conn2 = DriverManager.getConnection(myUrl, user, pwd);
                    PreparedStatement preparedStmt_2 = conn2.prepareStatement(update_pr_commit_query)) {
                conn2.setAutoCommit(false);
                List<List<String>> commits = io.readCSV(csvFile_dir);


                for (List<String> line : commits) {
                    if (!line.get(0).equals("")) {
                        /** put commit into commit_TABLE**/
                        int count = 0;
                        String sha = line.get(8);


                        //commitsha_id,
                        int commitID = io.getCommitID(sha);
                        preparedStmt_2.setInt(1, commitID);
                        // projectID,
                        preparedStmt_2.setInt(2, projectID);
                        // pull_request_id
                        preparedStmt_2.setInt(3, pr_id);
                        //commitsha_id,
                        preparedStmt_2.setInt(4, commitID);
                        // projectID,
                        preparedStmt_2.setInt(5, projectID);
                        // pull_request_id
                        preparedStmt_2.setInt(6, pr_id);
                        preparedStmt_2.addBatch();

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_2);
                            conn2.commit();
                        }
                    }
                    System.out.println("inserting " + commits.size() + " commits into database");
                    io.executeQuery(preparedStmt_2);
                    conn2.commit();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

        }

    }

}
