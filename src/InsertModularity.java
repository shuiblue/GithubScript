import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashSet;
import java.util.List;

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
            historyDirPath = output_dir + "comithis/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        InsertModularity im = new InsertModularity();
        /** 1 */
//        im.getMod_multipleCriteria();

        /** 2*/
        im.getLast500Mod();
    }

    private static HashSet<String> getExistMod() {
        String query = "SELECT projectID,filterout_stopFile,threshold_num_files_per_commit,num_latest_year\n" +
                "FROM Modularity";


        HashSet<String> list = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                list.add(rs.getInt(1) + "," + rs.getInt(2) + "," + rs.getInt(3) + "," + rs.getInt(4));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;


    }

    public void getMod_multipleCriteria() {
        IO_Process io = new IO_Process();
        List<String> mod_left_repos = io.getListFromFile("mod_left.txt");
        for (String repo : mod_left_repos) {
            //projectID,filterout_stopFile,threshold_num_files_per_commit,num_latest_year
            HashSet<String> existingModResult = getExistMod();

            String insertIssueQuery = "INSERT INTO fork.Modularity(projectID, filterout_stopFile, num_latest_year, threshold_num_files_per_commit,modularity,num_commits_total,num_file_total) " +
                    "VALUES (?,?,?,?,?,?,?)";


            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertIssueQuery);) {
                conn1.setAutoCommit(false);
                final int[] count = {0};
                Files.newDirectoryStream(Paths.get(historyDirPath), path -> path.toFile().isFile())
                        .forEach(file -> {

                                    String fileName = file.getFileName().toString();
                                    if (fileName.endsWith(".txt")) {
                                        if (fileName.contains(repo.replace("/", "~"))) {
                                            System.out.println(fileName);
                                            String[] modResult = {};
                                            try {
                                                modResult = io.readResult(String.valueOf(file.toAbsolutePath())).split("\n")[0].split(",");
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                            String repoURL = modResult[0];
                                            int projectID = io.getRepoId(repoURL);
                                            System.out.println("project id:" + projectID + " url " + repoURL);

                                            String[] arr = fileName.split("_");
                                            boolean filterout_stopFile = arr[arr.length - 1].contains("noStopFile") ? true : false;
                                            int filter = filterout_stopFile ? 1 : 0;
                                            String year = arr[arr.length - 3];
                                            String threshold = arr[arr.length - 4];


                                            //projectID,filterout_stopFile,threshold_num_files_per_commit,num_latest_year
                                            if (!existingModResult.contains(projectID + "," + filter + "," + threshold + "," + year)) {
                                                if (!modResult[1].equals("NaN")) {
                                                    float mod = Float.parseFloat(modResult[1]);
                                                    System.out.println(" mod " + mod);
                                                    int num_commit = Integer.parseInt(modResult[2]);
                                                    int num_file = Integer.parseInt(modResult[3].trim());
                                                    try {
                                                        preparedStmt_1.setInt(1, projectID);
                                                        preparedStmt_1.setBoolean(2, filterout_stopFile);
                                                        preparedStmt_1.setInt(3, Integer.parseInt(year));
                                                        preparedStmt_1.setInt(4, Integer.parseInt(threshold));
                                                        preparedStmt_1.setFloat(5, mod);
                                                        preparedStmt_1.setInt(6, num_commit);
                                                        preparedStmt_1.setInt(7, num_file);
                                                        preparedStmt_1.addBatch();
                                                        System.out.println("count " + count[0]);
                                                        if (++count[0] % batchSize == 0) {
                                                            io.executeQuery(preparedStmt_1);
                                                            conn1.commit();
                                                        }
                                                    } catch (SQLException e) {
                                                        e.printStackTrace();
                                                    }
                                                } else {
                                                    io.writeTofile(fileName + "\n", output_dir + "/Mod_NaN.txt");
                                                }
                                            } else {
                                                System.out.println(repoURL + " exist in database :)");
                                                return;
                                            }
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

    public void getLast500Mod() {
        IO_Process io = new IO_Process();
        List<String> mod_left_repos = io.getListFromFile("mod_left.txt");
        for (String repoURL : mod_left_repos) {
            System.out.println(repoURL);

            String insertIssueQuery = "UPDATE  fork.Final_modularity SET `1000Mod_allFile` =?,`1000Mod_filterFile`=? WHERE projectID = ? ";


            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(insertIssueQuery);) {
                conn1.setAutoCommit(false);
                final int[] count = {0};

//spree-contrib~spree_slider_50_10_year_allfile.txt
                String allfile_fileName = historyDirPath + repoURL.replace("/", "~") + "_50_10_year_allfile.txt";
                String filtered_fileName = historyDirPath + repoURL.replace("/", "~") + "_50_10_year_noStopFile.txt";

                String[] all_Mod_result = {};
                String[] filter_Mod_result = {};
                try {
                    all_Mod_result = io.readResult(allfile_fileName).split("\n");
                    filter_Mod_result = io.readResult(filtered_fileName).split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                all_Mod_result = all_Mod_result[all_Mod_result.length - 1].split(",");
                filter_Mod_result = filter_Mod_result[filter_Mod_result.length - 1].split(",");


                int projectID = io.getRepoId(repoURL);
                System.out.println("project id:" + projectID + " url " + repoURL);


                int num_commit = 0, num_file_all = 0, num_file_afterFilter = 0;
                double mod_all = 0, mod_filter = 0;
                //projectID,filterout_stopFile,threshold_num_files_per_commit,num_latest_year
                if (!all_Mod_result[1].equals("NaN")) {
                    mod_all = Double.parseDouble(all_Mod_result[1]);
                    System.out.println(" mod " + mod_all);
                    num_commit = Integer.parseInt(all_Mod_result[2]);
                    num_file_all = Integer.parseInt(all_Mod_result[3].trim());

                } else {
                    io.writeTofile(repoURL + ",all" + "\n", output_dir + "/Mod_NaN.txt");
                }

                if (!filter_Mod_result[1].equals("NaN")) {
                    mod_filter = Double.parseDouble(filter_Mod_result[1]);
                    System.out.println(" mod " + mod_filter);

                } else {
                    io.writeTofile(repoURL + ",filter\n", output_dir + "/Mod_NaN.txt");
                }

                num_file_afterFilter = Integer.parseInt(filter_Mod_result[3].trim());
                try {

                    preparedStmt_1.setDouble(1, mod_all);
                    preparedStmt_1.setDouble(2, mod_filter);
                    preparedStmt_1.setInt(3, projectID);
                    preparedStmt_1.addBatch();
                    System.out.println("count " + count[0]);
                    if (++count[0] % batchSize == 0) {
                        io.executeQuery(preparedStmt_1);
                        conn1.commit();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                System.out.println("inserting modularity");
                io.executeQuery(preparedStmt_1);
                conn1.commit();
                System.out.println("done ");

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
