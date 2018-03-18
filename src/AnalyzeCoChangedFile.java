import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * Created by shuruiz on 2/27/18.
 */
public class AnalyzeCoChangedFile {
    static String tmpDirPath;
    static String resultDirPath;
    static String current_dir;
    static String pathname, historyDirPath;
    static HashSet<String> stopFileSet;
//    static String output_dir = "/Users/shuruiz/Box Sync/ForkData";
    static String output_dir = "/home/feature/shuruiz/ForkData";


    static public void main(String[] args) throws IOException, GitAPIException {

//        String analyzingRepoOrFork = "repo";
        String analyzingRepoOrFork = "fork";

        stopFileSet = stopFiles(analyzingRepoOrFork);
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

        AnalyzeCoChangedFile acc = new AnalyzeCoChangedFile();
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
//                    acc.calculateCodeChangeModularity(forkurl, repoUrl);
                String locHistory = historyDirPath + repoUrl + "/changedSize_LOC.csv";

                io.rewriteFile("repoUrl,forkurl,addFile,modifyFile,deleteFile,copyFile,renameFile,linesAdded,readmeAdded\n", locHistory);

                acc.getLOCdiff(repoUrl);
//                    acc.getChangedFileTable(forkurl, repoUrl);
                // acc.generatingFileCoChangeGraph(repoUrl);

            }
        }
    }

    private void calculateCodeChangeModularity(String forkurl, String repoUrl) throws IOException, GitAPIException {
        System.out.println("analyzing repo: " + repoUrl);
        /**   clone **/
        pathname = tmpDirPath + forkurl + "/";
        String filepath = historyDirPath + repoUrl + "/changedFile_history.csv";
        HashMap<HashSet<String>, Integer> result = new HashMap<>();
        IO_Process io = new IO_Process();
        io.rewriteFile("", filepath);
        Repository repo = new FileRepository(pathname + ".git");
        io.rewriteFile("", historyDirPath + repoUrl + "/commitSize_file.csv");
        Git git = new Git(repo);
        List<Ref> branches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        HashSet<String> commitSET = new HashSet<>();
        HashSet<String> fileSET = new HashSet<>();
        ArrayList<HashSet<String>> changedFile_Set = new ArrayList<>();

        int total = 0;
        for (Ref branch : branches) {
            String branchName = branch.getName();
            Iterable<RevCommit> commits = git.log().add(repo.resolve(branchName.replace("refs/", ""))).call();

            for (RevCommit commit : commits) {
                String sha = commit.getName();
                if (!commitSET.contains(sha)) {
                    commitSET.add(sha);
                }
            }
        }
    }


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

        int total = 0;
        int num_commit = 0;
        for (Ref branch : branches) {
            String branchName = branch.getName();
            Iterable<RevCommit> commits = git.log().add(repo.resolve(branchName.replace("refs/", ""))).call();


            for (RevCommit commit : commits) {
                /** latest 200 commits  **/
//                if (num_commit <= 200) {

                    total++;
                    String sha = commit.getName();
                    if (!commitSET.contains(sha)) {
                        commitSET.add(sha);
                        RevWalk rw = new RevWalk(repo);
                        if (commit.getParentCount() > 0) {
                            RevCommit parent = rw.parseCommit(commit.getParent(0).getId());
                            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
                            df.setRepository(repo);
                            df.setDiffComparator(RawTextComparator.DEFAULT);
                            df.setDetectRenames(true);
                            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                            int linesAdded = 0;
                            int linesDeleted = 0;
                            HashSet<String> commit_fileSet = new HashSet<>();

//                        System.out.println(sha);
                            if (diffs.size() < 100) {
                                if (diffs.size() > 0) {
                                    num_commit++;
                                    io.writeTofile(repoUrl + "," + sha + "," + diffs.size() + "\n", historyDirPath + repoUrl + "/commitSize_file.csv");
//                                System.out.println(repoUrl + "," + sha + "," + diffs.size() + "\n");
                                }
                                for (DiffEntry diff : diffs) {
                                    String fileName = diff.getNewPath();
                                    if (!isStopFile(fileName)) {
                                        commit_fileSet.add(fileName);
                                    }
                            for (Edit edit : df.toFileHeader(diff).toEditList()) {
                                linesDeleted += edit.getEndA() - edit.getBeginA();
                                linesAdded += edit.getEndB() - edit.getBeginB();
                            }
                            io.writeTofile(sha + "," + diff.getChangeType().name() + "," + diff.getNewPath() + "," + linesAdded + "," + linesDeleted + "\n", filepath);


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

                        } else {
                            System.out.println(sha);
                        }
                    }

//                }
            }
        }

        ArrayList<String> fileList = new ArrayList<>();
        fileList.addAll(fileSET);
        fileList.removeAll(stopFileSet);


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
            } else if (co_changedFiles.size() == 1) {
                support_matrix[index_a][index_a] += 1;
            }


        }

        System.out.println(" confidence matrix");
        StringBuilder sb_pair = new StringBuilder();
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

