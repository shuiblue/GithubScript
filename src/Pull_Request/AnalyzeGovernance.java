package Pull_Request;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeGovernance {
    static String working_dir, pr_dir, output_dir, clone_dir, apiResult_dir, passResult_dir, PR_ISSUE_dir;
    static String myUrl, user, pwd;
    static int batchSize = 100;

    AnalyzeGovernance() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            apiResult_dir = output_dir + "shurui_crossRef.cache/";
            passResult_dir = output_dir + "shurui_crossRef.cache_pass/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new AnalyzeGovernance();
        /** parsing commit, comments, body of PR/issue **/
//        parsingPR_ISSUE_Line_byText();


        /** parsing event, cross reference **/
//        System.out.println(" analyzing event of pr and issue");
        parsingPR_ISSUE_Line_byEvent();

//        parsingIssueParticipants_PR_map();
////
//
        /** analyzing pre-processed csv file **/
        System.out.println(" insert cross ref to database");
        insertCrossRefToDatabase();
//        System.out.println(" insert user and issue map");
//        insertRepo_issue_user_map()[;
//        System.out.println("insert label of pr and issue");
        insertIssue_label();

//        parsingPR_ISSUE_Line_byEvent(128, "wbond/package_control_channel");


    }

    public static HashMap<Integer, HashSet<Integer>> getRepoIssueMap() {
        HashMap<Integer, HashSet<Integer>> repo_issue_map = new HashMap<>();
        String query = "SELECT  projectID,  issue1_id\n" +
                "FROM crossReference\n" +
                "  WHERE issue1_type = 'issue' AND issue2_type = 'pr' AND projectID=ref_projectID\n" +
                "GROUP BY 1,2 \n" +
                "ORDER BY issue1_id";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int repoID = rs.getInt(1);
                int issueID = rs.getInt(2);
                HashSet<Integer> issueSet = new HashSet<>();
                if (repo_issue_map.get(repoID) != null) {
                    issueSet = repo_issue_map.get(repoID);
                }
                issueSet.add(issueID);
                repo_issue_map.put(repoID, issueSet);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return repo_issue_map;

    }

    private static void parsingIssueParticipants_PR_map() {
        IO_Process io = new IO_Process();
        HashMap<Integer, HashSet<Integer>> repo_issue_map = getRepoIssueMap();
        final int[] count = {0};
        String query = "UPDATE fork.crossReference\n" +
                "SET pre_communication = ?, same_owner = ?\n" +
                "WHERE projectID = ? AND issue1_id = ? AND issue1_type = 'issue'\n" +
                "      AND issue2_id = ? AND issue2_type = 'pr'\n" +
                "      AND ref_projectID = ?";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(query);) {
            conn1.setAutoCommit(false);


            repo_issue_map.forEach((projectID, issueSet) -> {
                String projectURL = io.getRepoUrlByID(projectID);
                System.out.println(projectURL);
                StringBuilder sb = new StringBuilder();
                for (Integer issue_id : issueSet) {
                    String file_name = passResult_dir + "get_issue_timeline." + projectURL.replace("/", ".") + "_" + issue_id + ".csv";
                    if (new File(file_name).exists()) {
                        String issue_owner = io.getIssueOwner(projectID, issue_id, "ISSUE");
                        HashSet<String> participants = new HashSet<>();
                        if (!issue_owner.equals("")) {
                            participants.add(issue_owner);
                        }
                        HashMap<Integer, Boolean> PR_participant = new HashMap<>();
                        System.out.println(projectURL + " issue " + issue_id);
                        List<List<String>> rows = io.readCSV(file_name);
                        for (List<String> r : rows) {
                            if (!r.get(0).equals("") & r.size() > 9) {
                                String event_type = r.get(9);
                                String event_author = r.get(2);
                                if (event_type.equals("cross-referenced")) {
                                    int prID = Integer.parseInt(r.get(10));
                                    String ref_type = r.get(14);
                                    if (ref_type.equals("pull_request")) {
                                        String author = getPRAuthor(projectID, prID);

                                        if (!author.equals("")) {
                                            if (!event_author.equals(author)) {
                                                System.out.println("event author: " + event_author + " pr " + prID + " author :" + author);
                                            }
                                            if (participants.contains(author)) {
                                                System.out.println(author + " communicate before pr");
                                                boolean pre_com = true;
                                                boolean sameOwner = false;
                                                if (author.equals(issue_owner)) {
                                                    System.out.println("same owner " + author);
                                                    sameOwner = true;
                                                }
                                                sb.append(projectID + "," + issue_id + "," + prID + "," + pre_com + "," + sameOwner + "\n");
                                                try {
                                                    preparedStmt_1.setBoolean(1, pre_com);
                                                    preparedStmt_1.setBoolean(2, sameOwner);
                                                    preparedStmt_1.setInt(3, projectID);
                                                    preparedStmt_1.setInt(4, issue_id);
                                                    preparedStmt_1.setInt(5, prID);
                                                    preparedStmt_1.setInt(6, projectID);
                                                    preparedStmt_1.addBatch();
                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                }


                                                if (++count[0] % 100 == 0) {
                                                    io.executeQuery(preparedStmt_1);
                                                    try {
                                                        conn1.commit();
                                                    } catch (SQLException e) {
                                                        e.printStackTrace();
                                                    }
                                                    System.out.println(count + " count ");
                                                    io.writeTofile(sb.toString(), output_dir + "pre_comm.txt");
                                                    sb = new StringBuilder();
                                                }

                                                PR_participant.put(prID, true);
                                            }
                                            participants.add(author);
                                        }
                                    }
                                    participants.add(event_author);

                                }
                            }
                        }
                    } else {
                        io.writeTofile(file_name + "\n", output_dir + "miss_issueTimeline.txt");
                    }
                }

                io.writeTofile(sb.toString(), output_dir + "pre_comm.txt");
            });

            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private static String getPRAuthor(Integer projectID, int prID) {
        String query = "SELECT authorName\n" +
                "FROM Pull_Request\n" +
                "WHERE projectID = " + projectID + " AND pull_request_ID= " + prID;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";

    }


    private static void parsingPR_ISSUE_Line_byEvent() {
        List<String> repoList = new IO_Process().getListFromFile("crossRef_repoList.txt");
        IO_Process io = new IO_Process();
//        List<String> passedFile = null;
//        try {
//            passedFile = Arrays.asList(io.readResult(PR_ISSUE_dir + "/passedFile.txt").split("\n"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        for (String repo : repoList) {
            System.out.println("repo " + repo);
            int projectID = io.getRepoId(repo);
            HashSet<String> comment_userSet = new HashSet<>();
            HashMap<String, String> use_map = new HashMap<>();
            try {
//                List<String> finalPassedFile = passedFile;
                String finalRepo = repo;
                Files.newDirectoryStream(Paths.get(apiResult_dir), path -> path.toFile().isFile())
                        .forEach(file -> {
                                    StringBuilder sb_crossRef = new StringBuilder();
                                    StringBuilder sb_issue_user = new StringBuilder();
                                    StringBuilder sb_issue_label = new StringBuilder();
                                    StringBuilder sb_event = new StringBuilder();
                                    StringBuilder sb_passedFile = new StringBuilder();
                                    String file_name = file.getFileName().toString();
                                    if ((file_name.startsWith("get_issue_timeline") || file_name.startsWith("get_pr_timeline"))
                                            && file_name.contains(finalRepo.replace("/", ".") + "_")) {
                                        System.out.println(file.toString());
//                                        if (!finalPassedFile.contains(file_name)) {
                                        sb_passedFile.append(file_name + "\n");
                                        String[] arr = file_name.split("_");
                                        String issueID_str = arr[arr.length - 1].replaceAll("\\D+", "").replace(".csv", "");
                                        if (issueID_str.equals("")) {
                                            return;
                                        }
                                        int issue_id = Integer.parseInt(issueID_str);

                                        String issue_type = file_name.contains("get_issue_timeline") ? "issue" : "pr";
                                        HashSet<String> labels = new HashSet<>();

                                        List<List<String>> rows = io.readCSV(file.toFile());
                                        HashSet<String> loginID_set = new HashSet<>();
                                        for (int i = 1; i < rows.size(); i++) {
                                            List<String> r = rows.get(i);
                                            if (r.size() == 15) {
                                                String event_type = r.get(9);
                                                if (event_type.equals("labeled")) {
                                                    labels.add(r.get(11));
                                                } else if (event_type.equals("cross-referenced")) {

                                                    int id = Integer.parseInt(r.get(10));
                                                    String ref_projectURL = r.get(12);

                                                    String state = r.get(13);
                                                    String ref_type = r.get(14);
                                                    sb_crossRef.append(projectID + "," + issue_id + "," + issue_type + "," + ref_projectURL + "," + id + "," + ref_type + "," + state + "\n");
                                                }

                                                String full_name = "", login_id = "", email = "";
                                                if (event_type.equals("committed")) {
                                                    full_name = r.get(2);
                                                    email = r.get(8);
                                                    login_id = email;
                                                } else {
                                                    login_id = r.get(2);
                                                    sb_event.append(projectID + "," + issue_id + "," + issue_type + "," + event_type + "\n");

                                                }
                                                if (!login_id.equals("")) {
                                                    loginID_set.add(login_id);
                                                    String author_association = r.get(3);
                                                    String user_type = r.get(4);
                                                    if (!use_map.keySet().contains(login_id)) {
                                                        if (event_type.equals("commented")) {
                                                            comment_userSet.add(login_id);
                                                        }
                                                        use_map.put(login_id, author_association + "," + user_type + "," + full_name + "," + email);
                                                    } else {
                                                        if (!comment_userSet.contains(login_id) && event_type.equals("commented")) {
                                                            comment_userSet.add(login_id);
                                                            use_map.put(login_id, author_association + "," + user_type + "," + full_name + "," + email);

                                                        }
                                                    }

                                                }
                                            }
                                        }
                                        if (loginID_set.size() > 0) {

                                            for (String login_id : loginID_set) {
                                                sb_issue_user.append(projectID + "," + issue_id + "," + issue_type + "," + login_id + "," + use_map.get(login_id) + "\n");
                                            }

                                            if (labels.size() > 0) {
                                                sb_issue_label.append(projectID + "," + issue_id + "," + issue_type + ",[");
                                                labels.forEach(p -> sb_issue_label.append(p + "/"));
                                                sb_issue_label.append("]\n");
                                            }
                                            System.out.println(" issue " + issue_id + " of " + finalRepo + " write to file");
                                            io.writeTofile(sb_passedFile.toString(), PR_ISSUE_dir + "passedFile.txt");
                                            io.writeTofile(sb_crossRef.toString(), PR_ISSUE_dir + "crossRef.csv");
                                            io.writeTofile(sb_event.toString(), PR_ISSUE_dir + "eventList.csv");
                                            io.writeTofile(sb_issue_label.toString(), PR_ISSUE_dir + "issue_label.csv");
                                            io.writeTofile(sb_issue_user.toString(), PR_ISSUE_dir + "issue_pariticipants.csv");

                                            System.out.println("move file " + file);
                                            try {
                                                io.fileCopy(String.valueOf(file), file.toString().replace(apiResult_dir, passResult_dir));
                                                Files.deleteIfExists(file);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }

//                                        } else {
//                                            try {
//                                                io.fileCopy(String.valueOf(file), file.toString().replace(apiResult_dir, passResult_dir));
//                                                Files.deleteIfExists(file);
//                                            } catch (IOException e) {
//                                                e.printStackTrace();
//                                            }
//
//                                            System.out.println("skip " + file);
//                                        }
                                    }
                                }
                        );
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    private static void parsingPR_ISSUE_Line_byEvent(int projectID, String repo) {
        IO_Process io = new IO_Process();
        System.out.println(repo);
        try {
            Files.newDirectoryStream(Paths.get(passResult_dir), path -> path.toFile().isFile())
                    .forEach(file -> {
                                StringBuilder sb_crossRef = new StringBuilder();
                                StringBuilder sb_issue_label = new StringBuilder();

                                String file_name = file.getFileName().toString();
                                if ((file_name.startsWith("get_issue_timeline") || file_name.startsWith("get_pr_timeline"))
                                        && file_name.contains(repo.replace("/", "."))) {
                                    System.out.println(file_name);

                                    String[] arr = file_name.split("_");
                                    int issue_id = Integer.parseInt(arr[arr.length - 1].replace(".csv", ""));
                                    String issue_type = file_name.contains("get_issue_timeline") ? "issue" : "pr";
                                    HashSet<String> labels = new HashSet<>();

                                    List<List<String>> rows = io.readCSV(file.toFile());
                                    HashSet<String> loginID_set = new HashSet<>();
                                    for (List<String> r : rows) {
                                        if (!r.get(0).equals("") & r.size() > 9) {
                                            String event_type = r.get(9);


                                            if (event_type.equals("labeled")) {
                                                labels.add(r.get(11));
                                            } else if (event_type.equals("cross-referenced")) {

                                                int id = Integer.parseInt(r.get(10));
                                                String ref_projectURL = r.get(12);

                                                String state = r.get(13);
                                                String ref_type = r.get(14);
                                                sb_crossRef.append(projectID + "," + issue_id + "," + issue_type + "," + ref_projectURL + "," + id + "," + ref_type + "," + state + "\n");
                                            }
                                        }
                                    }


                                    if (labels.size() > 0) {
                                        sb_issue_label.append(projectID + "," + issue_id + "," + issue_type + ",[");
                                        labels.forEach(p -> sb_issue_label.append(p + "/"));
                                        sb_issue_label.append("]\n");
                                    }
                                    System.out.println(" issue " + issue_id + " of " + repo + " write to file");
                                    io.writeTofile(sb_crossRef.toString(), PR_ISSUE_dir + projectID + "_crossRef.csv");
                                    io.writeTofile(sb_issue_label.toString(), PR_ISSUE_dir + projectID + "_issue_label.csv");
                                }
                            }
                    );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void insertIssue_label() {
        IO_Process io = new IO_Process();
//        List<String> passedLines = new ArrayList<>();
//        try {
//            passedLines = Arrays.asList(io.readResult(PR_ISSUE_dir + "passed_label.txt").split("\n"));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }


        String[] lines = {};
        try {
            lines = io.readResult(PR_ISSUE_dir + "issue_label.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        StringBuilder sb = new StringBuilder();
        String update_issue_query = "UPDATE fork.ISSUE\n" +
                "SET labels=?" +
                "\n WHERE issue_id = ? AND projectID=? ;\n";

        try (Connection conn_issue = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_issue = conn_issue.prepareStatement(update_issue_query);

        ) {
            conn_issue.setAutoCommit(false);

            for (String ref : lines) {
//                if (!passedLines.contains(ref)) {
                sb.append(ref + "\n");
                String[] arr = ref.split(",");
                //projectID + "," + issue_id + "," + issue_type + labels
                int projectID = Integer.parseInt(arr[0]);
                int issue_id = Integer.parseInt(arr[1]);
                String issue_type = arr[2];
                String label = io.normalize(io.removeBrackets(arr[3]));

                if (issue_type.equals("issue")) {
                    preparedStmt_issue.setString(1, label);
                    preparedStmt_issue.setInt(3, projectID);
                    preparedStmt_issue.setInt(2, issue_id);


                    System.out.println("inserting issue label issue_id " + issue_id + " project ID " + projectID);
                    System.out.println("affect " + preparedStmt_issue.executeUpdate() + "rows");
                }

                io.writeTofile(ref + "\n", PR_ISSUE_dir + "passed_label.txt");
//                }
            }
            conn_issue.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }


        String update_pr_query = "UPDATE fork.Pull_Request\n" +
                " SET labels = ? " +
                " WHERE projectID = ? AND pull_request_ID = ?;";


        try (
                Connection conn_pr = DriverManager.getConnection(myUrl, user, pwd);
                PreparedStatement preparedStmt_pr = conn_pr.prepareStatement(update_pr_query);
        ) {
            conn_pr.setAutoCommit(false);

            for (String ref : lines) {
//                if (!passedLines.contains(ref)) {
                String[] arr = ref.split(",");
                //projectID + "," + issue_id + "," + issue_type + labels
                int projectID = Integer.parseInt(arr[0]);
                int issue_id = Integer.parseInt(arr[1]);
                String issue_type = arr[2];
                String label = io.normalize(io.removeBrackets(arr[3]));

                if (issue_type.equals("pr")) {
                    preparedStmt_pr.setString(1, label);
                    preparedStmt_pr.setInt(2, projectID);
                    preparedStmt_pr.setInt(3, issue_id);
                    System.out.println("inserting pr label pr_id " + issue_id + " project ID " + projectID);
                    System.out.println("affect " + preparedStmt_pr.executeUpdate() + "rows");

                }

//                }
            }
            conn_pr.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private static void insertRepo_issue_user_map() {
        IO_Process io = new IO_Process();
        List<String> passedLines = new ArrayList<>();
        try {
            passedLines = Arrays.asList(io.readResult(PR_ISSUE_dir + "31_40_passed_user.txt").split("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }


        StringBuilder sb = new StringBuilder();


        String[] lines = {};
        int count = 0;
        try {
            lines = io.readResult(PR_ISSUE_dir + "31_40_issue_pariticipants.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "INSERT INTO Repo_Issue_Paritcipant_Map (projectID, issueID, issue_type, userID, author_association) VALUES (?,?,?,?,?)";
        HashSet<String> existingMap = new HashSet<>();
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(query);) {
            conn1.setAutoCommit(false);

            for (String ref : lines) {
                if (!passedLines.contains(ref)) {
                    sb.append(ref + "\n");
                    String[] arr = ref.split(",");
                    ////projectID + "," + issue_id + "," + issue_type + "," + login_id + ","+author_association + "," + user_type + "," + full_name + "," + email
                    int projectID = Integer.parseInt(arr[0]);
                    preparedStmt_1.setInt(1, projectID);

                    int issue1_id = Integer.parseInt(arr[1]);
                    preparedStmt_1.setInt(2, issue1_id);

                    String issue1_type = arr[2];
                    preparedStmt_1.setString(3, issue1_type);

                    String type = "", full_name = "", email = "", association = "";
                    String login_ID = arr[3];
                    if (arr.length == 8) {

                        type = arr[5];
                        full_name = io.normalize(arr[6]);
                        email = io.normalize(arr[7]);
                    } else {
                        association = arr[4];
                    }
                    //loginID, String full_name,String email, String user_type
                    int userID = io.insertUser(login_ID, full_name, email, type);
                    if (userID == -1) {
                        System.out.println("user id is -1 !");
                    } else {
                        System.out.println(" user id " + userID);
                    }

                    if (!existingMap.contains(projectID + "," + issue1_id + "," + userID)) {
                        existingMap.add(projectID + "," + issue1_id + "," + userID);

                        preparedStmt_1.setInt(4, userID);

                        preparedStmt_1.setString(5, association);

                        preparedStmt_1.addBatch();
                        System.out.println("count " + count + " ... issue " + issue1_id + " user " + login_ID + " repo " + projectID);
                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_1);
                            System.out.println("insert 100 query for project " + projectID);
                            conn1.commit();
                            io.writeTofile(sb.toString(), PR_ISSUE_dir + "31_40_passed_user.txt");
                            sb = new StringBuilder();
                        }
                    }


                } else {
                    System.out.println("skip " + ref);
                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        io.writeTofile(sb.toString(), PR_ISSUE_dir + "31_40_passed_user.txt");
    }

    static HashMap<String, Integer> url_id = new HashMap<>();

    private static void insertCrossRefToDatabase() {
        IO_Process io = new IO_Process();
        List<String> passedLines = new ArrayList<>();
        try {
            passedLines = Arrays.asList(io.readResult(PR_ISSUE_dir + "pass_crossRef.txt").split("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }


        String[] lines = {};
        StringBuilder sb = new StringBuilder();
        int count = 0;
        try {
            lines = io.readResult(PR_ISSUE_dir + "crossRef.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "INSERT IGNORE INTO fork.crossReference (projectID, issue1_id, issue1_type, issue2_id, issue2_type,  issue2_state, ref_projectURL, ref_projectID)\n" +
                "VALUES (?,?,?,?,?,?,? ,?)";

        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(query);) {
            conn1.setAutoCommit(false);

            for (String ref : lines) {
                if (!passedLines.contains(ref)) {
                    sb.append(ref + "\n");
                    String[] arr = ref.split(",");
                    String ref_projectURL = arr[3];
                    int ref_projectID;
                    if (url_id.get(ref_projectURL) == null) {
                        ref_projectID = io.getRepoId(ref_projectURL);
                        url_id.put(ref_projectURL, ref_projectID);
                    } else {
                        ref_projectID = url_id.get(ref_projectURL);
                    }


                    if (ref_projectID == -1) {
                        ref_projectID = io.insertRepo(ref_projectURL);
                        System.out.println("insert repo " + ref_projectURL + " for cross reference id:" + ref_projectID);
                    }


                    //projectID + "," + issue_id + "," + issue_type + "," + ref_projectURL + "," + id + "," + ref_type + "," + state + "\n");
                    int projectID = Integer.parseInt(arr[0]);
                    preparedStmt_1.setInt(1, projectID);

                    int issue1_id = Integer.parseInt(arr[1]);
                    preparedStmt_1.setInt(2, issue1_id);

                    String issue1_type = arr[2];
                    preparedStmt_1.setString(3, issue1_type);


                    int issue2_id = Integer.parseInt(arr[4]);
                    preparedStmt_1.setInt(4, issue2_id);

                    String issue2_type = arr[5];
                    preparedStmt_1.setString(5, issue2_type.equals("pull_request") ? "pr" : "issue");

                    String issue2_state = arr[6];
                    preparedStmt_1.setString(6, issue2_state);


                    preparedStmt_1.setString(7, ref_projectURL);
                    preparedStmt_1.setInt(8, ref_projectID);


                    preparedStmt_1.addBatch();
                    System.out.println("count " + count + "  ... insert cross ref " + issue1_id + " with  " + issue2_id + "  of repo " + projectID);

                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt_1);
                        System.out.println("insert 100 query for project " + projectID);
                        conn1.commit();
                        io.writeTofile(sb.toString(), PR_ISSUE_dir + "pass_crossRef.txt");
                        sb = new StringBuilder();
                    }
                } else {
                    System.out.println("skip " + ref);
                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
            io.writeTofile(sb.toString(), PR_ISSUE_dir + "pass_crossRef.txt");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public static void parsingPR_ISSUE_Line_byText() {
        AnalyzeGovernance ag = new AnalyzeGovernance();
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String projectUrl : repos) {
            String csvPRfile = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
            HashMap<Integer, String> pr_title = new HashMap<>();
            if (new File(csvPRfile).exists()) {
                List<List<String>> prCSV_List = io.readCSV(csvPRfile);
                for (List<String> line : prCSV_List) {
                    if (line.size() == 14) {
                        String pr_id = line.get(9);
                        String title = line.get(12);
                        pr_title.put(Integer.valueOf(pr_id), title);
                    }
                }

                int projectID = io.getRepoId(projectUrl);

                System.out.println(projectUrl);
                ag.createIssuePRLink(projectUrl, projectID, pr_title);

            } else {
                System.out.println(projectUrl + " pr api result not available");
                io.writeTofile(projectUrl + "\n", output_dir + "miss_pr_api.txt");
                continue;
            }
        }
    }

    private void createIssuePRLink(String projectUrl, int projectID, HashMap<Integer, String> pr_title_map) {
        IO_Process io = new IO_Process();
        int count = 0;
        HashSet<String> pr_issueMap_Exist = io.exist_PR_issueMap(projectID);

        System.out.println("get issue id of project " + projectUrl);
        HashMap<Integer, String> issueSet = io.getIssueSet(projectID);
        String insert_PR_issueMap = " INSERT INTO fork.PR_TO_ISSUE(repoID, pull_request_id, issue_id, issue_created_at) " + "VALUES (?,?,?,?)";


        List<List<String>> comments, commits;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insert_PR_issueMap)) {
            conn.setAutoCommit(false);

            for (Integer pr_id : pr_title_map.keySet()) {
                System.out.println("pr# " + pr_id);
                HashSet<Integer> issues_candidate = new HashSet<>();
                Pattern issueLink = Pattern.compile("\\#[1-9][\\d]{1,4}([^0-9]|$)");
                String pr_title_str = pr_title_map.get(pr_id);
                Matcher m = issueLink.matcher(pr_title_str);
                while (m.find()) {
                    System.out.println("match : " + m.group());
                    int s = Integer.parseInt(m.group().replaceAll("[^\\d]", "").trim());
                    issues_candidate.add(s);
                    System.out.println("pr title match issue link " + s);
                }


                String comment_csvFile_dir = output_dir + "shurui.cache/get_pr_comments." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
                String comment_csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_comments." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
                String commit_csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
                String commit_csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
                boolean csvFileExist = new File(comment_csvFile_dir).exists();
                boolean csvFileAlter_Exist = new File(comment_csvFile_dir_alternative).exists();
                if (csvFileExist) {
                    comments = io.readCSV(comment_csvFile_dir);
                    commits = io.readCSV(commit_csvFile_dir);
                } else if (csvFileAlter_Exist) {
                    comments = io.readCSV(comment_csvFile_dir_alternative);
                    commits = io.readCSV(commit_csvFile_dir_alternative);
                } else {

                    continue;
                }
                System.out.println("starting to parse commits and comments... " + projectUrl);
                issues_candidate.addAll(getIssueCandidates(comments, projectID, "comment"));
                issues_candidate.addAll(getIssueCandidates(commits, projectID, "commit"));
                System.out.println(" issue candidats :" + issues_candidate.size());

                for (int issue_candi : issues_candidate) {
                    if (issueSet.keySet().contains(issue_candi) && pr_id != issue_candi) {
                        if (!pr_issueMap_Exist.contains(pr_id + "," + issue_candi)) {
                            //repoID
                            preparedStmt.setInt(1, projectID);
                            // , pull_request_id,
                            preparedStmt.setInt(2, pr_id);
                            // issue_id,
                            preparedStmt.setInt(3, issue_candi);
                            // issue_created_at
                            preparedStmt.setString(4, issueSet.get(issue_candi));
                            preparedStmt.addBatch();
                            System.out.println("add pr:" + pr_id + "," + "issue: " + issue_candi);
                            if (++count % batchSize == 0) {
                                System.out.println("count " + count);
                                io.executeQuery(preparedStmt);
                                conn.commit();
                            }
                        }
                    }
                }

            }

            io.executeQuery(preparedStmt);
            conn.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private HashSet<Integer> getIssueCandidates(List<List<String>> texts, int projectID, String option) {
        int text_index = 0;
        if (option.equals("comment")) {
            text_index = 2;
        } else if (option.equals("commit")) {
            text_index = 6;
        }
        Pattern issueLink = Pattern.compile("\\#[1-9][\\d]{1,4}([^0-9]|$)");
        HashSet<Integer> issues = new HashSet<>();
        for (List<String> comment : texts) {
            if (comment.size() > text_index) {
                if (!comment.get(0).equals("")) {
                    String text = comment.get(text_index);
                    Matcher m = issueLink.matcher(text);
                    while (m.find()) {
                        System.out.println("match : " + m.group());
                        int s = Integer.parseInt(m.group().replaceAll("[^\\d]", "").trim());
                        issues.add(s);
                    }
                }
            }
        }
        return issues;
    }


}
