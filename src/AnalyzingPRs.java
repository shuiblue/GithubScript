import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/12/18.
 */
public class AnalyzingPRs {

    static String working_dir, pr_dir, output_dir, clone_dir;
    static String current_dir = System.getProperty("user.dir");
    static String myUrl;
    static String user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;

    AnalyzingPRs() {
        IO_Process io = new IO_Process();
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

        String selectSQL = " SELECT repoURL FROM fork.repository";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectSQL)) {

            // create a mysql database connection
            Class.forName(myDriver);
            /*** insert repoList to repository table ***/
            String[] repos = io.readResult(current_dir + "/input/prList.txt").split("\n");
//
//            for (int i = 1; i <= repos.length; i++) {
//                String repo = repos[i-1];
//                String loginID = repo.split("/")[0];
//                String repoName = repo.split("/")[1];
//                String query = " insert into repository ( repoURL,loginID,repoName) "+
//                        " values (?,?,?)";
//                 preparedStmt = conn.prepareStatement(query);
//                preparedStmt.setString(1, repo);
//                preparedStmt.setString(2, loginID);
//                preparedStmt.setString(3, repoName);
//                preparedStmt.execute();
//            }

            /*** insert pr info to  repo_PR table***/
            //Execute select SQL stetement
            ResultSet rs;

            for (String repoURL : repos) {
                System.out.println(repoURL);
                long start = System.nanoTime();

                int repoID = io.getRepoId(repoURL);

                long end = System.nanoTime();
                System.out.println("get repo ID :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                System.out.println("inserting pr..");
                start = System.nanoTime();
                analyzingPRs.insertPR(repoURL, repoID);
                end = System.nanoTime();
                System.out.println("inserting prs of a repo :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                System.out.println("inserting fork from pr..");
                start = System.nanoTime();
                analyzingPRs.insertForkasPRauthor(repoURL, repoID);
                end = System.nanoTime();
                System.out.println("inserting forks based on pr author :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                System.out.println("inserting commit in pr..");
                start = System.nanoTime();
                analyzingPRs.insertCommitInPR(repoURL, repoID);
                end = System.nanoTime();
                System.out.println("inserting commits for all prs :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                System.out.println("inserting pr_commit mapping..");
                start = System.nanoTime();
                analyzingPRs.insertPR_Commit_mapping(repoURL, repoID);
                end = System.nanoTime();
                System.out.println("inserting pr_commits mapping for all :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");


                System.out.println("analyze changed file in commit ..");
                start = System.nanoTime();
                analyzingPRs.analyzeChangedFile(repoURL, repoID);
                end = System.nanoTime();
                System.out.println("analyze changed file in commit  :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");


            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param repoUrl
     * @param projectID
     * @return
     */
    public void insertCommitInPR(String repoUrl, int projectID) {
        String[] pr_json;
        IO_Process io = new IO_Process();
        Connection conn;

        LocalDateTime now = LocalDateTime.now();

        try {
            conn = DriverManager.getConnection(myUrl, user, pwd);
            conn.setAutoCommit(false);
            String pr_json_string = io.readResult(pr_dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                int count = 0;
                String update_commit_query = " INSERT INTO fork.commit (commitSHA,  repoURL, upstreamURL, data_update_at, repoID, belongToRepoID)" +
                        "  SELECT *" +
                        "  FROM (SELECT" +
                        "          ? AS a,? AS b, ? AS c, ? AS d,? AS x, ? AS y) AS tmp" +
                        "  WHERE NOT EXISTS(" +
                        "      SELECT commitSHA" +
                        "      FROM fork.commit AS cc" +
                        "      WHERE cc.commitSHA = ?" +
                        "  )" +
                        "  LIMIT 1";

                PreparedStatement preparedStmt = conn.prepareStatement(update_commit_query);

                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    System.out.println(s);
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkURL = getForkURL(pr_info);

                    int forkID = io.getRepoId(forkURL);
                    if (forkID == -1) {
                        System.out.println("id = -1!!! fork: " + forkURL);
                    }


                    /** put commit into commit_TABLE**/
                    HashSet<String> commitSet = new HashSet<>();
                    String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                    for (String commit : commitArray) {
                        if (!commit.equals("")) {
                            commitSet.add(commit);
                        }
                    }

                    for (String commit : commitSet) {
                        System.out.println("forkURL " + forkURL + " commit " + commit);
                        preparedStmt.setString(1, commit);
                        preparedStmt.setString(2, forkURL);
                        preparedStmt.setString(3, repoUrl);
                        preparedStmt.setString(4, String.valueOf(now));
                        preparedStmt.setInt(5, forkID);
                        preparedStmt.setInt(6, projectID);
                        preparedStmt.setString(7, commit);
                        preparedStmt.addBatch();

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public void analyzeChangedFile(String repoUrl, int projectID) {
        String[] pr_json;
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();

        //clone upstream
        JgitUtility jg = new JgitUtility();
        ArrayList<String> upstream_branchList = jg.cloneRepo(repoUrl);


        String insert_changedFile_query = " INSERT INTO fork.commit_changedFiles (" +
                "added_files_list  , added_files_num  , modify_files_list, modify_files_num , renamed_files_list ,renamed_files_num ,copied_files_list ," +
                " copied_files_num, deleted_files_list , deleted_files_num , add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commitSHA_id,readme_loc )" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a1,? AS a2,? AS a3,? AS a4,?  AS a17,? AS a5,? AS a6," +
                "? AS a7,? AS a8,? AS a9,? AS a10,? AS a11,? AS a12,? AS a13,? AS a14,? AS a15,? AS a16 ) AS tmp" +
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
            String pr_json_string = io.readResult(pr_dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                int count = 0;


                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];

                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkURL = getForkURL(pr_info);

                    if (!forkURL.equals("")) {
                        System.out.println(forkURL);
                        String fork_clone_dir = clone_dir + forkURL + "/";
                        String upstream_pathname = clone_dir + repoUrl + "/";

                        /** clone fork **/
                        Repository repo = new FileRepository(fork_clone_dir + ".git");
                        ArrayList<String> branchList = jg.cloneRepo(forkURL);

                        File branchListFile = new File(fork_clone_dir + forkURL.split("/")[0] + "_branchList.txt");
                        if (!branchListFile.exists()) {
                            System.out.println("fork is deleted, check upstream ");
                            branchList.addAll(upstream_branchList);
                            repo = new FileRepository(upstream_pathname + ".git");
                        } else {
                            if (branchList.size() == 0) {
                                try {
                                    branchList.addAll(Arrays.asList(io.readResult(fork_clone_dir + forkURL.split("/")[0] + "_branchList.txt").split("\n")));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }


                        int forkID = io.getRepoId(forkURL);
                        if (forkID == -1) {
                            System.out.println("id = -1!!! fork: " + forkURL);
                        }

                        Git git = new Git(repo);
                        DiffFormatter df = io.getDiffFormat(repo);

                        /** put commit into commit_TABLE**/
                        HashSet<String> commitSet = new HashSet<>();
                        String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                        for (String commit : commitArray) {
                            if (!commit.equals("")) {
                                commitSet.add(commit);
                            }
                        }
                        HashSet<String> commit_fileSet = new HashSet<>();
                        long start = System.nanoTime();
                        for (String sha : commitSet) {
                            RevCommit commit = io.getCommit(sha, repo, git, branchList);
                            if (commit != null) {
                                List<DiffEntry> diffs = io.getCommitDiff(commit, repo);
                                for (int index_d = 1; index_d <= diffs.size(); index_d++) {
                                    HashSet<String> addFileSet = new HashSet();
                                    HashSet<String> deleteFileSet = new HashSet();
                                    HashSet<String> renameFileSet = new HashSet();
                                    HashSet<String> modifyFileSet = new HashSet();
                                    HashSet<String> copyFileSet = new HashSet();
                                    int readmeAdded = 0;
                                    int addFile_linesAdded = 0;
                                    int modifyFile_linesAdded = 0;
                                    int linesDeleted = 0;

                                    DiffEntry diff = diffs.get(index_d - 1);
                                    String fileName = diff.getNewPath();
                                    commit_fileSet.add(fileName);
                                    int tmp_linesAdded = 0;
                                    int tmp_linesDeleted = 0;
                                    FileHeader fileHeader = df.toFileHeader(diff);
                                    EditList edits = fileHeader.toEditList();
                                    for (int ss = 0; ss < edits.size(); ss++) {
                                        Edit edit = edits.get(ss);
                                        if (edit.toString().toLowerCase().startsWith("insert")) {
                                            tmp_linesAdded += edit.getEndB() - edit.getBeginB();
                                        } else if (edit.toString().toLowerCase().startsWith("delete")) {
                                            tmp_linesDeleted += edit.getEndA() - edit.getBeginA();
                                        } else {
                                            tmp_linesDeleted += edit.getEndA() - edit.getBeginA();
                                            tmp_linesAdded += edit.getEndB() - edit.getBeginB();
                                        }
                                    }
                                    linesDeleted += tmp_linesDeleted;
                                    if (fileName.toLowerCase().contains("readme")) {
                                        readmeAdded += tmp_linesAdded;
                                    }

                                    String changeType = diff.getChangeType().name();
                                    if (changeType.equals("ADD")) {
                                        addFileSet.add(fileName);
                                        addFile_linesAdded += tmp_linesAdded;
                                    } else if (changeType.equals("COPY")) {
                                        copyFileSet.add(fileName);
                                    } else if (changeType.equals("DELETE")) {
                                        deleteFileSet.add(fileName);
                                    } else if (changeType.equals("MODIFY")) {
                                        modifyFileSet.add(fileName);
                                        modifyFile_linesAdded += tmp_linesAdded;
                                    } else if (changeType.equals("RENAME")) {
                                        renameFileSet.add(fileName);
                                    }

                                    /**   upsdate commit information into table**/

                                    int commitshaID = io.getcommitID(sha);

                                    preparedStmt.setString(1, addFileSet.toString());
                                    preparedStmt.setInt(2, addFileSet.size());
                                    preparedStmt.setString(3, modifyFileSet.toString());
                                    preparedStmt.setInt(4, modifyFileSet.size());
                                    preparedStmt.setString(5, renameFileSet.toString());
                                    preparedStmt.setInt(6, renameFileSet.size());
                                    preparedStmt.setString(7, copyFileSet.toString());
                                    preparedStmt.setInt(8, copyFileSet.size());
                                    preparedStmt.setString(9, deleteFileSet.toString());
                                    preparedStmt.setInt(10, deleteFileSet.size());
                                    preparedStmt.setInt(11, addFile_linesAdded);
                                    preparedStmt.setInt(12, modifyFile_linesAdded);
                                    preparedStmt.setInt(13, linesDeleted);
                                    preparedStmt.setString(14, String.valueOf(now));
                                    preparedStmt.setInt(15, index_d);
                                    preparedStmt.setInt(16, commitshaID);
                                    preparedStmt.setInt(17, readmeAdded);
                                    preparedStmt.setInt(18, commitshaID);
                                    preparedStmt.setInt(19, index_d);
                                    preparedStmt.addBatch();

                                    if (++count % batchSize == 0) {
                                        io.executeQuery(preparedStmt);
                                        conn.commit();

                                    }
                                }
                            }
                        }
                        long end = System.nanoTime();
                        System.out.println("analyze commit from a pr  :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();

            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void insertPR_Commit_mapping(String repoUrl, int projectID) {
        String[] pr_json;
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

            String pr_json_string = io.readResult(pr_dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");

                int count = 0;


                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(json_string.split("----")[0].trim());
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkURL = getForkURL(pr_info);

                    /** put commit into commit_TABLE**/
                    HashSet<String> commitSet = new HashSet<>();
                    String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                    for (String commit : commitArray) {
                        if (!commit.equals("")) {
                            commitSet.add(commit);
                        }
                    }

                    for (String commit : commitSet) {
                        int sha_id = getSHAID(commit);
                        if (sha_id == -1) {
                            System.out.print("forkURL " + forkURL + " commit is -1 !!! ");
                        }

                        int fork_id = io.getRepoId(forkURL);
                        preparedStmt.setInt(1, sha_id);
                        preparedStmt.setInt(2, fork_id);
                        preparedStmt.setInt(3, pr_id);
                        preparedStmt.setInt(4, sha_id);
                        preparedStmt.setInt(5, fork_id);
                        preparedStmt.setInt(6, pr_id);
                        preparedStmt.addBatch();
                        System.out.println("forkURL " + forkURL + " commit " + commit + " pr" + pr_id);

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }

                    }

                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void insertPR(String repoUrl, int projectID) {
        String[] pr_json;
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();


        String insert_query_1 = " INSERT INTO fork.repo_PR( " +
                "projectID,pull_request_ID, forkName, authorName,forkURL,forkID," +
                "created_at, closed, closed_at, merged, data_update_at) " +
                " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d,? AS e,? AS f, ?AS ds,?AS de,?AS d3,?AS d2, ? AS d5) AS tmp" +
                " WHERE NOT EXISTS (" +
                " SELECT projectID FROM repo_PR WHERE projectID = ? AND pull_request_ID = ?" +
                ") LIMIT 1";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {

            conn.setAutoCommit(false);

            String pr_json_string = io.readResult(pr_dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");

                int count = 0;
                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(json_string.split("----")[0].trim());
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkName = (String) pr_info.get("forkName");
                    String author = pr_info.get("author").toString();
                    String created_at = pr_info.get("created_at").toString();
                    String closed_at = pr_info.get("closed_at").toString();
                    String closed = pr_info.get("closed").toString();
                    String merged = pr_info.get("merged").toString();
                    String forkURL = getForkURL(pr_info);

                    int forkID = io.getRepoId(forkURL);
                    preparedStmt.setInt(1, projectID);
                    preparedStmt.setInt(2, pr_id);
                    preparedStmt.setString(3, forkName);
                    preparedStmt.setString(4, author);
                    preparedStmt.setString(5, forkURL);
                    preparedStmt.setInt(6, forkID);
                    preparedStmt.setString(7, created_at);
                    preparedStmt.setString(8, closed);
                    preparedStmt.setString(9, closed_at);
                    preparedStmt.setString(10, merged);
                    preparedStmt.setString(11, String.valueOf(now));
                    preparedStmt.setInt(12, projectID);
                    preparedStmt.setInt(13, pr_id);
                    preparedStmt.addBatch();

//                    System.out.println("forkURL " + forkURL + " pr " + pr_id);
                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt);
                        conn.commit();
                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void insertForkasPRauthor(String repoUrl, int projectID) {
        String[] pr_json;
        IO_Process io = new IO_Process();
        try {
            Connection conn = DriverManager.getConnection(myUrl, user, pwd);
            conn.setAutoCommit(false);
            String pr_json_string = io.readResult(pr_dir + repoUrl + "/pr.txt");
            String query = "  INSERT INTO repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo,upstreamID,projectID)" +
                    " SELECT * FROM (SELECT ? AS repourl,? AS login,? AS reponame,? AS isf,? AS upurl,? AS belo,? AS id1, ? AS id2) AS tmp" +
                    " WHERE NOT EXISTS (" +
                    "SELECT repoURL FROM repository WHERE repoURL = ?" +
                    ") LIMIT 1";

            PreparedStatement preparedStmt = conn.prepareStatement(query);
            int count = 0;
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");


                for (int s = pr_json.length - 1; s >= 0; s--) {

                    String json_string = pr_json[s];
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String author = pr_info.get("author").toString();
                    String forkURL = getForkURL(pr_info);
                    if (!forkURL.equals("")) {

                        preparedStmt.setString(1, forkURL);
                        preparedStmt.setString(2, author);
                        preparedStmt.setString(3, forkURL.split("/")[1]);
                        preparedStmt.setBoolean(4, true);
                        preparedStmt.setString(5, repoUrl);
                        preparedStmt.setString(6, repoUrl);
                        preparedStmt.setInt(7, projectID);
                        preparedStmt.setInt(8, projectID);
                        preparedStmt.setString(9, forkURL);
                        preparedStmt.addBatch();

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }

                    }
                }


            }
            io.executeQuery(preparedStmt);
            conn.commit();
            preparedStmt.close();
            conn.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    private int getSHAID(String commit) {
        int sha_id = -1;
        Connection conn;
        try {
            conn = DriverManager.getConnection(myUrl, user, pwd);
            conn.setAutoCommit(false);

            String getSHAID = "SELECT id FROM commit WHERE commitSHA =?";
            PreparedStatement preparedStmt = conn.prepareStatement(getSHAID);
            preparedStmt.setString(1, commit);

            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {               // Position the cursor                  4
                sha_id = rs.getInt(1);        // Retrieve the first column valu
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sha_id;
    }


    public String getForkURL(JSONObject pr_info) {
        String forkURL = pr_info.get("forkURL").toString();
        if (forkURL.contains(":")) {
            String[] arr = forkURL.split("/");
            String[] loginID = arr[0].split(":");
            forkURL = loginID[0] + "/" + arr[1];
        }
        return forkURL;
    }
}
