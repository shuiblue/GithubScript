package Pull_Request;

import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
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

//     c***************
//    calculating hotness for commits fiRst, then PR,, time consuming, skip for now

//    *****************
//    public static void main(String[] args) {
//        int timeWwindowInDays = 90;
//        AnalyzedPRHotness analyzedPRHotness = new AnalyzedPRHotness();
//        List<String> repos = io.getList_byFilePath(current_dir + "/input/file_repoList.txt");
//        System.out.println(repos.size() + " projects ");
//
//        for (String projectURL : repos) {
//            int projectID = io.getRepoId(projectURL);
//            System.out.println("projectID: " + projectID + ", " + projectURL);
//            System.out.println("get all PR that has not been analyzed for hotness , repo: " + projectURL);
//            List<Integer> prSet = io.getClosedPRList_unfinishedHotness(projectID);
//            final int[] count_db = {0};
//            String query = "UPDATE fork.Commit SET num_hotFiles_touched_fromAPI = ? WHERE SHA = ?";
//
//            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//                 PreparedStatement preparedStmt = conn.prepareStatement(query);
//            ) {
//                conn.setAutoCommit(false);
//                for (int prID : prSet) {
//                    System.out.println(projectURL + " pr: " + prID);
//                    HashMap<String, Set<String>> commit_file_map = analyzedPRHotness.getChangedFileForEachCommits_api(projectID, prID);
//                    System.out.println(commit_file_map.keySet().size() + " commits in pr");
//
//                    if (commit_file_map.keySet().size() == 0) continue;
//
//                    Set<String> hotFileSet = analyzedPRHotness.getHotFiles(projectID, prID, timeWwindowInDays);
//                    if (hotFileSet.size() == 0) continue;
//                    System.out.println(hotFileSet.size() + " hot files in last 3 months");
//
//
//                    HashMap<String, Integer> commit_numOfHotFile = new HashMap<>();
//                    commit_file_map.forEach((sha, set) -> {
//                        int count = 0;
//                        for (String file : set) {
//                            if (hotFileSet.contains(file)) count++;
//                        }
//                        if (count > 0) {
//                            commit_numOfHotFile.put(sha, count);
//                            System.out.println(sha + " touched " + count + " files");
//                        }
//                    });
//                    commit_numOfHotFile.forEach((sha, numFile) -> {
//                        try {
//                            preparedStmt.setInt(1, numFile);
//                            preparedStmt.setString(2, sha);
//
//                            preparedStmt.addBatch();
//                            if (++count_db[0] % 100 == 0) {
//                                io.executeQuery(preparedStmt);
//                                try {
//                                    conn.commit();
//                                } catch (SQLException e) {
//                                    e.printStackTrace();
//                                }
//                                System.out.println(count_db[0] + " count ");
//
//                            }
//                        } catch (SQLException e) {
//                            e.printStackTrace();
//                        }
//
//
//                    });
//
//                }
//                io.executeQuery(preparedStmt);
//                conn.commit();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//
//
//        }
//
//
//    }


    public List<Integer> getProjectID_forAnalyzingdHotness() {
        List<Integer> repoList = new ArrayList<>();

        String mergedCommitID_query = "SELECT DISTINCT  projectID\n" +
                "FROM commit_files_GHAPI ";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                repoList.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return repoList;
    }


    public static void main(String[] args) {
        int timeWwindowInDays = 90;
        AnalyzedPRHotness analyzedPRHotness = new AnalyzedPRHotness();
//        List<Integer> repos = analyzedPRHotness.getProjectID_forAnalyzingdHotness();
//        System.out.println(repos.size() + " projects ");

        int[] repos = {1};
        for (int projectID : repos) {
            String projectURL = io.getRepoUrlByID(projectID);
            System.out.println("projectID: " + projectID + ", " + projectURL);
            System.out.println("get all PR that has not been analyzed for hotness , repo: " + projectURL);
            List<Integer> prSet = io.getClosedPRList_unfinishedHotness(projectID);
            System.out.println(prSet.size() + " pr to go....");
            int num_analyzedPR = 0;
            final int[] count_db = {0};
            String query = "UPDATE fork.Pull_Request pr\n" +
                    "SET pr.num_commits_on_files_touched = ?\n" +
                    "WHERE pr.projectID = ? AND pr.pull_request_ID = ?";

            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(query);
            ) {
                conn.setAutoCommit(false);
                for (int prID : prSet) {
                    System.out.println((num_analyzedPR++) + "/" + prSet.size() + " pr analyzed");
                    System.out.println(projectURL + " pr: " + prID);

                    Set<String> hotFileSet = analyzedPRHotness.getHotFiles(projectID, prID, timeWwindowInDays);
                    if (hotFileSet.size() == 0) continue;
                    System.out.println(hotFileSet.size() + " hot files in last 3 months");


//                   int numHotCommits = analyzedPRHotness.getCommitsHotness(projectID, prID, hotFileSet);


                    Set<String> getCommitsHotness_new = analyzedPRHotness.getCommitsHotness_new(projectID, prID);
                    int numHotCommits = analyzedPRHotness.countHotCommits(getCommitsHotness_new,hotFileSet);


                    System.out.println(numHotCommits + " commits  touch hot files");
                    try {
                        preparedStmt.setInt(1, numHotCommits);
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
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }


                }
                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }


        }


    }


    private HashMap<String, Set<String>> getChangedFileForEachCommits(int projectID, int prID) {
        String query = "SELECT cc.commit_SHA,\n" +
                "  cc.concat_AllChangedFile_ExceptDeleteFile\n" +
                "FROM commit_changedFiles cc\n" +
                "  RIGHT JOIN PR_Commit_map prc ON prc.sha = cc.commit_SHA  and cc.concat_changedFile_exist_int=1\n" +
                "WHERE prc.projectID = " + projectID +
                " AND prc.pull_request_ID =  " + prID;

        HashMap<String, Set<String>> commitFileMap = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String sha = rs.getString(1);
                String file = rs.getString(2);

                if (sha != null) {
                    Set<String> fileSet = new HashSet<>();
                    if (commitFileMap.get(sha) != null) {
                        fileSet = commitFileMap.get(sha);
                    }
                    fileSet.add(file);
                    commitFileMap.put(sha, fileSet);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commitFileMap;


    }

    private HashMap<String, Set<String>> getChangedFileForEachCommits_api(int projectID, int prID) {
        String query = "select cf.sha, cf.filename\n" +
                "from commit_files_GHAPI  cf LEFT JOIN  PR_Commit_map prc on cf.sha=prc.sha\n" +
                "WHERE prc.projectID = " + projectID +
                " AND prc.pull_request_ID =  " + prID;

        HashMap<String, Set<String>> commitFileMap = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String sha = rs.getString(1);
                String file = rs.getString(2);

                if (sha != null) {
                    Set<String> fileSet = new HashSet<>();
                    if (commitFileMap.get(sha) != null) {
                        fileSet = commitFileMap.get(sha);
                    }
                    fileSet.add(file);
                    commitFileMap.put(sha, fileSet);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commitFileMap;


    }


    private int getCommitsHotness(int projectID, int prID, Set<String> hotFiles) {

        LocalDateTime now = LocalDateTime.now();
        String query = "select cf.sha, cf.filename\n" +
                "from commit_files_GHAPI  cf LEFT JOIN  PR_Commit_map prc on cf.sha=prc.sha\n" +
                "WHERE prc.projectID = " + projectID +
                " AND prc.pull_request_ID =  " + prID;

        Set<String> checkedCommitSet = new HashSet<>();
        int count = 0;
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            String currentSHA = "";
            while (rs.next()) {
                String sha = rs.getString(1);
                if (checkedCommitSet.contains(sha)) continue;
                currentSHA = sha;
                String file = rs.getString(2);
                if (hotFiles.contains(file)) {
                    count++;
                    checkedCommitSet.add(currentSHA);
                }

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        LocalDateTime now1 = LocalDateTime.now();
        System.out.println((now1.getNano() - now.getNano()) + "ms calculating hotness commits");

        return count;


    }


    private int countHotCommits(Set<String> commitFileSet, Set<String> hotFiles) {
        Set<String> checkedCommitSet = new HashSet<>();
        String currentSHA = "";
        int count = 0;
        for (String cf : commitFileSet) {
            String[] str = cf.split(",");
            String sha = str[0];
            if (checkedCommitSet.contains(sha)) continue;
            currentSHA = sha;
            String file = str[1];
            if (hotFiles.contains(file)) {
                count++;
                checkedCommitSet.add(currentSHA);
            }
        }
        return count;
    }

    private Set<String> getCommitsHotness_new(int projectID, int prID) {

        String query = "select cf.sha, cf.filename\n" +
                "from commit_files_GHAPI  cf LEFT JOIN  PR_Commit_map prc on cf.sha=prc.sha\n" +
                "WHERE prc.projectID = " + projectID +
                " AND prc.pull_request_ID =  " + prID;
        Set<String> commitFileSet = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {
                String sha = rs.getString(1);
                String file = rs.getString(2);
                commitFileSet.add(sha + "," + file);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


        return commitFileSet;


    }


    private Set<String> getHotFiles(int projectID, int prID, int timeWindowInDays) {
        System.out.println("getting hot files in last 3 months");
        String query = "SELECT cf.filename\n" +
                "FROM PR_Commit_map prc\n" +
                "  RIGHT JOIN commit_files_GHAPI cf ON prc.SHA = cf.sha\n" +
                "WHERE prc.projectID = " + projectID
                + " AND !cf.isRemoved AND prc.pull_request_ID IN (SELECT pr2.pull_request_ID\n" +
                "                                                          FROM Pull_Request pr1\n" +
                "                                                      INNER JOIN Pull_Request AS pr2 ON pr1.projectID = pr2.projectID\n" +
                "                                                    WHERE pr1.projectID =" + projectID + " AND\n" +
                "                                                          pr1.pull_request_ID =" + prID + " AND pr1.pull_request_ID > pr2.pull_request_ID and \n" +
                "                                                          pr2.isClosed AND\n" +
                "                                                          pr2.age_in_day BETWEEN pr1.age_in_day AND pr1.age_in_day +\n" +
                "                                                                                                        90);";


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
