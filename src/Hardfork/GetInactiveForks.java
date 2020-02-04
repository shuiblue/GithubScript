package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GetInactiveForks {
    IO_Process io = new IO_Process();

    public static void main(String[] args) {
        GetInactiveForks getInactiveForks = new GetInactiveForks();
        getInactiveForks.getInactiveForksFromDir();

    }

    private void getInactiveForksFromDir() {


        List<String> forklist = new ArrayList<>();
        try {
            Files.newDirectoryStream(Paths.get("/DATA/shurui/ForkData/analyzed_FP_inactiveAfterForking"),
                    path -> path.toString().endsWith("category.csv"))
                    .forEach(
                            filepath -> {
                                String forkUrl_str = filepath.getFileName().toString().replace("_commit_date_category.csv", "")
                                        .replace("/", ".");
                                System.out.println(forkUrl_str);
                                forklist.add(forkUrl_str);

                            });
        } catch (IOException e) {
            e.printStackTrace();

        }

        String query = "UPDATE HardFork_withCriteria\n" +
                "    SET FP_InactiveAfterForking = TRUE \n" +
                "WHERE hardfork_url_tmp = ? ";
        final int[] count = {0};
        try (Connection conn = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);) {

            for (String fork : forklist) {
                conn.setAutoCommit(false);
                preparedStmt.setString(1, fork);
                preparedStmt.addBatch();
                if (++count[0] % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                    System.out.println(count + " count ");
                }

            }
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {

            e.printStackTrace();
        }
    }
}
