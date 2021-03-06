package Pull_Request;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by shuruiz on 2/12/18.
 */
public class AnalyzingPRs {

    static String working_dir, output_dir;
    static String myUrl, user, pwd;
    final int batchSize = 200;

    public AnalyzingPRs() {
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
            repos = io.readResult(current_dir + "/input/analyze_prList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            System.out.println(projectUrl);

            ArrayList<String> prList = io.getPRNumlist(projectUrl);
            if (prList.size() == 0) {
                System.out.println(projectUrl + " pr api result not available");
                continue;
            }

            int projectID = io.getRepoId(projectUrl);
//            HashSet<String> analyzedPR = io.getPRinDataBase(projectID);

//            prList.removeAll(analyzedPR);

            if (prList.size() == 0) {
                System.out.println(projectUrl + " pr analysis  done :)");
                continue;
            }
            System.out.println("start : " + projectUrl);
            if (projectID == -1) {
                System.out.println(projectUrl + "projectID = -1");
            }

//            analyzingPRs.insertFork(projectUrl, projectID, prList);
//            analyzingPRs.getPRfiles(projectUrl, projectID, prList);
//            analyzingPRs.getPRTitle(projectUrl, projectID, prList);
            analyzingPRs.calculateHotnessOfPR(projectUrl, projectID, prList);

        }
    }

    private void getPRTitle(String projectUrl, int projectID, ArrayList<String> prList) {
        System.out.println("analyze pr files of " + projectUrl);
        IO_Process io = new IO_Process();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();

        String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
        if (new File(csvFile_dir).exists()) {
            List<List<String>> prs = io.readCSV(csvFile_dir);
            String insert_query_1 = " UPDATE fork.Pull_Request SET title = ? WHERE projectID = ? AND pull_request_ID = ?";
            int count = 0;
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                conn.setAutoCommit(false);

                for (List<String> pr : prs) {
                    if (!pr.get(0).equals("") && pr.size() > 9) {
                        int pr_id = Integer.parseInt(pr.get(9));
                        if (prList.contains(pr_id)) {
                            System.out.println("pass " + pr_id);
                            continue;
                        }


                        String title = pr.get(12);

                        preparedStmt.setString(1, io.normalize(title));
                        preparedStmt.setInt(2, projectID);
                        preparedStmt.setInt(3, pr_id);
                        preparedStmt.addBatch();
                        System.out.println(count + "/ " + prList.size() + " ..." + projectUrl);
                        System.out.println(projectUrl + " PR " + pr_id + "   " + title);

                        if (++count % batchSize == 0) {
                            System.out.println("insert " + count + "pr from repo" + projectUrl);
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    public void insertFork(String projectUrl, int projectID, List<String> prList) {
        System.out.println("analyze pr files of " + projectUrl);
        IO_Process io = new IO_Process();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");

        String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";

        if (new File(csvFile_dir).exists()) {
            List<List<String>> prs = io.readCSV(csvFile_dir);
            int count = 0;
            HashSet<String> existingForks = io.getForkListFromRepoTable(projectID);
            System.out.println(existingForks.size() + " forks in database...");

            String insertProject = "INSERT INTO fork.repository (repoURL_old,isFork,projectID,upstreamID,loginID)" +
                    "VALUES (?,?,?,?,?)";
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insertProject)) {
                conn.setAutoCommit(false);

                for (List<String> pr : prs) {
                    if (!pr.get(0).equals("")) {
                        int pr_id = Integer.parseInt(pr.get(9));
                        if (prList.contains(pr_id)) {
                            System.out.println("pass " + pr_id);
                            continue;
                        }

                        String author = pr.get(1);
                        /** insert fork to database**/
                        String forkURL = pr.get(7);
                        if (existingForks.contains(forkURL) || forkURL.equals("")) {
                            continue;
                        }
                        boolean isFork = true;
                        if (!forkURL.trim().equals("") & !forkURL.equals(projectUrl)) {
                            isFork = true;
                        }

                        preparedStmt.setString(1, forkURL);
                        preparedStmt.setBoolean(2, isFork);
                        preparedStmt.setInt(3, projectID);
                        preparedStmt.setInt(4, projectID);
                        preparedStmt.setString(5, author);
                        preparedStmt.addBatch();

                        if (++count % batchSize == 0) {
                            System.out.println("insert " + count + "repo");
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    }
                }
                System.out.println("insert " + count + "repo ");
                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        }
    }

    public void getPRfiles(String projectUrl, int projectID, List<String> prList) {
        System.out.println("analyze pr files of " + projectUrl);
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
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                conn.setAutoCommit(false);

                for (List<String> pr : prs) {
                    if (!pr.get(0).equals("")) {
                        int pr_id = Integer.parseInt(pr.get(9));
                        if (prList.contains(pr_id)) {
                            System.out.println("pass " + pr_id);
                            continue;
                        }

                        String author = pr.get(1);
                        /** insert fork to database**/
                        String forkURL = pr.get(7);
                        if (forkURL.equals("")) {
                            continue;
                        }
                        int forkID = io.getRepoId(forkURL);

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
                        System.out.println(count + "/ " + prList.size() + " ..." + projectUrl);

                        if (++count % batchSize == 0) {
                            System.out.println("insert " + count + "pr from repo" + projectUrl);
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    public void calculateHotnessOfPR(String projectUrl, int projectID, List<String> prList) {
    }

    public void getPR_withTimeWindow() {
        int timeWindow = 90;

        HashMap<Integer, HashSet<Integer>> project_pr_map = new HashMap<>();
        String query = "SELECT\n" +
                "  pr1.projectID,\n" +
                "  pr1.pull_request_ID AS pr1,\n" +
                "  pr2.pull_request_ID AS pr2\n" +
                "FROM Pull_Request pr1\n" +
                "  INNER JOIN Pull_Request AS pr2\n" +
                "    ON " +
                "pr1.projectID = 6 AND " +
                "pr1.projectID = pr2.projectID AND pr1.closed = 'true' AND pr2.closed = 'true' AND\n" +
                "       pr2.age_in_day BETWEEN pr1.age_in_day + 1 AND pr1.age_in_day + timeWindow;";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            ResultSet rs = preparedStmt.executeQuery();
            int previous_pr = -1;
            HashSet<Integer> old_prSet = new HashSet<>();
            while (rs.next()) {
                int projectID = rs.getInt(1);
                int current_prid = rs.getInt(2);
                if (current_prid == previous_pr) {
                    int old_prid = rs.getInt(3);
                    old_prSet.add(old_prid);

                }else{

                }


            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

}
