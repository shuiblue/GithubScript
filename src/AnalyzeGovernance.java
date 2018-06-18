import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeGovernance {
    static String working_dir, pr_dir, output_dir, clone_dir;
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
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
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
