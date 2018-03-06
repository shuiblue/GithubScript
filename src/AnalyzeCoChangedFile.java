import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by shuruiz on 2/27/18.
 */
public class AnalyzeCoChangedFile {
    static String tmpDirPath;
    static String resultDirPath;
    static String current_dir;
    static String pathname,historyDirPath;


    static public void main(String[] args) {
        String  analyzingRepoOrFork = "repo";
//        String  analyzingRepoOrFork = "fork";


        current_dir = System.getProperty("user.dir");
        tmpDirPath = current_dir + "/cloneRepos/";
        historyDirPath = current_dir + "/commitHistory/";
        resultDirPath = current_dir + "/result/";
        IO_Process io = new IO_Process();
        String[] repoList = {}, forkList = {};
        current_dir = System.getProperty("user.dir");
        System.out.println("current dir = " + current_dir);
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/

        try {
            repoList = io.readResult(current_dir + "/input/repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }



        AnalyzeCoChangedFile acc = new AnalyzeCoChangedFile();

        for (String repoUrl : repoList) {
            try {
                forkList = io.readResult(resultDirPath + repoUrl + "/ActiveForklist.txt").split("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (String forkINFO : forkList) {
                String forkurl = forkINFO.split(",")[0];
                pathname = tmpDirPath + forkurl + "/";
                System.out.println(repoUrl);
                acc.getChangedFileTable(forkurl, repoUrl);

                // acc.generatingFileCoChangeGraph(repoUrl);
            }
            acc.getLOCdiff(repoUrl);
        }
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
        HashMap<String, ArrayList<String>> commit_fileMap = new HashMap<>();
        StringBuilder sb = new StringBuilder();

        pathname = tmpDirPath + repoUrl + "/";


        String[] forkListInfo = {};
        try {
            forkListInfo = io.readResult(resultDirPath + repoUrl + "/PR_graph_info.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (int i = 1; i < forkListInfo.length; i++) {
            String forkINFO = forkListInfo[i];
            String forkurl = forkINFO.split(",")[1];
            pathname = tmpDirPath + forkurl + "/";

            String changedFileHistory[] = null;
            try {
                changedFileHistory = io.readResult(pathname + "File_history.csv").split("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (String line : changedFileHistory) {
                String[] changedFile_result = line.split(",");
                String current_sha = changedFile_result[2];
                if (!commit_fileMap.keySet().contains(current_sha)) {
                    ArrayList<String> fileList;
                    if (commit_fileMap.get(current_sha) != null) {
                        fileList = commit_fileMap.get(current_sha);
                    } else {
                        fileList = new ArrayList<>();
                    }

                    fileList.add(line);
                    commit_fileMap.put(current_sha, fileList);
                }

            }


            String[] columns = forkINFO.split(",");
            HashSet<String> codeChangeCommits = new HashSet<>();
            List<String> commits_onlyF = Arrays.asList(io.removeBrackets(columns[33]).split("/ "));
            List<String> commits_F2U = Arrays.asList(io.removeBrackets(columns[34]).split("/ "));
            codeChangeCommits.addAll(commits_onlyF);
            codeChangeCommits.addAll(commits_F2U);

            HashSet<String> allFileSet = new HashSet<>();
            HashSet<String> addFileSet = new HashSet<>();
            HashSet<String> modify_FileSet = new HashSet<>();
            int addLine = 0;

            for (String commit : codeChangeCommits) {
                if (!commit.trim().equals("")) {
                    ArrayList<String> fileList = commit_fileMap.get(commit);
                    if(fileList==null){
                        System.out.println();
                    }
                    for (String file : fileList) {
                        String[] array_file = file.split(",");
                        String filename = array_file[3];
                        allFileSet.add(filename);
                        String status = array_file[4];
                        int add = Integer.parseInt(array_file[5]);
                        int delete = Integer.parseInt(array_file[6]);
                        if (status.equals("M")) {
                            addLine += add;
                        } else if (status.equals("A")) {
                            addLine += add;
                            addFileSet.add(file);

                        }

                    }
                }
            }
           io.writeTofile(repoUrl+","+forkurl+","+allFileSet.size()+","+addFileSet.size()+","+addLine+","+allFileSet.toString()+","+addFileSet.toString()+"\n",resultDirPath+"/changefileSummary.csv");

        }


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
            getAllPairs_string(set);
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
