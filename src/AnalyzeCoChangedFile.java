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

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/27/18.
 */
public class AnalyzeCoChangedFile {
//    static String tmpDirPath,pathname, historyDirPath,resultDirPath,current_dir,output_dir;

    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath, resultDirPath;

    static HashSet<String> stopFileSet = new HashSet<>();

    static String myUrl, user, pwd;
    final int batchSize = 100;

    //    static String analyzingRepoOrFork = "repo";
    static String analyzingRepoOrFork = "fork";

    public AnalyzeCoChangedFile() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            historyDirPath = output_dir + "commitHistory/";
            resultDirPath = output_dir + "result/";


            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (analyzingRepoOrFork.equals("repo")) {
            stopFileSet.add("gitignore");
            stopFileSet.add("license");
            stopFileSet.add("/dev/null");
            stopFileSet.add(".pdf");
            stopFileSet.add(".mk");
            stopFileSet.add(".xlsx");
            stopFileSet.add(".png");
            stopFileSet.add(".fon");
            stopFileSet.add(".hex");
            stopFileSet.add(".tst");
            stopFileSet.add(".elf");
            stopFileSet.add(".txt");
            stopFileSet.add(".ini");
            stopFileSet.add(".css");
            stopFileSet.add(".html");
            stopFileSet.add("changelog");
            stopFileSet.add(".rst");
            stopFileSet.add(".sch");
            stopFileSet.add(".ino");
            stopFileSet.add(".sh");
            stopFileSet.add(".dll");
            stopFileSet.add(".svn-base");
            stopFileSet.add(".bin");
            stopFileSet.add(".png");
            stopFileSet.add(".xml");
            stopFileSet.add(".DS_Store");
        }
    }

    String[] commitType = {"onlyF", "F2U"};

    static public void main(String[] args) {
        AnalyzeCoChangedFile acc = new AnalyzeCoChangedFile();

        IO_Process io = new IO_Process();
        String[] repoList = {};
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/
        String current_dir = System.getProperty("user.dir");
        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }


