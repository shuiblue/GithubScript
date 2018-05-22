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

        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            int projectID = io.getRepoId(projectUrl);
            System.out.println(projectUrl);
            ArrayList<String> prList = io.getPRNumlist(projectUrl);
            if (prList.size() == 0) {
                System.out.println(projectUrl + " pr api result not available");
                io.writeTofile(projectUrl + "\n", output_dir + "miss_pr_api.txt");
                continue;
            }


            for (String pr_id : prList) {
                String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
                String csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
                boolean csvFileExist = new File(csvFile_dir).exists();
                boolean csvFileAlter_Exist = new File(csvFile_dir_alternative).exists();

                if (csvFileAlter_Exist || csvFileExist) {
                    System.out.println("pr# " + pr_id);
                    ArrayList<String> commitList = analyzingPRs.getCommitsInPR(projectUrl, projectID, Integer.parseInt(pr_id), csvFileExist, csvFileAlter_Exist);
                    analyzingPRs.insertMap_Commits_PR(projectID, Integer.parseInt(pr_id), commitList);

                } else {
                    System.out.println("pr#" + pr_id + " csv not available.");
                    io.writeTofile(pr_id + "," + projectUrl + "\n", output_dir + "missPR_" + projectUrl + ".txt");
                    break;
                }
            }
        }
    }


    public ArrayList<String> getCommitsInPR(String projectUrl, int projectID, int pr_id, boolean csvFileExist, boolean csvFileAlter_Exist) {
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();
        ArrayList<String> commitList = new ArrayList<>();

        String update_commit_query = " INSERT INTO fork.Commit  (commitSHA,loginID,author_name,email, projectID, data_update_at,created_at)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c, ? AS d,? AS c1, ? AS d1, ? AS d2) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT commitSHA" +
                "      FROM fork.Commit AS cc" +
                "      WHERE cc.commitSHA = ?" +
                "  )" +
                "  LIMIT 1";


        String updatePR = "UPDATE fork.Pull_Request SET num_commit = ? WHERE pull_request_ID = ? AND projectID = ?";

        String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
        String csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";

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

                /**   get commits exist in DATABASE***/
                HashSet<String> existCommitInPRCmap = io.getExistCommits(projectID, pr_id);

                for (List<String> line : commits) {
                    if (!line.get(0).equals("")) {
                        /** put commit into commit_TABLE**/
                        int count = 0;
                        String sha = line.get(8);

                        int commit_id = io.getCommitID(sha);
                        if (commit_id == -1) {
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
                            preparedStmt_1.setString(7, line.get(4));
                            preparedStmt_1.setString(8, line.get(8));
                            preparedStmt_1.addBatch();
                        }else{
                            System.out.println("commit "+sha + "exist :)");
                        }

                        if(!existCommitInPRCmap.contains(sha)){
                            commitList.add(sha);
                        }

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_1);
                            conn1.commit();
                        }
                    }
                    System.out.println("inserting " + commits.size() + " commits from " + projectUrl + " , pr " + pr_id);
                    io.executeQuery(preparedStmt_1);
                    conn1.commit();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return commitList;
    }

    public void insertMap_Commits_PR(int projectID, int pr_id, ArrayList<String> commitList) {
        IO_Process io = new IO_Process();

        String update_pr_commit_query = " INSERT INTO fork.PR_Commit_map (commitsha_id,projectID,pull_request_id)" +
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


        try (
                Connection conn2 = DriverManager.getConnection(myUrl, user, pwd);
                PreparedStatement preparedStmt_2 = conn2.prepareStatement(update_pr_commit_query)) {
            conn2.setAutoCommit(false);

            int count = 0;
            for (String sha : commitList) {
                //commitsha_id,
                int commitID = io.getCommitID(sha);
                if (commitID == -1) {
                    System.out.println("project id " + projectID + " commit is -1 : " + sha);
                    io.writeTofile(projectID + "," + sha + "\n", output_dir + "commitNotINDB.txt");
                }
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
            System.out.println("inserting " + commitList.size() + " commits into database");
            io.executeQuery(preparedStmt_2);
            conn2.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

}