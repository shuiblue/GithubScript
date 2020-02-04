package Commit;

import Pull_Request.GetPRfromGHTorrent;
import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GetCommitFromGHTorrent {

    public static void main(String[] args) {
        GetCommitFromGHTorrent getCommitFromGHTorrent = new GetCommitFromGHTorrent();
        String[] forkList = null;
        try {
            forkList = new IO_Process().readResult("./input/hardforkList.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String str : forkList) {
            System.out.println(str);
            String fork = str.split(",")[0];
            String upstream = str.split(",")[1];
//            getCommitFromGHTorrent.getCommitbyAuthor(fork, upstream);
            getCommitFromGHTorrent.getCommitofUpstream(fork, upstream);
        }

    }


    private void getCommitofUpstream( String fork,String upstream) {
        IO_Process io = new IO_Process();

        HashMap<Integer, List<String>> prHistory = new HashMap<>();
        String query = "select count(*)\n" +
                "FROM `ghtorrent-2018-03`.commits c\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.projects p on c.project_id = p.id\n" +
                "WHERE p.url = 'https://api.github.com/repos/"+upstream+"';";

        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);

            try {
                int upstreamCommits=0;
                ResultSet rs = preparedStmt.executeQuery();
                if (rs.next()) {
                    upstreamCommits = rs.getInt(1);
                }


                System.out.println(upstreamCommits);
                String updatePR = "UPDATE fork.HardFork_withCriteria\n" +
                        "    SET upstream_commitTotal=?\n" +
                        "WHERE hardfork_url=?";
                try (Connection conn1 = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
                     PreparedStatement preparedStmt_updatePR = conn1.prepareStatement(updatePR);) {
                    conn1.setAutoCommit(false);
                    preparedStmt_updatePR.setInt(1, upstreamCommits);
                    preparedStmt_updatePR.setString(2, fork);
                    preparedStmt_updatePR.executeUpdate();
                    conn1.commit();

                } catch (
                        SQLException e)

                {
                    e.printStackTrace();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void getCommitbyAuthor(String fork, String upstream) {
        IO_Process io = new IO_Process();

        HashMap<Integer, List<String>> prHistory = new HashMap<>();
        String query = "select count(*)\n" +
                "FROM `ghtorrent-2018-03`.commits c\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.projects p on c.project_id = p.id\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.users u on c.committer_id=u.id\n" +
                "WHERE p.url = 'https://api.github.com/repos/"+upstream+"'\n" +
                "      and u.login= '"+fork.split("/")[0]+"';";

        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);

            try {
                int commitsByFork=0;
                ResultSet rs = preparedStmt.executeQuery();
                if (rs.next()) {
                    commitsByFork = rs.getInt(1);
                }


                System.out.println(commitsByFork);
                String updatePR = "UPDATE fork.HardFork_withCriteria\n" +
                        "    SET mergedCommit_byGHT=?\n" +
                        "WHERE hardfork_url=?";
                try (Connection conn1 = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
                     PreparedStatement preparedStmt_updatePR = conn1.prepareStatement(updatePR);) {
                    conn1.setAutoCommit(false);
                    preparedStmt_updatePR.setInt(1, commitsByFork);
                    preparedStmt_updatePR.setString(2, fork);
                    preparedStmt_updatePR.executeUpdate();
                    conn1.commit();

                } catch (
                        SQLException e)

                {
                    e.printStackTrace();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
