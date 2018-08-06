package Pull_Request;

import Pull_Request.Merge_PR_Status.GetMergedPR;
import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class AnalyzePRComment {

    static String working_dir, output_dir,clone_dir,timeline_dir;
    static String myUrl, user, pwd;

    AnalyzePRComment() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            timeline_dir = output_dir + "shurui_timeline.cache/";
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

//        detectDuplicateRelatedComments(repos);

//        calculatePRCommentLength(repos);
//        calculatePRCommentLength_fromTimeline(repos);
        calculatePRCommentLength_fromTimeline_new(repos);
    }

    private static void calculatePRCommentLength_fromTimeline_new(String[] repos) {
        AnalyzePRComment analyzePRComment = new AnalyzePRComment();
        HashMap<Integer, HashSet<Integer>> PR_set = analyzePRComment.getPR_noCommentParticipantsAnalysis();
        PR_set.forEach((projectID, prSet) -> {
            analyzePRComment.checkCommentsAndParticipants(projectID, prSet);
        });

    }

    private void checkCommentsAndParticipants(Integer projectID, HashSet<Integer> prSet) {
        IO_Process io = new IO_Process();
        String projectURL = io.getRepoUrlByID(projectID);
        if (projectURL.equals("")) return;
        String insert_query_1 = " UPDATE fork.Pull_Request" +
                " SET num_comments_before_close = ? , num_comments_after_close = ? ,num_participants_before_close = ? , num_participants_after_close = ?  " +
                "WHERE projectID = ? AND pull_request_ID=? ";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
            conn.setAutoCommit(false);
            final int[] count = {0};
            for (int pr_id: prSet) {
                System.out.println(projectURL + " , pr " + pr_id);

                String timeline_file = timeline_dir + "get_pr_timeline." + projectURL.replace("/", ".") + "_" + pr_id + ".csv";
//        String timeline_file = "/Users/shuruiz/Work/get_pr_timeline.twbs.bootstrap_3072.csv";
                if (new File(timeline_file).exists()) {

                    List<List<String>> rows = io.readCSV(timeline_file);


                    HashSet<String> participants = new HashSet<>();
                    int num_comments = 0;
                    int num_comments_before_close = 0;
                    int num_comments_after_close;


                    int num_participants_before_close = 0;
                    int num_participants_after_close;
                    boolean hasCloseEvent = false;
                    for (int i = 1; i < rows.size() - 1; i++) {
                        List<String> line = rows.get(i);
                        if (line.size() > 9) {
                            String event = line.get(9);
                            String loginID = line.get(2);
                            participants.add(loginID);
                            if (event.equals("closed")) {
                                hasCloseEvent = true;
                                num_comments_before_close = num_comments;
                                num_participants_before_close = participants.size();
                            } else if (event.equals("commented")) {
                                num_comments++;
                            }

                        }
                    }

                    if (!hasCloseEvent) {
                        num_comments_before_close = num_comments;
                        num_participants_before_close = participants.size();
                    }
                    num_comments_after_close = num_comments;
                    num_participants_after_close = participants.size();
                    try {
                        preparedStmt.setInt(1, num_comments_before_close);
                        preparedStmt.setInt(2, num_comments_after_close);
                        preparedStmt.setInt(3, num_participants_before_close);
                        preparedStmt.setInt(4, num_participants_after_close);

                        preparedStmt.setInt(5, projectID);
                        preparedStmt.setInt(6, pr_id);
                        System.out.println(projectURL + "," + pr_id + "," + num_comments_before_close + "," + num_comments_after_close + ","
                                + num_participants_before_close + "," + num_participants_after_close + ",");
                        preparedStmt.addBatch();
                        int batchSize = 100;
                        if (++count[0] % batchSize == 0) {
                            System.out.println("update  " + count[0] + "comments from repo" + projectURL);
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }else {
                    io.writeTofile(projectURL + "," + pr_id + "\n", output_dir + "miss_timeline.txt");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            io.deleteDir(new File(clone_dir + projectURL));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private static void calculatePRCommentLength_fromTimeline(String[] repos) {

        IO_Process io = new IO_Process();
        final int[] count = {0};
        for (String projectUrl : repos) {
            int projectID = io.getRepoId(projectUrl);

            String insert_query_1 = " UPDATE fork.Pull_Request" +
                    " SET num_comments_before_close = ? , num_comments_after_close = ? ,num_participants_before_close = ? , num_participants_after_close = ?  " +
                    "WHERE projectID = ? AND pull_request_ID=? ";
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                conn.setAutoCommit(false);

                Files.newDirectoryStream(Paths.get(output_dir + "shurui_timeline.cache/"), path -> path.toFile().isFile())
                        .forEach(file -> {
                                    String file_name = file.getFileName().toString();
                                    if ((file_name.startsWith("get_pr_timeline"))
                                            && file_name.contains(projectUrl.replace("/", ".") + "_")) {
                                        System.out.println(file_name);
                                        String[] arr = file_name.split("_");
                                        String prID_str = arr[arr.length - 1].replace(".csv", "").replace(".0", "").replaceAll("\\D+","");;
                                        if (prID_str.equals("")) {
                                            return;
                                        }
                                        int pr_id = Integer.parseInt(prID_str);
                                        List<List<String>> rows = io.readCSV(file.toFile());

                                        HashSet<String> participants = new HashSet<>();
                                        int num_comments = 0;
                                        int num_comments_before_close = 0;
                                        int num_comments_after_close;


                                        int num_participants_before_close = 0;
                                        int num_participants_after_close;
                                        boolean hasCloseEvent = false;
                                        for (int i = 1; i < rows.size() - 1; i++) {
                                            List<String> line = rows.get(i);
                                            if (line.size() > 9) {
                                                String event = line.get(9);
                                                String loginID = line.get(2);
                                                participants.add(loginID);
                                                if (event.equals("closed")) {
                                                    hasCloseEvent = true;
                                                    num_comments_before_close = num_comments;
                                                    num_participants_before_close = participants.size();
                                                } else if (event.equals("commented")) {
                                                    num_comments++;
                                                }

                                            }
                                        }

                                        if (!hasCloseEvent) {
                                            num_comments_before_close = num_comments;
                                            num_participants_before_close = participants.size();
                                        }
                                        num_comments_after_close = num_comments;
                                        num_participants_after_close = participants.size();
                                        try {
                                            preparedStmt.setInt(1, num_comments_before_close);
                                            preparedStmt.setInt(2, num_comments_after_close);
                                            preparedStmt.setInt(3, num_participants_before_close);
                                            preparedStmt.setInt(4, num_participants_after_close);

                                            preparedStmt.setInt(5, projectID);
                                            preparedStmt.setInt(6, pr_id);
                                            System.out.println(projectUrl + "," + pr_id + "," + num_comments_before_close + "," + num_comments_after_close + ","
                                                    + num_participants_before_close + "," + num_participants_after_close + ",");
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


    private static void calculatePRCommentLength(String[] repos) {

        IO_Process io = new IO_Process();
        final int[] count = {0};
        for (String projectUrl : repos) {
            int projectID = io.getRepoId(projectUrl);

            String insert_query_1 = " UPDATE fork.Pull_Request" +
                    " SET num_comments = ?  " +
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
                                        List<List<String>> rows = io.readCSV(file.toFile());


                                        try {
                                            preparedStmt.setInt(1, rows.size());
                                            preparedStmt.setInt(2, projectID);
                                            preparedStmt.setInt(3, pr_id);
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

    private static void detectDuplicateRelatedComments(String[] repos) {
        IO_Process io = new IO_Process();
        final int[] count = {0};
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
//                                                isDup = io.isDuplicatePR(comment.toLowerCase());
                                                isDup = io.isDuplicateComment(comment.toLowerCase());
                                                if (isDup) {
                                                    dup_comments.add(comment);
                                                    return;
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

    public HashMap<Integer,HashSet<Integer>> getPR_noCommentParticipantsAnalysis() {

        HashMap<Integer, HashSet<Integer>>  PR_set = new HashMap<>();
        String query = "SELECT projectID,pull_request_ID\n" +
                "FROM Pull_Request\n"+
                "WHERE num_comments_before_close is null; ";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int projectID = rs.getInt(1);
                int pr_ID = rs.getInt(2);
                HashSet<Integer> prSet = new HashSet<>();
                if (PR_set.get(projectID) != null) {
                    prSet = PR_set.get(projectID);
                }
                prSet.add(pr_ID);
                PR_set.put(projectID, prSet);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return PR_set;

    }

//    private static void parsingIssueParticipants_PR_map() {
//        Util.IO_Process io = new Util.IO_Process();
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
