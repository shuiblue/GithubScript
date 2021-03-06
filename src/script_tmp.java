import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class script_tmp {
    static String user, pwd, myUrl;
    static String current_OS = System.getProperty("os.name").toLowerCase();
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_url = "https://github.com/";
    static String current_dir = System.getProperty("user.dir");
    static String working_dir, pr_dir, output_dir, clone_dir;
    static int batchSize = 5000;


    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");

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
//        updateLoginID();

//        checkProjectName();

//        checkAnalyzedGraph();
//        checkDupRepo();

        getRejectPR();

    }

    private static void checkDupRepo() {
        IO_Process io = new IO_Process();
        HashSet<String> all_repo = new HashSet<>();
        HashSet<String> analyzed_repo = new HashSet<>();
        try {
            all_repo.addAll(Arrays.asList(io.readResult(current_dir + "/input/graph_result_repoList.txt").split("\n")));
            analyzed_repo.addAll(Arrays.asList(io.readResult(current_dir + "/input/f89.txt").split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        for (String repo : all_repo) {
            if (analyzed_repo.contains(repo.replace("/", "."))) {
                sb.append(repo + "\n");
            }
        }
        io.writeTofile(sb.toString(), output_dir + "/analyzed_graph.txt");
    }

    private static void checkAnalyzedGraph() {
        IO_Process io = new IO_Process();
        HashSet<String> all_repo = new HashSet<>();
        HashSet<String> analyzed_repo = new HashSet<>();
        try {
            all_repo.addAll(Arrays.asList(io.readResult(current_dir + "/input/graph_result_repoList.txt").split("\n")));
            analyzed_repo.addAll(Arrays.asList(io.readResult(current_dir + "/input/f89.txt").split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        StringBuilder sb = new StringBuilder();
        for (String repo : all_repo) {
            if (!analyzed_repo.contains(repo.replace("/", "."))) {
                sb.append(repo + "\n");
            }
        }
        io.writeTofile(sb.toString(), output_dir + "/todo_graph.txt");
    }

    private static void checkProjectName() {
        String current_dir = System.getProperty("user.dir");
        HashSet<String> repoSet = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        try {
            List<String> repos = Arrays.asList(new IO_Process().readResult(current_dir + "/input/subjectProject.txt").split("\n"));
            for (String r : repos) {
                for (String r1 : repos) {
                    if (r1.contains(r) & !r1.equals(r)) {
                        repoSet.add(r);
                    }
                }
            }
            for (String rr : repoSet) {
                sb.append(rr + "\n");
            }
            new IO_Process().rewriteFile(sb.toString(), output_dir + "comment_redo.txt");


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateLoginID() {
        IO_Process io = new IO_Process();
        String query = "SELECT repoURL_old,id,loginID\n" +
                "FROM repository";

        ArrayList<String> repoList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                while (rs.next()) {
                    repoList.add(rs.getString(1) + "," + rs.getInt(2) + "," + rs.getString(3));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int count = 0;
        String updateLoginId = "UPDATE repository\n" +
                "SET loginID = ?" +
                "WHERE id =  ?";


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(updateLoginId);) {
            System.out.println(repoList.size() + " repo has no login id");
            conn.setAutoCommit(false);
            for (String line : repoList) {
                String[] arr = line.split(",");

                if (arr.length < 3 || (arr[2].contains("/") || arr[2].equals("") || arr[2].equals("null"))) {
                    System.out.println("login id: " + line);
                    String repoid = arr[1];
                    String loginid = arr[0].split("/")[0];
                    preparedStmt.setString(1, loginid);
                    preparedStmt.setInt(2, Integer.parseInt(repoid));
                    preparedStmt.addBatch();
                    if (++count % batchSize == 0) {
                        System.out.println("\n add " + count + " to database..");
                        io.executeQuery(preparedStmt);
                        conn.commit();
                    }
                }

            }
            System.out.println("\n add all login ID set data to database..");
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    public static void getRejectPR() {

        String query = "SELECT repo.repoURL_old,  pr.pull_request_ID\n" +
                "FROM Pull_Request pr , repository repo \n" +
                "WHERE pr.merge_allType = FALSE and repo.id = pr.projectID ;";


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String url = rs.getString(1);
                int prID = rs.getInt(2);
                new IO_Process().writeTofile(url + "," + prID + "\n", output_dir + "rejectPR.txt");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
}