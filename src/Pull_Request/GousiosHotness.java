package Pull_Request;

import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.util.*;

public class GousiosHotness {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    static IO_Process io = new IO_Process();

    GousiosHotness() {
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
        GousiosHotness gousiosHotness = new GousiosHotness();
        List<String> repos = io.getList_byFilePath(current_dir + "/input/repoList.txt");
        System.out.println(repos.size() + " projects ");
        for (String projectURL : repos) {
            int projectID = io.getRepoId(projectURL);
            System.out.println("projectID: " + projectID + ", " + projectURL);
            System.out.println("get all PR that has not been analyzed for hotness , repo: " + projectURL);
            List<Integer> prSet = io.getClosedPRList_partial(projectID);
            System.out.println(prSet.size() + " pr to go....");
            int num_analyzedPR = 0;
            final int[] count_db = {0};
            String query = "UPDATE fork.Pull_Request pr\n" +
                    "SET pr.GousiosHotness_partial = ?\n" +
                    "WHERE pr.projectID = ? AND pr.pull_request_ID = ?";

            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(query);
            ) {
                conn.setAutoCommit(false);
                for (int prID : prSet) {

                    System.out.println((num_analyzedPR++) + "/" + prSet.size() + " pr analyzed");
                    System.out.println(projectURL + " pr: " + prID);

                    // step 1: get changed files for this PR
                    Set<String> prFileSet = gousiosHotness.getFiles_partial(projectID, prID);
                    if (prFileSet.size() == 0) {
                        System.out.println(" no  file");
                        continue;
                    }

                    final int[] hotness = {0};
                    //step 2: get commits in last 3 month
                    Set<String> commitSet = gousiosHotness.getCommitsIn3Month(projectID, prID);
                    if (commitSet.size() > 0) {
                        HashMap<String, Set<String>> commit_files_map = gousiosHotness.getCommitFileMaps(commitSet);

                        commit_files_map.forEach((sha, files) -> {
                            if (!Collections.disjoint(prFileSet, files)) {
                                hotness[0]++;
                            }
                        });
                    }

                    System.out.println(hotness[0] + " commits  touch these files");

                    preparedStmt.setInt(1, hotness[0]);
                    preparedStmt.setInt(2, projectID);
                    preparedStmt.setInt(3, prID);

                    preparedStmt.addBatch();
                    if (++count_db[0] % 100 == 0) {
                        io.executeQuery(preparedStmt);
                        try {
                            conn.commit();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        System.out.println(count_db[0] + " count ");
                    }

                }
                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        }
    }

    private HashMap<String, Set<String>> getCommitFileMaps(Set<String> commitSet) {
        HashMap<String, Set<String>> commit_file_map = new HashMap<>();

        String commitList = new GousiosHotness().transferSetToString(commitSet);


        String queryCommitFiles = "SELECT commit_SHA, concat_AllChangedFile_ExceptDeleteFile\n" +
                "FROM fork.commit_changedFiles\n" +
                "WHERE commit_SHA IN (" + commitList + ")";


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(queryCommitFiles);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String sha = rs.getString(1);
                String file = rs.getString(2);

                Set<String> fileSet = new HashSet<>();
                if (commit_file_map.containsKey(sha)) {
                    fileSet = commit_file_map.get(sha);
                }
                fileSet.add(file);

                commit_file_map.put(sha, fileSet);


            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return commit_file_map;

    }

    private Set<String> getCommitsIn3Month(int projectID, int prID) {
        String query = "SELECT prc.sha\n" +
                "FROM PR_Commit_map prc\n" +
                "WHERE prc.projectID =" + projectID +
                " AND prc.pull_request_ID IN (SELECT pr2.pull_request_ID\n" +
                "                                                    FROM Pull_Request pr1\n" +
                "                                                      INNER JOIN Pull_Request AS pr2 ON pr1.projectID = pr2.projectID\n" +
                "                                                    WHERE pr1.projectID =" + projectID + " AND\n" +
                "                                                          pr1.pull_request_ID =" + prID + " AND pr1.pull_request_ID > pr2.pull_request_ID and \n" +
                "                                                          pr2.isClosed AND\n" +
                "                                                          pr2.age_in_day BETWEEN pr1.age_in_day AND pr1.age_in_day +\n" +
                "                                                                                                        90);";


        HashSet<String> commitSet = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                commitSet.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return commitSet;

    }

    private Set<String> getFiles_partial(int projectID, int prID) {

        String commitString = new GousiosHotness().getCommitsForPR(projectID, prID);
        HashSet<String> fileSet = new HashSet<>();
        if(commitString.length()==0) return fileSet;
        String query = "select cc.concat_AllChangedFile_ExceptDeleteFile\n" +
                "from commit_changedFiles cc \n" +
                "WHERE cc.commit_SHA in (" + commitString + ")";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String file = rs.getString(1);
                if (file != null) {
                    fileSet.add(file);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return fileSet;
    }

    private String getCommitsForPR(int projectID, int prID) {

        String query = "select pr.sha\n" +
                "from PR_Commit_map pr\n" +
                "WHERE pr.projectID = " + projectID + "  and pr.pull_request_ID =" + prID;


        HashSet<String> commitSet = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String sha = rs.getString(1);
                if (sha != null) {
                    commitSet.add(sha);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        String result = "";
        if (commitSet.size() > 0) {
            result = new GousiosHotness().transferSetToString(commitSet);
        }
        return result;

    }


    private String transferSetToString(Set<String> commitSet) {
        String result = "";
        for (String c : commitSet) {
            result += "\"" + c + "\",";

        }

        return result.substring(0, result.length() - 1);
    }
}
