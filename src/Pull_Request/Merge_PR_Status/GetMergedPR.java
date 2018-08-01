package Pull_Request.Merge_PR_Status;

import Util.IO_Process;

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
            timeline_dir = output_dir + "shurui_timeline.cache/";
//            timeline_dir = output_dir + "shurui_crossRef.cache/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public HashMap<Integer, HashSet<Integer>> getClosedPR() {
        HashMap<Integer, HashSet<Integer>> rejectedPR = new HashMap<>();
        String query = "SELECT projectID,pull_request_ID\n" +
                "FROM Pull_Request\n";
//                "WHERE merge_allType = FALSE AND Pull_Request.merge_2Ref IS NULL AND closed = 'true'; ";
//                "AND " +
//                "(Pull_Request.merge_2 IS NULL OR Pull_Request.merge_3 IS NULL OR Pull_Request.merge_4 IS NULL);";
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
//        HashMap<Integer, HashSet<Integer>> rejectedPR = getMergedPR.getClosedPR();
//        rejectedPR.forEach((projectID, prSet) -> {
//            getMergedPR.checkPRstatus(projectID, prSet);
//        });


    }

    private void checkPRstatus(Integer projectID, HashSet<Integer> prSet) {
        IO_Process io = new IO_Process();
        String projectURL = io.getRepoUrlByID(projectID);
        if (projectURL.equals("")) return;


        for (int pr : prSet) {
            getEachPR(projectURL, pr);
        }

        try {
            io.deleteDir(new File(clone_dir + projectURL));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean containsPRid(String lastComment) {
        Pattern p1 = Pattern.compile("(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)");
        Matcher matcher = p1.matcher(lastComment.toLowerCase());

        Pattern p_issueComment = Pattern.compile("(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)#issuecomment-");
        Matcher matcher_issueComment = p_issueComment.matcher(lastComment.toLowerCase());

        if (matcher.find()) {
            String pr_url = matcher.group();
            System.out.println(pr_url);

            if (matcher_issueComment.find()) {
                String issueComment_url = matcher_issueComment.group();
                System.out.println(issueComment_url);

                if (!issueComment_url.contains(pr_url.replace("#", ""))) {
                    System.out.println(pr_url);
                    return true;
                }
            } else {
                System.out.println(pr_url);
                return true;
            }
        }
        return false;
    }

    private boolean hasType_4_MergeKeywords(String body) {
        //todo add https://github.com/nodejs/node/pull/3174
        //todo check : https://github.com/nodejs/node/pull/113  landed
        //https://github.com/angular/angular.js/pull/1127

        Pattern p1 = Pattern.compile("(merg|apply|appl|pull|push|integrat|land|cherry(-|\\s+)pick|squash)(ing|i?ed)");
        Matcher matcher = p1.matcher(body.toLowerCase());
        if (matcher.find()) {
            String text = matcher.group(1);
            System.out.println(text);
            return true;
        }
        return false;
    }


    private boolean hasMergeKeywords(String body) {
        //todo add https://github.com/nodejs/node/pull/3174
        //todo check : https://github.com/nodejs/node/pull/113  landed
        //https://github.com/angular/angular.js/pull/1127

        Pattern p1 = Pattern.compile("(merg|apply|appl|pull|push|integrat|land|cherry(-|\\s+)pick|squash|fix)(ing|i?ed)"); // todo  add  fix, done
        Matcher matcher = p1.matcher(body.toLowerCase());
        if (matcher.find()) {
            String text = matcher.group();
            System.out.println(text);
            return true;
        }

        Pattern p2 = Pattern.compile("fix(es|ed)"); // todo  add  fix, done
        Matcher matcher2 = p2.matcher(body.toLowerCase());
        if (matcher2.find()) {
            String text = matcher2.group();
            System.out.println(text);
            return true;
        }

        Pattern p3 = Pattern.compile("done"); // todo   done
        Matcher matcher3 = p3.matcher(body.toLowerCase());
        if (matcher3.find()) {
            String text = matcher3.group();
            System.out.println(text);
            return true;
        }

        Pattern p4 = Pattern.compile("clos(e|ed|ing)"); // todo   close
        Matcher matcher4 = p4.matcher(body.toLowerCase());
        if (matcher4.find()) {
            String text = matcher4.group();
            System.out.println(text);
            return true;
        }

        return false;
    }


    private String containsPotentialSHA(String body) {
        Pattern pattern = Pattern.compile("\\b[0-9a-f]{7}|\\b[0-9a-f]{40}\\b");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String sha = matcher.group();
            System.out.println("potential sha :" + sha);
            return sha;
        }
        return "";
    }


    private boolean containsMergedSHA(String sha, String projectURL) {

        System.out.println("potential sha :" + sha);

        if (commitIsMerged(sha, projectURL)) {
            System.out.println("is a merged commit");
            return true;
        } else {
            System.out.println(" does not merged");
        }


        return false;
    }

    public boolean commitIsMerged(String sha, String projectURL) {
        IO_Process io = new IO_Process();
        io.cloneRepo("", projectURL);
        String cloneForkCmd = "git cat-file -t " + sha;
        if (io.exeCmd(cloneForkCmd.split(" "), clone_dir + projectURL + "/").trim().equals("commit")) {
            return true;
        }
        return false;


    }


    public void getEachPR(String projectURL, int pr) {
//        projectURL = "twbs/bootstrap";
//        pr = 8;
//        docker/docker,24731
//        docker/docker,8348

        System.out.println(projectURL + " , pr " + pr);

        IO_Process io = new IO_Process();
        String timeline_file = timeline_dir + "get_pr_timeline." + projectURL.replace("/", ".") + "_" + pr + ".csv";
//        String timeline_file = "/Users/shuruiz/Work/get_pr_timeline.twbs.bootstrap_3072.csv";
        if (new File(timeline_file).exists()) {

            List<List<String>> timeline = io.readCSV(timeline_file);
            int close_event_index = 0;
            HashSet<String> referencedSHA = new HashSet<>();

            for (int i = timeline.size() - 1; i > 0; i--) {
                List<String> line = timeline.get(i);
                if (line.size() > 9) {
                    String event = line.get(9);
                    String sha = line.get(6);

                    /**  type 2:  check pr is closed by a commit*/
                    // https://blog.github.com/2013-01-22-closing-issues-via-commit-messages/
                    if (event.equals("closed")) {
                        close_event_index = i;
                        if (!sha.equals("")) {
                            io.writeTofile(projectURL + "," + pr + ",merged-2" + "\n", output_dir + "update_mergedPR.txt");
                            System.out.println(projectURL + "," + pr + ",merged-2");
                            return;
                        }
                    }
                    if (event.equals("merged")) {
                        io.writeTofile(projectURL + "," + pr + ",merged-1" + "\n", output_dir + "update_mergedPR.txt");
                        System.out.println(projectURL + "," + pr + ",merged-1");
                        return;

                    }

                    if (event.equals("referenced") && !sha.equals("")) { // // https://github.com/moby/moby/pull/34248
                        // todo: close PR and merge through other commit, only by refering to it?
                        // https://stackoverflow.com/questions/17818167/find-a-pull-request-on-github-where-a-commit-was-originally-created
                        // https://blog.github.com/2014-10-13-linking-merged-pull-requests-from-commits/
                        // https://blog.github.com/2013-04-01-branch-and-tag-labels-for-commit-pages/
                        referencedSHA.add(sha);
                    }
                }
            }


            if (close_event_index == 0) {
                close_event_index = timeline.size();
            }

            /**   type 3: check the last 3 comment before closing the pr contains keywords and sha*/
            ArrayList<String> last3Comments = new ArrayList<>();
            boolean checkLastComment = false;
            for (int s = close_event_index - 1; s > 0; s--) {
                List<String> comment_candidate = timeline.get(s);
                if (comment_candidate.size() > 9) {
                    String event = comment_candidate.get(9);
                    if (event.equals("commented")) {
                        String comment = comment_candidate.get(5);
                        if (!comment.equals("")) {
                            last3Comments.add(comment);
                        }
                        if (last3Comments.size() == 3) {
                            break;
                        }
                    }

                    /**   type 5, 4: check the comment before closing the pr */
                    if (!checkLastComment && last3Comments.size() == 1) {
                        checkLastComment = true;
                        String lastComment = last3Comments.get(0);

                        /**   type 5: check the comment before closing the pr contains Merged SHA*/
                        String potentialSHA = containsPotentialSHA(lastComment);
                        if (!potentialSHA.equals("")
                                && !lastComment.contains("codecov")) {
                            boolean shaMerged = containsMergedSHA(potentialSHA, projectURL);

                            //hardcode for moby or docker
                            if (!io.isDuplicateComment(lastComment)) {
                                if (shaMerged) {
                                    io.writeTofile(projectURL + "," + pr + ",merged-5" + "\n", output_dir + "update_mergedPR.txt");
                                    System.out.println(projectURL + "," + pr + ",merged-5");
                                    return;
                                }
                            } else {

                                if (containsPRid(lastComment) || shaMerged) { //todo sha should be merged?
                                    io.writeTofile(projectURL + "," + pr + "," + lastComment.replaceAll("[,]", "    ").replaceAll("[\\s]+", " ") + "\n", output_dir + "duplicateComment.txt");
                                    return;
                                }
                            }
                        }


                        /**   type 4: check the comment before closing the pr contains keywords*/
                        if (!containsPRid(lastComment)) {
                            if (hasType_4_MergeKeywords(lastComment)) {
                                io.writeTofile(projectURL + "," + pr + ",merged-4" + "\n", output_dir + "update_mergedPR.txt");
                                System.out.println(projectURL + "," + pr + ",merged-4");
                                return;
                            }
                        } else {
                            io.writeTofile(projectURL + "," + pr + " " + "\n", output_dir + "rejectedPR.txt");
                            return;
                        }
                    }
                }
            }


            /**   type 3: check the last 3 comment before closing the pr contains keywords and sha*/

            for (String comment : last3Comments) {
                boolean shaMerged = false;
                if (hasMergeKeywords(comment)) {
                    System.out.println(" comment has keywords");
                    String potentialSHA = containsPotentialSHA(comment);
                    if (!potentialSHA.equals("")) {
                        shaMerged = containsMergedSHA(potentialSHA, projectURL);
                    }
                    if (shaMerged) {
                        System.out.println(" commit merged");
                    } else {
                        System.out.println(" SHA is not found/merged");
                    }
                }
                if (!comment.equals("") && shaMerged) {
                    System.out.println(projectURL + ", pr# " + pr + " is merged-3");
                    io.writeTofile(projectURL + "," + pr + ",merged-3" + "\n", output_dir + "update_mergedPR.txt");
                    return;
                }
            }

            if (referencedSHA.size() > 0) {
                io.writeTofile(projectURL + "," + pr + "," + referencedSHA.toString() + "\n", output_dir + "referenceCommit.txt");
            }

            boolean merge2Ref = false;
            for (String refSHA : referencedSHA) {
                if (commitIsMerged(refSHA, projectURL)) {
                    System.out.println(projectURL + ", pr# " + pr + " is merged-2r");
                    io.writeTofile(projectURL + "," + pr + ",merged-2ref，"+refSHA + "\n", output_dir + "update_mergedPR.txt");
                    merge2Ref = true;
                }
            }

            if(merge2Ref==true) return;

            io.writeTofile(projectURL + "," + pr + " " + "\n", output_dir + "rejectedPR.txt");
        } else {
            io.writeTofile(projectURL + "," + pr + "\n", output_dir + "miss_timeline.txt");
        }

    }


}
