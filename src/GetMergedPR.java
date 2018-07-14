import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetMergedPR {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir;
    static String myUrl, user, pwd;

    GetMergedPR() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            timeline_dir = output_dir + "shurui_crossRef.cache_pass/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public HashMap<Integer, HashSet<Integer>> getClosedPR() {
        HashMap<Integer, HashSet<Integer>> rejectedPR = new HashMap<>();
        String query = "SELECT projectID, pull_request_ID\n" +
                "FROM fork.Pull_Request\n" +
                "WHERE merged = 'false' AND closed = 'true' AND " +
                "(Pull_Request.merge_2 IS NULL OR Pull_Request.merge_3 IS NULL OR Pull_Request.merge_4 IS NULL);";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int projectID = rs.getInt(1);
                int pr_ID = rs.getInt(2);
                HashSet<Integer> prSet = new HashSet<>();
                if (rejectedPR.get(projectID) != null) {
                    prSet = rejectedPR.get(projectID);
                }
                prSet.add(pr_ID);
                rejectedPR.put(projectID, prSet);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rejectedPR;
    }

    public static void main(String[] args) {
        GetMergedPR getMergedPR = new GetMergedPR();
        HashMap<Integer, HashSet<Integer>> rejectedPR = getMergedPR.getClosedPR();



        rejectedPR.forEach((projectID, prSet) -> {
            getMergedPR.checkPRstatus(projectID, prSet);
        });


    }

    private void checkPRstatus(Integer projectID, HashSet<Integer> prSet) {
        IO_Process io = new IO_Process();
        String projectURL = io.getRepoUrlByID(projectID);

        boolean cloneExist = false;

        HashSet<String> prSet_status = new HashSet<>();
        for (int pr : prSet) {

                String timeline_file = timeline_dir + "get_pr_timeline." + projectURL.replace("/", ".") + "_" + pr + ".csv";
                if (new File(timeline_file).exists()) {
                    if (!cloneExist) {
                        io.cloneRepo("", projectURL);
                        cloneExist=true;
                    }
                    List<List<String>> timeline = io.readCSV(timeline_file);
                    int close_event_index = 0;
                    for (int i = 1; i < timeline.size(); i++) {
                        List<String> line = timeline.get(i);
                        if (line.size() > 9) {
                            String event = line.get(9);
                            String sha = line.get(6);


                            /**  type 2:  check pr is closed by a commit*/
                            if (event.equals("closed")) {
                                close_event_index = i;
                                if (!sha.equals("")) {
                                    prSet_status.add(pr + ",merged-2");
                                    io.writeTofile(projectURL + "," + pr + ",merged-2" + "\n", output_dir + "update_mergedPR.txt");
//                            System.out.println(projectURL + "," + pr + ",merged-2");
                                    break;
                                }
                            }
                        }
                    }

                    /**   type 3: check the last 3 comment before closing the pr contains keywords and sha*/
                    String comment = "";
                    int count_comment = 0;
                    boolean isMerge4 = false;
                    for (int s = close_event_index - 1; s > 0; s--) {
                        List<String> comment_candidate = timeline.get(s);
                        if (comment_candidate.size() > 9) {
                            String event = comment_candidate.get(9);
                            if (event.equals("commented")) {
                                count_comment++;
                                comment += comment_candidate.get(5);
                                if (count_comment == 3) {
                                    break;
                                }
                            }

                            /**   type 4: check the comment before closing the pr contains keywords*/
                            if (count_comment == 1) {
                                if (checkPRIsMerged(comment)) {
                                    prSet_status.add(pr + ",merged-4");
//                            System.out.println(projectURL + ", pr# " + pr + " is merged-4");
                                    io.writeTofile(projectURL + "," + pr + ",merged-4" + "\n", output_dir + "update_mergedPR.txt");
                                    isMerge4 = true;
                                    break;
                                }
                            }
                        }
                    }


                    /**   type 3: check the last 3 comment before closing the pr contains keywords and sha*/
                    if (!isMerge4) {
                        System.out.println(projectURL + ", " + pr + " Check type-3 merge:");
                        boolean commentHasKeywords = checkPRIsMerged(comment);
                        boolean shaMerged = false;
                        if (commentHasKeywords) {
                            System.out.println(" comment has keywords");
                            shaMerged = containsMergedSHA(comment, projectURL);
                            if (shaMerged) {
                                System.out.println(" commit merged");
                            } else {
                                System.out.println(" SHA is not found/merged");
                            }
                        }
                        if (!comment.equals("") && shaMerged) {
                            prSet_status.add(pr + ",merged-3");
                            System.out.println(projectURL + ", pr# " + pr + " is merged-3");
                            io.writeTofile(projectURL + "," + pr + ",merged-3" + "\n", output_dir + "update_mergedPR.txt");
                            break;
                        }
                        io.writeTofile(projectURL + "," + pr + " " + "\n", output_dir + "check_type3_mergedPR.txt");
                    }
                } else {
                    io.writeTofile(projectURL + "," + pr + "\n", output_dir + "miss_timeline.txt");
                }

        }
        try {
            io.deleteDir(new File(clone_dir + projectURL));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean checkPRIsMerged(String body) {
//        Pattern pattern = Pattern.compile("(?:merg|appl|pull|push|integrat)(?:ing|i?ed)");
        Pattern pattern = Pattern.compile("\\b(merg|apply|appl|pull|push|integrat)(ing|i?ed)\\b");
//        Pattern pattern = Pattern.compile("\\b(merg|apply|appl|pull|push|integrat|add)(ing|i?ed)\\b");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String text = matcher.group(1);
            System.out.println(text);
            return true;
        }
        return false;
    }

    private boolean containsMergedSHA(String body, String projectURL) {
        Pattern pattern = Pattern.compile("\\b[0-9a-f]{5,40}\\b");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String sha = matcher.group();
            System.out.println("potential sha :" + sha);
            if (commitIsMerged(sha, projectURL)) {
                System.out.println("is a merged commit");
                return true;
            }
        }

        return false;
    }

    private boolean commitIsMerged(String sha, String projectURL) {
        IO_Process io = new IO_Process();

        String cloneForkCmd = "git cat-file -t " + sha;
        if (io.exeCmd(cloneForkCmd.split(" "), clone_dir + projectURL + "/").trim().equals("commit")) {
            return true;
        }
        return false;


    }


}
