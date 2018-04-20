import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by shuruiz on 2/27/18.
 */
public class AnalyzeCoChangedFile {
    static String tmpDirPath;
    static String resultDirPath;
    static String current_dir;
    static String pathname, historyDirPath;
    static HashSet<String> stopFileSet = new HashSet<>();
    static String output_dir;
    static String current_OS = System.getProperty("os.name").toLowerCase();
    //    static String analyzingRepoOrFork = "repo";
    static String analyzingRepoOrFork = "fork";
    String myUrl;
    String user;

    public AnalyzeCoChangedFile() {

        if (current_OS.indexOf("mac") >= 0) {
            output_dir = "/Users/shuruiz/Box Sync/ForkData";
            myUrl = "jdbc:mysql://localhost:3306/Fork";
            user = "root";
        } else {
//            output_dir = "/home/feature/shuruiz/ForkData";
            output_dir = "/usr0/home/shuruiz/ForkData";
            myUrl = "jdbc:mysql://localhost:3306/fork";
            user = "shuruiz";
        }

        if (analyzingRepoOrFork.equals("repo")) {
            stopFileSet.add("gitignore");
            stopFileSet.add("license");
//            stopFileSet.add("readme");
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
//            stopFileSet.add(".js");
            stopFileSet.add(".rst");
            stopFileSet.add(".sch");
            stopFileSet.add(".ino");
//            stopFileSet.add(".md");
            stopFileSet.add(".sh");
            stopFileSet.add(".dll");
            stopFileSet.add(".svn-base");
            stopFileSet.add(".bin");
            stopFileSet.add(".xml");
        }
//        stopFileSet.add("example_configurations");
//        stopFileSet.add("config");
//        stopFileSet.add(".h");
//        stopFileSet.add("arduinoaddons");
    }