//        System.out.println("print support matrix");
//        /**  print support matrix **/
//        io.rewriteFile("filename," + io.removeBrackets(fileSET.toString()) + ",\n", historyDirPath + repoUrl + "/supportMatrix.csv");
//        StringBuilder sb = new StringBuilder();
//        System.out.println(fileList.size() + " files");
//        for (int i = 0; i < fileList.size(); i++) {
//            System.out.print(i + ",");
//            io.writeTofile(fileList.get(i) + ",", historyDirPath + repoUrl + "/supportMatrix.csv");
//
//            for (int j = 0; j < fileList.size(); j++) {
//                io.writeTofile(support_matrix[i][j] + ",", historyDirPath + repoUrl + "/supportMatrix.csv");
//
//            }
//            io.writeTofile("\n", historyDirPath + repoUrl + "/supportMatrix.csv");
//
//
//        }
//        io.writeTofile(sb.toString(), historyDirPath + repoUrl + "/supportMatrix.csv");

        int filesTotal = fileList.size();

        System.out.println("write to file");
        float modularity = (float) count_bigger_than_zero / (filesTotal * filesTotal - filesTotal);
        io.writeTofile(repoUrl + " modularity: " + modularity * 100 + " %  = " + count_bigger_than_zero + " / "
                + (filesTotal * filesTotal - filesTotal) + " , " + commitSET.size() + " unique commits in total, all branches has " + total + " commits\n"
                + " , files in total: " + fileList.size() + "\n------\n", historyDirPath + "/repoModularity.csv");
        System.out.println(repoUrl + " modularity: " + modularity * 100 + " %  = " + count_bigger_than_zero + " / "
                + (filesTotal * filesTotal - filesTotal) + " , " + commitSET.size() + " unique commits in total, all branches has " + total + " commits , files in total: " + fileList.size() + "\n------\n");
        io.writeTofile(repoUrl + "," + modularity * 100 + "\n", historyDirPath + "/repo_ECI.csv");

    }
