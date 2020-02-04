package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UpdateLanguageOfHardFork {


    static IO_Process io = new IO_Process();


    public static void main(String[] args) {
        String hardfork_dir = io.hardfork_dir;
        String[] forkList = null;
        try {
            forkList = new IO_Process().readResult(hardfork_dir + "hardfork_language_ghapi.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "UPDATE fork.HardFork_withCriteria\n" +
                "    SET hardfork_language=? " +
                "WHERE hardfork_url=?";
        int count = 0;
        try (Connection conn = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            for (String str : forkList) {
                System.out.println(str);
                String[] arr = str.split(",");
                if (arr.length < 2) {
                    System.out.println("no language info, "+ str);
                    continue;
                }
                String fork = str.split(",")[0];
                String language = str.split(",")[1].trim();

                IO_Process io = new IO_Process();
                conn.setAutoCommit(false);
                preparedStmt.setString(1, language);
                preparedStmt.setString(2, fork);
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
