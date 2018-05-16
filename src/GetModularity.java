import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
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

public class GetModularity {
    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath, resultDirPath;
    static String myUrl, user, pwd;
    HashSet<String> stopFileSet = new HashSet<>();


    public GetModularity() {
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

            stopFileSet.addAll(Arrays.asList(io.readResult(current_dir + "input/StopFiles.txt").split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //todo time window 2 years
    //todo  get commit created at
    static public void main(String[] args) {
        GetModularity getModularity = new GetModularity();

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

        /** todo: this threshold is used for filtering out 20% large commits **/
        boolean[] filterOutStopFile = {true, false};
        for (boolean b : filterOutStopFile) {
            int threshold = 10;
            while (threshold < 100) {
                for (String repoUrl : repoList) {
                    System.out.println("analyzing repo: " + repoUrl + ", threshold is " + threshold);
                    getModularity.measureModularity(repoUrl, threshold, b);
                }
                threshold += 5;
            }
        }
    }

    /***
     * This function calculates modularity for each project by generating support matrix
     * @param repoUrl
     * @throws IOException
     * @throws GitAPIException
     */
    public void measureModularity(String repoUrl, int threshold, boolean filterOutStopFile) {
        String repoCloneDir = clone_dir + repoUrl + "/";
        String filepath = historyDirPath + repoUrl + "/changedFile_history.csv";

        IO_Process io = new IO_Process();
        io.rewriteFile("", filepath);
        Repository repo = null;
        try {
            repo = new FileRepository(repoCloneDir + ".git");
        } catch (IOException e) {
            e.printStackTrace();
        }
        io.rewriteFile("", historyDirPath + repoUrl + "/commitSize_file.csv");
        io.rewriteFile("", historyDirPath + repoUrl + "/SC_table.csv");


        Git git = new Git(repo);
        List<Ref> branches = null;
        try {
            branches = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        HashSet<String> commitSET = new HashSet<>();
        HashSet<String> fileSET = new HashSet<>();
        ArrayList<HashSet<String>> changedFile_Set = new ArrayList<>();
        int total = 0;
        //todo git log -1 9b63430f349f7083d09d2db24d24908e1d277379 --pretty="%ai" get author date
        for (Ref branch : branches) {
            String branchName = branch.getName();
            Iterable<RevCommit> commits = null;
            try {
                commits = git.log().add(repo.resolve(branchName.replace("refs/", ""))).call();

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
            } catch (GitAPIException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
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