//    public void measureModularity(String repoUrl) {
//
//        try {
//            getCommitHistory(repoUrl);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (GitAPIException e) {
//            e.printStackTrace();
//        }
//
//
////        /*** read file**/
////        IO_Process io = new IO_Process();
////        HashSet<String> commitSet = new HashSet<>();
////        String changedFileHistory[] = null;
////        try {
////            changedFileHistory = io.readResult(historyDirPath + repoUrl + "/changedFile_history.csv").split("branch:\n");
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
////
////        for (int i = 0; i < changedFileHistory.length; i++) {
////            String branch_line = changedFileHistory[i];
////            String[] changedFile_result = branch_line.split("\\$");
////
////            for (int j = 0; j < changedFile_result.length; j++) {
////                String commit = changedFile_result[j];
////
////                if (commit.contains("\t")) {
////                    String[] lines_line = commit.split("\n");
////                    String sha = lines_line[0].replace("\'", "");
////                    if (!commitSet.contains(sha)) {
////                        commitSet.add(sha);
////                        for (int s = 2; s < lines_line.length; s++) {
////                            String[] file = lines_line[s].split("\t");
////                            if (file.length == 3) {
////                                String filename = file[2];
////
////                            }
////                        }
////
////
////                    }
////                }
////            }
////        }
//
//    }


    public static HashSet<String> stopFiles(String analyzingRepoOrFork) {
        HashSet<String> stopFileSet = new HashSet<>();
        stopFileSet.add("gitignore");
        stopFileSet.add("license");
        stopFileSet.add("readme");
        stopFileSet.add("/dev/null");
        stopFileSet.add(".pdf");
        stopFileSet.add(".mk");
        stopFileSet.add(".xlsx");
        stopFileSet.add(".png");
        stopFileSet.add("arduinoaddons");
        stopFileSet.add(".fon");
        stopFileSet.add(".hex");
        stopFileSet.add(".txt");
        stopFileSet.add(".ini");
        stopFileSet.add(".css");
        stopFileSet.add(".html");
        stopFileSet.add("changelog");
        stopFileSet.add(".js");
        stopFileSet.add(".rst");
        stopFileSet.add(".sch");
        stopFileSet.add(".ino");
        stopFileSet.add(".md");
        stopFileSet.add(".sh");
        stopFileSet.add(".dll");
        stopFileSet.add(".svn-base");
        stopFileSet.add(".bin");
        stopFileSet.add(".xml");
        if (analyzingRepoOrFork.equals("repo")) {
            stopFileSet.add(".h");
        }
        return stopFileSet;

    }

    private boolean isStopFile(String fileName) {

        for (String file : stopFileSet) {
            if (fileName.toLowerCase().contains(file)) {
                return true;
            }
        }
        return false;
    }


    public void getChangedFileTable(String forkurl, String repoUrl) {
        System.out.println("analyzing repo: " + repoUrl + " fork: " + forkurl);

        /**   clone **/
        JgitUtility jg = new JgitUtility();
        jg.cloneRepo(forkurl);

        String repoName = forkurl.split("/")[0];

        IO_Process io = new IO_Process();
        io.rewriteFile("", pathname + "changedFile_history.csv");
        io.rewriteFile("", pathname + "status_history.csv");

        String[] branchArray;
        try {
            branchArray = io.readResult(pathname + repoName + "_branchList.txt").split("\n");
            for (String branch : branchArray) {
                String[] get_ChangedFileCMD = {"git", "log", branch, "--full-history", "--numstat", "--pretty=\'$%H\'"};
                String changedFile_result = io.exeCmd(get_ChangedFileCMD, pathname + ".git");
                io.writeTofile("branch:\n" + changedFile_result, historyDirPath + "changedFile_history.csv");


                String[] get_StatusFileCMD = {"git", "log", branch, "--full-history", "--name-status", "--pretty=\'$%H\'"};
                String status_result = io.exeCmd(get_StatusFileCMD, pathname + ".git");
                io.writeTofile("branch:\n" + status_result, historyDirPath + "status_history.csv");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        /***  combine --stat and --namestatus result**/
        HashSet<String> commitSet = new HashSet<>();
        String changedFileHistory[] = null;
        String statusHistory[] = null;
        try {
            changedFileHistory = io.readResult(historyDirPath + "changedFile_history.csv").split("branch:\n");
            statusHistory = io.readResult(historyDirPath + "status_history.csv").split("branch:\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


//        /** remove local copy of fork**/
//        try {
//            io.deleteDir(new File(current_dir + "/cloneRepos/" + forkurl));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        for (int i = 0; i < changedFileHistory.length; i++) {
            String branch_line = changedFileHistory[i];
            String branch_status = statusHistory[i];
            String[] changedFile_result = branch_line.split("\\$");
            String[] status_result = branch_status.split("\\$");

            for (int j = 0; j < changedFile_result.length; j++) {
                String commit = changedFile_result[j];
                String commit_status_ = status_result[j];

                if (commit.contains("\t")) {
                    String[] lines_line = commit.split("\n");
                    String[] lines_status = commit_status_.split("\n");
                    String sha = lines_line[0].replace("\'", "");
                    if (!commitSet.contains(sha)) {
                        commitSet.add(sha);
                        for (int s = 2; s < lines_line.length; s++) {
                            String[] file = lines_line[s].split("\t");
                            if (file.length == 3) {
                                String add = file[0];
                                String delete = file[1];
                                String filename = file[2];
                                String status = lines_status[s].split("\t")[0];
                                io.writeTofile(repoUrl + "," + forkurl + "," + sha + "," + filename + "," + status + "," + add + "," + delete + "\n", pathname + "File_history.csv");

                            }
                        }


                    }
                }
            }
        }
    }


    private void getLOCdiff(String repoUrl) {
        IO_Process io = new IO_Process();
        String[] forkListInfo = {};
        try {
            forkListInfo = io.readResult(resultDirPath + repoUrl + "/PR_graph_info.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String locHistory = historyDirPath + repoUrl + "/changedSize_LOC.csv";

        for (int i = 1; i < forkListInfo.length; i++) {
            String forkINFO = forkListInfo[i];
            String forkurl = forkINFO.split(",")[1];
            pathname = tmpDirPath + forkurl + "/";


            /** clone fork **/
            JgitUtility jg = new JgitUtility();
            jg.cloneRepo(forkurl);


            HashMap<HashSet<String>, Integer> result = new HashMap<>();
            Repository repo = null;
            try {
                repo = new FileRepository(pathname + ".git");
            } catch (IOException e) {
                e.printStackTrace();
            }
            Git git = new Git(repo);


            String[] columns = forkINFO.split(",");
            HashSet<String> codeChangeCommits = new HashSet<>();
            List<String> commits_onlyF = Arrays.asList(io.removeBrackets(columns[33]).split("/ "));
            List<String> commits_F2U = Arrays.asList(io.removeBrackets(columns[34]).split("/ "));
            codeChangeCommits.addAll(commits_F2U);
            codeChangeCommits.addAll(commits_onlyF);
            codeChangeCommits.remove("");
            RevWalk rw = new RevWalk(repo);

            int readmeAdded = 0;
            int linesAdded = 0;

            HashSet<String> addFileSet = new HashSet();
            HashSet<String> deleteFileSet = new HashSet();
            HashSet<String> renameFileSet = new HashSet();
            HashSet<String> modifyFileSet = new HashSet();
            HashSet<String> copyFileSet = new HashSet();


            for (String sha : codeChangeCommits) {
                System.out.println(sha);
                RevCommit commit = getCommit(sha, repo, git);
                if (commit != null && commit.getParentCount() > 0) {

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
                            for (DiffEntry diff : diffs) {
                                String fileName = diff.getNewPath();
                                if (!isStopFile(fileName)) {
                                    commit_fileSet.add(fileName);
                                    int tmp_linesAdded = 0;
                                    int tmp_linesDeleted = 0;

                                    for (Edit edit : df.toFileHeader(diff).toEditList()) {
                                        tmp_linesAdded += edit.getEndA() - edit.getBeginA();
                                        tmp_linesDeleted += edit.getEndB() - edit.getBeginB();
                                    }
                                    linesAdded += tmp_linesAdded;

                                    if (fileName.toLowerCase().contains("readme")) {
                                        readmeAdded += tmp_linesAdded;
                                    }

                                    String changeType = diff.getChangeType().name();
                                    if (changeType.equals("ADD")) {
                                        addFileSet.add(fileName);
                                    } else if (changeType.equals("COPY")) {
                                        copyFileSet.add(fileName);
                                    } else if (changeType.equals("DELETE")) {
                                        deleteFileSet.add(fileName);
                                    } else if (changeType.equals("MODIFY")) {
//                                        System.out.println(fileName);
//                                        System.out.println("before:"+modifyFileSet.size());
                                        modifyFileSet.add(fileName);
//                                        System.out.println("after:"+modifyFileSet.size());
                                    } else if (changeType.equals("RENAME")) {
                                        renameFileSet.add(fileName);
                                    }

                                }
                            }


                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    System.out.println(sha);
                }

            }

            System.out.println(repoUrl + "," + forkurl + "," + addFileSet.size() + "," + modifyFileSet.size() + ","
                    + deleteFileSet.size() + "," + copyFileSet.size() + "," + renameFileSet.size() + "," + linesAdded + "," + readmeAdded + "\n");

            io.writeTofile(repoUrl + "," + forkurl + "," + addFileSet.size() + "," + modifyFileSet.size() + ","
                    + deleteFileSet.size() + "," + copyFileSet.size() + "," + renameFileSet.size() + "," + linesAdded + "," + readmeAdded + ","
                    + addFileSet.toString() + "," + modifyFileSet.toString() + ","
                    + deleteFileSet.toString() + "," + copyFileSet.toString() + "," + renameFileSet.toString() + "\n", locHistory);

            /** remove local copy of fork**/
            System.out.println("delete clone " + forkurl);
            try {
                io.deleteDir(new File(tmpDirPath + forkurl));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }


    public RevCommit getCommit(String idString, Repository repository, Git git) {
        try {

            ObjectId o1 = repository.resolve(idString);
//            ObjectId o1 = ObjectId.fromString( idString );
            if (o1 != null) {
                RevWalk walk = new RevWalk(repository);
                RevCommit commit = walk.parseCommit(o1);
//                    PersonIdent ai = commit.getAuthorIdent();
//                    System.out.println("bbb >>" + ai.getEmailAddress() + "|" + ai.getTimeZone() + "|" + ai.getWhen() + ", ref :" + idString);
                return commit;
            } else {
                System.err.println("Could not get commit with SHA :" + idString);
            }
        } catch (Exception e) {
//            System.out.println("err :" + e);
            e.printStackTrace();
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


    private void generatingFileCoChangeGraph(String repoUrl) {
        StringBuilder sb = new StringBuilder();
        IO_Process io = new IO_Process();
        String changedFileHistory[] = null;
        try {
            changedFileHistory = io.readResult(pathname + "changedFile_history.csv").split("branch:\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        HashSet<String> commitSet = new HashSet<>();
        HashSet<String> allFileSet = new HashSet<>();

        for (String branch : changedFileHistory) {
            String[] changedFile_result = branch.split("\\$");
            for (String commit : changedFile_result) {
                if (commit.contains("\t")) {
                    String[] lines = commit.split("\n");
                    String sha = lines[0].replace("\'", "");
                    if (!commitSet.contains(sha)) {
                        commitSet.add(sha);
                        HashSet<String> commit_files = new HashSet<>();
                        for (int i = 2; i < lines.length; i++) {
                            String[] file = lines[i].split("\t");
                            if (file.length == 3) {
                                String filename = file[2];
                                commit_files.add(filename);
                            }
                        }
                        allFileSet.addAll(commit_files);
                    }
                }


            }
        }

        ArrayList<String> allFileList = new ArrayList<>();
        allFileList.addAll(allFileSet);
        sb.append("*Vertices " + allFileList.size() + "\n");
        for (int i = 1; i <= allFileList.size(); i++) {
            sb.append(i + " \"" + allFileList.get(i - 1) + "\"\n");
        }

        commitSet = new HashSet<>();
        sb.append("*Arcs\n");
        for (String branch : changedFileHistory) {
            String[] changedFile_result = branch.split("\\$");
            for (String commit : changedFile_result) {
                if (commit.contains("\t")) {
                    String[] lines = commit.split("\n");
                    String sha = lines[0].replace("\'", "");
                    if (!commitSet.contains(sha)) {
                        commitSet.add(sha);
                        HashSet<String> commit_files = new HashSet<>();
                        for (int i = 2; i < lines.length; i++) {
                            String[] file = lines[i].split("\t");
                            if (file.length == 3) {
//                                String add = file[0];
//                                String delete = file[1];
                                String filename = file[2];
                                commit_files.add(filename);
                            }

                        }

                        if (commit_files.size() > 1) {
                            HashSet<HashSet<String>> allPairs_string = getAllPairs_string(commit_files);
                            for (HashSet<String> pairSet : allPairs_string) {
                                ArrayList<String> pair = new ArrayList<>();
                                pair.addAll(pairSet);
                                int a = allFileList.indexOf(pair.get(0)) + 1;
                                int b = allFileList.indexOf(pair.get(1)) + 1;
                                sb.append(a + " " + b + "\n");
                            }
                        }
                    }
                }


            }
        }
        io.rewriteFile(sb.toString(), pathname + "CoChangedFileGraph.pajek.net");


    }


}
