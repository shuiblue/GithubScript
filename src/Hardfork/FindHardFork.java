package Hardfork;

import Commit.GraphBasedAnalyzer;
import Pull_Request.AnalyzingPRs;
import Pull_Request.GetDupPR_pair;
import Pull_Request.Merge_PR_Status.GetMergedPR;
import Util.IO_Process;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class FindHardFork {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir;
    static String myUrl, user, pwd;

    FindHardFork() {
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

    static public void main(String[] args) {
        IO_Process io = new IO_Process();
        FindHardFork findHardFork = new FindHardFork();

//        findHardFork.getNameChangeForks();
//        findHardFork.insertResultToDB();


        LineIterator it = null;
        try {
            it = FileUtils.lineIterator(new File("/Users/shuruiz/Work/ForkData/hardfork-exploration/hardfork.csv"), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (it.hasNext()) {
                String line = it.nextLine();
                if(!line.equals("url\turl")) {
                    String[] arr = line.split("\t");
                    String repo1 = arr[0].replace("https://api.github.com/repos/","");
                    String repo2 = arr[1].replace("https://api.github.com/repos/","");
                    findHardFork.getCommitDiffForTwoRepos(repo1, repo2);
                }
            }
        } finally {
            LineIterator.closeQuietly(it);
        }


    }


    private void insertResultToDB() {
        IO_Process io = new IO_Process();
        String[] arr = {};
        try {
            arr = io.readResult(output_dir + "hardfork_cand_2.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "UPDATE repository_contribution SET levDistance_biggerThan_2 = ? WHERE repoURL = ?";
        int count = 0;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);


            for (String line : arr) {
                String[] info = line.split(",");
                String repoURL = info[1];
                preparedStmt.setBoolean(1, true);
                preparedStmt.setString(2, repoURL);

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

    private void getNameChangeForks() {


        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        String query = "SELECT repoURL,upstreamURL,projectID,update_projectURL " +
                "FROM repository_contribution \n" +
                "WHERE update_projectURL IS NOT NULL ";
        IO_Process io = new IO_Process();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String repoURL = rs.getString(1);
                String repo = repoURL.split("/")[1];
                String upstreamURL = rs.getString(2);
                String upstream = upstreamURL.split("/")[1];
                String update_projectURL = rs.getString(4);
                String project = update_projectURL.split("/")[1];
                int projectID = rs.getInt(3);

                if (levenshteinDistance.distance(repo, project) > 2) {
//                if (levenshteinDistance.distance(repo, upstream) > 3) {
                    System.out.println(projectID + "," + repo + "," + upstream);
                    io.writeTofile(projectID + "," + repoURL + "," + upstreamURL + "\n", output_dir + "hardfork_cand_2.txt");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void getCommitDiffForTwoRepos(String repo1, String repo2) {
        IO_Process io = new IO_Process();
        io.cloneRepo(repo1, repo2);

        new GraphBasedAnalyzer().analyzeCommitHistory(repo1, repo2, false);

    }
}
