package misc;

import Pull_Request.AnalyzedPRHotness;
import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class InsertForkConact {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    static IO_Process io = new IO_Process();

    InsertForkConact() {
        current_dir = System.getProperty("user.dir");
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
    //todo


    public void updateNOTFORK() {
        int timeWwindowInDays = 90;
        InsertForkConact insertForkConact = new InsertForkConact();
        IO_Process io = new IO_Process();
        String[] repolist = null;

        try {
            repolist = io.readResult(output_dir + "hardfork-exploration/inputData/NOT_FORK.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        final int[] count_db = {0};
        String query = "UPDATE fork.HardFork_withCriteria \n" +
                "SET isFork=FALSE\n" +
                "WHERE hardfork_url = ?";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            conn.setAutoCommit(false);
            for (String repo : repolist) {
                preparedStmt.setString(1, repo);
                preparedStmt.addBatch();
                if (++count_db[0] % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    try {
                        conn.commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    System.out.println(count_db[0] + " count ");

                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }



    public void updateContact() {
        int timeWwindowInDays = 90;
        InsertForkConact insertForkConact = new InsertForkConact();
        IO_Process io = new IO_Process();
        String[] repolist = null;

        try {
            repolist = io.readResult(output_dir + "hardfork-exploration/inputData/contactInfo.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        final int[] count_db = {0};
        String query = "update fork.HardFork_withCriteria\n" +
                "    set hardfork_email=? ,hardfork_owner_name=?,fork_userType=?," +
                " upstream_email=?, upstream_owner_name=?, upstream_userType =? \n" +
                "      WHERE hardfork_url = ?";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            conn.setAutoCommit(false);
            for (String repo : repolist) {
                System.out.println(repo);
                String[] arr = repo.split(",");
                String forkURL = arr[0].equals("NULL")?"":arr[0];
                String forkEmail = arr[1].equals("NULL")?"":arr[1];
                String fork_username = arr[2].equals("NULL")?"":arr[2];
                String fork_usertype = arr[3].equals("NULL")?"":arr[3];
                String upstreamEmail = arr[4].equals("NULL")?"":arr[4];
                String upstream_username = arr[5].equals("NULL")?"":arr[5];
                String upstream_usertype = arr[6].equals("NULL")?"":arr[6];

                preparedStmt.setString(1, forkEmail);
                preparedStmt.setString(2, fork_username);
                preparedStmt.setString(3, fork_usertype);
                preparedStmt.setString(4, upstreamEmail);
                preparedStmt.setString(5, upstream_username);
                preparedStmt.setString(6, upstream_usertype);
                preparedStmt.setString(7, forkURL);
                preparedStmt.addBatch();
                if (++count_db[0] % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    try {
                        conn.commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    System.out.println(count_db[0] + " count ");

                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
    public static void main(String[] args) {

        new InsertForkConact().updateContact();

    }

}
