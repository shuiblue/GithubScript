import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeGovernance {
    static String working_dir, pr_dir, output_dir, clone_dir, apiResult_dir, PR_ISSUE_dir;
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
        parsingPR_ISSUE_Line_byEvent();


        /** analyzing pre-processed csv file **/
        insertCrossRefToDatabase();
        insertRepo_issue_user_map();
        insertIssue_label();


    }

    private static void insertIssue_label() {
        IO_Process io = new IO_Process();
        String[] lines = {};
        try {
            lines = io.readResult(PR_ISSUE_dir + "issue_label.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String update_issue_query = "UPDATE fork.ISSUE\n" +
                "SET labels=?" +
                "\n WHERE issue_id = ? AND projectID=? ;\n";

        try (Connection conn_issue = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_issue = conn_issue.prepareStatement(update_issue_query);

        ) {
            conn_issue.setAutoCommit(false);

            for (String ref : lines) {
                String[] arr = ref.split(",");
                //projectID + "," + issue_id + "," + issue_type + labels
                int projectID = Integer.parseInt(arr[0]);
                int issue_id = Integer.parseInt(arr[1]);
                String issue_type = arr[2];
                String label = io.removeBrackets(arr[3]);

                if (issue_type.equals("issue")) {
                    preparedStmt_issue.setString(1, label);
                    preparedStmt_issue.setInt(3, projectID);
                    preparedStmt_issue.setInt(2, issue_id);

                    System.out.println(  preparedStmt_issue.toString());
                  System.out.println("affect "+ preparedStmt_issue.executeUpdate()+ "rows");
                }

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
                String[] arr = ref.split(",");
                //projectID + "," + issue_id + "," + issue_type + labels
                int projectID = Integer.parseInt(arr[0]);
                int issue_id = Integer.parseInt(arr[1]);
                String issue_type = arr[2];
                String label = io.removeBrackets(arr[3]);

                if (issue_type.equals("pr")) {
                    preparedStmt_pr.setString(1, label);
                    preparedStmt_pr.setInt(2, projectID);
                    preparedStmt_pr.setInt(3, issue_id);
                    System.out.println(  preparedStmt_pr.toString());
                    System.out.println("affect "+ preparedStmt_pr.executeUpdate()+ "rows");
                }

            }

            conn_pr.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    private static void insertRepo_issue_user_map() {
        IO_Process io = new IO_Process();
        String[] lines = {};
        int count = 0;
        try {
            lines = io.readResult(PR_ISSUE_dir + "issue_pariticipants.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "INSERT INTO Repo_Issue_Paritcipant_Map (projectID, issueID, issue_type, userID, author_association) VALUES (?,?,?,?,?)";

        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(query);) {
            conn1.setAutoCommit(false);

            for (String ref : lines) {
                System.out.println(ref);
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
                    full_name = arr[6];
                    email = arr[7];
                } else {
                    association = arr[4];
                }
                //loginID, String full_name,String email, String user_type
                int userID = io.insertUser(login_ID, full_name, email, type);
                preparedStmt_1.setInt(4, userID);

                preparedStmt_1.setString(5, association);

                preparedStmt_1.addBatch();
                System.out.print(count + " ...");

                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt_1);
                    System.out.println("insert 100 query for project " + projectID);
                    conn1.commit();
                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void insertCrossRefToDatabase() {
        IO_Process io = new IO_Process();
        String[] lines = {};
        int count = 0;
        try {
            lines = io.readResult(PR_ISSUE_dir + "crossRef.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "INSERT INTO fork.crossRef (projectID, issue1_id, issue1_type, issue2_id, issue2_type,  issue2_state, ref_projectURL)\n" +
                "VALUES (?,?,?,?,?,?,? )";

        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(query);) {
            conn1.setAutoCommit(false);

            for (String ref : lines) {
                System.out.println(ref);
                String[] arr = ref.split(",");


                //projectID + "," + issue_id + "," + issue_type + "," + ref_projectURL + "," + id + "," + ref_type + "," + state + "\n");
                int projectID = Integer.parseInt(arr[0]);
                preparedStmt_1.setInt(1, projectID);

                int issue1_id = Integer.parseInt(arr[1]);
                preparedStmt_1.setInt(2, issue1_id);

                String issue1_type = arr[2];
                preparedStmt_1.setString(3, issue1_type);

                String ref_projectURL = arr[3];
                preparedStmt_1.setString(7, ref_projectURL);

                int issue2_id = Integer.parseInt(arr[4]);
                preparedStmt_1.setInt(4, issue2_id);

                String issue2_type = arr[5];
                preparedStmt_1.setString(5, issue2_type.equals("pull_request") ? "pr" : "issue");

                String issue2_state = arr[6];
                preparedStmt_1.setString(6, issue2_state);
                preparedStmt_1.addBatch();
                System.out.print(count + " ...");

                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt_1);
                    System.out.println("insert 100 query for project " + projectID);
                    conn1.commit();
                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void parsingPR_ISSUE_Line_byEvent() {
        String[] repoList = new IO_Process().getRepoList("crossRef_repoList.txt");
        IO_Process io = new IO_Process();
        List<String> passedFile = null;
        try {
            passedFile = Arrays.asList(io.readResult(PR_ISSUE_dir + "/passedFile.txt").split("\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String repo : repoList) {
            int projectID = io.getRepoId(repo);
            HashSet<String> comment_userSet = new HashSet<>();
            HashMap<String, String> use_map = new HashMap<>();
            try {
                List<String> finalPassedFile = passedFile;
                Files.newDirectoryStream(Paths.get(apiResult_dir), path -> path.toFile().isFile())
                        .forEach(file -> {
                                    StringBuilder sb_crossRef = new StringBuilder();
                                    StringBuilder sb_issue_user = new StringBuilder();
                                    StringBuilder sb_issue_label = new StringBuilder();
                                    StringBuilder sb_event = new StringBuilder();
                                    StringBuilder sb_passedFile = new StringBuilder();
                                    String file_name = file.getFileName().toString();
                                    System.out.print(file_name);
                                    if ((file_name.contains("get_issue_timeline") || file_name.contains("get_pr_timeline"))
                                            && file_name.contains(repo.replace("/", "."))) {
                                        if (!finalPassedFile.contains(file_name)) {
                                            System.out.println(file_name);
                                            sb_passedFile.append(file_name + "\n");
                                            String[] arr = file_name.split("_");
                                            int issue_id = Integer.parseInt(arr[arr.length - 1].replace(".csv", ""));
                                            String issue_type = file_name.contains("get_issue_timeline") ? "issue" : "pr";
                                            HashSet<String> labels = new HashSet<>();

                                            List<List<String>> rows = io.readCSV(file.toFile());
                                            HashSet<String> loginID_set = new HashSet<>();
                                            for (List<String> r : rows) {
                                                if (!r.get(0).equals("")) {
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
                                                        login_id = full_name;
                                                        email = r.get(8);
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

                                            for (String login_id : loginID_set) {
                                                sb_issue_user.append(projectID + "," + issue_id + "," + issue_type + "," + login_id + "," + use_map.get(login_id) + "\n");
                                            }

                                            if (labels.size() > 0) {
                                                sb_issue_label.append(projectID + "," + issue_id + "," + issue_type + ",[");
                                                labels.forEach(p -> sb_issue_label.append(p + "/"));
                                                sb_issue_label.append("]\n");
                                            }

                                            io.writeTofile(sb_passedFile.toString(), PR_ISSUE_dir + "passedFile.txt");
                                            io.writeTofile(sb_crossRef.toString(), PR_ISSUE_dir + "crossRef.csv");
                                            io.writeTofile(sb_event.toString(), PR_ISSUE_dir + "eventList.csv");
                                            io.writeTofile(sb_issue_label.toString(), PR_ISSUE_dir + "issue_label.csv");
                                            io.writeTofile(sb_issue_user.toString(), PR_ISSUE_dir + "issue_pariticipants.csv");

                                        } else {

                                            System.out.println("skip " + file);
                                        }
                                    }
                                }
                        );
            } catch (IOException e) {
                e.printStackTrace();
            }

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
