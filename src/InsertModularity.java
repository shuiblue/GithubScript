import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class InsertModularity {
    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    static int batchSize = 100;


    InsertModularity() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            historyDirPath = output_dir + "commitHistory/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        new InsertModularity();
        IO_Process io = new IO_Process();
        String insertIssueQuery = "INSERT INTO fork.Modularity(projectID, filterout_stopFile, num_latest_year, threshold_num_files_per_commit,modularity,num_commits_total,num_file_total) " +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c,? AS d ,? AS e, ? AS f, ? AS g) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT *" +
                "      FROM fork.Modularity AS m" +
                "      WHERE m.projectID = ?" +
                "      AND m.filterout_stopFile= ?" +
                "      AND m.num_latest_year= ?" +
                "      AND m.threshold_num_files_per_commit= ?" +
                "  )" +
                "  LIMIT 1";


        try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertIssueQuery);) {
            conn1.setAutoCommit(false);
            final int[] count = {0};
            Files.newDirectoryStream(Paths.get(historyDirPath), path -> path.toFile().isFile())
                    .forEach(file -> {
                                if (file.getFileName().toString().endsWith(".txt")) {
                                    System.out.println(file.getFileName().toString());
                                    String[] modResult = {};
                                    try {
                                        modResult = io.readResult(String.valueOf(file.toAbsolutePath())).split("\n")[0].split(",");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    String repoURL = modResult[0];
                                    int projectID = io.getRepoId(repoURL);

                                    int modular_exist = io.checkModularityExist(projectID);
                                    if (modular_exist == 0) {
                                        float mod = Float.parseFloat(modResult[1]);
                                        int num_commit = Integer.parseInt(modResult[2]);
                                        int num_file = Integer.parseInt(modResult[3].trim());


                                        String[] arr = file.getFileName().toString().split("_");
                                        boolean filterout_stopFile = arr[arr.length - 1].contains("noStopFile") ? true : false;
                                        String year = arr[arr.length - 3];
                                        String threshold = arr[arr.length - 4];

                                        try {
                                            preparedStmt_1.setInt(1, projectID);
                                            preparedStmt_1.setBoolean(2, filterout_stopFile);
                                            preparedStmt_1.setInt(3, Integer.parseInt(year));
                                            preparedStmt_1.setInt(4, Integer.parseInt(threshold));
                                            preparedStmt_1.setFloat(5, mod);
                                            preparedStmt_1.setInt(6, num_commit);
                                            preparedStmt_1.setInt(7, num_file);

                                            preparedStmt_1.setInt(8, projectID);
                                            preparedStmt_1.setBoolean(9, filterout_stopFile);
                                            preparedStmt_1.setInt(10, Integer.parseInt(year));
                                            preparedStmt_1.setInt(11, Integer.parseInt(threshold));
                                            preparedStmt_1.addBatch();

                                            if (++count[0] % batchSize == 0) {
                                                io.executeQuery(preparedStmt_1);
                                                conn1.commit();
                                            }
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    }else{
                                        System.out.println(repoURL + " exist in database :)");
                                        return;
                                    }
                                }
                            }
                    );
            System.out.println("inserting modularity");
            io.executeQuery(preparedStmt_1);
            conn1.commit();
            System.out.println("done ");
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
