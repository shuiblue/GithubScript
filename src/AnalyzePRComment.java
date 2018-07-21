import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzePRComment {

    static String working_dir, output_dir;
    static String myUrl, user, pwd;

    AnalyzePRComment() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        new AnalyzePRComment();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prComment_repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        final int[] count = {0};

        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            int projectID = io.getRepoId(projectUrl);
            System.out.println("start : " + projectUrl);
            if (projectID == -1) {
                System.out.println(projectUrl + "projectID = -1");
            }

            String insert_query_1 = " UPDATE fork.Pull_Request" +
                    " SET dupPR_comment = ? , dup_related_comment = ?" +
                    "WHERE projectID = ? AND pull_request_ID=? ";
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                conn.setAutoCommit(false);

                Files.newDirectoryStream(Paths.get(output_dir + "shurui.cache/"), path -> path.toFile().isFile())
                        .forEach(file -> {
                                    String file_name = file.getFileName().toString();
                                    if ((file_name.startsWith("get_pr_comments"))
                                            && file_name.contains(projectUrl.replace("/", ".") + "_")) {
                                        System.out.println(file_name);
                                        String[] arr = file_name.split("_");
                                        int pr_id = Integer.parseInt(arr[arr.length - 1].replace(".csv", "").replace(".0", ""));
                                        ArrayList<String> dup_comments = new ArrayList<>();
                                        List<List<String>> rows = io.readCSV(file.toFile());
                                        boolean isDup = false;
                                        for (List<String> r : rows) {
                                            if (!r.get(0).equals("")) {
                                                String comment = r.get(2);
                                                isDup = isDuplicatePR(comment.toLowerCase());
                                                if(isDup) {
                                                    dup_comments.add(comment);
//                                                    break;
                                                }
                                            }
                                        }
                                        if (dup_comments.size() > 0) {
                                            try {
                                                preparedStmt.setBoolean(1, isDup);
//                                                preparedStmt.setString(2, io.normalize(dup_comments.toString()));
                                                preparedStmt.setString(2, io.normalize(dup_comments.toString()));
                                                preparedStmt.setInt(3, projectID);
                                                preparedStmt.setInt(4, pr_id);
                                                preparedStmt.addBatch();
                                                int batchSize = 100;
                                                if (++count[0] % batchSize == 0) {
                                                    System.out.println("update  " + count[0] + "comments from repo" + projectUrl);
                                                    io.executeQuery(preparedStmt);
                                                    conn.commit();
                                                }
                                            } catch (SQLException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                        );
                if (count[0] > 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }


        }

    }

    private static boolean isDuplicatePR(String comment) {

        Pattern p1 = Pattern.compile("(clos(e|ed|ing)|dup(licate(d)?|e)?|super(c|s)ee?ded?|obsoleted?|replaced?|redundant|better (implementation|solution)" +
                "|solved|fixed|done|going|merged|addressed|already|land(?:ed|ing))[ ]{1,}(by|in|with|as|of|in favor of|at|since|via)?[ ]{1,}" +
                "(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)");
        Matcher m1 = p1.matcher(comment);
        if (m1.find()) {
            System.out.println(m1.group());
            System.out.println("match 1.");
            return true;
        }
        Pattern p2 = Pattern.compile("(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+).{1,}" +
                "(better (implementation|solution)|dup(licate(d)?|e)?|(fixe(d|s)?|obsolete(d|s)?|replace(d|s)?))");
        Matcher m2 = p2.matcher(comment);
        if (m2.find()) {
            System.out.println(m2.group());
            System.out.println("match 2.");
            return true;
        }
        Pattern p3 = Pattern.compile("dup(?:licated?)?:?[ ]{1,}(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)");
        Matcher m3 = p3.matcher(comment);
        if (m3.find()) {
            System.out.println(m3.group());
            System.out.println("match 3.");
            return true;
        }
        return false;
    }



    public static HashMap<Integer, HashSet<Integer>> getRepoPRMapFromCrossRefTable() {
        HashMap<Integer, HashSet<Integer>> repo_issue_map = new HashMap<>();
        String query = "SELECT  projectID,  issue2_id\n" +
                "FROM crossReference\n" +
                "  WHERE issue1_type = 'pr' AND issue2_type = 'pr' AND projectID=ref_projectID\n" +
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

//    private static void parsingIssueParticipants_PR_map() {
//        IO_Process io = new IO_Process();
//        HashMap<Integer, HashSet<Integer>> repo_issue_map = getRepoPRMapFromCrossRefTable();
//        final int[] count = {0};
//        String query = "UPDATE fork.crossReference\n" +
//                "SET pre_communication = ?, same_owner = ?\n" +
//                "WHERE projectID = ? AND issue1_id = ? AND issue1_type = 'pr'\n" +
//                "      AND issue2_id = ? AND issue2_type = 'pr'\n" +
//                "      AND ref_projectID = ?";
//        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt_1 = conn1.prepareStatement(query);) {
//            conn1.setAutoCommit(false);
//
//
//            repo_issue_map.forEach((projectID, issueSet) -> {
//                String projectURL = io.getRepoUrlByID(projectID);
//                System.out.println(projectURL);
//                StringBuilder sb = new StringBuilder();
//                for (Integer issue_id : issueSet) {
//                    String file_name = passResult_dir + "get_issue_timeline." + projectURL.replace("/", ".") + "_" + issue_id + ".csv";
//                    if (new File(file_name).exists()) {
//                        String issue_owner =io. getIssueOwner(projectID, issue_id,"Pull_Request");
//                        HashSet<String> participants = new HashSet<>();
//                        if (!issue_owner.equals("")) {
//                            participants.add(issue_owner);
//                        }
//                        HashMap<Integer, Boolean> PR_participant = new HashMap<>();
//                        System.out.println(projectURL + " issue " + issue_id);
//                        List<List<String>> rows = io.readCSV(file_name);
//                        for (List<String> r : rows) {
//                            if (!r.get(0).equals("") & r.size() > 9) {
//                                String event_type = r.get(9);
//                                String event_author = r.get(2);
//                                if (event_type.equals("cross-referenced")) {
//                                    int prID = Integer.parseInt(r.get(10));
//                                    String ref_type = r.get(14);
//                                    if (ref_type.equals("pull_request")) {
//                                        String author = getPRAuthor(projectID, prID);
//
//                                        if (!author.equals("")) {
//                                            if (!event_author.equals(author)) {
//                                                System.out.println("event author: " + event_author + " pr " + prID + " author :" + author);
//                                            }
//                                            if (participants.contains(author)) {
//                                                System.out.println(author + " communicate before pr");
//                                                boolean pre_com = true;
//                                                boolean sameOwner = false;
//                                                if (author.equals(issue_owner)) {
//                                                    System.out.println("same owner " + author);
//                                                    sameOwner = true;
//                                                }
//                                                sb.append(projectID + "," + issue_id + "," + prID + "," + pre_com + "," + sameOwner + "\n");
//                                                try {
//                                                    preparedStmt_1.setBoolean(1, pre_com);
//                                                    preparedStmt_1.setBoolean(2, sameOwner);
//                                                    preparedStmt_1.setInt(3, projectID);
//                                                    preparedStmt_1.setInt(4, issue_id);
//                                                    preparedStmt_1.setInt(5, prID);
//                                                    preparedStmt_1.setInt(6, projectID);
//                                                    preparedStmt_1.addBatch();
//                                                } catch (SQLException e) {
//                                                    e.printStackTrace();
//                                                }
//
//
//                                                if (++count[0] % 100 == 0) {
//                                                    io.executeQuery(preparedStmt_1);
//                                                    try {
//                                                        conn1.commit();
//                                                    } catch (SQLException e) {
//                                                        e.printStackTrace();
//                                                    }
//                                                    System.out.println(count + " count ");
//                                                    io.writeTofile(sb.toString(), output_dir + "pre_comm.txt");
//                                                    sb = new StringBuilder();
//                                                }
//
//                                                PR_participant.put(prID, true);
//                                            }
//                                            participants.add(author);
//                                        }
//                                    }
//                                    participants.add(event_author);
//
//                                }
//                            }
//                        }
//                    } else {
//                        io.writeTofile(file_name + "\n", output_dir + "miss_issueTimeline.txt");
//                    }
//                }
//
//                io.writeTofile(sb.toString(), output_dir + "pre_comm.txt");
//            });
//
//            io.executeQuery(preparedStmt_1);
//            conn1.commit();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//    }




}
