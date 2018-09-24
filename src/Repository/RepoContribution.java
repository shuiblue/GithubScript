package Repository;

import Commit.InsertCommit_graphBasedResult;
import Util.IO_Process;
import Util.JgitUtility;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class RepoContribution {

    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath, resultDirPath;
    static String myUrl, user, pwd;
    final int batchSize = 100;
    String[] commitType = {"onlyF", "F2U"};

    public RepoContribution() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            historyDirPath = output_dir + "commitHistory/";
            resultDirPath = output_dir + "ClassifyCommit_new/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public void main(String[] args) {
        InsertCommit_graphBasedResult acc = new InsertCommit_graphBasedResult();
        new RepoContribution();
        IO_Process io = new IO_Process();
        int count_insert = 0;
        /** get repo list **/
        String current_dir = System.getProperty("user.dir");
        List<String> repoList = io.getList_byFilePath(current_dir + "/input/graph_result_repoList.txt");

        System.out.println(repoList.size() + " repos ");
        for (String repoUrl : repoList) {
            int projectID = io.getRepoId(repoUrl);
            String graph_result_csv = resultDirPath + repoUrl.replace("/", ".") + "_graph_result_allFork.csv";
            if (new File(graph_result_csv).exists()) {
                System.out.println("analyze " + repoUrl);
                String[] forkListInfo = new String[0];
                //todo check empty graph csv
                try {
                    String forkListInfoString = io.readResult(graph_result_csv);
                    forkListInfo = forkListInfoString.split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                HashMap<String, HashMap<String, List<String>>> forkOwnCode = acc.getForkOwnCode(forkListInfo);


                String insert_query_1 = " INSERT ignore INTO fork.repository_contribution (repoURL, upstreamURL,forkid,upstreamID,projectID , onlyF , F2U , OnlyU , U2F )" +
                        "VALUES (?,?,?,?,?,?,?,?,?)";
                try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                     PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                    conn.setAutoCommit(false);

                    for (int i = 1; i < forkListInfo.length; i++) {

                        String line = forkListInfo[i];
                        String[] arr = line.split(",");
                        String forkURL = arr[0];
                        int forkid = io.getRepoId(forkURL);
                        String upstreamURL = arr[1];
                        int upstreamID = io.getRepoId(upstreamURL);
                        int onlyF = forkOwnCode.get("onlyF").get(forkURL).size();
                        int f2u = forkOwnCode.get("F2U").get(forkURL).size();
                        int onlyU = Integer.parseInt(arr[3]);
                        int u2f = Integer.parseInt(arr[5]);

                        preparedStmt.setString(1, forkURL);
                        preparedStmt.setString(2, upstreamURL);
                        preparedStmt.setInt(3, forkid);
                        preparedStmt.setInt(4, upstreamID);
                        preparedStmt.setInt(5, projectID);
                        preparedStmt.setInt(6, onlyF);
                        preparedStmt.setInt(7, f2u);
                        preparedStmt.setInt(8, onlyU);
                        preparedStmt.setInt(9, u2f);

                        System.out.println(preparedStmt.toString());
                        preparedStmt.addBatch();
                        if (++count_insert % 100 == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    }

                    io.executeQuery(preparedStmt);
                    conn.commit();
                } catch (SQLException e) {
                    e.printStackTrace();
                }


            } else {
                System.out.println(repoUrl + " not exist ");
                io.writeTofile(repoUrl + "\n", output_dir + "miss_graph.txt");
            }
        }
    }


}
