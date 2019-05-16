package Pull_Request;

import Util.IO_Process;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class CheckSocialConnection {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    static IO_Process io = new IO_Process();

    CheckSocialConnection() {
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
        new CheckSocialConnection();
        List<String> repos = io.getList_byFilePath(current_dir + "/input/file_repoList.txt");
        System.out.println(repos.size() + " projects ");
        final int[] count = {0};
        for (String projectURL : repos) {
            int projectID = io.getRepoId(projectURL);
            System.out.println("projectID: " + projectID + ", " + projectURL);
            System.out.println("get all PR for " + projectURL);
//            HashMap<String, HashMap<String, ArrayList<Integer>>> maintainer_owners = io.getClosedPROwnerMaintainer(projectID);
            HashMap<String, HashMap<String, ArrayList<String>>> maintainer_owners = io.getClosedPROwnerMaintainer(projectID);


            String query = "UPDATE Pull_Request pr\n" +
                    "SET pr.socialConnection = 1\n" +
                    "WHERE pr.projectID= " +projectID+
                    " AND pr.pull_request_ID = ?" +
                    " AND  ? > ?" ;
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(query)) {
                conn.setAutoCommit(false);
                maintainer_owners.forEach((maintainer, ownerSet) -> {
                    System.out.println("get follower of "+maintainer);
//                    Set<String> followerSet = io.getFollower(maintainer);
                    HashMap<String,String> follower_followDate = io.getFollower(maintainer);
                    Set<String> followerSet = follower_followDate.keySet();
                    ownerSet.forEach((owner, prSet) -> {
                        if (followerSet.contains(owner)) {
                            System.out.println(owner + " follows "+maintainer+" , "+prSet.size() +" PRs");
//                            for (int prID : prSet) {
                            String followDate = follower_followDate.get(owner);
                            for (String prID_createAt : prSet) {
                                int prID = Integer.parseInt(prID_createAt.split(",")[0]);
                                String createAt = prID_createAt.split(",")[1];
                                try {
                                    preparedStmt.setInt(1,prID);
                                    preparedStmt.setString(2,createAt);
                                    preparedStmt.setString(3,followDate);
                                    preparedStmt.addBatch();
                                    if (++count[0] % 100 == 0) {
                                        io.executeQuery(preparedStmt);
                                        try {
                                            conn.commit();
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                        System.out.println(count + " count ");

                                    }
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }

                        }
                    });
                });
                    io.executeQuery(preparedStmt);
                    conn.commit();

                } catch(SQLException e){
                    e.printStackTrace();
                }

            }


        }

    }


