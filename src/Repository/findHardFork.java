package Repository;

import Pull_Request.AnalyzingPRs;
import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class findHardFork {

    static ArrayList<String> stopRepoList = new ArrayList<>();
    static ArrayList<String> stopForkName = new ArrayList<>();
    static String myUrl, user,pwd;
    findHardFork() {
        stopForkName.add("io.js");
        stopRepoList.add("docker");
        stopRepoList.add("node-v0.x-archive");
        stopRepoList.add("hubpress.io");
        //template
        stopRepoList.add("minimal-mistakes");
        stopRepoList.add("jekyll-now");
        stopRepoList.add("google-interview-university");
        stopRepoList.add("slate");
        stopRepoList.add("reveal.js");
        stopRepoList.add("octopress");

        //online course
        stopRepoList.add("open-source-society/computer-science");
    }

    static public void main(String[] args) {
        new findHardFork();
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();


        String myDriver = "com.mysql.jdbc.Driver";

        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        try {
             String current_dir = System.getProperty("user.dir");
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }



        Connection conn = null;
        try {
            conn = DriverManager.getConnection(myUrl, user, pwd);
            PreparedStatement preparedStmt = null;

            // create a mysql database connection
            Class.forName(myDriver);

            String query = "select repoURL_old, UpstreamURL,id,upstreamID " +
                    "from repository " +
                    "WHERE isFork = 1;";

            preparedStmt = conn.prepareStatement(query);
            System.out.println(preparedStmt.toString());
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {

                String fork = rs.getString("repoURL");
                String upstream = rs.getString("UpstreamURL");

                int forkid = rs.getInt("id");
                int upstreamid = rs.getInt("upstreamID");
                String forkName = fork.split("/")[fork.split("/").length - 1];
                String upstreamName = upstream.split("/")[1];
                String insertQuery =
                        "INSERT INTO  hardForkCandidates (forkID, upstreamID, forkURL, upstreamURL) " +

                                "  SELECT *" +
                                "  FROM (SELECT" +
                                "          ? AS a,? AS b, ? AS c, ? as d) AS tmp" +
                                "  WHERE NOT EXISTS(" +
                                "      SELECT *" +
                                "      FROM fork.hardForkCandidates AS hfc" +
                                "      WHERE hfc.forkID = ? and hfc.upstreamID = ?" +
                                "  )" +
                                "  LIMIT 1";


                if (!forkName.equals(upstreamName) && !stopRepoList.contains(upstreamName) && !stopForkName.contains(forkName)) {
                    preparedStmt = conn.prepareStatement(insertQuery);
                    System.out.println(preparedStmt.toString());
                    preparedStmt.setInt(1, forkid);
                    preparedStmt.setInt(2, upstreamid);
                    preparedStmt.setString(3, fork);
                    preparedStmt.setString(4, upstream);
                    preparedStmt.setInt(5, forkid);
                    preparedStmt.setInt(6, upstreamid);
                    preparedStmt.execute();

                    sb.append(fork+","+upstream+"\n");
                }


            }
            io.writeTofile(sb.toString(), "/Users/shuruiz/Box Sync/ForkData/hardForkList.csv");
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }
}