//        io.rewriteFile("", historyDirPath + "/repoModularity.csv");
//        io.rewriteFile("", historyDirPath + "/repo_ECI.csv");


        JgitUtility jg = new JgitUtility();
        for (String repoUrl : repoList) {

            if (analyzingRepoOrFork.equals("repo")) {
                jg.cloneRepo(repoUrl);
                try {
                    acc.measureModularity(repoUrl);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (GitAPIException e) {
                    e.printStackTrace();
                }
            } else if (analyzingRepoOrFork.equals("fork")) {
                String[] forkListInfo = new String[0];
                try {
                    forkListInfo = io.readResult(resultDirPath + repoUrl + "/graph_result.txt").split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                HashMap<String, HashMap<String, List<String>>> forkOwnCode = getForkOwnCode(forkListInfo);

                for (int i = 1; i < forkListInfo.length; i++) {
                    String forkINFO = forkListInfo[i];
                    String forkurl = forkINFO.split(",")[0];
                    String repoCloneDir = clone_dir + forkurl + "/";

                    /** clone fork **/
                    ArrayList<String> branchList = jg.cloneRepo(forkurl);
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
        /** remove local copy of fork**/
//        try {
//            io.deleteDir(new File(tmpDirPath));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


    /***
     * This function calculates modularity for each project by generating support matrix
     * @param repoUrl
     * @throws IOException
     * @throws GitAPIException
     */
    public void measureModularity(String repoUrl) throws IOException, GitAPIException {
        System.out.println("analyzing repo: " + repoUrl);
        String repoCloneDir = clone_dir + repoUrl + "/";

        String filepath = historyDirPath + repoUrl + "/changedFile_history.csv";
        HashMap<HashSet<String>, Integer> result = new HashMap<>();
        IO_Process io = new IO_Process();
        io.rewriteFile("", filepath);
        Repository repo = new FileRepository(repoCloneDir + ".git");
        io.rewriteFile("", historyDirPath + repoUrl + "/commitSize_file.csv");

        io.rewriteFile("", historyDirPath + repoUrl + "/SC_table.csv");


        Git git = new Git(repo);
        List<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        HashSet<String> commitSET = new HashSet<>();
        HashSet<String> fileSET = new HashSet<>();
        ArrayList<HashSet<String>> changedFile_Set = new ArrayList<>();

        /** this threshold is used for filtering out 20% large commits **/
        int threshold = 10;
//        if (repoUrl.equals("MarlinFirmware/Marlin")) {
//            threshold = 36;
//        } else if (repoUrl.equals("Smoothieware/Smoothieware")) {
//            threshold = 14;
//        }


        int total = 0;
        for (Ref branch : branches) {
            String branchName = branch.getName();
            Iterable<RevCommit> commits = git.log().add(repo.resolve(branchName.replace("refs/", ""))).call();


            for (RevCommit commit : commits) {
                if (commit.getParentCount() == 1) {
                    total++;
                    String sha = commit.getName();
                    if (!commitSET.contains(sha)) {
                        commitSET.add(sha);
                        RevWalk rw = new RevWalk(repo);
                        RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
                        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                        df.setRepository(repo);
                        df.setDiffComparator(RawTextComparator.DEFAULT);
                        df.setDetectRenames(true);
                        List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                        int linesAdded = 0;
                        int linesDeleted = 0;
                        HashSet<String> commit_fileSet = new HashSet<>();

                        if (diffs.size() < threshold) {
                            for (DiffEntry diff : diffs) {
                                String changeType = diff.getChangeType().name();
                                /** only analyze added and modified files, ignoring deleted, renamed files**/
                                if (changeType.equals("ADD") || changeType.equals("MODIFY")) {
                                    String fileName = diff.getNewPath();
//                                    if (!isStopFile(fileName)) {
                                    commit_fileSet.add(fileName);
                                    for (Edit edit : df.toFileHeader(diff).toEditList()) {
                                        linesDeleted += edit.getEndA() - edit.getBeginA();
                                        linesAdded += edit.getEndB() - edit.getBeginB();
                                    }
                                    io.writeTofile(sha + "," + changeType + "," + diff.getNewPath() + "," + linesAdded + "," + linesDeleted + "\n", filepath);
//                                    }
                                }
                            }

                            if (commit_fileSet.size() > 0) {
                                io.writeTofile(repoUrl + "," + sha + "," + diffs.size() + "\n", historyDirPath + repoUrl + "/commitSize_file.csv");

                            }

                            if (commit_fileSet.size() == 1) {
                                fileSET.addAll(commit_fileSet);
                                changedFile_Set.add(commit_fileSet);
                            } else if (commit_fileSet.size() > 1) {

                                fileSET.addAll(commit_fileSet);
                                HashSet<HashSet<String>> pairs = getAllPairs_string(commit_fileSet);
                                changedFile_Set.addAll(pairs);
                            }
                        }
                    }
                }
            }
        }

        ArrayList<String> fileList = new ArrayList<>();
        fileList.addAll(fileSET);
//        fileList.removeAll(stopFileSet);


        /** generating graph, node--file, edge -- co-changed relation */
//        StringBuilder sb = new StringBuilder();
//        sb.append("*Vertices " + fileList.size() + "\n");
//        for (int i = 1; i <= fileList.size(); i++) {
//            sb.append(i + " \"" + fileList.get(i - 1) + "\"\n");
//        }
//        sb.append("*Arcs\n");


        /**  init support matrix **/
        System.out.println(" init support matrix");
        float[][] support_matrix = new float[fileList.size()][fileList.size()];
        for (int i = 0; i < fileList.size(); i++) {
            for (int j = 0; j < fileList.size(); j++) {
                support_matrix[i][j] = 0;
            }
        }

        System.out.println(" support matrix, changed file set size: " + changedFile_Set.size());
        int index = 0;
        for (HashSet<String> co_changedFiles : changedFile_Set) {
            System.out.println(index++);
            List<String> list = new ArrayList<>();
            list.addAll(co_changedFiles);
            String a = list.get(0);
            int index_a = fileList.indexOf(a);
            if (co_changedFiles.size() == 2) {
                String b = list.get(1);
                int index_b = fileList.indexOf(b);
                support_matrix[index_a][index_b] += 1;
                support_matrix[index_b][index_a] += 1;
                support_matrix[index_a][index_a] += 1;
                support_matrix[index_b][index_b] += 1;


                /** generating graph, node--file, edge -- co-changed relation */
//                sb.append((index_a + 1) + "," + (index_b + 1) + "\n");
            } else if (co_changedFiles.size() == 1) {
                support_matrix[index_a][index_a] += 1;
            }


        }
/** generating graph, node--file, edge -- co-changed relation */
//        /** generating graph, node--file, edge -- co-changed relation */
//        io.rewriteFile(sb.toString(), historyDirPath + repoUrl + "/CoChangedFileGraph.pajek.net");


        System.out.println(" confidence matrix");
        int count_bigger_than_zero = 0;
        for (int i = 0; i < fileList.size(); i++) {
            float appearance = support_matrix[i][i];

            support_matrix[i][i] = 1;
            for (int j = 0; j < fileList.size(); j++) {
                if (i != j && support_matrix[i][j] > 2) {
                    float support = support_matrix[i][j];
                    support_matrix[i][j] = support_matrix[i][j] / appearance;
                    io.writeTofile(fileList.get(i) + "," + fileList.get(j) + "," + support + "," + support_matrix[i][j] + "," + appearance + "\n", historyDirPath + repoUrl + "/SC_table.csv");
                    count_bigger_than_zero++;
                }
            }

        }


        int filesTotal = fileList.size();
        System.out.println("write to file");
        float modularity = (float) count_bigger_than_zero / (filesTotal * filesTotal - filesTotal);
        io.writeTofile(repoUrl + " modularity: " + modularity * 100 + " %  = " + count_bigger_than_zero + " / "
                + (filesTotal * filesTotal - filesTotal) + " , " + commitSET.size() + " unique commits in total, all branches has " + total + " commits\n"
                + " , files in total: " + fileList.size() + "\n------\n", historyDirPath + "/repoModularity.csv");
        System.out.println(repoUrl + " modularity: " + modularity * 100 + " %  = " + count_bigger_than_zero + " / "
                + (filesTotal * filesTotal - filesTotal) + " , " + commitSET.size() + " unique commits in total, all branches has " + total + " commits , files in total: " + fileList.size() + "\n------\n");
        io.writeTofile(repoUrl + "," + modularity * 100 + "\n", historyDirPath + "/" + threshold + "_repo_ECI_all_file.csv");

    }


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

                            int commitshaID = io.getCommitID(sha);

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
                            int commitid = io.getCommitID(sha);
                            if (commitid == -1) {
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
                            int commitid = io.getCommitID(sha);
                            if (commitid != -1) {
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
        result.forEach((k, v) -> v.removeAll(redundantCommit));
        return result;
    }

    public static HashSet<String> getPRcommits(String forkurl) {
        Connection conn = null;
        String query = "SELECT c.commitSHA " +
                "FROM repo_PR AS pr, pr_commit AS pr_c, commit AS c " +
                "WHERE pr_c.pull_request_id = pr.pull_request_ID AND pr.forkID = pr_c.repoID AND c.id = pr_c.commitsha_id AND pr.forkURL = ?";

        HashSet<String> commitsFromPr = new HashSet<>();
        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);
            PreparedStatement preparedStmt = conn.prepareStatement(query);
            preparedStmt.setString(1, forkurl);

            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                commitsFromPr.add(rs.getString(1));

            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commitsFromPr;

    }


    public HashSet<HashSet<String>> getAllPairs_string(HashSet<String> set) {
        HashSet<HashSet<String>> allPairs_string = new HashSet<>();
        List<String> list = new ArrayList<>(set);
        String first = list.remove(0);
        for (String node : list) {
            HashSet<String> currentPair = new HashSet<>();
            currentPair.add(first);
            currentPair.add(node);
            allPairs_string.add(currentPair);
        }
        if (set.size() > 2) {
            set.remove(first);
            allPairs_string.addAll(getAllPairs_string(set));
        } else {
            allPairs_string.add(set);
        }

        return allPairs_string;
    }


    private boolean isStopFile(String fileName) {

        for (String file : stopFileSet) {
            if (fileName.toLowerCase().contains(file)) {
                return true;
            }
        }
        return false;
    }


}
