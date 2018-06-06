import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/27/18.
 */
public class AnalyzeChangedFile_ClassifyCommit {
    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath, resultDirPath;
    static String myUrl, user, pwd;
    final int batchSize = 100;
    String[] commitType = {"onlyF", "F2U"};

    public AnalyzeChangedFile_ClassifyCommit() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            historyDirPath = output_dir + "commitHistory/";
            resultDirPath = output_dir + "ClassifyCommit/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public void main(String[] args) {
        AnalyzeChangedFile_ClassifyCommit acc = new AnalyzeChangedFile_ClassifyCommit();
        IO_Process io = new IO_Process();
        String[] repoList = {};
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/
        String current_dir = System.getProperty("user.dir");
        try {
            repoList = io.readResult(current_dir + "/input/graph_repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        JgitUtility jg = new JgitUtility();
        for (String repoUrl : repoList) {

            String[] forkListInfo = new String[0];
            try {
                forkListInfo = io.readResult(resultDirPath + repoUrl.replace("/", ".") + "_graph_result.csv").split("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }

            HashMap<String, HashMap<String, List<String>>> forkOwnCode = getForkOwnCode(forkListInfo);

            for (int i = 1; i < forkListInfo.length; i++) {
                String forkINFO = forkListInfo[i];
                String forkurl = forkINFO.split(",")[0];
                String forkName = forkurl.split("/")[0];
                String repoCloneDir = clone_dir + forkurl + "/";


                /**   get all branch list**/
                String fork_cloneDir = clone_dir + forkurl + "/";
                String cmd_getOringinBranch = "git branch -a --list " + forkName + "*";
                String branchResult = io.exeCmd(cmd_getOringinBranch.split(" "), fork_cloneDir);

                String[] branchList_array = branchResult.split("\n");
                ArrayList<String> branchList = new ArrayList<>();
                for (String br : branchList_array) {
                    if (!br.contains("HEAD")) {
                        branchList.add(br.trim());
                    }
                }


                /** clone fork **/
//                ArrayList<String> branchList = jg.cloneRepo(forkurl);
                File branchListFile = new File(repoCloneDir + forkurl.split("/")[0] + "_branchList.txt");
                if (!branchListFile.exists()) {
                    System.out.println("fork is not available");
                    continue;
                }
                if (branchList.size() == 0) {
                    try {
                        branchList.addAll(Arrays.asList(io.readResult(clone_dir + forkurl.split("/")[0] + "_branchList.txt").split("\n")));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("insert commit...");
                acc.insertCommitFromGraphResult(repoUrl, forkOwnCode, forkurl, branchList);
                System.out.println("update existing commit...");
                acc.updateCommitFromGraphResult(repoUrl, forkOwnCode, forkurl, branchList);
                System.out.println("analyzing changed files...");
                acc.getCodechangedLOC(repoUrl, forkOwnCode, forkurl, branchList);
            }


        }
    }


    /***
     * This function calculates modularity for each project by generating support matrix
     * @param repoUrl
     * @throws IOException
     * @throws GitAPIException
     */


    /**
     * This function calculates code changes at LOC level, identifies number of lines
     * There are 5 types of changes, ADD,MODIFY, DELETE, RENAME, COPY
     *
     * @param repoUrl
     */
    private void getCodechangedLOC(String repoUrl, HashMap<String, HashMap<String, List<String>>> forkOwnCode, String forkurl, ArrayList<String> branchList) {
        LocalDateTime now = LocalDateTime.now();
        IO_Process io = new IO_Process();
        PreparedStatement preparedStmt;
        HashMap<String, List<String>> onlyF_map = forkOwnCode.get("onlyF");
        HashMap<String, List<String>> F2U_map = forkOwnCode.get("F2U");
        HashSet<String> codeChangeCommits = new HashSet<>();
        try {
            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            /** Get contribution code : onlyF and F2U  **/
            Repository repo = new FileRepository(clone_dir + ".git");
            Git git = new Git(repo);
            RevWalk rw = new RevWalk(repo);

            long start = System.nanoTime();
            codeChangeCommits.addAll(onlyF_map.get(forkurl));
            codeChangeCommits.addAll(F2U_map.get(forkurl));
            int count = 0;
            String insert_changedFile_query = " INSERT INTO fork.commit_changedFiles (" +
                    "added_files_list  , added_files_num  , modify_files_list, modify_files_num , renamed_files_list ,renamed_files_num ,copied_files_list ," +
                    " copied_files_num, deleted_files_list , deleted_files_num , add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commit_uuid,readme_loc )" +
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
            preparedStmt = conn.prepareStatement(insert_changedFile_query);

            for (String sha : codeChangeCommits) {
                RevCommit commit = io.getCommit(sha, repo, git, branchList);


                if (commit != null && commit.getParentCount() > 0) {
                    DiffFormatter df = io.getDiffFormat(repo);
                    List<DiffEntry> diffs = io.getCommitDiff(commit, repo);

                    HashSet<String> commit_fileSet = new HashSet<>();
                    if (diffs.size() < 100) {
                        start = System.nanoTime();
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
                            for (int s = 0; s < edits.size(); s++) {
                                Edit edit = edits.get(s);
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

                            String commitshaID = io.getCommitID(sha);

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
                            preparedStmt.setString(16, commitshaID);
                            preparedStmt.setInt(17, readmeAdded);
                            preparedStmt.setString(18, commitshaID);
                            preparedStmt.setInt(19, index_d);
                            preparedStmt.addBatch();

                            if (++count % batchSize == 0) {
                                io.executeQuery(preparedStmt);
                                conn.commit();

                            }
                        }

                    }
                }
            }
            long end = System.nanoTime();
            long used = end - start;
            System.out.println("update  getCodechangedLOC :" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");

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


    private void insertCommitFromGraphResult(String repoUrl, HashMap<String, HashMap<String, List<String>>> forkOwnCode, String forkurl, ArrayList<String> branchList) {
        IO_Process io = new IO_Process();
        HashMap<String, List<String>> onlyF_map = forkOwnCode.get("onlyF");
        HashMap<String, List<String>> F2U_map = forkOwnCode.get("F2U");
        PreparedStatement preparedStmt_insert = null;
        int onlyf_boolean, f2u_boolean;

        try {
            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            Repository repo = new FileRepository(clone_dir + ".git");
            Git git = new Git(repo);

            /** Get contribution code : onlyF and F2U  **/
            HashSet<String> codeChangeCommits = new HashSet<>();
            RevWalk rw = new RevWalk(repo);
            LocalDateTime now = LocalDateTime.now();

            for (String type : commitType) {
                if (type.equals("onlyF")) {
                    codeChangeCommits.addAll(onlyF_map.get(forkurl));
                    onlyf_boolean = 1;
                    f2u_boolean = 0;
                } else {
                    codeChangeCommits.addAll(F2U_map.get(forkurl));
                    onlyf_boolean = 0;
                    f2u_boolean = 1;
                }

                int count = 0;
                String update_commit_query = " INSERT INTO fork.commit (commitSHA,  repoURL, num_changedFiles, authorName, email, data_update_at, upstreamURL,repoID,belongToRepoID,only_f,f2u)" +
                        "  SELECT *" +
                        "  FROM (SELECT" +
                        "          ? AS a,? AS b, ? AS c, ? AS d, ? AS e,? AS f, ? AS g ,? AS x, ? AS y,? AS type, ? AS type2) AS tmp" +
                        "  WHERE NOT EXISTS(" +
                        "      SELECT commitSHA" +
                        "      FROM fork.commit AS cc" +
                        "      WHERE cc.commitSHA = ?" +
                        "  )" +
                        "  LIMIT 1";
                preparedStmt_insert = conn.prepareStatement(update_commit_query);
                long start_codeChangeCommits = System.nanoTime();
                for (String sha : codeChangeCommits) {
                    long start_getOneCommit = System.nanoTime();
                    RevCommit commit = io.getCommit(sha, repo, git, branchList);
                    if (commit != null && commit.getParentCount() > 0) {
                        String author_fullName = io.normalize(commit.getAuthorIdent().getName());
                        String email = commit.getAuthorIdent().getEmailAddress();
                        List<DiffEntry> diffs = io.getCommitDiff(commit, repo);

                        if (diffs.size() < 100) {
                            String commitid = io.getCommitID(sha);
                            if (commitid.equals("")) {
                                int repoID = io.getRepoId(forkurl);
                                int projectID = io.getRepoId(repoUrl);

                                preparedStmt_insert.setString(1, sha);

                                preparedStmt_insert.setString(2, forkurl);

                                preparedStmt_insert.setInt(3, diffs.size());

                                preparedStmt_insert.setString(4, author_fullName);

                                preparedStmt_insert.setString(5, email);

                                preparedStmt_insert.setString(6, String.valueOf(now));
                                preparedStmt_insert.setString(7, repoUrl);
                                preparedStmt_insert.setInt(8, repoID);
                                preparedStmt_insert.setInt(9, projectID);
                                preparedStmt_insert.setInt(10, onlyf_boolean);
                                preparedStmt_insert.setInt(11, f2u_boolean);
                                preparedStmt_insert.setString(12, sha);
                                preparedStmt_insert.addBatch();


                                if (++count % batchSize == 0) {
                                    io.executeQuery(preparedStmt_insert);
                                    conn.commit();
                                }
                            }
                        }
                    }
                    long end_getOneCommit = System.nanoTime();
                    System.out.println("generate 1 insert commit query :" + TimeUnit.NANOSECONDS.toMillis(end_getOneCommit - start_getOneCommit) + " ms");
                }
                long end_codeChangeCommits = System.nanoTime();
                System.out.println("insert codeChangeCommits from 1 fork :" + TimeUnit.NANOSECONDS.toMillis(end_codeChangeCommits - start_codeChangeCommits) + " ms");
                io.executeQuery(preparedStmt_insert);
                conn.commit();
            }

            preparedStmt_insert.close();
            conn.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static HashMap<String, HashMap<String, List<String>>> getForkOwnCode(String[] forkListInfo) {
        HashMap<String, HashMap<String, List<String>>> forkOwnCode = new HashMap<>();
        HashMap<String, List<String>> F2U_map = removeRedundantCommits(forkListInfo, "F2U");
        HashMap<String, List<String>> onlyF_map = removeRedundantCommits(forkListInfo, "onlyF");
        forkOwnCode.put("F2U", F2U_map);
        forkOwnCode.put("onlyF", onlyF_map);
        return forkOwnCode;
    }


    private void updateCommitFromGraphResult(String repoUrl, HashMap<String, HashMap<String, List<String>>> forkOwnCode, String forkurl, ArrayList<String> branchList) {
        IO_Process io = new IO_Process();
        HashMap<String, List<String>> onlyF_map = forkOwnCode.get("onlyF");
        HashMap<String, List<String>> F2U_map = forkOwnCode.get("F2U");

        long start;
        int onlyf_boolean, f2u_boolean;

        try {
            PreparedStatement preparedStmt_update = null;
            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            Repository repo = new FileRepository(clone_dir + ".git");
            Git git = new Git(repo);

            /** Get contribution code : onlyF and F2U  **/
            HashSet<String> codeChangeCommits = new HashSet<>();

            LocalDateTime now = LocalDateTime.now();

            for (String type : commitType) {

                if (type.equals("onlyF")) {
                    codeChangeCommits.addAll(onlyF_map.get(forkurl));
                    onlyf_boolean = 1;
                    f2u_boolean = 0;
                } else {
                    codeChangeCommits.addAll(F2U_map.get(forkurl));
                    onlyf_boolean = 0;
                    f2u_boolean = 1;
                }

                System.out.println("analyzing " + type + " commits...");
                String insert_changedFile_query = " UPDATE commit" +
                        " SET num_changedFiles = ?, authorName = ?, email = ?, data_update_at = ?, only_f = ?, f2u = ? " +
                        " WHERE commitSHA =?";
                preparedStmt_update = conn.prepareStatement(insert_changedFile_query);

                start = System.nanoTime();
                for (String sha : codeChangeCommits) {
                    RevCommit commit = io.getCommit(sha, repo, git, branchList);
                    if (commit != null && commit.getParentCount() > 0) {
                        String author_fullName = io.normalize(commit.getAuthorIdent().getName());
                        String email = commit.getAuthorIdent().getEmailAddress();

                        List<DiffEntry> diffs = io.getCommitDiff(commit, repo);

                        if (diffs.size() < 100) {
                            String commitid = io.getCommitID(sha);
                            if (!commitid.equals("")) {
                                System.out.println("updating existing commit ... ");
                                preparedStmt_update.setInt(1, diffs.size());
                                preparedStmt_update.setString(2, author_fullName);
                                preparedStmt_update.setString(3, email);
                                preparedStmt_update.setString(4, String.valueOf(now));
                                preparedStmt_update.setInt(5, onlyf_boolean);
                                preparedStmt_update.setInt(6, f2u_boolean);
                                preparedStmt_update.setString(7, sha);
                                preparedStmt_update.addBatch();

                            }
                        }
                    }

                }
                long end = System.nanoTime();
                long used = end - start;
                System.out.println("update " + codeChangeCommits.size() + " commit from a fork :" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");

                io.executeQuery(preparedStmt_update);
                conn.commit();
            }

            preparedStmt_update.close();
            conn.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private static HashMap<String, List<String>> removeRedundantCommits(String[] forkListInfo, String type) {
        HashSet<String> codeChangeCommits_all = new HashSet<>();
        HashSet<String> redundantCommit = new HashSet<>();
        HashMap<String, List<String>> result = new HashMap<>();

        IO_Process io = new IO_Process();
        for (int i = 1; i < forkListInfo.length; i++) {
            String forkINFO = forkListInfo[i];
            String forkurl = forkINFO.split(",")[0];
            String repoCloneDir = clone_dir + forkurl + "/";
            /** Get contribution code : onlyF and F2U  **/
            String[] columns = forkINFO.split(",");
            ArrayList<String> commits;
//            HashSet<String> commitsFromPr = new HashSet<>();
            if (type.equals("onlyF")) {

                commits = new ArrayList<>(Arrays.asList(io.removeBrackets(columns[6]).split("/ ")));
            } else {
                commits = new ArrayList<>(Arrays.asList(io.removeBrackets(columns[8]).split("/ ")));
//                System.out.println("Get .. commit belong to PR  into F2U... repo:" + forkurl);
//                commitsFromPr = getPRcommits(forkurl);
//                System.out.println("commit belong to PR" + commitsFromPr.size());

            }
            commits.removeAll(redundantCommit);
            commits.remove("");
//            System.out.println("commit from Graph" + commits.size());
//            if (type.equals("F2U")) {
//                commits.addAll(commitsFromPr);
//            }
//            System.out.println("ALL commits" + commits.size());

            for (String commit : commits) {
                if (codeChangeCommits_all.contains(commit)) {
                    redundantCommit.add(commit);
                }
            }

            codeChangeCommits_all.addAll(commits);
            result.put(forkurl, commits);
        }

        System.out.println(redundantCommit.size() + " redundanct commits");
        StringBuilder sb = new StringBuilder();
        sb.append(type + "\n");
        result.forEach((k, v) -> {
            v.removeAll(redundantCommit);
            sb.append(k + "," + v.size() + "\n");
        });
        io.writeTofile(sb.toString(), resultDirPath + "tim_graph_result.txt");
        return result;
    }


}
