import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

public class AnalyzeCommitChangedFile {
    static String working_dir, pr_dir, output_dir, clone_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;

    AnalyzeCommitChangedFile() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
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


    public void analyzeChangedFile(HashSet<String> commitSet) {
        IO_Process io = new IO_Process();
        LocalDateTime now = LocalDateTime.now();

        String insert_changedFile_query = " INSERT INTO fork.commit_changedFiles (" +
                "added_file  , added_files_num  , modified_file, modify_files_num , renamed_file ,renamed_files_num ,copied_file ," +
                " copied_files_num, deleted_file , deleted_files_num , " +
                "add_loc , modify_loc , delete_loc , data_update_at ,index_changedFile,commitSHA_id,readme_loc, about_config)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a1,? AS a2,? AS a3,? AS a4,?  AS a30,? AS a5,? AS a6," +
                "? AS a7,? AS a8,? AS a9,? AS a10,? AS a11,? AS a12,? AS a13,? AS a14,? AS a15,? AS a16 ,? AS a17) AS tmp" +
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

            long start = System.nanoTime();
            for (String commit : commitSet) {
                String[] commit_info = commit.split(",");
                //repo.repoURL, c.commitSHA, c.id
                String projectURL = commit_info[0];
                String sha = commit_info[1];
                int commitshaID = Integer.parseInt(commit_info[2]);

                System.out.println(sha + "," + projectURL);
                long start_getcommit = System.nanoTime();
                ArrayList<String> changedfiles = io.getCommitFromCMD(sha, projectURL);
                if (changedfiles == null) {
                    System.out.println("commit does not exist in local git history");
                    io.writeTofile(sha + "," + projectURL + "\n", output_dir + "lostCommit.txt");
                    continue;
                } else if (changedfiles.get(0).trim().equals("")) {
                    System.out.println("no commit in pr");
                    io.writeTofile(sha + "," + projectURL + "\n", output_dir + "noCommit.txt");
                    continue;
                }

                long end_getcommit = System.nanoTime();
                System.out.println("get a commit changed file from cmd:" + TimeUnit.NANOSECONDS.toMillis(end_getcommit - start_getcommit) + " ms");
                int index_d = 0;

                long start_commit = System.nanoTime();
                int count = 0;
                for (String file : changedfiles) {
                    index_d++;
                    String[] arr = file.split("\t");
                    int addLine = 0, deleteLine = 0;
                    String fileName , changeType ;
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
                        preparedStmt.setInt(16, commitshaID);
                        preparedStmt.setInt(17, readmeAdded);
                        preparedStmt.setInt(18, aboutConfig);
                        preparedStmt.setInt(19, commitshaID);
                        preparedStmt.setInt(20, index_d);
                        preparedStmt.addBatch();
                    } else {
                        io.writeTofile(sha + "," + projectURL  + "\n", output_dir + "noEditableFiles.txt");
                        continue;

                    }
                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt);
                        conn.commit();
                    }

                }
                long end_commit = System.nanoTime();
                System.out.println("——--" + changedfiles.size() + " changed files in one commit :" + TimeUnit.NANOSECONDS.toMillis(end_commit - start_commit) + " ms");

            }
            long end = System.nanoTime();
            System.out.println("generating insert querys for a commit  :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

            io.executeQuery(preparedStmt);
            conn.commit();

        } catch (
                SQLException e)

        {
            e.printStackTrace();
        }

    }


    static public void main(String[] args) {
        AnalyzeCommitChangedFile accf = new AnalyzeCommitChangedFile();
        IO_Process io = new IO_Process();
        HashSet<String> todo_commits = io.get_un_analyzedCommit();
        System.out.println(todo_commits.size()+" commits in query");
        accf.analyzeChangedFile(todo_commits);
    }


}
