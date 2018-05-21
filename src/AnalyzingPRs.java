import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/12/18.
 */
public class AnalyzingPRs {

    static String working_dir, pr_dir, output_dir, clone_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;

    AnalyzingPRs() {
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
        IO_Process io = new IO_Process();
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, Integer> finished_repos = new HashMap<>();
        try {
            String[] result = io.readResult(output_dir + "AnalyzePR/finish_PRanalysis.txt").split("\n");
            for (String s : result) {
                if (!s.equals("")) {
                    String[] arr = s.split(",");
                    finished_repos.put(arr[0], Integer.valueOf(arr[1]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            int startPR = -1;
            if (finished_repos.size() > 0) {
                startPR = finished_repos.get(projectUrl);
            }

            String prNumList_filePath = output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt";
            List<String> prList = new ArrayList<>();
            int latestPRid = -1;
            if (new File(prNumList_filePath).exists()) {
                String prListString = "";
                try {
                    prListString = io.readResult(prNumList_filePath);

                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (!prListString.trim().equals("")) {
                    prList = Arrays.asList(prListString.split("\n"));
                    String lastPR = prList.get(prList.size() - 1);
                    latestPRid = Integer.parseInt(lastPR);
                }
            }

            if (startPR <= latestPRid || !finished_repos.containsKey(projectUrl)) {
                System.out.println(projectUrl);
                int projectID = io.getRepoId(projectUrl);

                if (!analyzingPRs.getPRfiles(projectUrl, projectID)) {
                    System.out.println(projectUrl + " pr not exsit");
                    break;
                }
                int startIndex = 0;
                if (finished_repos.containsKey(projectUrl)) startIndex = prList.indexOf(startPR);
                System.out.println("start with project :" + projectUrl + " pr#: " + startPR + " index: " + startIndex);
                for (int i = startIndex; i < prList.size(); i++) {
                    String pr_id_str = prList.get(i);
                    int pr_id = Integer.parseInt(pr_id_str);
                    System.out.println(pr_id);
                    boolean fileExist = analyzingPRs.getCommitsInPR(projectUrl, projectID, pr_id);
                    if (fileExist) {
                        analyzingPRs.insertMap_Commits_PR(projectUrl, projectID, pr_id);
                    } else {
                        io.writeTofile(projectUrl + "," + pr_id + "\n", output_dir + "AnalyzePR/finish_PRanalysis.txt");
                        return;
                    }
                }
                io.writeTofile(projectUrl + "," + latestPRid + "\n", output_dir + "AnalyzePR/finish_PRanalysis.txt");
            } else {
                io.rewriteFile(projectUrl + "," + latestPRid + "\n", output_dir + "AnalyzePR/finish_PRanalysis.txt");
            }

        }
    }


    public static void tmp_main(String[] args) {
        IO_Process io = new IO_Process();
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();
        String current_dir = System.getProperty("user.dir");
        String selectSQL = " SELECT repoURL FROM fork.repository";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectSQL)) {
            Class.forName(myDriver);
            /*** insert repoList to repository table ***/
            String[] repos = io.readResult(current_dir + "/input/prList.txt").split("\n");

            /*** insert pr info to  Pull_Request table***/
            for (String projectUrl : repos) {
                LocalDateTime now = LocalDateTime.now();
                System.out.println(projectUrl);
                long start = System.nanoTime();
                int projectID = io.getRepoId(projectUrl);
                long end = System.nanoTime();
                System.out.println("get repo ID :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                String filePath = pr_dir + projectUrl + "/pr.txt";
                File prFile = new File(filePath);
                if (prFile.exists()) {
                    String pr_json_string = io.readResult(filePath);
                    if (pr_json_string.contains("----")) {
                        String[] pr_json = pr_json_string.split("\n");
                        for (int s = pr_json.length - 1; s >= 0; s--) {
                            String json_string = pr_json[s];
                            String[] arr = json_string.split("----\\[");
                            if (arr.length > 1) {
                                int pr_id = Integer.parseInt(arr[0].trim());
                                json_string = arr[1];
                                System.out.println(pr_id);

                                JSONObject pr_info = new JSONObject(json_string.substring(0, json_string.lastIndexOf("]")));
                                String forkUrl = io.getForkURL(pr_info);

                                int forkID = -1;
                                if (!forkUrl.equals("")) {
                                    forkID = io.getRepoId(forkUrl);
                                    if (forkID == -1) {
                                        io.insertNewRepo(projectID, forkUrl);
                                    }
                                } else {
                                    System.out.println("fork url is empty .");
                                }


                                String author = pr_info.get("author").toString();
                                HashSet<String> commitSet = new HashSet<>();
                                String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                                for (String commit : commitArray) {
                                    if (!commit.equals("")) {
                                        commitSet.add(commit);
                                    }
                                }


                                System.out.println("inserting pr..");
                                start = System.nanoTime();
                                analyzingPRs.insertPR(projectID, pr_id, pr_info, forkID, now);
                                end = System.nanoTime();
                                System.out.println("inserting prs of a repo :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");
//
                                if (!forkUrl.equals("")) {
                                    System.out.println("inserting fork from pr..");
                                    start = System.nanoTime();
                                    analyzingPRs.insertForkasPRauthor(projectID, author, forkUrl, now);
                                    end = System.nanoTime();
                                    System.out.println("inserting forks based on pr author :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");
                                }

                                System.out.println("inserting commit in pr.... from fork " + forkUrl);
                                start = System.nanoTime();
                                analyzingPRs.insertCommitInPR(forkID, projectID, commitSet, now);
                                end = System.nanoTime();
                                System.out.println("inserting commits for all prs :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                                /**get CommitIdMap **/
                                HashMap<String, Integer> sha_commitID_map = io.getCommitIdMap(commitSet);


                                System.out.println("inserting pr_commit mapping... from fork " + forkUrl);
                                start = System.nanoTime();
                                analyzingPRs.insertPR_Commit_mapping(projectID, pr_id, forkID, commitSet, sha_commitID_map);
                                end = System.nanoTime();
                                System.out.println("inserting pr_commits mapping for all :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                                System.out.println("analyze changed file in commit .. from fork " + forkUrl);
                                start = System.nanoTime();
                                analyzingPRs.analyzeChangedFile(projectUrl, pr_id, forkUrl, commitSet, sha_commitID_map);
                                end = System.nanoTime();

                                System.out.println("analyze changed file in commit  :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");
                            }
                        }
                    }
                    io.writeTofile(projectUrl + "\n", output_dir + "/finish_PRanalysis.txt");
                } else {
                    io.writeTofile(projectUrl + "\n", output_dir + "miss_pr_api.txt");
                }
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void insertCommitInPR(int forkID, int projectID, HashSet<String> commitSet, LocalDateTime now) {
        IO_Process io = new IO_Process();

        String update_commit_query = " INSERT INTO fork.commit (commitSHA, repoID, data_update_at, belongToRepoID)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c, ? AS d) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT commitSHA" +
                "      FROM fork.commit AS cc" +
                "      WHERE cc.commitSHA = ?" +
                "  )" +
                "  LIMIT 1";
        long start_commit = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(update_commit_query)) {
            conn.setAutoCommit(false);
            /** put commit into commit_TABLE**/
            int count = 0;
            for (String commit : commitSet) {
                preparedStmt.setString(1, commit);
                preparedStmt.setInt(2, forkID);
                preparedStmt.setString(3, String.valueOf(now));
                preparedStmt.setInt(4, projectID);
                preparedStmt.setString(5, commit);
                preparedStmt.addBatch();

                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }

            }
            io.executeQuery(preparedStmt);
            conn.commit();


        } catch (SQLException e) {
            e.printStackTrace();
        }
        long end_commit = System.nanoTime();
        System.out.println("insert commits from ONE PR :" + TimeUnit.NANOSECONDS.toMillis(end_commit - start_commit) + " ms");
    }


    public void analyzeChangedFile(String projectURL, int pr_id, String forkURL, HashSet<String> commitSet, HashMap<String, Integer> insertCommitInPR) {
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();

        String insert_changedFile_query = " INSERT INTO fork.commit_changedFiles (" +
                "added_file  , added_files_num  , modified_file, modify_files_num , renamed_file ,renamed_files_num ,copied_file ," +
                " copied_files_num, deleted_file , deleted_files_num , " +
                "add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commitSHA_id,readme_loc, about_config)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a1,? AS a2,? AS a3,? AS a4,?  AS a30,? AS a5,? AS a6," +
                "? AS a7,? AS a8,? AS a9,? AS a10,? AS a11,? AS a12,? AS a13,? AS a14,? AS a15,? AS a16 ,? AS a17) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.commit_changedFiles AS cc" +
                "      WHERE cc.commitsha_id = ?" +
                "      AND cc.index_changedFile = ?" +
                "  )" +
                "  LIMIT 1";

        try (
                Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                PreparedStatement preparedStmt = conn.prepareStatement(insert_changedFile_query);
        ) {
            conn.setAutoCommit(false);

            System.out.println("from fork:" + forkURL);
            long start = System.nanoTime();
            for (String sha : commitSet) {
                System.out.println(sha + "," + forkURL + "," + pr_id);
                long start_getcommit = System.nanoTime();
                ArrayList<String> changedfiles = io.getCommitFromCMD(sha, forkURL, projectURL);
                if (changedfiles == null) {
                    System.out.println("commit does not exist in local git history");
                    io.writeTofile(sha + "," + forkURL + "," + pr_id + "\n", output_dir + "lostCommit.txt");
                    continue;
                } else if (changedfiles.get(0).trim().equals("")) {
                    System.out.println("no commit in pr");
                    io.writeTofile(sha + "," + forkURL + "," + pr_id + "\n", output_dir + "noCommit.txt");
                    continue;
                }

                long end_getcommit = System.nanoTime();
                System.out.println("get a commit changed file from cmd:" + TimeUnit.NANOSECONDS.toMillis(end_getcommit - start_getcommit) + " ms");
                int index_d = 0;

                long start_commit = System.nanoTime();
                int count = 0;
                for (String file : changedfiles) {
                    index_d++;
                    String[] arr = file.split("\t");
                    int addLine = 0, deleteLine = 0;
                    String fileName = "", changeType = "";
                    String addedFile = "";
                    String deletedFile = "";
                    String renamedFile = "";
                    String modifiedFile = "";
                    String copiedFile = "";
                    int readmeAdded = 0;
                    int addFile_linesAdded = 0;
                    int modifyFile_linesAdded = 0;
                    if (arr.length == 4) {
                        addLine = Integer.parseInt(arr[0]);
                        deleteLine = Integer.parseInt(arr[1]);
                        fileName = arr[2];
                        changeType = arr[3];


                        if (fileName.toLowerCase().contains("readme")) {
                            readmeAdded += addLine;
                        }

                        int aboutConfig = 0;
                        if (fileName.toLowerCase().contains("config")) {
                            aboutConfig = 1;
                        }

                        if (changeType.equals("A")) {
                            addedFile = fileName;
                            addFile_linesAdded = addLine;
                        } else if (changeType.equals("C")) {
                            copiedFile = fileName;
                        } else if (changeType.equals("D")) {
                            deletedFile = fileName;
                        } else if (changeType.equals("M")) {
                            modifyFile_linesAdded = addLine;
                            modifiedFile = fileName;
                        } else if (changeType.equals("R")) {
                            renamedFile = fileName;
                        }

                        /**   upsdate commit information into table**/

                        int commitshaID = insertCommitInPR.get(sha);

                        preparedStmt.setString(1, addedFile);
                        preparedStmt.setInt(2, addedFile.equals("") ? 0 : 1);
                        preparedStmt.setString(3, modifiedFile);
                        preparedStmt.setInt(4, modifiedFile.equals("") ? 0 : 1);
                        preparedStmt.setString(5, renamedFile);
                        preparedStmt.setInt(6, renamedFile.equals("") ? 0 : 1);
                        preparedStmt.setString(7, copiedFile);
                        preparedStmt.setInt(8, copiedFile.equals("") ? 0 : 1);
                        preparedStmt.setString(9, deletedFile);
                        preparedStmt.setInt(10, deletedFile.equals("") ? 0 : 1);
                        preparedStmt.setInt(11, addFile_linesAdded);
                        preparedStmt.setInt(12, modifyFile_linesAdded);
                        preparedStmt.setInt(13, deleteLine);
                        preparedStmt.setString(14, String.valueOf(now));
                        preparedStmt.setInt(15, index_d);
                        preparedStmt.setInt(16, commitshaID);
                        preparedStmt.setInt(17, readmeAdded);
                        preparedStmt.setInt(18, aboutConfig);
                        preparedStmt.setInt(19, commitshaID);
                        preparedStmt.setInt(20, index_d);
                        preparedStmt.addBatch();
                    } else {
                        io.writeTofile(sha + "," + forkURL + ", pr#" + pr_id + "\n", output_dir + "noEditableFiles.txt");
                        continue;

                    }
                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt);
                        conn.commit();
                    }

                }
                long end_commit = System.nanoTime();
                System.out.println("——--" + changedfiles.size() + " changed files in one commit :" + TimeUnit.NANOSECONDS.toMillis(end_commit - start_commit) + " ms");

            }
            long end = System.nanoTime();
            System.out.println("generating insert querys for a commit  :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

            io.executeQuery(preparedStmt);
            conn.commit();

        } catch (
                SQLException e)

        {
            e.printStackTrace();
        }

    }


    public void insertPR_Commit_mapping(int projectID, int pr_id, int fork_id, HashSet<String> commitSet, HashMap<String, Integer> sha_commitID_map) {
        IO_Process io = new IO_Process();
        String insert_commit_query = " INSERT INTO fork.pr_commit (commitsha_id,repoID,pull_request_id)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c ) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.pr_commit AS cc" +
                "      WHERE cc.commitsha_id = ?" +
                "      AND cc.repoID = ?" +
                "      AND cc.pull_request_id = ?" +
                "  )" +
                "  LIMIT 1";
        try (
                Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                PreparedStatement preparedStmt = conn.prepareStatement(insert_commit_query);
        ) {
            conn.setAutoCommit(false);
            /** put commit into commit_TABLE**/
            int count = 0;
            for (String sha : commitSet) {
                int commitID = sha_commitID_map.get(sha);

                preparedStmt.setInt(1, commitID);
                preparedStmt.setInt(2, fork_id);
                preparedStmt.setInt(3, pr_id);
                preparedStmt.setInt(4, commitID);
                preparedStmt.setInt(5, fork_id);
                preparedStmt.setInt(6, pr_id);
                preparedStmt.addBatch();
                System.out.println(" commit " + sha + " pr" + pr_id);

                if (++count % batchSize == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void insertPR(int projectID, int pr_id, JSONObject pr_info, int forkID, LocalDateTime now) {
        IO_Process io = new IO_Process();
        String insert_query_1 = " INSERT INTO fork.Pull_Request( " +
                "projectID,pull_request_ID, forkName, authorName,forkID," +
                "created_at, closed, closed_at, merged, data_update_at) " +
                " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d,? AS f, ?AS ds,?AS de,?AS d3,?AS d2, ? AS d5) AS tmp" +
                " WHERE NOT EXISTS (" +
                " SELECT projectID FROM Pull_Request WHERE projectID = ? AND pull_request_ID = ?" +
                ") LIMIT 1";
        long start = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {

            String forkName = (String) pr_info.get("forkName");
            String author = pr_info.get("author").toString();
            String created_at = pr_info.get("created_at").toString();
            String closed_at = pr_info.get("closed_at").toString();
            String closed = pr_info.get("closed").toString();
            String merged = pr_info.get("merged").toString();

            preparedStmt.setInt(1, projectID);
            preparedStmt.setInt(2, pr_id);
            preparedStmt.setString(3, forkName);
            preparedStmt.setString(4, author);
            preparedStmt.setInt(5, forkID);
            preparedStmt.setString(6, created_at);
            preparedStmt.setString(7, closed);
            preparedStmt.setString(8, closed_at);
            preparedStmt.setString(9, merged);
            preparedStmt.setString(10, String.valueOf(now));
            preparedStmt.setInt(11, projectID);
            preparedStmt.setInt(12, pr_id);

            System.out.println("insert pr " + pr_id + " to database, affected " + preparedStmt.executeUpdate() + " row.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        long end = System.nanoTime();
        System.out.println("insert ONE PR :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

    }


    public void insertForkasPRauthor(int projectID, String author, String forkURL, LocalDateTime now) {
        IO_Process io = new IO_Process();
        String query = "  INSERT INTO repository ( repoURL,loginID,repoName,isFork,projectID)" +
                " SELECT * FROM (SELECT ? AS repourl,? AS login,? AS reponame,? AS isf,? AS id1) AS tmp" +
                " WHERE NOT EXISTS (" +
                "SELECT repoURL FROM repository WHERE repoURL = ?" +
                ") LIMIT 1";
        long start = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            preparedStmt.setString(1, forkURL);
            preparedStmt.setString(2, author);
            preparedStmt.setString(3, forkURL.split("/")[1]);
            preparedStmt.setBoolean(4, true);
            preparedStmt.setInt(5, projectID);
            preparedStmt.setString(6, forkURL);
            System.out.println("insert repo " + forkURL + " to database, affected " + preparedStmt.executeUpdate() + " row.");


        } catch (SQLException e) {
            e.printStackTrace();
        }
        long end = System.nanoTime();
        System.out.println("insert a fork from ONE PR :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

    }


    public boolean getPRfiles(String projectUrl, int projectID) {
        AnalyzeRepository analyzeRepository = new AnalyzeRepository();
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();
//        String cmd_etPR = "ls get_prs." + projectUrl.replace("/", ".") + "*";

        String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
        if (new File(csvFile_dir).exists()) {
            List<List<String>> prs = io.readCSV(csvFile_dir);
            String insert_query_1 = " INSERT INTO fork.Pull_Request( " +
                    "projectID,pull_request_ID, authorName,forkID," +
                    "created_at, closed, closed_at, merged, data_update_at,labels,dupPR_label) " +
                    " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d, ?AS ds,?AS de,?AS d3,?AS d2, ? AS d5,? AS label,? AS labelDup) AS tmp" +
                    " WHERE NOT EXISTS (" +
                    " SELECT projectID FROM Pull_Request WHERE projectID = ? AND pull_request_ID = ?" +
                    ") LIMIT 1";
            int count = 0;
            HashSet<String> existingForks = new HashSet<>();
            for (List<String> pr : prs) {
                if (!pr.get(0).equals("")) {
                    System.out.println();
                    long start = System.nanoTime();
                    try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                         PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                        conn.setAutoCommit(false);
                        int pr_id = Integer.parseInt(pr.get(9));
                        sb.append(pr_id + "\n");
                        String author = pr.get(1);

                        /** insert fork to database**/
                        String forkURL = pr.get(7);
                        int forkID = io.getRepoId(forkURL);
                        if (!existingForks.contains(forkURL) || forkID == -1) {
                            io.insertRepo(forkURL);
                            GithubRepository fork = new GithubRepository().getRepoInfo(forkURL, projectUrl);
                            analyzeRepository.updateRepoInfo(fork);
                            forkID = io.getRepoId(forkURL);
                        } else {
                            System.out.println(forkURL + "already in database :)");
                        }

                        if (forkID == -1) {
                            System.out.println("fork: " + forkURL + " id is -1");
                        }
                        existingForks.add(forkURL);


                        String created_at = pr.get(6);

                        String closed_at = pr.get(5);
                        String closed = closed_at.equals("") ? "false" : "true";
                        String merged_at = pr.get(11);
                        String merged = merged_at.equals("") ? "false" : "true";

                        String labels = pr.get(10);

                        //projectID
                        preparedStmt.setInt(1, projectID);
                        //pull_request_ID
                        preparedStmt.setInt(2, pr_id);
                        //, authorName,
                        preparedStmt.setString(3, author);
                        //forkID," +
                        preparedStmt.setInt(4, forkID);
                        // created_at,
                        preparedStmt.setString(5, created_at);
                        // closed,
                        preparedStmt.setString(6, closed);
                        // closed_at,
                        preparedStmt.setString(7, closed_at);
                        // merged,
                        preparedStmt.setString(8, merged);
                        //data_update_at
                        preparedStmt.setString(9, String.valueOf(now));
                        //,labels
                        preparedStmt.setString(10, labels);

                        boolean dup_pr = false;
                        if (labels.toLowerCase().contains("duplicate")) {
                            dup_pr = true;
                        }
                        preparedStmt.setBoolean(11, dup_pr);
                        preparedStmt.setInt(12, projectID);
                        preparedStmt.setInt(13, pr_id);
                        preparedStmt.addBatch();

//                    System.out.println("insert pr " + pr_id + " to database, affected " + preparedStmt.executeUpdate() + " row.");

                        long end = System.nanoTime();
                        System.out.println("insert ONE PR :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");


                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }


                        io.executeQuery(preparedStmt);
                        conn.commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                }
            }

            io.writeTofile(sb.toString(), output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");

            return true;
        } else {
            System.out.println(projectUrl + "pr not exist.");
            return false;
        }
    }


    public boolean getCommitsInPR(String projectUrl, int projectID, int pr_id) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();
//        String cmd_getPR = "ls get_prs." + projectUrl.replace("/", ".") + "*";

        String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
        String csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
        String update_commit_query = " INSERT INTO fork.Commit  (commitSHA,loginID,author_name,email, projectID, data_update_at)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c, ? AS d,? AS c1, ? AS d1) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT commitSHA" +
                "      FROM fork.Commit AS cc" +
                "      WHERE cc.commitSHA = ?" +
                "  )" +
                "  LIMIT 1";


        String updatePR = "UPDATE fork.Pull_Request SET num_commit = ? WHERE pull_request_ID = ? AND projectID = ?";

        boolean csvFileExist = new File(csvFile_dir).exists();
        boolean csvFileAlter_Exist = new File(csvFile_dir_alternative).exists();


        if (csvFileAlter_Exist || csvFileExist) {

            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                 Connection conn3 = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt_updatePR = conn3.prepareStatement(updatePR);
                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(update_commit_query);) {
                conn1.setAutoCommit(false);
                List<List<String>> commits;
                if (csvFileExist) {
                    commits = io.readCSV(csvFile_dir);
                } else {
                    commits = io.readCSV(csvFile_dir_alternative);
                }

                preparedStmt_updatePR.setInt(1, commits.size());
                preparedStmt_updatePR.setInt(2, pr_id);
                preparedStmt_updatePR.setInt(3, projectID);
                System.out.println(preparedStmt_updatePR.executeUpdate() + " rows updated num_commits in PR" + pr_id);

                for (List<String> line : commits) {
                    if (!line.get(0).equals("")) {
                        /** put commit into commit_TABLE**/
                        int count = 0;
                        String sha = line.get(8);
                        //commitSHA
                        preparedStmt_1.setString(1, sha);
                        // loginID,
                        preparedStmt_1.setString(2, line.get(1));
                        //author_name
                        preparedStmt_1.setString(3, line.get(3));
                        //,email,
                        preparedStmt_1.setString(4, line.get(2));
                        // projectID,
                        preparedStmt_1.setInt(5, projectID);
                        //data_update_at
                        preparedStmt_1.setString(6, String.valueOf(now));
                        //sha
                        preparedStmt_1.setString(7, line.get(8));
                        preparedStmt_1.addBatch();


                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_1);
                            conn1.commit();
                        }
                    }
                    System.out.println("inserting " + commits.size() + " commits into database");
                    io.executeQuery(preparedStmt_1);
                    conn1.commit();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return true;
        } else {
            return false;
        }

    }

    public void insertMap_Commits_PR(String projectUrl, int projectID, int pr_id) {
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();
//        String cmd_getPR = "ls get_prs." + projectUrl.replace("/", ".") + "*";

        String csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";

        String update_pr_commit_query = " INSERT INTO fork.PR_Commit (commitsha_id,projectID,pull_request_id)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c ) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.PR_Commit AS cc" +
                "      WHERE cc.commitsha_id = ?" +
                "      AND cc.projectID = ?" +
                "      AND cc.pull_request_id = ?" +
                "  )" +
                "  LIMIT 1";


        if (new File(csvFile_dir).exists()) {
            try (
                    Connection conn2 = DriverManager.getConnection(myUrl, user, pwd);
                    PreparedStatement preparedStmt_2 = conn2.prepareStatement(update_pr_commit_query)) {
                conn2.setAutoCommit(false);
                List<List<String>> commits = io.readCSV(csvFile_dir);


                for (List<String> line : commits) {
                    if (!line.get(0).equals("")) {
                        /** put commit into commit_TABLE**/
                        int count = 0;
                        String sha = line.get(8);


                        //commitsha_id,
                        int commitID = io.getCommitID(sha);
                        preparedStmt_2.setInt(1, commitID);
                        // projectID,
                        preparedStmt_2.setInt(2, projectID);
                        // pull_request_id
                        preparedStmt_2.setInt(3, pr_id);
                        //commitsha_id,
                        preparedStmt_2.setInt(4, commitID);
                        // projectID,
                        preparedStmt_2.setInt(5, projectID);
                        // pull_request_id
                        preparedStmt_2.setInt(6, pr_id);
                        preparedStmt_2.addBatch();

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt_2);
                            conn2.commit();
                        }
                    }
                    System.out.println("inserting " + commits.size() + " commits into database");
                    io.executeQuery(preparedStmt_2);
                    conn2.commit();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

        }

    }

}
