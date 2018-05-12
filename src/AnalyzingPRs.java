import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by shuruiz on 2/12/18.
 */
public class AnalyzingPRs {

    static String dir;
    static String current_dir;

    static String output_dir;
    static String current_OS = System.getProperty("os.name").toLowerCase();
    static String myUrl;
    static String user;
    final int batchSize = 100;

    public ArrayList<PullRequest> createPR(String repoUrl) {
        ArrayList<PullRequest> prList = new ArrayList<>();
        String[] pr_json, pr_num = {};
        IO_Process io = new IO_Process();
        try {
            pr_json = io.readResult(dir + repoUrl + "/pr.txt").split("\n");
            pr_num = io.readResult(dir + repoUrl + "/pr_num.txt").split(", ");

            for (int i = 0; i < pr_json.length; i++) {
                String json_string = pr_json[i];
                String pr_id = io.removeBrackets(pr_num[i]).replace("{\"number\": ", "").replace("}", "").trim();
                JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                String forkName = (String) pr_info.get("forkName");
                String author = pr_info.get("author").toString();
                String created_at = pr_info.get("created_at").toString();
                String closed_at = pr_info.get("closed_at").toString();
                String closed = pr_info.get("closed").toString();
                String merged = pr_info.get("merged").toString();
                String forkURL = pr_info.get("forkURL").toString();

                HashSet<String> commitSet = new HashSet<>();
                String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                for (String commit : commitArray) {
                    if (!commit.equals("")) {
                        commitSet.add(commit);
                    }
                }

                prList.add(new PullRequest(pr_id, forkName, author, forkURL, created_at, closed_at, closed, merged, commitSet));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return prList;
    }

    /**
     * @param repoUrl
     * @param projectID
     * @return
     */
    public void insertCommitInPR(String repoUrl, int projectID) {
        String[] pr_json, pr_num;
        IO_Process io = new IO_Process();
        HashMap<String, String> forkPRstring_map = new HashMap<>();
        Connection conn;

        LocalDateTime now = LocalDateTime.now();

        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);
            String pr_json_string = io.readResult(dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                pr_num = io.readResult(dir + repoUrl + "/pr_num.txt").split(", ");

                PreparedStatement preparedStmt = null;
                int count = 0;
                String update_commit_query = " INSERT INTO fork.commit (commitSHA,  repoURL, upstreamURL, data_update_at, repoID, belongToRepoID)" +
                        "  SELECT *" +
                        "  FROM (SELECT" +
                        "          ? AS a,? AS b, ? AS c, ? AS d,? AS x, ? AS y) AS tmp" +
                        "  WHERE NOT EXISTS(" +
                        "      SELECT commitSHA" +
                        "      FROM fork.commit AS cc" +
                        "      WHERE cc.commitSHA = ?" +
                        "  )" +
                        "  LIMIT 1";

                preparedStmt = conn.prepareStatement(update_commit_query);

                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(io.removeBrackets(pr_num[s]).replace("{\"number\": ", "").replace("}", "").trim());
                    System.out.println(s);
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkURL = getForkURL(pr_info);

                    int forkID = io.getRepoId(forkURL);
                    if (forkID == -1) {
                        System.out.println("id = -1!!! fork: " + forkURL);
                    }


                    /** put commit into commit_TABLE**/
                    HashSet<String> commitSet = new HashSet<>();
                    String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                    for (String commit : commitArray) {
                        if (!commit.equals("")) {
                            commitSet.add(commit);
                        }
                    }

                    for (String commit : commitSet) {
                        System.out.println("forkURL " + forkURL + " commit " + commit);
                        preparedStmt.setString(1, commit);
                        preparedStmt.setString(2, forkURL);
                        preparedStmt.setString(3, repoUrl);
                        preparedStmt.setString(4, String.valueOf(now));
                        preparedStmt.setInt(5, forkID);
                        preparedStmt.setInt(6, projectID);
                        preparedStmt.setString(7, commit);
                        preparedStmt.addBatch();

                        System.out.println("count" + count);
                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();

                        }
                    }

                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void insertPR_Commit_mapping(String repoUrl, int projectID) {
        String[] pr_json, pr_num;
        IO_Process io = new IO_Process();
        HashMap<String, String> forkPRstring_map = new HashMap<>();
        Connection conn;
        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            String pr_json_string = io.readResult(dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                pr_num = io.readResult(dir + repoUrl + "/pr_num.txt").split(", ");

                PreparedStatement preparedStmt = null;
                int count = 0;
                String insert_commit_query = " INSERT INTO fork.pr_commit (commitsha_id,repoID,pull_request_id)" +
                        "  SELECT *" +
                        "  FROM (SELECT" +
                        "          ? AS a,? AS b, ? AS c ) AS tmp" +
                        "  WHERE NOT EXISTS(" +
                        "      SELECT *" +
                        "      FROM fork.pr_commit AS cc" +
                        "      WHERE cc.commitsha_id = ?" +
                        "      AND cc.repoID = ?" +
                        "      AND cc.pull_request_id = ?" +
                        "  )" +
                        "  LIMIT 1";


                preparedStmt = conn.prepareStatement(insert_commit_query);

                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(io.removeBrackets(pr_num[s]).replace("{\"number\": ", "").replace("}", "").trim());

                    System.out.println(s);
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkURL = getForkURL(pr_info);

                    /** put commit into commit_TABLE**/
                    HashSet<String> commitSet = new HashSet<>();
                    String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                    for (String commit : commitArray) {
                        if (!commit.equals("")) {
                            commitSet.add(commit);
                        }
                    }

                    for (String commit : commitSet) {
                        int sha_id = getSHAID(commit);
                        if (sha_id == -1) {
                            System.out.print("forkURL " + forkURL + " commit is -1 !!! ");
                        }
                        int fork_id = io.getRepoId(forkURL);
                        preparedStmt.setInt(1, sha_id);
                        preparedStmt.setInt(2, fork_id);
                        preparedStmt.setInt(3, pr_id);
                        preparedStmt.setInt(4, sha_id);
                        preparedStmt.setInt(5, fork_id);
                        preparedStmt.setInt(6, pr_id);
                        preparedStmt.addBatch();
                        System.out.println("forkURL " + forkURL + " commit " + commit + " pr" + pr_id);
                        System.out.println("count " + count);

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }

                    }

                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    private int getSHAID(String commit) {
        int sha_id = -1;
        Connection conn;
        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            String getSHAID = "SELECT id FROM commit WHERE commitSHA =?";
            PreparedStatement preparedStmt = conn.prepareStatement(getSHAID);
            preparedStmt.setString(1, commit);

            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {               // Position the cursor                  4
                sha_id = rs.getInt(1);        // Retrieve the first column valu
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sha_id;
    }


    public void insertPR(String repoUrl, int projectID) {
        String[] pr_json, pr_num;
        IO_Process io = new IO_Process();
        HashMap<String, String> forkPRstring_map = new HashMap<>();
        Connection conn;
        LocalDateTime now = LocalDateTime.now();

        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            String pr_json_string = io.readResult(dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                pr_num = io.readResult(dir + repoUrl + "/pr_num.txt").split(", ");

                String insert_query_1 = " INSERT INTO fork.repo_PR( " +
                        "projectID,pull_request_ID, forkName, authorName,forkURL,forkID," +
                        "created_at, closed, closed_at, merged, data_update_at) " +
                        " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d,? AS e,? AS f, ?AS ds,?AS de,?AS d3,?AS d2, ? AS d5) AS tmp" +
                        " WHERE NOT EXISTS (" +
                        " SELECT projectID FROM repo_PR WHERE projectID = ? AND pull_request_ID = ?" +
                        ") LIMIT 1";

                PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1);
                int count = 0;
                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(io.removeBrackets(pr_num[s]).replace("{\"number\": ", "").replace("}", "").trim());

                    System.out.println(s);
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkName = (String) pr_info.get("forkName");
                    String author = pr_info.get("author").toString();
                    String created_at = pr_info.get("created_at").toString();
                    String closed_at = pr_info.get("closed_at").toString();
                    String closed = pr_info.get("closed").toString();
                    String merged = pr_info.get("merged").toString();
                    String forkURL = getForkURL(pr_info);
                    int forkID = io.getRepoId(forkURL);
                    preparedStmt.setInt(1, projectID);
                    preparedStmt.setInt(2, pr_id);
                    preparedStmt.setString(3, forkName);
                    preparedStmt.setString(4, author);
                    preparedStmt.setString(5, forkURL);
                    preparedStmt.setInt(6, forkID);
                    preparedStmt.setString(7, created_at);
                    preparedStmt.setString(8, closed);
                    preparedStmt.setString(9, closed_at);
                    preparedStmt.setString(10, merged);
                    preparedStmt.setString(11, String.valueOf(now));
                    preparedStmt.setInt(12, projectID);
                    preparedStmt.setInt(13, pr_id);
                    preparedStmt.addBatch();
                    System.out.println("forkURL " + forkURL + " pr " + pr_id);
                    System.out.println("count " + count);

                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt);
                        conn.commit();
                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void insertForkasPRauthor(String repoUrl, int projectID) {
        String[] pr_json, pr_num = {};
        IO_Process io = new IO_Process();
        HashMap<String, String> forkPRstring_map = new HashMap<>();
        Connection conn;
        LocalDateTime now = LocalDateTime.now();

        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);
            String pr_json_string = io.readResult(dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                pr_num = io.readResult(dir + repoUrl + "/pr_num.txt").split(", ");


                String query = "  INSERT INTO repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo,upstreamID,projectID)" +
                        " SELECT * FROM (SELECT ? AS repourl,? AS login,? AS reponame,? AS isf,? AS upurl,? AS belo,? AS id1, ? AS id2) AS tmp" +
                        " WHERE NOT EXISTS (" +
                        "SELECT repoURL FROM repository WHERE repoURL = ?" +
                        ") LIMIT 1";

                PreparedStatement preparedStmt = conn.prepareStatement(query);
                int count = 0;
                for (int s = pr_json.length - 1; s >= 0; s--) {

                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(io.removeBrackets(pr_num[s]).replace("{\"number\": ", "").replace("}", "").trim());

                    System.out.println("index " + s);
                    System.out.println("count " + count);
                    json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String author = pr_info.get("author").toString();
                    String forkURL = getForkURL(pr_info);
                    if (!forkURL.equals("")) {

                        preparedStmt.setString(1, forkURL);
                        preparedStmt.setString(2, author);
                        preparedStmt.setString(3, forkURL.split("/")[1]);
                        preparedStmt.setBoolean(4, true);
                        preparedStmt.setString(5, repoUrl);
                        preparedStmt.setString(6, repoUrl);
                        preparedStmt.setInt(7, projectID);
                        preparedStmt.setInt(8, projectID);
                        preparedStmt.setString(9, forkURL);
                        preparedStmt.addBatch();
                        System.out.println("forkURL " + forkURL);

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }

                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
                preparedStmt.close();
                conn.close();

            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


    public void combineGraphResult(String repoURL, HashMap<String, String> forkPR_map) {
        String current_dir = System.getProperty("user.dir");
        String resultPath = current_dir + "/result/" + repoURL + "/graph_result.txt";
        String graph_info_csv_Path = current_dir + "/result/" + repoURL + "/graph_info.csv";
        IO_Process io = new IO_Process();


        StringBuilder sb = new StringBuilder();
        sb.append("repo,fork,upstream,only_F,only_U,F2U,U2F,"
                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age,"
                + "allPR,mergedPR,rejectedPR,num_allPRcommit, num_mergedPRcommits, num_rejectedPRcommits,allPRcommit,mergedPRcommits,rejectedPRcommits,"
                + "final_OnlyF,final_F2U,num_mergedCommitsNotThroughPR,OnlyF_list,F2U_list,mergedCommitsNotThroughPR_list"
                + "\n");

        String[] graphResult = {};
        String[] graph_info_csv = {};
        try {
            graphResult = io.readResult(resultPath).split("\n");
            graph_info_csv = io.readResult(graph_info_csv_Path).split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        HashMap<String, String> graph_info_map = new HashMap<>();
        for (String fork : graph_info_csv) {
            String info_fork_name = fork.split(",")[1].split("/")[0];
            graph_info_map.put(info_fork_name, fork);
        }


        for (int i = 1; i < graphResult.length; i++) {
            String forkResult = graphResult[i];
            String result[] = forkResult.split(",");
            String forkName = result[0].split("/")[0];

            System.out.println(forkName);
            List<String> OnlyF_list = Arrays.asList(io.removeBrackets(result[6]).split("/ "));
            List<String> F2U_list = Arrays.asList(io.removeBrackets(result[8]).split("/ "));
            HashSet<String> OnlyF = new HashSet<>();
            for (String c : OnlyF_list) {
                if (!c.trim().equals("")) {
                    OnlyF.add(c);
                }
            }

            HashSet<String> F2U = new HashSet<>();
            for (String c : F2U_list) {
                if (!c.trim().equals("")) {
                    F2U.add(c);
                }
            }

            String forkPRinfo;
            if (graph_info_map.get(forkName) != null) {
                if (forkPR_map.get(forkName) == null) {

                    /** forkname,allPR,mergedPR,rejectedPR,#allPRcommit, #mergedPRcommits, #rejectedPRcommits, allPRcommit,  mergedPRcommits,  rejectedPRcommits **/
                    sb.append(graph_info_map.get(forkName) + ",0,0,0,0,0,0,[],[],[]," + OnlyF.size() + "," + F2U.size() + ",0," +
                            OnlyF.toString().replace(",", "/") + "," + F2U.toString().replace(",", "/") + ",[]\n");

                } else {

                    forkPRinfo = forkPR_map.get(forkName);
                    String[] prResult = forkPRinfo.split(",");
                    List<String> mergedPRCommits = Arrays.asList(io.removeBrackets(prResult[8].trim()).split("/ "));
                    List<String> rejectedPRCommits_list = Arrays.asList(io.removeBrackets(prResult[9].trim()).split("/ "));
                    HashSet<String> rejectedPRCommits = new HashSet<>();
                    rejectedPRCommits.addAll(rejectedPRCommits_list);

                    HashSet<String> mergeThroughOtherMechanisum = new HashSet();

                    for (String c : F2U) {
                        if (!c.trim().equals("")) {
                            if (!mergedPRCommits.contains(c)) {
                                mergeThroughOtherMechanisum.add(c);
                            }
                            if (rejectedPRCommits.contains(c)) {
                                mergeThroughOtherMechanisum.add(c);
                            }
                        }
                    }

                    F2U.remove("");
                    F2U.addAll(mergedPRCommits);
                    rejectedPRCommits.removeAll(F2U);
                    OnlyF.removeAll(F2U);

                    sb.append(graph_info_map.get(forkName) + "," + forkPRinfo.replace("\n", "").replaceAll(forkName + ",", "") + ","
                            + OnlyF.size() + "," + F2U.size() + "," + mergeThroughOtherMechanisum.size() + ","
                            + OnlyF.toString().replace(",", "/") + "," + F2U.toString().replace(",", "/") + "," + mergeThroughOtherMechanisum.toString().replace(",", "/") + "\n");

                }
            }

        }
        io.rewriteFile(sb.toString(), current_dir + "/result/" + repoURL + "/pr_graph_info.csv");


    }


    public static void main(String[] args) {
        current_dir = System.getProperty("user.dir");
        user = "shuruiz";
        if (current_OS.indexOf("mac") >= 0) {
            dir = "/Users/shuruiz/Box Sync/queryGithub/";
            myUrl = "jdbc:mysql://localhost:3307/fork";

        } else {
            dir = "/usr0/home/shuruiz/queryGithub/";
            myUrl = "jdbc:mysql://localhost:3306/fork";

        }


        AnalyzingPRs analyzingPRs = new AnalyzingPRs();
        try {
            String myDriver = "com.mysql.jdbc.Driver";
            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");

            PreparedStatement preparedStmt;

            // create a mysql database connection
            Class.forName(myDriver);
            IO_Process io = new IO_Process();

            /*** insert repoList to repository table ***/
            String[] repos = io.readResult(current_dir + "/input/repoList.txt").split("\n");
//
//            for (int i = 1; i <= repos.length; i++) {
//                String repo = repos[i-1];
//                String loginID = repo.split("/")[0];
//                String repoName = repo.split("/")[1];
//                String query = " insert into repository ( repoURL,loginID,repoName) "+
//                        " values (?,?,?)";
//                 preparedStmt = conn.prepareStatement(query);
//                preparedStmt.setString(1, repo);
//                preparedStmt.setString(2, loginID);
//                preparedStmt.setString(3, repoName);
//                preparedStmt.execute();
//            }

            /*** insert pr info to  repo_PR table***/
            String selectSQL = " SELECT repoURL FROM fork.repository";
            preparedStmt = conn.prepareStatement(selectSQL);

            //Execute select SQL stetement
            ResultSet rs;

            for (String repoURL : repos) {
                System.out.println(repoURL);
                int repoID = io.getRepoId(repoURL);
                analyzingPRs.insertPR(repoURL, repoID);
//                analyzingPRs.insertForkasPRauthor(repoURL, repoID);
//                analyzingPRs.insertCommitInPR(repoURL, repoID);
//                analyzingPRs.insertPR_Commit_mapping(repoURL, repoID);


            }

            preparedStmt.close();
            conn.close();

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public String getForkURL(JSONObject pr_info) {
        String forkURL = pr_info.get("forkURL").toString();
        if (forkURL.contains(":")) {
            String[] arr = forkURL.split("/");
            String[] loginID = arr[0].split(":");
            forkURL = loginID[0] + "/" + arr[1];
        }
        return forkURL;
    }
}
