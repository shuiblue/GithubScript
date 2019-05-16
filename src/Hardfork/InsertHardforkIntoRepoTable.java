package Hardfork;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class InsertHardforkIntoRepoTable {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir, hardfork_dir, checkedCandidates, graph_dir;
    static String myUrl, user, pwd;

    InsertHardforkIntoRepoTable() {
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
            hardfork_dir = output_dir + "/hardfork-exploration/";
            checkedCandidates = hardfork_dir + "checked_repo_pairs.txt";
            graph_dir = output_dir + "ClassifyCommit_new/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        InsertHardforkIntoRepoTable insertHardforkIntoRepoTable = new InsertHardforkIntoRepoTable();
        //list all the hard fork url
        String query = "SELECT hardfork_url,upstream_url FROM fork.HardFork";
        Set<String> urlSet = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
            System.out.println(query);
            while (rs.next()) {
                urlSet.add(rs.getString("hardfork_url"));
                urlSet.add(rs.getString("upstream_url"));

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // insert urls into repository table
        insertHardforkIntoRepoTable.insertRepoByURL(urlSet);

    }


    public void insertRepoByURL(Set<String> urlSet) {
        int batchSize = 100;
        IO_Process io = new IO_Process();

        String insertForkQuery = "INSERT IGNORE INTO fork.repository (repoURL) VALUES (?)";

        for (String repo : urlSet) {
            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertForkQuery);) {
                conn1.setAutoCommit(false);

                int count = 0;

                preparedStmt_1.setString(1, repo);

                preparedStmt_1.addBatch();
                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt_1);
                    conn1.commit();
                }
                io.executeQuery(preparedStmt_1);
                conn1.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        }
    }

}

