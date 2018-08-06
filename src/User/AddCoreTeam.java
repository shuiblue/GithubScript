package User;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AddCoreTeam {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd, token;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;


    AddCoreTeam() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        new AddCoreTeam();
        IO_Process io = new IO_Process();
        String[] result = {};
        try {
            result = io.readResult(output_dir + "coreTeam0805.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        int count = 0;
        String query = "UPDATE coreTeam\n" +
                "SET num_coreTeamMember = ?, memberList = ? " +
                "WHERE projectID = ? ";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn1.prepareStatement(query)) {
            conn1.setAutoCommit(false);
            for (String str : result) {
                System.out.println(str);



                String[] arr = str.split(",\\[");
                String url = arr[0].split(",")[0];

                int projectID = io.getRepoId(url);


                int num_coreTeam = Integer.parseInt(arr[0].split(",")[1]);
                String members = io.removeBrackets(arr[1]);

                preparedStmt.setInt(1, num_coreTeam);
                preparedStmt.setString(2, members);
                preparedStmt.setInt(3, projectID);
                preparedStmt.addBatch();
                if (++count % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    conn1.commit();
                }
            }
            io.executeQuery(preparedStmt);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
