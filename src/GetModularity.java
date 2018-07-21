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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

            stopFileSet.addAll(Arrays.asList(io.readResult(current_dir + "/input/StopFiles.txt").split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    static public void main(String[] args) {
        GetModularity getModularity = new GetModularity();

        IO_Process io = new IO_Process();
        String[] repoList = {};
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/
        String current_dir = System.getProperty("user.dir");
        try {
            repoList = io.readResult(current_dir + "/input/mod_repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean[] filterOutStopFile = {true, false};
        for (String projectURL : repoList) {
            File clone = new File(clone_dir + projectURL);
            if (!clone.exists()) {
                System.out.println(projectURL + " clone does not exist , cloning now..");
                io.cloneRepo("", projectURL);

            }

            System.out.println(projectURL + "  start to calculate modularity...");

            String cmd_getFirstCommit = "git rev-list --max-parents=0 HEAD --pretty=\"%ar\"";
            String projectCloneDir = clone_dir + projectURL + "/";
            String[] commitArray = io.exeCmd(cmd_getFirstCommit.split(" "), projectCloneDir).split("\n");
            System.out.println(commitArray.length + " root commits");

//            int firstCommitCreatedAt = 0;
//            for (String line : commitArray) {
//                if (line.contains("years")) {
//                    System.out.println(line);
//                    int currentResult = Integer.parseInt(line.split("years")[0].trim());
//                    firstCommitCreatedAt = firstCommitCreatedAt > currentResult ? firstCommitCreatedAt : currentResult;
//                } else if (line.contains("year")) {
//                    System.out.println(line);
//                    int currentResult = Integer.parseInt(line.split("year")[0].trim());
//                    firstCommitCreatedAt = firstCommitCreatedAt > currentResult ? firstCommitCreatedAt : currentResult;
//                }
//            }
//            System.out.println("pre firstCommitCreatedAt = " + firstCommitCreatedAt);
//
////                if (firstCommitCreatedAt > 20) firstCommitCreatedAt = 20;
//            if (firstCommitCreatedAt > 10) firstCommitCreatedAt = 10;
//
//            System.out.println("after firstCommitCreatedAt = " + firstCommitCreatedAt);


            for (boolean b : filterOutStopFile) {
                System.out.println("calculating modularity for " + projectURL + " filter out stop file -- " + b);
//                    int threshold = 10;
//                    while (threshold < 40) {
////                    while (threshold < 100) {
//                        for (int year = 1; year <= firstCommitCreatedAt + 1; year++) {
//                            System.out.println("analyzing repo: " + projectURL + ", threshold is " + threshold + "，within " + year + " years");
                getModularity.measureModularity(projectURL, 50, b, 10);
//                        }
//                        threshold += 20;
//                    }
            }

            System.out.println("delete " + projectURL);
            try {
                io.deleteDir(new File(clone_dir + projectURL));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /***
     * This function calculates modularity for each project by generating support matrix
     * @param projectURL
     * @throws IOException
     * @throws GitAPIException
     */
    public void measureModularity(String projectURL, int threshold, boolean filterOutStopFile, int within_year) {
        IO_Process io = new IO_Process();
        String after_Date = ZonedDateTime.now(ZoneOffset.UTC).minusYears(within_year).toInstant().toString();
        String project_cloneDir = clone_dir + projectURL + "/";
        String fileScope = "allfile";
        if (filterOutStopFile) {
            fileScope = "noStopFile";
        }

        String outputFileName = projectURL.replace("/", "~") + "_" + threshold + "_" + within_year + "_year_" + fileScope;
        String outputPath = historyDirPath + outputFileName + ".txt";
        File output = new File(outputPath);
//        if (output.exists()) {
//            System.out.println("result exist: " + outputFileName);
//            return;
//        }


        /**  get all the branches **/
        String cmd_getOringinBranch = "git branch -a --list origin*";
        String[] branchList_array = io.exeCmd(cmd_getOringinBranch.split(" "), project_cloneDir).split("\n");
        ArrayList<String> branchList = new ArrayList<>();
        for (String br : branchList_array) {
            if (!br.contains("HEAD")) {
                branchList.add(br.trim());
            }
        }

        /** get commit from all branches **/
        HashSet<String> commitSET = new HashSet<>();
        ArrayList<String> commitList = new ArrayList<>();
        for (String br : branchList) {
            System.out.println("getting commits from branch " + br);
            List<String> shaList = io.getCommitInBranch(br, after_Date, project_cloneDir);
            commitSET.addAll(shaList);
            commitList.addAll(shaList);
            System.out.println(commitSET.size() + " commits for now");
            if (commitSET.size() > 500) {
                break;
            }
        }

        /** analyze commit in each branch **/
        HashSet<String> allFileSet = new HashSet<>();
        ArrayList<HashSet<String>> changedFilePair_Set = new ArrayList<>();

        HashSet<String> analyzedSHA = new HashSet<>();
            for (String sha : commitList) {
                if(analyzedSHA.size()>500){
                    break;
                }
                if (analyzedSHA.contains(sha)) {
                    System.out.println("same sha , pass ");
                    continue;
                }
                analyzedSHA.add(sha);

//                System.out.println(sha + " count " + analyzedSHA.size());
                ArrayList<String> changedFiles = io.getChangedFileStatus_ofCommit_FromCMD(sha, projectURL);
                HashSet<String> commit_fileSet = new HashSet<>();
                if (changedFiles.size() <= threshold) {
                    for (String file : changedFiles) {
                        if (!file.equals("")) {
                            String[] arr = file.split("\t");
                            String status = arr[0];
                            String fileName = arr[1];
                            if (status.equals("A") || status.equals("M")) {
                                if ((filterOutStopFile && !io.isStopFile(fileName)) || !filterOutStopFile) {
                                    commit_fileSet.add(fileName);
                                }
                                allFileSet.addAll(commit_fileSet);
                            }
                        }
                    }

                    if (commit_fileSet.size() == 1) {
                        changedFilePair_Set.add(commit_fileSet);
                    } else if (commit_fileSet.size() > 1) {
                        HashSet<HashSet<String>> pairs = io.getAllPairs_string(commit_fileSet);
                        changedFilePair_Set.addAll(pairs);
                    }
                }


            }


        ArrayList<String> fileList = new ArrayList<>();
        fileList.addAll(allFileSet);
        if (filterOutStopFile) {
            fileList.removeAll(stopFileSet);
        }


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


        System.out.println(" FileList size " + fileList.size());
        System.out.println(" support matrix, changed file set size: " + changedFilePair_Set.size());
        int index = 0;
        for (HashSet<String> co_changedFiles : changedFilePair_Set) {
            List<String> list = new ArrayList<>();
            if (filterOutStopFile) {
                co_changedFiles.removeAll(stopFileSet);
            }

            if (co_changedFiles.size() > 0) {
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

        }
/** generating graph, node--file, edge -- co-changed relation */
//        /** generating graph, node--file, edge -- co-changed relation */
//        io.rewriteFile(sb.toString(), historyDirPath + projectURL + "/CoChangedFileGraph.pajek.net");


        System.out.println(" confidence matrix");
        int count_bigger_than_zero = 0;
        for (int i = 0; i < fileList.size(); i++) {
            float appearance = support_matrix[i][i];

            support_matrix[i][i] = 1;
            for (int j = 0; j < fileList.size(); j++) {
                if (i != j && support_matrix[i][j] > 2) {
                    float support = support_matrix[i][j];
                    support_matrix[i][j] = support_matrix[i][j] / appearance;
//                    io.writeTofile(fileList.get(i) + "," + fileList.get(j) + "," + support + "," + support_matrix[i][j] + "," + appearance + "\n", historyDirPath + projectURL + "/SC_table.csv");
                    count_bigger_than_zero++;
                }
            }

        }


        int filesTotal = fileList.size();
        System.out.println("write to file");
        float modularity = (float) count_bigger_than_zero / (filesTotal * filesTotal - filesTotal);

        int total_commit = commitSET.size();
//        System.out.println("write to file:" + historyDirPath + outputFileName + "_Modularity.csv");
//        io.writeTofile(projectURL + " modularity: " + modularity * 100 + " %  = " + count_bigger_than_zero + " / "
//                + (filesTotal * filesTotal - filesTotal) + " , " + total + " unique commits in total, all branches has " + total + " commits\n"
//                + " , files in total: " + fileList.size() + "\n------\n", historyDirPath + outputFileName + "_Modularity.txt");
        System.out.println(projectURL + " modularity: " + modularity * 100 + " %  = " + count_bigger_than_zero + " / "
                + (filesTotal * filesTotal - filesTotal) + " , " + total_commit + " unique commits in total, all branches has " + total_commit + " commits , files in total: " + fileList.size() + "\n------\n");

        io.writeTofile(projectURL + "," + modularity * 100 + "," + total_commit + "," + filesTotal + "\n", outputPath);

        System.out.println("write to file:" + outputPath);

    }


}
