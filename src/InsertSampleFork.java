import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;

public class InsertSampleFork {
    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath, resultDirPath;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    InsertSampleFork() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            historyDirPath = output_dir + "commitHistory/";
            resultDirPath = output_dir + "result/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        InsertSampleFork insertSampleFork = new InsertSampleFork();
        insertSampleFork.insertActiveForkList();

    }

    private void insertActiveForkList() {
        String[] repoList = {};
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");

        try {
            repoList = io.readResult(current_dir + "/input/activeFork_repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
        String insertForkQuery = "INSERT INTO fork.repository (repoURL,projectID,isFork,upstreamID) VALUES (?,?,?,?)";

        for (String repo : repoList) {

            int projectID = io.getRepoId(repo);
            HashSet<String> existingForks = io.getForkListFromRepoTable(projectID);

            String allActiveForkListPath = resultDirPath + repo + "/all_ActiveForklist.txt";
            if (new File(allActiveForkListPath).exists()) {
                String[] activeFork_arr = new String[0];
                try {
                    activeFork_arr = io.readResult(allActiveForkListPath).split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println(activeFork_arr.length + " activeforks in " + repo);


                try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                     PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertForkQuery);) {
                    conn1.setAutoCommit(false);

                    int count = 0;
                    for (String line : activeFork_arr) {
                        String[] arr = line.split(",");
                        String forkName = arr[0];

                        if (!existingForks.contains(forkName)) {
                            int forkId = io.getRepoId(forkName);
                            if (forkId == -1) {
                                System.out.println("fork is not in the database, inserting fork " + forkName);
                                String upstreamName = arr[1];
                                int upstreamID = projectID;
                                if (!upstreamName.equals(repo)) {
                                    upstreamID = io.getRepoId(upstreamName);
                                }

                                preparedStmt_1.setString(1, forkName);
                                preparedStmt_1.setInt(2, projectID);
                                preparedStmt_1.setBoolean(3, true);
                                preparedStmt_1.setInt(4, upstreamID);

                                preparedStmt_1.addBatch();
                                if (++count % batchSize == 0) {
                                    io.executeQuery(preparedStmt_1);
                                    conn1.commit();
                                }
                            } else {
                                System.out.println("FORK " + forkName + " exist :) skip");
                            }
                        }
                    }
                    System.out.println("inserting " + activeFork_arr.length + " forks from " + repo);
                    io.executeQuery(preparedStmt_1);
                    conn1.commit();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

            }
        }
    }
}
