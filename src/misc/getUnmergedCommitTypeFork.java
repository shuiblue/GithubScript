package misc;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.List;

public class getUnmergedCommitTypeFork {
    public static void main(String[] args) {
        IO_Process io = new IO_Process();

        String[] forkList = null;
        try {
            forkList = io.readResult("./input/UnmergedCommitTypeForkList.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        String query = "SELECT\n" +
                "  count(*)\n" +
                "FROM `ghtorrent-2018-03`.commits cc1\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.commits cc2 ON cc1.sha = cc2.sha\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.projects fork ON cc1.project_id = fork.id\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.projects upstream\n" +
                "    ON fork.forked_from  != cc2.project_id AND fork.forked_from IS NOT NULL\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.project_stars ps ON fork.id = ps.project_id\n" +
                "WHERE fork.url =? AND upstream.url = ?;";

        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);
            final int[] count = {0};
            for (String str : forkList) {
                String fork = str.split(",")[1];
                String upstream = str.split(",")[0];
                System.out.println(str);
                try {
                    preparedStmt.setString(1, fork);
                    preparedStmt.setString(2, upstream);
                    System.out.println("unmerged commits: " + count[0]);
                    int count_unmergedCommit = 0;
                    ResultSet rs = preparedStmt.executeQuery();
                    if (rs.next()) {
                      count_unmergedCommit=  rs.getInt(1);

                    }
//                    conn.commit();
                    System.out.println("");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


}
