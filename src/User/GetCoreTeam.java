package User;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class GetCoreTeam {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, apiResult_dir;
    static String myUrl, user, pwd;

    GetCoreTeam() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            apiResult_dir = output_dir + "shurui_timeline.cache/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GetCoreTeam getCoreTeam = new GetCoreTeam();
        getCoreTeam.getCoreTeamFromTimeline();
//
//        getCoreTeam.UpdatePRfromCoreTeam();
//        getCoreTeam.UpdatePRfromCoreTeamToDeveloperTable();
//        getCoreTeam.UpdateCoreTeamToFinalTable();


    }

    private void UpdateCoreTeamToFinalTable() {
        IO_Process io = new IO_Process();

        String[] repos = new String[0];
        try {
            repos = io.readResult(output_dir + "coreTeam0811.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> repoTeam = Arrays.asList(repos);
        HashMap<String, Integer> url_id = new HashMap<>();

        int count = 0;
        String updatePR = "UPDATE Final\n" +
                "SET num_coreMember =? \n" +
                "WHERE repoID = ? ";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(updatePR);) {
            conn1.setAutoCommit(false);

            for (String repoInfo : repoTeam) {
                String[] arr = repoInfo.split(",\\[");
                String url = arr[0].split(",")[0];
                int num_coreMember = Integer.parseInt(arr[0].split(",")[1]);
                int repoID;
                if (url_id.get(url) != null) {
                    repoID = url_id.get(url);
                } else {
                    repoID = io.getRepoId(url);
                    url_id.put(url, repoID);
                }


                System.out.println(num_coreMember + " in " + url);
                preparedStmt_1.setInt(1, num_coreMember);
                preparedStmt_1.setInt(2, repoID);
                preparedStmt_1.addBatch();

                System.out.println(count);
                System.out.println(preparedStmt_1.toString());
                if (++count % 100 == 0) {
                    io.executeQuery(preparedStmt_1);
                    System.out.println("update 100 pr for  " + url);
                    conn1.commit();

                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private void UpdatePRfromCoreTeamToDeveloperTable() {
        IO_Process io = new IO_Process();

        String[] repos = new String[0];
        try {
            repos = io.readResult(output_dir + "coreTeam0811.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> repoTeam = Arrays.asList(repos);
        HashMap<String, Integer> url_id = new HashMap<>();

        int count = 0;
        String updatePR = "UPDATE developer\n" +
                "SET isCoreMember = TRUE \n" +
                "WHERE projectID = ? AND loginID = ?";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(updatePR);) {
            conn1.setAutoCommit(false);

            for (String repoInfo : repoTeam) {
                String[] arr = repoInfo.split(",\\[");
                String url = arr[0].split(",")[0];
                int repoID;
                if (url_id.get(url) != null) {
                    repoID = url_id.get(url);
                } else {
                    repoID = io.getRepoId(url);
                    url_id.put(url, repoID);
                }


                String[] members = io.removeBrackets(arr[1]).split(",");

                for (String m : members) {
                    m = m.trim();
                    if (!m.equals("")) {
                        System.out.println(m + " in " + url);
                        preparedStmt_1.setInt(1, repoID);
                        preparedStmt_1.setString(2, m);
                        preparedStmt_1.addBatch();

                        System.out.println(count);
                        System.out.println(preparedStmt_1.toString());
                        if (++count % 100 == 0) {
                            io.executeQuery(preparedStmt_1);
                            System.out.println("update 100 pr for  " + url);
                            conn1.commit();
                        }
                    }
                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private void UpdatePRfromCoreTeam() {
        IO_Process io = new IO_Process();

        String[] repos = new String[0];
        try {
            repos = io.readResult(output_dir + "coreTeam0811.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> repoTeam = Arrays.asList(repos);

        int count = 0;
        String updatePR = "UPDATE Pull_Request\n" +
                "SET fromCoreTeam = TRUE \n" +
                "WHERE projectID = ? AND authorName = ?";
        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(updatePR);) {
            conn1.setAutoCommit(false);

            for (String repoInfo : repoTeam) {
                String[] arr = repoInfo.split(",\\[");
                String repoUrl = arr[0].split(",")[0];
                int repoID = io.getRepoId(repoUrl);
                String[] members = io.removeBrackets(arr[1]).split(",");

                for (String m : members) {
                    m = m.trim();
                    if (!m.equals("")) {
                        System.out.println(m + " in " + repoUrl);
                        preparedStmt_1.setInt(1, repoID);
                        preparedStmt_1.setString(2, m);
                        preparedStmt_1.addBatch();


                        System.out.println(count);
                        System.out.println(preparedStmt_1.toString());
                        if (++count % 5 == 0) {
                            io.executeQuery(preparedStmt_1);
                            System.out.println("update 100 pr for  " + repoUrl);
                            conn1.commit();
                        }
                    }
                }
            }
            io.executeQuery(preparedStmt_1);
            conn1.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private static String getIssueAuthor(int repoID, int issue_id, String issue_type) {
        String query;
        if (issue_type.equals("pr")) {
            query = "select authorName\n" +
                    "from Pull_Request\n" +
                    "where projectID =" + repoID + " and pull_request_ID = " + issue_id;
        } else {
            query = "  select author\n" +
                    "from ISSUE\n" +
                    "where projectID =" + repoID + " and issue_id= " + issue_id;
        }


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String author = rs.getString(1);
                if (!author.equals("")) {
                    return author;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }


    public void getCoreTeamFromTimeline() {
        IO_Process io = new IO_Process();
        List<String> repoList = io.getListFromFile("repoList.txt");
        HashMap<String, HashSet<String>> coreTeam = new HashMap<>();
        for (String repo : repoList) {
            coreTeam.put(repo, new HashSet<>());
            int repoID = io.getRepoId(repo);
            try {
                Files.newDirectoryStream(Paths.get(apiResult_dir), path -> path.toFile().isFile())
                        .forEach(file -> {
                                    String file_name = file.getFileName().toString();
                                    if ((file_name.startsWith("get_pr_timeline") || file_name.startsWith("get_issue_timeline"))
                                            && file_name.contains(repo.replace("/", ".") + "_")) {
                                        System.out.println(file_name);

                                        String[] arr = file_name.split("_");
                                        String issueID_str = arr[arr.length - 1].replace(".csv", "").replaceAll("\\D+", "");
                                        if (issueID_str.equals("")) {
                                            return;
                                        }
                                        int issue_id = Integer.parseInt(issueID_str);
                                        String issue_type = file_name.contains("get_issue_timeline") ? "issue" : "pr";
                                        List<List<String>> rows = io.readCSV(file.toFile());

                                        int merge_index = -1;
                                        for (int index = 0; index < rows.size(); index++) {
                                            List<String> r = rows.get(index);
                                            if (!r.get(0).equals("") & r.size() > 9) {
                                                String event_type = r.get(9);
                                                String closer;
                                                if (event_type.equals("closed")) {
                                                    closer = r.get(2);
                                                    if (!closer.equals("")) {
                                                        System.out.println(closer);
                                                        String author = getIssueAuthor(repoID, issue_id, issue_type);
                                                        if (!closer.trim().equals(author)) {
                                                            System.out.println(repo + "  " + event_type + " " + issue_id + " closed by " + closer);
                                                            HashSet<String> team = coreTeam.get(repo);
                                                            team.add(closer);
                                                            coreTeam.put(repo, team);
                                                        } else if (index == merge_index + 1) {
                                                            System.out.println(repo + "  " + event_type + " " + issue_id + " merged by " + closer);
                                                            HashSet<String> team = coreTeam.get(repo);
                                                            team.add(closer);
                                                            coreTeam.put(repo, team);
                                                        }

                                                    }
                                                }

                                                if (event_type.equals("merged")) {
                                                    merge_index = index;
                                                }
                                                if (event_type.equals("commented")) {
                                                    String commenter = r.get(2);
                                                    String association = r.get(3);
                                                    if (association.toUpperCase().equals("MEMBER") ||
                                                            association.toUpperCase().equals("OWNER") ||
                                                            association.toUpperCase().equals("COLLABORATOR")) {
                                                        System.out.println(association + " " + commenter);
                                                        HashSet<String> team = coreTeam.get(repo);
                                                        team.add(commenter);
                                                        coreTeam.put(repo, team);
                                                    }
                                                }

                                            }
                                        }
                                    }

                                }
                        );
            } catch (IOException e) {
                e.printStackTrace();
            }

            HashSet<String> memberSet = coreTeam.get(repo);
            System.out.println(repo + ", " + coreTeam.get(repo).size() + " core member.");
            io.writeTofile(repo + "," + memberSet.size() + "," + memberSet.toString() + "\n", output_dir + "coreTeam0811.txt");
        }
    }
}
