package Commit;

import Util.IO_Process;

import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class AnalyzeEasiness {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    static int batchSize = 100;

    AnalyzeEasiness() {
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

    public static void main(String[] args) {

        new AnalyzeEasiness();
        IO_Process io = new IO_Process();

        /*** insert repoList to repository table ***/

        List<String> repos = io.getListFromFile("easiness_repoList.txt");

        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            int projectID = io.getRepoId(projectUrl);
            projectID = 70;
            System.out.println(projectUrl);
            String query = "SELECT\n" +
                    "  pr.pull_request_ID,\n" +
                    "  GROUP_CONCAT(cc.added_file SEPARATOR ' '),\n" +
                    "  GROUP_CONCAT(cc.modified_file SEPARATOR ' ')\n" +
                    "FROM Pull_Request pr, PR_Commit_map prc, Commit c, commit_changedFiles cc\n" +
                    "WHERE prc.pull_request_id = pr.pull_request_ID AND prc.sha = c.SHA AND c.sha = cc.commit_SHA AND\n" +
                    "      pr.projectID = prc.projectID AND prc.projectID = \n" + projectID +
                    " GROUP BY 1;";

            HashSet<String> pr_info = new HashSet<>();
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(query);
            ) {
                ResultSet rs = preparedStmt.executeQuery();
                while (rs.next()) {
                    pr_info.add(rs.getInt(1) + "," + rs.getString(2) + "," + rs.getString(3));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            System.out.println(pr_info.size() + " prs has been analyzed ...");

            String updatePR_easiness = "UPDATE fork.Pull_Request SET easiness = ? ,easiness_filtered = ? WHERE pull_request_ID = ? AND projectID = ?";
            try (Connection conn1 = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt_1 = conn1.prepareStatement(updatePR_easiness);) {
                conn1.setAutoCommit(false);
                int count = 0;

                for (String pr : pr_info) {
                    if(!pr.startsWith("1,")){
                        continue;
                    }
                    System.out.println(pr);
                    String[] arr = pr.split(",");
                    int pr_id;
                    HashSet<String> added_files = new HashSet<>();
                    HashSet<String> changed_files = new HashSet<>();
                    if (arr.length == 1) {
                        continue;
                    } else {
                        pr_id = Integer.parseInt(arr[0]);
                        added_files.addAll(Arrays.asList(arr[1].split(" ")));
                        added_files.remove("");
                        if (arr.length == 3) {
                            changed_files.addAll(Arrays.asList(arr[2].split(" ")));
                            changed_files.remove("");
                        }
                    }


                    int added_size = added_files.size();
                    int changed_size = changed_files.size();

                    if (added_size > 0) {
                        added_files.remove("");
                    }
                    if (changed_size > 0) {
                        changed_files.remove("");
                        changed_files.removeAll(added_files);
                    }
                    System.out.println(" add " + added_size + "changed " + changed_size);
                    double easiness = 0.0;
                    double easiness_filtered = 0.0;
                    if (added_size + changed_size > 0) {

                        easiness = (double) added_size / (added_size + changed_size);

                        List<String> filtered_added_file = io.removeStopFile(added_files);
                        int size_add_filter = filtered_added_file.size();

                        List<String> filtered_changed_file = io.removeStopFile(changed_files);
                        int size_change_filter = filtered_changed_file.size();
                        System.out.println("filtered add " + size_add_filter + " changed " + size_change_filter);
                        if (size_add_filter + size_change_filter > 0) {
                            easiness_filtered = (double) size_add_filter / (size_add_filter + size_change_filter);
                        }
                    }

                    System.out.println(projectUrl + " , pr " + pr_id + " ea: " + easiness + " f_ea: " + easiness_filtered);
                    preparedStmt_1.setDouble(1, easiness);
                    preparedStmt_1.setDouble(2, easiness_filtered);
                    preparedStmt_1.setInt(3, pr_id);
                    preparedStmt_1.setInt(4, projectID);
                    preparedStmt_1.addBatch();

                    if (++count % batchSize == 0) {
                        System.out.println(count + " add easiness to db... of repo " + projectUrl);
                        io.executeQuery(preparedStmt_1);
                        conn1.commit();
                    }
                }
                io.executeQuery(preparedStmt_1);
                conn1.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
