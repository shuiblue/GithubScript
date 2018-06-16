import org.apache.solr.common.util.Hash;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AnalyzeCommitChangedFile {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;

    AnalyzeCommitChangedFile() {
        IO_Process io = new IO_Process();
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
                "add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commit_uuid,readme_loc, about_config)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a1,? AS a2,? AS a3,? AS a4,?  AS a30,? AS a5,? AS a6," +
                "? AS a7,? AS a8,? AS a9,? AS a10,? AS a11,? AS a12,? AS a13,? AS a14,? AS a15,? AS a16 ,? AS a17) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.commit_changedFiles AS cc" +
                "      WHERE cc.commit_uuid = ?" +
                "      AND cc.index_changedFile = ?" +
                "  )" +
                "  LIMIT 1";

        int projectID = io.getRepoId(projectURL);
        HashSet<String> analyzedCommits = io.commitChangedFileExists(projectID);


        HashSet<String> project_forks = new HashSet<>();
        HashSet<String> forkLoginIDSet = new HashSet<>();

        for (String line : commitSet) {
            String[] arr = line.split(",");
            if (arr.length == 3) {
                String fork_loginID = line.split(",")[2];
                if (forkLoginIDSet.contains(fork_loginID)) continue;

                forkLoginIDSet.add(fork_loginID);
                String forkURL = io.getForkURL_by_loginID(fork_loginID, projectID);
                if (!forkURL.equals("")) {
                    System.out.println("fork " + forkURL);
                    project_forks.add(forkURL);
                }
            }
        }
        System.out.println("cloning " + project_forks.size() + " forks of" + projectURL);
        new JgitUtility().cloneRepo_cmd(project_forks, projectURL, false);


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insert_changedFile_query);) {
            conn.setAutoCommit(false);

            HashSet<String> miss_Clone_project = new HashSet<>();
            for (String commit : commitSet) {
                String[] commit_info = commit.split(",");
                String commitshaID = commit_info[1];


                if (analyzedCommits.contains(commitshaID)) {
                    String sha = commit_info[0];
                    //repo.repoURL, c.commitSHA, c.id
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

                        int count = 0;
                        for (String file : changedfiles) {
                            index_d++;
                            String[] arr = file.split("\t");
                            int addLine = 0, deleteLine = 0;
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
                                preparedStmt.setString(16, commitshaID);
                                preparedStmt.setInt(17, readmeAdded);
                                preparedStmt.setInt(18, aboutConfig);
                                preparedStmt.setString(19, commitshaID);
                                preparedStmt.setInt(20, index_d);
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
                } else {
                    System.out.println(commitshaID + " analyzed in repo " + projectURL);
                }
            }
            System.out.println("\n add all commit set data to database..");
            io.executeQuery(preparedStmt);
            conn.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    static public void main(String[] args) {
        AnalyzeCommitChangedFile accf = new AnalyzeCommitChangedFile();
        IO_Process io = new IO_Process();


        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/file_repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(repos.length + " projects ");
        int count = 0;

        while (count < repos.length) {
            System.out.println("restart loop..");
            count = 0;
            for (String projectURL : repos) {
                int projectID = io.getRepoId(projectURL);
                System.out.println("get all commit for " + projectURL);

                System.out.println("get all commit for database..");
                List<String> allcommits = io.getCommitList(projectID);
                Collections.shuffle(allcommits);

                System.out.println("Sampling 1/3 of all commits..");
                HashSet<String> sampledCommits = new HashSet<>();

                for (String s : allcommits) {
                    if (sampledCommits.size() > (allcommits.size() / 3)) {
                        break;
                    }
                    sampledCommits.add(s);
                }
                if (allcommits.size() > 0) {
                    System.out.println("start to analyze "+sampledCommits.size()+ " commits...");
                    accf.analyzeChangedFile(sampledCommits, projectURL);
                } else {
                    count++;
                }
                try {
                    System.out.println("delete " + projectURL);
                    io.deleteDir(new File(clone_dir + projectURL));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

    }


}
