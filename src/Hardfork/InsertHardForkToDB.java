package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class InsertHardForkToDB {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir, hardfork_dir, checkedCandidates, graph_dir;
    static String myUrl, user, pwd;

    InsertHardForkToDB() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            timeline_dir = output_dir + "shurui_crossRef.cache_pass/";
            hardfork_dir = output_dir + "/hardfork-exploration/";
            checkedCandidates = hardfork_dir + "checked_repo_pairs.txt";
            graph_dir = output_dir + "ClassifyCommit_new/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * This function insert hardfork that was identified by commit history graph
     * --- comparing upstream and fork's history
     */
    public void insertHardFork_commitsGraph() {
        IO_Process io = new IO_Process();
        String query = "INSERT fork.HardFork\n" +
                "SET hardfork_url =?,upstream_url = ?, OnlyF = ?, OnlyU=?, F2U = ?, U2F = ?";
        final int[] count = {0};
        try {
            Files.newDirectoryStream(Paths.get(graph_dir),
                    path -> path.toString().endsWith("2019.csv"))
                    .forEach(
                            filepath -> {
                                System.out.println(filepath);
                                List<String> row = io.readCSV(filepath.toFile()).get(0);
                                String fork = row.get(0);
                                String upstream = row.get(1);
//onlyFork.size() + "," + onlyUpstream.size() + "," +    fork2Upstream.size() + "," + upstream2Fork.size()
                                int onlyFork = Integer.parseInt(row.get(2));
                                int onlyUpstream = Integer.parseInt(row.get(3));
                                int fork2Upstream = Integer.parseInt(row.get(4));
                                int upstream2Fork = Integer.parseInt(row.get(5));

                                if (!(onlyFork == 0 && onlyUpstream == 0 & fork2Upstream == 0 & upstream2Fork == 0)) {


                                    try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                                         PreparedStatement preparedStmt = conn.prepareStatement(query);
                                    ) {
                                        conn.setAutoCommit(false);

                                        preparedStmt.setString(1, fork);
                                        preparedStmt.setString(2, upstream);
                                        preparedStmt.setInt(3, onlyFork);
                                        preparedStmt.setInt(4, onlyUpstream);
                                        preparedStmt.setInt(5, fork2Upstream);
                                        preparedStmt.setInt(6, upstream2Fork);

                                        preparedStmt.addBatch();
                                        if (++count[0] % 100 == 0) {
                                            io.executeQuery(preparedStmt);
                                            conn.commit();
                                            System.out.println(count + " count ");

                                        }
                                        io.executeQuery(preparedStmt);
                                        conn.commit();
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }

                                    System.out.println("move file " + filepath);
                                    try {
                                        io.fileCopy(String.valueOf(filepath), filepath.toString().replace("ClassifyCommit_new", "ClassifyCommit_checked"));
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
    }


    private void insertHardFork_description() {
        IO_Process io = new IO_Process();
        List<List<String>> candidate_list = io.readCSV("/Users/shuruiz/Work/ForkData/hardfork-exploration/aForkOf-3ForkLevel-origin.csv");


        String query = "INSERT INTO fork.HardFork (hardfork_url, hardfork_description, hardfork_language, hardfork_createdAt,\n" +
                "                           upstream_url, upstream_description, upstream_language, upstream_createdAt,\n" +
                "                             grandpa_repo_url, grandpa_description, grandpa_createdAt,grandpa_forkedFrom_ghtRepoID,\n" +
                "                           mentionForkInDescription,data_updatedAt)\n" +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)\n";


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            conn.setAutoCommit(false);

            int count = 1;
            for (int i = 1; i < candidate_list.size(); i++) {
                List<String> columns = candidate_list.get(i);
                int len = columns.size();

                System.out.println(columns.toString());
                String hardfork_url = columns.get(0);
                String hardfork_description = columns.get(1);
                String hardfork_language = columns.get(2);
                String hardfork_createdAt = columns.get(3);

                String upstream_url = columns.get(4);
                String upstream_description = columns.get(5);
                String upstream_language = columns.get(6);
                String upstream_createdAt = columns.get(7);


                String GrandPa_repo_url = columns.get(8);
                String GrandPa_description = columns.get(9);
                String GrandPa_createdAt = columns.get(10);

                String GrandPa_forkedFrom_ghtRepoID = "";
                int int_GrandPa_forkedFrom_ghtRepoID = -1;
                if (len > 11) {
                    GrandPa_forkedFrom_ghtRepoID = columns.get(11);
                    if (GrandPa_forkedFrom_ghtRepoID.equals("")) {
                        int_GrandPa_forkedFrom_ghtRepoID = -1;
                    } else {
                        int_GrandPa_forkedFrom_ghtRepoID = Integer.valueOf(GrandPa_forkedFrom_ghtRepoID);
                    }
                }

                preparedStmt.setString(1, hardfork_url);
                preparedStmt.setString(2, hardfork_description);
                preparedStmt.setString(3, hardfork_language);
                preparedStmt.setString(4, hardfork_createdAt);

                preparedStmt.setString(5, upstream_url);
                preparedStmt.setString(6, upstream_description);
                preparedStmt.setString(7, upstream_language);
                preparedStmt.setString(8, upstream_createdAt);

                preparedStmt.setString(9, GrandPa_repo_url);
                preparedStmt.setString(10, GrandPa_description);
                preparedStmt.setString(11, GrandPa_createdAt);
                preparedStmt.setInt(12, int_GrandPa_forkedFrom_ghtRepoID);
                preparedStmt.setBoolean(13, true);
                preparedStmt.setString(14, String.valueOf(Calendar.getInstance().getTime()));


                preparedStmt.addBatch();
//                if (++count % 100 == 0) {
                io.executeQuery(preparedStmt);
                conn.commit();

                System.out.println(count + " count ");

//                }


            }

//            io.executeQuery(preparedStmt);
//            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }


    private void updateContact() {
        IO_Process io = new IO_Process();
        List<List<String>> candidate_list = io.readCSV("/Users/shuruiz/Work/ForkData/hardfork-exploration/aForkOf-3ForkLevel_email-0413.csv");


        String query = "UPDATE HardFork\n" +
                "SET hardfork_email = ?, hardfork_lastUpdate = ?, upstream_email=?, upstream_lastUpdate=? ,grandpa_email = ?, grandpa_lastUpdate = ?" +
                "WHERE hardfork_url = ?";


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            conn.setAutoCommit(false);

            int count = 1;
            for (int i = 1; i < candidate_list.size(); i++) {
                List<String> columns = candidate_list.get(i);
                int len = columns.size();

                System.out.println(columns.toString());
                String hardfork_url = columns.get(0);
                String hardfork_email = columns.get(1);
                String hardfork_pushedat = columns.get(2);
                String upstream_email = columns.get(3);
                String upstream_pushedat = columns.get(4);

                String grandpa_email = columns.get(5);

                String grandpa_pushedat = "";
                if (len > 7) {
                    grandpa_pushedat = columns.get(6);
                }


                preparedStmt.setString(1, hardfork_email);
                preparedStmt.setString(2, hardfork_pushedat);
                preparedStmt.setString(3, upstream_email);
                preparedStmt.setString(4, upstream_pushedat);

                preparedStmt.setString(5, grandpa_email);
                preparedStmt.setString(6, grandpa_pushedat);
                preparedStmt.setString(7, hardfork_url);

                preparedStmt.addBatch();
                if (++count % 100 == 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                    System.out.println(count + " count ");
                }
            }
            io.executeQuery(preparedStmt);
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }


    public void updateHardFork_commitsGraph() {
        IO_Process io = new IO_Process();
        String query = "UPDATE fork.HardFork_withCriteria\n" +
                "SET  OnlyF = ?, OnlyU=?, F2U = ?, U2F = ?,berforeForking = ? " +
                "WHERE hardfork_url =? ";
        final int[] count = {0};
        try {
            Files.newDirectoryStream(Paths.get(graph_dir),
                    path -> path.toString().endsWith("2019.csv"))
                    .forEach(
                            filepath -> {
                                System.out.println(filepath);
                                List<List<String>> lists = io.readCSV(filepath.toFile());
                                try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                                     PreparedStatement preparedStmt = conn.prepareStatement(query);
                                ) {
                                    for (List<String> row : lists) {
                                        String fork = row.get(0);
                                        String upstream = row.get(1);
//onlyFork.size() + "," + onlyUpstream.size() + "," +    fork2Upstream.size() + "," + upstream2Fork.size()
                                        int onlyFork = Integer.parseInt(row.get(2));
                                        int onlyUpstream = Integer.parseInt(row.get(3));
                                        int fork2Upstream = Integer.parseInt(row.get(4));
                                        int upstream2Fork = Integer.parseInt(row.get(5));
                                        int beforeForking_commits = Integer.parseInt(row.get(6));

                                        if (!(onlyFork == 0 && onlyUpstream == 0 & fork2Upstream == 0 & upstream2Fork == 0)) {


                                            conn.setAutoCommit(false);


                                            preparedStmt.setInt(1, onlyFork);
                                            preparedStmt.setInt(2, onlyUpstream);
                                            preparedStmt.setInt(3, fork2Upstream);
                                            preparedStmt.setInt(4, upstream2Fork);
                                            preparedStmt.setInt(5, beforeForking_commits);
                                            preparedStmt.setString(6, fork);

                                            preparedStmt.addBatch();
                                            if (++count[0] % 100 == 0) {
                                                io.executeQuery(preparedStmt);
                                                conn.commit();
                                                System.out.println(count + " count ");
                                            }

                                        }
                                    }
                                    io.executeQuery(preparedStmt);
                                    conn.commit();
                                } catch (SQLException e) {

                                    e.printStackTrace();
                                }
                                System.out.println("move file " + filepath);
                                try {
                                    io.fileCopy(String.valueOf(filepath), filepath.toString().replace("ClassifyCommit_new", "ClassifyCommit_checked"));
                                    Files.deleteIfExists(filepath);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }


                            }

                    );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        InsertHardForkToDB insertHardForkToDB = new InsertHardForkToDB();


        /** insert hardfork candidates identified by comparing upstream & fork's commit history **/
//        insertHardForkToDB.insertHardFork_commitsGraph();
        insertHardForkToDB.updateHardFork_commitsGraph();

        /** insert hardfork candidates identified by 'is a fork of' from repo description **/
//        insertHardForkToDB.insertHardFork_description();


        /**  update fork upstream grandpa contact info and updated info **/

//        insertHardForkToDB.updateContact();


    }


}
