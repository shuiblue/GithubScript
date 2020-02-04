package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class updateDateOfHardFork {


    static IO_Process io = new IO_Process();


    public static void main(String[] args) {
        String hardfork_dir = io.hardfork_dir;
        UpdateStarOfHardfork updateStarOfHardfork = new UpdateStarOfHardfork();
        String[] forkList = null;
        try {
            forkList = new IO_Process().readResult(hardfork_dir + "inputData/updateAt_ghapi.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "UPDATE fork.HardFork_withCriteria\n" +
                "    SET hardfork_lastUpdate=?, upstream_lastUpdate=?\n" +
                "WHERE hardfork_url=?";
        int count = 0;
        try (Connection conn = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            for (String str : forkList) {
//                System.out.println(str);
                String fork = str.split(",")[0];
                String fork_star = str.split(",")[2];
                String upstream_star = str.split(",")[3];

                IO_Process io = new IO_Process();
                conn.setAutoCommit(false);
                preparedStmt.setString(1, fork_star);
                preparedStmt.setString(2, upstream_star);
                preparedStmt.setString(3, fork);
                preparedStmt.addBatch();

                if (++count % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                    System.out.println(count + " count ");
                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
            System.out.println(count + " count ");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


}
