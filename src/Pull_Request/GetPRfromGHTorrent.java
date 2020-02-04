package Pull_Request;

import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GetPRfromGHTorrent {
    IO_Process io = new IO_Process();

    //    public void insertPRcount(String fork, int pr_total, int pr_merged, int pr_closed, int pr_opened) {
    public void insertPRcount(String fork, int[] pr_count_array) {

        String updatePR = "UPDATE fork.HardFork_withCriteria\n" +
                "    SET submittedPR = ?, mergedPR = ?, closedPR=?, openedPR=?\n" +
                "WHERE hardfork_url=?";
        try (Connection conn1 = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
             PreparedStatement preparedStmt_updatePR = conn1.prepareStatement(updatePR);) {
            conn1.setAutoCommit(false);
            preparedStmt_updatePR.setInt(1, pr_count_array[0]);
            preparedStmt_updatePR.setInt(2, pr_count_array[1]);
            preparedStmt_updatePR.setInt(3, pr_count_array[2]);
            preparedStmt_updatePR.setInt(4, pr_count_array[3]);
            preparedStmt_updatePR.setString(5, fork);
            preparedStmt_updatePR.executeUpdate();
//            io.executeQuery(preparedStmt_updatePR);
//            System.out.println(preparedStmt_updatePR.);

            conn1.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public void getPRbyAuthor(String fork, String upstream) {
        IO_Process io = new IO_Process();

        HashMap<Integer, List<String>> prHistory = new HashMap<>();
        String query = "SELECT\n" +
                "  pullreq_id,\n" +
                "  prh.action\n" +
                "FROM `ghtorrent-2018-03`.projects fork\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.pull_requests pr ON pr.head_repo_id = fork.id\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.projects upstream on pr.base_repo_id = upstream.id\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.pull_request_history prh ON pr.id = prh.pull_request_id\n" +
                "WHERE fork.url = 'https://api.github.com/repos/" + fork + "'\n" +
                "      and  upstream.url = 'https://api.github.com/repos/" + upstream + "'\n" +
                "      AND pullreq_id IS NOT NULL;";

        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);

            try {

                String state;
                int prID;
                ResultSet rs = preparedStmt.executeQuery();
                int pr_count = 0;

                while (rs.next()) {
                    pr_count++;
                    prID = rs.getInt(1);
                    state = rs.getString(2);

                    List<String> prActionList = new ArrayList<>();
                    if (prHistory.get(prID) != null) {

                        prActionList = prHistory.get(prID);
                    }
                    prActionList.add(state);
                    prHistory.put(prID, prActionList);


                }

                int[] result = {0, 0, 0, 0};
                if (pr_count > 0) {
                    result = analyzePRState(prHistory);
                }
                insertPRcount(fork, result);
// conn.commit();
                System.out.println("");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private int[] analyzePRState(HashMap<Integer, List<String>> prHistory) {
        final int[] pr_merged_count = {0};
        final int[] pr_closed_count = {0};
        final int[] pr_opened_count = {0};
        int[] result = new int[4];
        prHistory.forEach((prid, actionList) -> {
            if (actionList.contains("merged")) {
                pr_merged_count[0] += 1;
                return;
            }
            if (actionList.contains("closed")) {
                pr_closed_count[0] += 1;
                return;
            }
            pr_opened_count[0] += 1;
        });

        result[0] = prHistory.keySet().size();
        result[1] = pr_merged_count[0];
        result[2] = pr_closed_count[0];
        result[3] = pr_opened_count[0];

        return result;

    }


    private void getPRofUpstream(String fork, String upstream) {
        IO_Process io = new IO_Process();

        HashMap<Integer, List<String>> prHistory = new HashMap<>();
        String query = "select count(*)\n" +
                "FROM `ghtorrent-2018-03`.pull_requests pr\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.projects p on pr.base_repo_id = p.id\n" +
                "WHERE p.url = 'https://api.github.com/repos/" + upstream + "';";

        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);

            try {
                int upstreamPRs = 0;
                ResultSet rs = preparedStmt.executeQuery();
                if (rs.next()) {
                    upstreamPRs = rs.getInt(1);
                }


                System.out.println(upstreamPRs);
                String updatePR = "UPDATE fork.HardFork_withCriteria\n" +
                        "    SET upstream_PR_total=?\n" +
                        "WHERE hardfork_url=?";
                try (Connection conn1 = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
                     PreparedStatement preparedStmt_updatePR = conn1.prepareStatement(updatePR);) {
                    conn1.setAutoCommit(false);
                    preparedStmt_updatePR.setInt(1, upstreamPRs);
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

    public static void main(String[] args) {
        GetPRfromGHTorrent getPRfromGHTorrent = new GetPRfromGHTorrent();
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
            getPRfromGHTorrent.getPRbyAuthor(fork, upstream);
//            getPRfromGHTorrent.getPRofUpstream(fork, upstream);
        }

    }


}


