package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class updateDeletedHardFork {

    static IO_Process io = new IO_Process();


    public static void main(String[] args) {
        String hardfork_dir = io.hardfork_dir;
        UpdateStarOfHardfork updateStarOfHardfork = new UpdateStarOfHardfork();
        String[] forkList = null;
        try {
//            forkList = new IO_Process().readResult(hardfork_dir + "inputData/fork_404_pro.txt").split("\n");
            forkList = new IO_Process().readResult(hardfork_dir + "inputData/404_pro.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

//        String query = "UPDATE fork.HardFork_withCriteria\n" +
//                "    SET hardfork_exist=FALSE \n" +
//                "WHERE hardfork_url=?";
        String query = "UPDATE fork.HardFork_withCriteria\n" +
                "    SET upstream_deleted=TRUE \n" +
                "WHERE upstream_url=?";
        int count = 0;
        try (Connection conn = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            for (String fork : forkList) {
//                System.out.println(str);
            ;

                IO_Process io = new IO_Process();
                conn.setAutoCommit(false);
                preparedStmt.setString(1, fork);
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
