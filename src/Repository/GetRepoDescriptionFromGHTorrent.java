package Repository;

import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.List;

public class GetRepoDescriptionFromGHTorrent {


    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        GetRepoDescriptionFromGHTorrent getRepoDescriptionFromGHTorrent = new GetRepoDescriptionFromGHTorrent();
        String[] forkList = null;
        try {
//            forkList = new IO_Process().readResult("./input/hardforkList.csv").split("\n");
            forkList =io.readResult(io.hardfork_dir + "inputData/descriptionAforkOf_3star_update.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String str : forkList) {
            System.out.println(str);
            String fork = str.split(",")[0];
            String upstream = str.split(",")[1];
            String forkDesc = getRepoDescriptionFromGHTorrent.getRepoDescription(fork);
            System.out.println(forkDesc);
            String upstreamDesc = getRepoDescriptionFromGHTorrent.getRepoDescription(upstream);
            System.out.println(upstreamDesc);
            getRepoDescriptionFromGHTorrent.updateDescription(fork, forkDesc, upstreamDesc);
        }

    }

    private String getRepoDescription(String repo) {
        IO_Process io = new IO_Process();

        String query = "select p.description\n" +
                "from `ghtorrent-2018-03`.projects p\n" +
                "WHERE p.url = 'https://api.github.com/repos/" + repo + "';";

        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            conn.setAutoCommit(false);
            try {
                ResultSet rs = preparedStmt.executeQuery();
                if (rs.next()) {
                    String result = rs.getString(1);
                    if (result == null) {
                        System.out.println("result is null");
                        result = "";
                    }
                    return result;
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    private void updateDescription(String fork, String forkDesc, String upstreamDesc) {
        boolean fp = false;
        if (forkDesc.trim().equals(upstreamDesc.trim())) {
            fp = true;
        }
        IO_Process io = new IO_Process();
        String updatePR = "UPDATE fork.HardFork_withCriteria\n" +
                "    SET hardfork_description=?, upstream_description=?,FP_sameDescriptionFandU=?\n" +
                "WHERE hardfork_url='" + fork + "'";
        try (Connection conn1 = DriverManager.getConnection(io.myUrl, io.user, io.pwd);
             PreparedStatement preparedStmt_updatePR = conn1.prepareStatement(updatePR);) {
            conn1.setAutoCommit(false);
            preparedStmt_updatePR.setString(1, forkDesc);
            preparedStmt_updatePR.setString(2, upstreamDesc);
            preparedStmt_updatePR.setBoolean(3, fp);
            preparedStmt_updatePR.executeUpdate();
            conn1.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

}