    static public void main(String[] args) {
        AnalyzeCoChangedFile acc = new AnalyzeCoChangedFile();

        current_dir = System.getProperty("user.dir");

        tmpDirPath = output_dir + "/cloneRepos/";
        historyDirPath = output_dir + "/commitHistory/";
        resultDirPath = output_dir + "/result/";
        IO_Process io = new IO_Process();
        String[] repoList = {};
        current_dir = System.getProperty("user.dir");
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/

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
                acc.getCodechangedLOC(repoUrl);
            }
        }
    }


    /***
     * This function calculates modularity for each project by generating support matrix
     * @param repoUrl
     * @throws IOException
     * @throws GitAPIException
     */
    public void measureModularity(String repoUrl) throws IOException, GitAPIException {
        System.out.println("analyzing repo: " + repoUrl);
        pathname = tmpDirPath + repoUrl + "/";

        String filepath = historyDirPath + repoUrl + "/changedFile_history.csv";
        HashMap<HashSet<String>, Integer> result = new HashMap<>();
        IO_Process io = new IO_Process();
        io.rewriteFile("", filepath);
        Repository repo = new FileRepository(pathname + ".git");
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
                                    if (!isStopFile(fileName)) {
                                        commit_fileSet.add(fileName);
                                        for (Edit edit : df.toFileHeader(diff).toEditList()) {
                                            linesDeleted += edit.getEndA() - edit.getBeginA();
                                            linesAdded += edit.getEndB() - edit.getBeginB();
                                        }
                                        io.writeTofile(sha + "," + changeType + "," + diff.getNewPath() + "," + linesAdded + "," + linesDeleted + "\n", filepath);
                                    }
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
        fileList.removeAll(stopFileSet);


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
        io.writeTofile(repoUrl + "," + modularity * 100 + "\n", historyDirPath + "/" + threshold + "_repo_ECI.csv");

    }


    /**
     * This function calculates code changes at LOC level, identifies number of lines
     * There are 5 types of changes, ADD,MODIFY, DELETE, RENAME, COPY
     *
     * @param repoUrl
     */
    private void getCodechangedLOC(String repoUrl) {

        Connection conn = null;
        PreparedStatement preparedStmt;

        IO_Process io = new IO_Process();
        String[] forkListInfo = {};
        try {
            forkListInfo = io.readResult(resultDirPath + repoUrl + "/pr_graph_info.csv").split("\n");

            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (int i = 1; i < forkListInfo.length; i++) {
            String forkINFO = forkListInfo[i];
            String forkurl = forkINFO.split(",")[1];
            pathname = tmpDirPath + forkurl + "/";


            /** clone fork **/
            JgitUtility jg = new JgitUtility();
            ArrayList<String> branchList = jg.cloneRepo(forkurl);


            Repository repo = null;
            try {
                repo = new FileRepository(pathname + ".git");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Git git = new Git(repo);

            /** Get contribution code : onlyF and F2U  **/
            String[] columns = forkINFO.split(",");
            HashSet<String> codeChangeCommits = new HashSet<>();
            List<String> commits_onlyF = Arrays.asList(io.removeBrackets(columns[33]).split("/ "));
            List<String> commits_F2U = Arrays.asList(io.removeBrackets(columns[34]).split("/ "));
            codeChangeCommits.addAll(commits_F2U);
            codeChangeCommits.addAll(commits_onlyF);
            codeChangeCommits.remove("");
            RevWalk rw = new RevWalk(repo);


            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            for (String sha : codeChangeCommits) {
                System.out.println(sha);
                RevCommit commit = getCommit(sha, repo, git, branchList);
                if (commit != null && commit.getParentCount() > 0) {

                    String author_fullName = commit.getAuthorIdent().getName();
                    String email = commit.getAuthorIdent().getEmailAddress();


                    RevCommit parent = null;
                    List<DiffEntry> diffs = null;
                    DiffFormatter df = null;
                    try {
                        parent = rw.parseCommit(commit.getParent(0).getId());
                        df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                        df.setRepository(repo);
                        df.setDiffComparator(RawTextComparator.DEFAULT);
                        df.setDetectRenames(true);
                        diffs = df.scan(parent.getTree(), commit.getTree());


                        HashSet<String> commit_fileSet = new HashSet<>();
                        if (diffs.size() < 100) {

                            try {
                                String getcommitID = "SELECT id FROM commit WHERE commitSHA = ?";

                                int sha_id = -1;
                                preparedStmt = conn.prepareStatement(getcommitID);
                                preparedStmt.setString(1, sha);
                                ResultSet rs = preparedStmt.executeQuery();        // Get the result table from the query  3
                                if (rs.getRow() == 0) {

                                    String update_commit_query = " INSERT INTO fork.commit (commitSHA,  repoURL, num_changedFiles, authorName, email, data_update_at, upstreamURL)" +
                                            "  SELECT *" +
                                            "  FROM (SELECT" +
                                            "          ? AS a,? AS b, ? AS c, ? as d, ? as e,? as f, ? as g ) AS tmp" +
                                            "  WHERE NOT EXISTS(" +
                                            "      SELECT commitSHA" +
                                            "      FROM fork.commit AS cc" +
                                            "      WHERE cc.commitSHA = ?" +
                                            "  )" +
                                            "  LIMIT 1";

                                    preparedStmt = conn.prepareStatement(update_commit_query);
                                    preparedStmt.setString(1, sha);
                                    preparedStmt.setString(2, forkurl);
                                    preparedStmt.setInt(3, diffs.size());
                                    preparedStmt.setString(4, author_fullName);
                                    preparedStmt.setString(5, email);
                                    preparedStmt.setString(6, String.valueOf(now));
                                    preparedStmt.setString(7, repoUrl);
                                    preparedStmt.setString(8, sha);

                                    System.out.println(preparedStmt.toString());
                                    preparedStmt.execute();


                                    String updateForkID = "UPDATE fork.commit AS a" +
                                            "    SET" +
                                            "      a.repoID         = (SELECT b.id" +
                                            "                          FROM fork.repository AS b" +
                                            "                          WHERE b.repoURL =?)";
                                    preparedStmt = conn.prepareStatement(updateForkID);
                                    preparedStmt.setString(1, forkurl);
                                    preparedStmt.executeUpdate();


                                    String update_belongToRepo_ID = "UPDATE fork.commit AS a" +
                                            "    SET" +
                                            "      a.belongToRepoID         = (SELECT b.id" +
                                            "                          FROM fork.repository AS b" +
                                            "                          WHERE b.repoURL =?)";
                                    preparedStmt = conn.prepareStatement(update_belongToRepo_ID);
                                    preparedStmt.setString(1, repoUrl);
                                    preparedStmt.executeUpdate();


                                } else {
                                    String insert_changedFile_query = " UPDATE commit" +
                                            " SET num_changedFiles = ?, authorName = ?, email = ?, data_update_at = ? " +
                                            " WHERE commitSHA =?";

                                    preparedStmt = conn.prepareStatement(insert_changedFile_query);
                                    preparedStmt.setInt(1, diffs.size());
                                    preparedStmt.setString(2, author_fullName);
                                    preparedStmt.setString(3, email);
                                    preparedStmt.setString(4, String.valueOf(now));
                                    preparedStmt.setString(5, sha);
                                    preparedStmt.execute();
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }

                            System.out.println(sha);
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


                                System.out.println(index_d - 1);
                                DiffEntry diff = diffs.get(index_d - 1);
                                String fileName = diff.getNewPath();
//                                if (!isStopFile(fileName)) {
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
                                try {

                                    String commitshaID_QUERY = "SELECT id FROM commit WHERE commitSHA = ?";

                                    int commitshaID = -1;
                                    preparedStmt = conn.prepareStatement(commitshaID_QUERY);
                                    preparedStmt.setString(1, sha);
                                    ResultSet rs = preparedStmt.executeQuery();        // Get the result table from the query  3
                                    // Get the result table from the query  3
                                    while (rs.next()) {               // Position the cursor                  4
                                        commitshaID = rs.getInt(1);        // Retrieve the first column valu
                                    }


                                    String insert_changedFile_query = " INSERT INTO fork.commit_changedFiles (" +
                                            "added_files_list  , added_files_num  , modify_files_list, modify_files_num , renamed_files_list ,renamed_files_num ,copied_files_list ," +
                                            " copied_files_num, deleted_files_list , deleted_files_num , add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commitSHA_id,readme_loc )" +
//                                            " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                                            "  SELECT *" +
                                            "  FROM (SELECT" +
                                            "          ? as a1,? as a2,? as a3,? as a4,?  as a17,? as a5,? as a6," +
                                            "? as a7,? as a8,? as a9,? as a10,? as a11,? as a12,? as a13,? as a14,? as a15,? as a16 ) AS tmp" +
                                            "  WHERE NOT EXISTS(" +
                                            "      SELECT *" +
                                            "      FROM fork.commit_changedFiles AS cc" +
                                            "      WHERE cc.commitsha_id = ?" +
                                            "      and cc.index_changedFile = ?" +
                                            "  )" +
                                            "  LIMIT 1";

                                    preparedStmt = conn.prepareStatement(insert_changedFile_query);
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
                                    preparedStmt.executeUpdate();

                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }

                                System.out.println(sha);
                            }

//                            }


                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    System.out.println(sha);
                }


            }

            /** remove local copy of fork**/
            System.out.println("delete clone " + forkurl);
            try {
                io.deleteDir(new File(tmpDirPath + forkurl));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    public RevCommit getCommit(String idString, Repository repository, Git git, ArrayList<String> branchList) {
        for (String branchName : branchList) {
            try {
                git.checkout().
                        setCreateBranch(true).
                        setName(branchName).
                        setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).
                        setStartPoint("origin/" + branchName).
                        call();
                ObjectId o1 = repository.resolve(idString);
                if (o1 != null) {
                    RevWalk walk = new RevWalk(repository);
                    RevCommit commit = walk.parseCommit(o1);
                    return commit;
                } else {
                    System.err.println("Could not get commit with SHA :" + idString);
                }
            } catch (Exception e) {
                continue;
            }
        }
        return null;
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
