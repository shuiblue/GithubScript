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
    final int batchSize = 5000;

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

        while (true) {
            /*** insert pr info to  Pull_Request table***/
            for (String projectUrl : repos) {
                int projectID = io.getRepoId(projectUrl);
                System.out.println(projectUrl);

                ArrayList<String> prList = io.getPRNumlist(projectUrl);

                if (prList.size() == 0) {
                    System.out.println(projectUrl + " pr api result not available");
                    io.writeTofile(projectUrl + "\n", output_dir + "miss_pr_api.txt");
                    continue;
                } else {
                    System.out.println(prList.size() + " pr from " + projectUrl);
                }

                HashSet<String> analyzedPR = io.prList_ExistIN_PR_commitMap(projectID);
                System.out.println("pr list size: " + prList.size());
                prList.removeAll(analyzedPR);
                System.out.println("after removing skiped pr, now prlist size: " + prList.size());

                int csvFileExist = -1;

                if (csvFileExist == -1) {
                    String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + prList.get(0) + ".csv";
                    csvFileExist = new File(csvFile_dir).exists() ? 1 : 0;
                }
                if (csvFileExist != -1) {
                    ArrayList<String> pr_commit_map = analyzingPRs.getCommitsInPR(projectUrl, projectID, prList, csvFileExist);
                    analyzingPRs.insertMap_Commits_PR(pr_commit_map);
                }

            }
        }
    }


    public ArrayList<String> getCommitsInPR(String projectUrl, int projectID, ArrayList<String> prList, int csvFileExist) {
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();
        ArrayList<String> pr_commit_map = new ArrayList<>();

        HashSet<String> commits_existinDB = io.getExistCommits(projectID);


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
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             Connection conn3 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_updatePR = conn3.prepareStatement(updatePR);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(update_commit_query);) {
            conn1.setAutoCommit(false);
            int count = 0;


            for (String pr_id_string : prList) {
                int pr_id = Integer.valueOf(pr_id_string);
                String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
                List<List<String>> commits;
                if (csvFileExist == 1) {
                    commits = io.readCSV(csvFile_dir);
                } else {
                    String csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
                    commits = io.readCSV(csvFile_dir_alternative);
                }
                preparedStmt_updatePR.setInt(1, commits.size());
                preparedStmt_updatePR.setInt(2, pr_id);
                preparedStmt_updatePR.setInt(3, projectID);
                System.out.println(preparedStmt_updatePR.executeUpdate() + " rows updated num_commits in PR" + pr_id);

                if (commits.size() < 50 & commits.size() > 0) {
                    for (List<String> line : commits) {
                        if (!line.get(0).equals("")) {

                            String sha = line.get(8);
                            if (!commits_existinDB.contains(sha)) {
                                pr_commit_map.add(projectID + "," + pr_id + "," + sha);

                                //commitSHA
                                preparedStmt_1.setString(1, sha);
                                // loginID,
                                preparedStmt_1.setString(2, line.get(1));
                                //author_name
                                preparedStmt_1.setString(3, io.normalize(line.get(3)));
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
                                System.out.print(" add 1 commit... COUNT " + count + "\n");

                                if (++count % batchSize == 0) {
                                    System.out.println(count + " add batch to db... of repo "+projectUrl);
                                    io.executeQuery(preparedStmt_1);
                                    conn1.commit();
                                }
                            } else {
                                continue;
                            }
                        }
                    }
                } else {
                    System.out.println("skip pr , too big..");
                }

            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pr_commit_map;
    }


    public void insertMap_Commits_PR(ArrayList<String> pr_commit_map) {
        IO_Process io = new IO_Process();
        String update_pr_commit_query = " INSERT INTO fork.PR_Commit_map (commit_uuid,projectID,pull_request_id)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c ) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.PR_Commit_map AS cc" +
                "      WHERE cc.commit_uuid = ?" +
                "      AND cc.projectID = ?" +
                "      AND cc.pull_request_id = ?" +
                "  )" +
                "  LIMIT 1";

        try (Connection conn2 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_2 = conn2.prepareStatement(update_pr_commit_query)) {
            conn2.setAutoCommit(false);

            int count = 0;
            for (String map : pr_commit_map) {
                String[] arr = map.split(",");
                int projectID = Integer.parseInt(arr[0]);
                int pr_id = Integer.parseInt(arr[1]);
                /** put commit into commit_TABLE**/
                String sha = arr[2];
                //commitsha_id,
                String commitID = io.getCommitID(sha);
                if (commitID.equals("")) {
                    System.out.println("project id " + projectID + " commit is empty : " + sha);
                    io.writeTofile(projectID + "," + sha + "\n", output_dir + "commitNotINDB.txt");
                    continue;
                }
                preparedStmt_2.setString(1, commitID);
                // projectID,
                preparedStmt_2.setInt(2, projectID);
                // pull_request_id
                preparedStmt_2.setInt(3, pr_id);
                //commitsha_id,
                preparedStmt_2.setString(4, commitID);
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
            io.executeQuery(preparedStmt_2);
            conn2.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

}