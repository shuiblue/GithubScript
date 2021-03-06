package Commit;

import Util.IO_Process;
import Util.JgitUtility;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class AnalyzeCommitChangedFile {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;
    static IO_Process io = new IO_Process();

    AnalyzeCommitChangedFile() {
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


    public void analyzeChangedFile(HashSet<String> commitSet, String projectURL) {
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();

        String insert_changedFile_query = " INSERT INTO fork.commit_changedFiles (" +
                "added_file  , added_files_num  , modified_file, modify_files_num , renamed_file ,renamed_files_num ,copied_file ," +
                " copied_files_num, deleted_file , deleted_files_num , " +
                "add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commit_SHA,readme_loc, about_config)" +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        int projectID = io.getRepoId(projectURL);
        if (projectID == -1) {
            io.writeTofile(projectURL + "\n", output_dir + "repoNull.txt");
            return;
        }
        HashSet<String> analyzedCommits = io.commitChangedFileExists(projectID);
        System.out.println(analyzedCommits.size() + " commmit has been analyzed for changed File...");

        System.out.println("get sampled fork url");
        HashSet<String> commitCandidates = new HashSet<>();

        Set<String> forkLoginIDSet = new HashSet<>();
        for (String line : commitSet) {
            String[] arr = line.split(",");
            //  commitSHA,   ,loginID
            if (arr.length == 2) {
                String sha = arr[0];
                if (!analyzedCommits.contains(sha)) {
                    String fork_loginID = arr[1];
                    forkLoginIDSet.add(fork_loginID);
                    commitCandidates.add(sha);
                }
            }
        }

        if (forkLoginIDSet.size() == 0) {
            System.out.print("no commit to analyze");
            return;
        }
            Set<String> project_forks = io.getForkIDSet_by_loginID(forkLoginIDSet, projectID);


        System.out.println("cloning " + project_forks.size() + " forks of" + projectURL);

        if (project_forks.size() > 0) {
            new JgitUtility().cloneRepo_cmd(project_forks, projectURL, false);
            int count = 0;
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_changedFile_query);) {
                conn.setAutoCommit(false);
                System.out.println(commitCandidates.size() + " commits is in the queue...");
                HashSet<String> miss_Clone_project = new HashSet<>();
                for (String sha : commitCandidates) {
                    if (!miss_Clone_project.contains(projectURL) && new File(clone_dir + projectURL).exists()) {

                        System.out.println(sha);
                        ArrayList<String> changedfiles = io.getCommitFromCMD(sha, projectURL);
                        if (changedfiles == null) {
                            io.writeTofile(sha + "," + projectURL + "\n", output_dir + "lostCommit.txt");
                            continue;
                        } else if (changedfiles.get(0).trim().equals("")) {
                            io.writeTofile(sha + "," + projectURL + "\n", output_dir + "noCommit.txt");
                            continue;
                        }

                        int index_d = 0;


                        for (String file : changedfiles) {
                            index_d++;
                            String[] arr = file.split("\t");
                            int addLine, deleteLine;
                            String fileName, changeType;
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
                                preparedStmt.setString(16, sha);
                                preparedStmt.setInt(17, readmeAdded);
                                preparedStmt.setInt(18, aboutConfig);
                                preparedStmt.addBatch();
                                System.out.print("add 1 commit ...");
                            } else {
                                io.writeTofile(sha + "," + projectURL + "\n", output_dir + "noEditableFiles.txt");
                                continue;

                            }
                            if (++count % batchSize == 0) {
                                System.out.println("\n add " + count + " to database..");
                                io.executeQuery(preparedStmt);
                                conn.commit();
                            }

                        }
                    } else {
                        System.out.println(clone_dir + projectURL + " clone not available.");
                        io.writeTofile(projectURL + "\n", output_dir + "clone_miss.txt");
                        miss_Clone_project.add(projectURL);
                        continue;
                    }

                }
                if (count > 0) {
                    System.out.println("\n add all commit set data to database..");
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    private static HashSet<String> getCommitFromGraphResult(int projectID) {
        String query = "SELECT SHA, loginID\n" +
                "from fork.Commit\n" +
                "WHERE projectID = " + projectID +
                " and (f2u IS NOT NULL OR  only_f IS NOT NULL) ";


        HashSet<String> allcommits = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allcommits.add(rs.getString(1) + "," + rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


        return allcommits;

    }

    public static HashSet<String> samplePRs(List<String> prs) {
        HashSet<String> sampledCommits = new HashSet<>();

        if (prs.size() > 100) {
            Collections.shuffle(prs);
            for (String s : prs) {
                if (sampledCommits.size() > (prs.size() / 3)) {
                    break;
                }
                sampledCommits.add(s);
            }
        } else {
            sampledCommits.addAll(prs);
        }
        return sampledCommits;
    }


    static public void main(String[] args) {
        boolean samplePR = false;
        new AnalyzeCommitChangedFile();
        List<String> repos = io.getList_byFilePath(current_dir + "/input/file_repoList.txt");
        System.out.println(repos.size() + " projects ");

        for (String projectURL : repos) {
            int projectID = io.getRepoId(projectURL);
            System.out.println("get all PR for " + projectURL);

            List<String> PRs_merged = io.getPRList_String(projectID, true);
            System.out.println(PRs_merged.size() + " merged PR for " + projectURL);

            List<String> PRs_rejected = io.getPRList_String(projectID, false);
            System.out.println(PRs_rejected.size() + " reject PR for " + projectURL);


            HashSet<String> sampledCommits;
            if (samplePR) {
                System.out.println("sampling prs for project " + projectURL);
                HashSet<String> sampled_PRs = new HashSet<>();
                sampled_PRs.addAll(samplePRs(PRs_merged));
                sampled_PRs.addAll(samplePRs(PRs_rejected));
                if (sampled_PRs.size() == 0) continue;
                sampledCommits = io.getCommitsByPRlist(sampled_PRs, projectID);
            } else {

                HashSet<String> all_PRs = new HashSet<>();
                all_PRs.addAll(PRs_merged);
                all_PRs.addAll(PRs_rejected);
                if (all_PRs.size() == 0) continue;
                sampledCommits = io.getCommitsByPRlist(all_PRs, projectID);
            }


//            HashSet<String> commit_from_graph = getCommitFromGraphResult(projectID);
//            System.out.println("sampled commits from pr :" + sampledCommits.size());
//            sampledCommits.addAll(commit_from_graph);
//            System.out.println("added commits from graph :" + sampledCommits.size());

            System.out.println("start to analyze " + sampledCommits.size() + " commits...");
            new AnalyzeCommitChangedFile().analyzeChangedFile(sampledCommits, projectURL);

            try {
                System.out.println("\ndelete " + projectURL);
                io.deleteDir(new File(clone_dir + projectURL));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

}
