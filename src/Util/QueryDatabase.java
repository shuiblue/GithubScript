package Util;

import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class QueryDatabase {


    String token;
    String user, pwd, myUrl;
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_url = "https://github.com/";
    String current_dir = System.getProperty("user.dir");
    static String working_dir, pr_dir, output_dir, clone_dir;
    final int batchSize = 500;
    HashSet<String> stopFileSet = new HashSet<>();
    HashSet<String> sourceCodeSuffix = new HashSet<>();
    IO_Process io = new IO_Process();

    public QueryDatabase() {
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            token = io.readResult(current_dir + "/input/token.txt").trim();
            stopFileSet.addAll(Arrays.asList(io.readResult(current_dir + "/input/StopFiles.txt").split("\n")));
            sourceCodeSuffix.addAll(Arrays.asList(io.readResult(current_dir + "/input/sourceCode.txt").split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public List<String> getCoreTeamList(String projectURL) {
        String query = "select cf.memberList\n" +
                "  from coreTeam cf LEFT JOIN  Final f on cf.projectID = f.repoID\n" +
                "WHERE f.repoURL = \'" + projectURL + "\'";
        String[] memberList_arr = {};

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                if (rs.next()) {
                    memberList_arr = rs.getString(1).split(", ");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Arrays.asList(memberList_arr);
    }
}
