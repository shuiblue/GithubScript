package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class analyzeForkPR {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir;
    static String myUrl, user, pwd;

    analyzeForkPR() {
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
        new analyzeForkPR();

//        calculatePR();
//
        insertDB();

    }

    private static void insertDB() {

        IO_Process io = new IO_Process();
        String[] arr = {};
        try {
            arr = io.readResult(output_dir + "hardFork_PR.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "UPDATE repository_contribution " +
                " SET num_external_contributor  = ? ,num_external_PR = ?, num_all_PR = ? " +
                " WHERE repoURL = ?";
        int count = 0;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);


            for (String line : arr) {
                String[] info = line.split(",");
                String repoURL = info[0];
                int num_external_contributor = Integer.parseInt(info[1]);
                int num_external_PR = Integer.parseInt(info[2]);
                int num_all_PR = Integer.parseInt(info[3]);
                preparedStmt.setInt(1, num_external_contributor);
                preparedStmt.setInt(2, num_external_PR);
                preparedStmt.setInt(3, num_all_PR);
                preparedStmt.setString(4, repoURL);

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

    private static void calculatePR() {
        IO_Process io = new IO_Process();
        List<String> sampledForks = io.getListFromFile("sampledForks.csv");

        for (String fork : sampledForks) {
            String forkAuthor = fork.split("/")[0];
            String file = output_dir + "shurui_timeline.cache/get_prs." + fork.replace("/", ".") + ".csv";
            System.out.println(file);
            List<List<String>> prList = io.readCSV(file);

            HashMap<String, Integer> author_count = new HashMap<>();
            if (prList.size() == 0) {
                System.out.println("no pr " + fork);
                continue;
            }
            for (int i = 1; i < prList.size(); i++) {
                List<String> pr = prList.get(i);
                String pr_author = pr.get(1);
                int count = 0;
                if (author_count.get(pr_author) != null) {
                    count = author_count.get(pr_author);
                }
                author_count.put(pr_author, count + 1);
            }

            int self_submitPR = 0;
            int externalContributor = author_count.keySet().size();
            int externalPR_count = prList.size() - 1;
            if (author_count.keySet().contains(forkAuthor)) {
                self_submitPR = author_count.get(forkAuthor);
                externalContributor = externalPR_count - 1;
                externalPR_count = externalPR_count - self_submitPR;
            }

            io.writeTofile(fork + "," + externalContributor + "," + externalPR_count + "," + (prList.size() - 1) + "\n", output_dir + "hardFork_PR.txt");
            System.out.println(fork + "," + externalContributor + "," + externalPR_count + "\n");
        }
    }
}
