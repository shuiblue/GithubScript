package Pull_Request;

import Commit.AnalyzeCommitChangedFile;
import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class AnalyzedPRHotness {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    static IO_Process io = new IO_Process();

    AnalyzedPRHotness() {
        current_dir = System.getProperty("user.dir");
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
        int timeWwindowInDays = 90;
        AnalyzedPRHotness analyzedPRHotness = new AnalyzedPRHotness();
        List<String> repos = io.getList_byFilePath(current_dir + "/input/file_repoList.txt");
        System.out.println(repos.size() + " projects ");

        for (String projectURL : repos) {
//            int projectID = io.getRepoId(projectURL);
            int projectID = 6;
            System.out.println("projectID: " + projectID);
            System.out.println("get all PR for " + projectURL);
            List<Integer> prSet = io.getClosedPRList(projectID);

            for (int prID : prSet) {
//                prID = 13;
                System.out.println("\npr: " + prID);
                HashMap<String, Set<String>> commit_file_map = analyzedPRHotness.getChangedFileForEachCommits(projectID, prID);
                System.out.println(commit_file_map.keySet().size()+" commits in pr");

                if (commit_file_map.keySet().size() == 0) continue;

                Set<String> hotFileSet = analyzedPRHotness.getHotFiles(projectID, prID, timeWwindowInDays);
                if (hotFileSet.size() == 0) continue;
                System.out.println(hotFileSet.size()+" hot files");


                HashMap<String, Integer> commit_numOfHotFile = new HashMap<>();
                commit_file_map.forEach((sha, set) -> {
                    int count = 0;
                    for (String file : set) {
                        if (hotFileSet.contains(file)) count++;
                    }
                    if (count > 0) {
                        commit_numOfHotFile.put(sha, count);
                    }
                });
                analyzedPRHotness.updateCommitHotness(commit_numOfHotFile);
            }

        }


    }

    private void updateCommitHotness(HashMap<String, Integer> commit_numOfHotFile) {

        final int[] count = {0};
        String query = "UPDATE fork.Commit SET num_hotFiles_touched = ? WHERE SHA = ?";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            conn.setAutoCommit(false);
            commit_numOfHotFile.forEach((sha, numFile) -> {
                try {
                    preparedStmt.setInt(1, numFile);
                    preparedStmt.setString(2, sha);

                    preparedStmt.addBatch();
                    if (++count[0] % 100 == 0) {
                        io.executeQuery(preparedStmt);
                        try {
                            conn.commit();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        System.out.println(count + " count ");

                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }


            });

            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private HashMap<String, Set<String>> getChangedFileForEachCommits(int projectID, int prID) {
        String query = "SELECT cc.commit_SHA,\n" +
                "  cc.concat_AllChangedFile_ExceptDeleteFile\n" +
                "FROM commit_changedFiles cc\n" +
                "  RIGHT JOIN PR_Commit_map prc ON prc.sha = cc.commit_SHA  and cc.concat_changedFile_exist_int=1\n" +
                "WHERE prc.projectID = " + projectID +
                " AND prc.pull_request_ID =  " + prID ;

        HashMap<String, Set<String>> commitFileMap = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String sha = rs.getString(1);
                String file = rs.getString(2);

                Set<String> fileSet = new HashSet<>();
                if (commitFileMap.get(sha) != null) {
                    fileSet = commitFileMap.get(sha);
                }
                fileSet.add(file);
                commitFileMap.put(sha, fileSet);
                System.out.print("");

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commitFileMap;


    }

    private Set<String> getHotFiles(int projectID, int prID, int timeWwindowInDays) {
        String query = "SELECT DISTINCT concat(cc.added_file, cc.modified_file, cc.renamed_file, cc.copied_file) AS cur_files\n" +
                "         FROM Commit c\n" +
                "   LEFT JOIN PR_Commit_map prc ON c.projectID = prc.projectID AND c.SHA = prc.sha\n" +
                "  LEFT JOIN commit_changedFiles cc ON c.SHA = cc.commit_SHA\n" +
                "   WHERE c.projectID =" +projectID+
                "     AND prc.pull_request_ID IN (SELECT pr2.pull_request_ID\n" +
                "                                 FROM Pull_Request pr1\n" +
                "                                   INNER JOIN Pull_Request AS pr2\n" +
                "                                     ON pr1.projectID = " + projectID+
                "                                           AND pr1.pull_request_ID = " +projectID+
                "                                           AND pr1.projectID = pr2.projectID AND pr2.closed = 'true'\n" +
                "                                        AND pr2.age_in_day BETWEEN pr1.age_in_day + 1 AND pr1.age_in_day +" +timeWwindowInDays+
                ")\n" +
                "    AND concat(cc.added_file, cc.modified_file, cc.renamed_file, cc.copied_file) != ''";

        HashSet<String> fileSet = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                fileSet.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return fileSet;

    }


    private List<Integer> getOlderPRInTimeWindow(int projectID, int prID, int timeWwindowInDays) {
        String query = "SELECT\n" +
//                "  pr1.projectID,\n" +
//                "  pr1.pull_request_ID AS pr1,\n" +
                "  pr2.pull_request_ID AS pr2\n" +
                "FROM Pull_Request pr1\n" +
                "  INNER JOIN Pull_Request AS pr2\n" +
                "    ON pr1.projectID = " + projectID +
                "       AND pr1.pull_request_ID = " + prID +
                "       AND pr1.projectID = pr2.projectID  AND pr2.closed = 'true'\n" +
                "       AND pr2.age_in_day BETWEEN pr1.age_in_day + 1 AND pr1.age_in_day +" + timeWwindowInDays;

        List<Integer> allcommits = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allcommits.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allcommits;

    }

}
