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
public class AnalyzingPRs {

    static String working_dir, output_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    AnalyzingPRs() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) throws IOException {
        IO_Process io = new IO_Process();
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        String missPRFile = "miss_pr_api_pr2.txt";
        while (!io.readResult(output_dir + missPRFile).trim().equals("")) {
            System.out.println("new loop .....");
            io.rewriteFile("", output_dir + missPRFile);

            /*** insert pr info to  Pull_Request table***/
            for (String projectUrl : repos) {
                System.out.println(projectUrl);

                ArrayList<String> prList = io.getPRNumlist(projectUrl);
                if (prList.size() == 0) {
                    System.out.println(projectUrl + " pr api result not available");
                    io.writeTofile(projectUrl + "\n", output_dir + missPRFile);
                    continue;
                }

                int projectID = io.getRepoId(projectUrl);
                HashSet<String> analyzedPR = io.getPRinDataBase(projectID);

                prList.removeAll(analyzedPR);

                if (prList.size() == 0) {
                    System.out.println(projectUrl + " pr analysis  done :)");
                    continue;
                }
                System.out.println("start : " + projectUrl);
                analyzingPRs.getPRfiles(projectUrl, projectID, prList);
            }
        }
    }

    public void getPRfiles(String projectUrl, int projectID, List<String> prList) {
        System.out.println("analyze pr files of " + projectUrl);
        AnalyzeRepository analyzeRepository = new AnalyzeRepository();
        IO_Process io = new IO_Process();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();

        String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
        if (new File(csvFile_dir).exists()) {
            List<List<String>> prs = io.readCSV(csvFile_dir);
            String insert_query_1 = " INSERT INTO fork.Pull_Request( " +
                    "projectID,pull_request_ID, authorName,forkID," +
                    "created_at, closed, closed_at, merged, data_update_at,labels,dupPR_label) " +
                    " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d, ?AS ds,?AS de,?AS d3,?AS d2, ? AS d5,? AS label,? AS labelDup) AS tmp" +
                    " WHERE NOT EXISTS (" +
                    " SELECT projectID FROM Pull_Request WHERE projectID = ? AND pull_request_ID = ?" +
                    ") LIMIT 1";
            int count = 0;
            HashSet<String> existingForks = new HashSet<>();
            for (List<String> pr : prs) {
                if (!pr.get(0).equals("")) {
                    System.out.println();
                    long start = System.nanoTime();
                    try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                         PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                        conn.setAutoCommit(false);
                        int pr_id = Integer.parseInt(pr.get(9));
                        if (prList.contains(pr_id)) {
                            System.out.println("pass " + pr_id);
                            continue;
                        }

                        String author = pr.get(1);

                        /** insert fork to database**/
                        String forkURL = pr.get(7);
                        int forkID = io.getRepoId(forkURL);
                        if (!existingForks.contains(forkURL) || forkID == -1) {
                            io.insertRepo(forkURL);
//                            GithubRepository fork = new GithubRepository().getRepoInfo(forkURL, projectUrl);
//                            analyzeRepository.updateRepoInfo(fork);
                            forkID = io.getRepoId(forkURL);
                        } else {
                            System.out.println(forkURL + "already in database :)");
                        }

                        if (forkID == -1) {
                            System.out.println("fork: " + forkURL + " id is -1");
                        }
                        existingForks.add(forkURL);


                        String created_at = pr.get(6);

                        String closed_at = pr.get(5);
                        String closed = closed_at.equals("") ? "false" : "true";
                        String merged_at = pr.get(11);
                        String merged = merged_at.equals("") ? "false" : "true";

                        String labels = pr.get(10);

                        //projectID
                        preparedStmt.setInt(1, projectID);
                        //pull_request_ID
                        preparedStmt.setInt(2, pr_id);
                        //, authorName,
                        preparedStmt.setString(3, author);
                        //forkID," +
                        preparedStmt.setInt(4, forkID);
                        // created_at,
                        preparedStmt.setString(5, created_at);
                        // closed,
                        preparedStmt.setString(6, closed);
                        // closed_at,
                        preparedStmt.setString(7, closed_at);
                        // merged,
                        preparedStmt.setString(8, merged);
                        //data_update_at
                        preparedStmt.setString(9, String.valueOf(now));
                        //,labels
                        preparedStmt.setString(10, labels);

                        boolean dup_pr = false;
                        if (labels.toLowerCase().contains("duplicate")) {
                            dup_pr = true;
                        }
                        preparedStmt.setBoolean(11, dup_pr);
                        preparedStmt.setInt(12, projectID);
                        preparedStmt.setInt(13, pr_id);
                        preparedStmt.addBatch();

                        long end = System.nanoTime();
                        System.out.println("insert ONE PR :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }

                        io.executeQuery(preparedStmt);
                        conn.commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                }
            }
        }
    }


}
