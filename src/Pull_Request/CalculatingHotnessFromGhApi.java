package Pull_Request;

import Hardfork.InsertHardForkToDB;
import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class CalculatingHotnessFromGhApi {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    static IO_Process io = new IO_Process();

    CalculatingHotnessFromGhApi() {
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


    public static void main(String[] args) {
        new CalculatingHotnessFromGhApi();
        IO_Process io = new IO_Process();
        String query = "INSERT INTO fork.commit_files_GHAPI(index_changedFiles, sha, projectID, additions, changes, deletions, filename, status,isRemoved) \n" +
                "    VALUES (?,?,?,?,?,?,?,?,?) ";
        final int[] count = {0};

        List<String> repos = io.getList_byFilePath(current_dir + "/input/repoList.txt");
        System.out.println(repos.size() + " projects ");

        for (String projectURL : repos) {
            System.out.println(projectURL + " ");
            int projectID = io.getRepoId(projectURL);

            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(query);
            ) {
                conn.setAutoCommit(false);
                try {
                    Files.newDirectoryStream(Paths.get(output_dir + "shurui_commitFiles.cache/"),
                            path -> path.toString().endsWith(".csv"))
                            .forEach(
                                    filepath -> {
                                        String projectURL_dot = projectURL.replace("/", ".");
                                        if (filepath.toString().contains(projectURL_dot)) {
                                            System.out.println(filepath);
//get_commit_changedFile.twbs.bootstrap_0162c7dd2196bd4a82855016b5c462e04c876d99.csv
                                            String sha = filepath.getFileName().toString().replace("get_commit_changedFile." + projectURL_dot + "_", "").replace(".csv", "");

                                            List<List<String>> rows = io.readCSV(filepath.toFile());
                                            for (int i = 1; i < rows.size(); i++) {
                                                List<String> row = rows.get(i);
                                                int index = Integer.parseInt(row.get(0)) + 1;
                                                int additions = Integer.parseInt(row.get(1));
                                                int changes = Integer.parseInt(row.get(2));
                                                int deletions = Integer.parseInt(row.get(3));
                                                String filename = row.get(4);
                                                String status = row.get(5);
                                                boolean isRemoved = status.equals("removed") ? true : false;

//                                            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//                                                 PreparedStatement preparedStmt = conn.prepareStatement(query);
//                                            ) {
//                                                conn.setAutoCommit(false);

                                                try {

                                                    preparedStmt.setInt(1, index);
                                                    preparedStmt.setString(2, sha);
                                                    preparedStmt.setInt(3, projectID);
                                                    preparedStmt.setInt(4, additions);
                                                    preparedStmt.setInt(5, changes);
                                                    preparedStmt.setInt(6, deletions);
                                                    preparedStmt.setString(7, filename);
                                                    preparedStmt.setString(8, status);
                                                    preparedStmt.setBoolean(9, isRemoved);

                                                    preparedStmt.addBatch();
                                                    if (++count[0] % 100 == 0) {
                                                        io.executeQuery(preparedStmt);
                                                        try {
                                                            conn.commit();
                                                        } catch (SQLException e) {
                                                            e.printStackTrace();
                                                        }
                                                        System.out.println(count[0] + " count ");

                                                    }

                                                } catch (SQLException e) {
                                                    e.printStackTrace();
                                                }

                                            }
                                            try {
//                                                io.fileCopy(String.valueOf(filepath), filepath.toString().replace("shurui_commitFiles.cache", "shurui_commitFiles.cache_checked"));
                                                io.fileCopy(String.valueOf(filepath), "/DATA/shurui/ForkData/shurui_commitFiles.cache_checked/get_commit_changedFile." + projectURL_dot + "_" + sha + ".csv");
                                                Files.deleteIfExists(filepath);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }

                            );
                } catch (IOException e) {
                    e.printStackTrace();
                }

                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

}
