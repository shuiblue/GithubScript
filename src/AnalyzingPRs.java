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

    String dir = "/Users/shuruiz/Box Sync/queryGithub/";
    String myDriver = "com.mysql.jdbc.Driver";
    String myUrl = "jdbc:mysql://localhost:3306/Fork";
    PreparedStatement preparedStmt;

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

    public HashMap<String, String> getPRMap(String repoUrl, int repoID) {
        String[] pr_json, pr_num = {};
        IO_Process io = new IO_Process();
        HashMap<String, Fork> forkPR_map = new HashMap<>();
        HashMap<String, String> forkPRstring_map = new HashMap<>();
        HashMap<String, Integer> allPr_map = new HashMap<>();
        HashMap<String, Integer> mergedPr_map = new HashMap<>();
        HashMap<String, Integer> rejectedPr_map = new HashMap<>();
        HashSet<Fork> forkSet = new HashSet<>();
        Connection conn = null;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        try {
            conn = DriverManager.getConnection(myUrl, "root", "shuruiz");
            String pr_json_string = io.readResult(dir + repoUrl + "/pr.txt");
            if (pr_json_string.contains("----")) {
                pr_json = pr_json_string.split("\n");
                 pr_num = io.readResult(dir + repoUrl + "/pr_num.txt").split(", ");


                for (int s = 0; s < pr_json.length; s++) {
                    String json_string = pr_json[s];
                    int pr_id = Integer.parseInt(io.removeBrackets(pr_num[s]).replace("{\"number\": ", "").replace("}", "").trim());

                    System.out.println(s);
//                    JSONObject pr_info = new JSONObject(json_string.split("----")[1].substring(1, json_string.lastIndexOf("]")));
                   json_string = json_string.split("----")[1];
                    JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

                    String forkName = (String) pr_info.get("forkName");


                    String author = pr_info.get("author").toString();
                    String created_at = pr_info.get("created_at").toString();
                    String closed_at = pr_info.get("closed_at").toString();
                    String closed = pr_info.get("closed").toString();
                    String merged = pr_info.get("merged").toString();
                    String forkURL = pr_info.get("forkURL").toString();
//                    String forkURL = "";
                    if (!forkURL.equals("")) {
                        HashSet<String> commitSet = new HashSet<>();
                        String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
                        for (String commit : commitArray) {
                            if (!commit.equals("")) {
                                commitSet.add(commit);
                            }
                        }
                        String insert_query_1 = " insert into Fork.repo_PR( repoID,pull_request_ID, forkName, authorName,forkURL, created_at, closed, closed_at, merged, data_update_at) " +
//                            " values (?,?,?,?,?,?,?,?,?,?)";
                                " SELECT * FROM (SELECT ? as a,? as b,? as c,? as d,? as e,? as f, ?as ds,?as de,?as d3,?as d2) AS tmp" +
                                " WHERE NOT EXISTS (" +
                                " SELECT repoID FROM repo_PR WHERE repoID = ? AND pull_request_ID = ?" +
                                ") LIMIT 1";

                        preparedStmt = conn.prepareStatement(insert_query_1);
                        preparedStmt.setInt(1, repoID);
                        preparedStmt.setInt(2, pr_id);
                        preparedStmt.setString(3, forkName);
                        preparedStmt.setString(4, author);
                        preparedStmt.setString(5, forkURL);
                        preparedStmt.setString(6, created_at);
                        preparedStmt.setString(7, closed);
                        preparedStmt.setString(8, closed_at);
                        preparedStmt.setString(9, merged);
                        preparedStmt.setString(10, String.valueOf(now));
                        preparedStmt.setInt(11, repoID);
                        preparedStmt.setInt(12, pr_id);
                        preparedStmt.execute();


                        String query = "  INSERT into repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo)" +
                                " SELECT * FROM (SELECT ? as repourl,? as login,? as reponame,? as isf,? as upurl,? as belo) AS tmp" +
                                " WHERE NOT EXISTS (" +
                                "SELECT repoURL FROM repository WHERE repoURL = ?" +
                                ") LIMIT 1";

                        preparedStmt = conn.prepareStatement(query);
                        preparedStmt.setString(1, forkURL);
                        preparedStmt.setString(2, author);
                        preparedStmt.setString(3, forkURL.split("/")[1]);
                        preparedStmt.setBoolean(4, true);
                        preparedStmt.setString(5, repoUrl);
                        preparedStmt.setString(6, repoUrl);
                        preparedStmt.setString(7, forkURL);
                        preparedStmt.execute();


                        /** put commit into commit_TABLE**/
                        for (String commit : commitSet) {

                            String getSHAID = "SELECT id FROM commit WHERE commitSHA =?";
                            preparedStmt = conn.prepareStatement(getSHAID);
                            preparedStmt.setString(1, commit);

                            int sha_id = -1;
                            ResultSet rs = preparedStmt.executeQuery();        // Get the result table from the query  3

                            if (rs.getRow() == 0) {
                                try {
                                    String update_commit_query = " INSERT INTO Fork.commit (commitSHA,  repoURL, upstreamURL, data_update_at)" +
                                            "  SELECT *" +
                                            "  FROM (SELECT" +
                                            "          ? AS a,? AS b, ? AS c, ? as d ) AS tmp" +
                                            "  WHERE NOT EXISTS(" +
                                            "      SELECT commitSHA" +
                                            "      FROM Fork.commit AS cc" +
                                            "      WHERE cc.commitSHA = ?" +
                                            "  )" +
                                            "  LIMIT 1";

                                    preparedStmt = conn.prepareStatement(update_commit_query);
                                    preparedStmt.setString(1, commit);
                                    preparedStmt.setString(2, forkURL);
                                    preparedStmt.setString(3, repoUrl);
                                    preparedStmt.setString(4, String.valueOf(now));
                                    preparedStmt.setString(5, commit);
                                    preparedStmt.execute();
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }

                            preparedStmt = conn.prepareStatement(getSHAID);
                            preparedStmt.setString(1, commit);
                            rs = preparedStmt.executeQuery();

                            while (rs.next()) {               // Position the cursor                  4
                                sha_id = rs.getInt(1);        // Retrieve the first column valu
                            }


                            String getForkID = "SELECT id FROM repository WHERE repoURL = ?";

                            int fork_id = -1;
                            preparedStmt = conn.prepareStatement(getForkID);
                            preparedStmt.setString(1, forkURL);
                            rs = preparedStmt.executeQuery();        // Get the result table from the query  3
                            // Get the result table from the query  3
                            while (rs.next()) {               // Position the cursor                  4
                                fork_id = rs.getInt(1);        // Retrieve the first column valu
                            }


//                            String insert_commit_query = "  INSERT into pr_commit (commitsha_id,repoID,pull_request_id)" +
//                                    " VALUES (?,?,?)";

                            String insert_commit_query = " INSERT INTO Fork.pr_commit (commitsha_id,repoID,pull_request_id)" +
                                    "  SELECT *" +
                                    "  FROM (SELECT" +
                                    "          ? AS a,? AS b, ? AS c ) AS tmp" +
                                    "  WHERE NOT EXISTS(" +
                                    "      SELECT *" +
                                    "      FROM Fork.pr_commit AS cc" +
                                    "      WHERE cc.commitsha_id = ?" +
                                    "      and cc.repoID = ?" +
                                    "      and cc.pull_request_id = ?" +
                                    "  )" +
                                    "  LIMIT 1";


                            preparedStmt = conn.prepareStatement(insert_commit_query);
                            preparedStmt.setInt(1, sha_id);
                            preparedStmt.setInt(2, fork_id);
                            preparedStmt.setInt(3, pr_id);
                            preparedStmt.setInt(4, sha_id);
                            preparedStmt.setInt(5, fork_id);
                            preparedStmt.setInt(6, pr_id);
                            preparedStmt.execute();

                        }

                    }

//            PullRequest pr = new PullRequest("", forkName, author, "", created_at, closed_at, closed, merged, new HashSet<>());
//
//
//
//            Fork fork;
//            if (forkPR_map.get(forkName) == null) {
//                fork = new Fork(forkName);
//            } else {
//                fork = forkPR_map.get(forkName);
//            }
//
//            HashSet<String> mergedCommit = new HashSet<>();
//            HashSet<String> rejectedCommit = new HashSet<>();
//            HashSet<String> pendingCommit = new HashSet<>();
//            if (merged.equals("true")) {
//
//                int mergePr = 0;
//                if (mergedPr_map.get(forkName) != null) {
//                    mergePr = mergedPr_map.get(forkName);
//                    mergedCommit = fork.getMergedCommits();
//                }
//                mergedCommit.addAll(commitSet);
//                fork.setMergedCommits(mergedCommit);
//                mergedPr_map.put(forkName, mergePr + 1);
//
//            } else if (merged.equals("false") && closed.equals("true")) {
//
//                int rejectPr = 0;
//                if (rejectedPr_map.get(forkName) != null) {
//                    rejectPr = rejectedPr_map.get(forkName);
//                    rejectedCommit = fork.getRejectedCommits();
//                }
//                rejectedCommit.addAll(commitSet);
//                fork.setRejectedCommits(rejectedCommit);
//                rejectedPr_map.put(forkName, rejectPr + 1);
//            } else {
//                pendingCommit.addAll(commitSet);
//                fork.setPendingCommits(pendingCommit);
//            }
//
//
//            pr.setCommitSet(commitSet);
//            forkPR_map.put(forkName, fork);
//            fork.getAll_PR_commits().addAll(commitSet);
//            int numberOfPr = 0;
//            if (allPr_map.get(forkName) != null) {
//                numberOfPr = allPr_map.get(forkName);
//            } else {
//                forkSet.add(fork);
//            }
//            allPr_map.put(forkName, numberOfPr + 1);
                }
//        StringBuilder sb = new StringBuilder();
//        /** forkname,allPR,mergedPR,rejectedPR,#allPRcommit,#mergedPRcommits,#rejectedPRcommits ,allPRcommit,mergedPRcommits,rejectedPRcommits **/
//        for (Fork f : forkSet) {
//            String forkName = f.getForkName();
//            int mergedPR = mergedPr_map.get(forkName) == null ? 0 : mergedPr_map.get(forkName);
//            int rejectedPR = rejectedPr_map.get(forkName) == null ? 0 : rejectedPr_map.get(forkName);
//            HashSet<String> mergedCommits = f.getMergedCommits();
//            HashSet<String> rejectedCommits = f.getRejectedCommits();
//            rejectedCommits.removeAll(mergedCommits);
//
//            sb.append(forkName + "," + allPr_map.get(forkName) + "," + mergedPR + "," + rejectedPR + ","
//                    + f.getAll_PR_commits().size() + ","
//                    + mergedCommits.size() + ","
//                    + rejectedCommits.size() + ","
//                    + f.getAll_PR_commits().toString().replace(",", "/") + ","
//                    + mergedCommits.toString().replace(",", "/") + ","
//                    + rejectedCommits.toString().replace(",", "/") + "\n");
//
//            forkPRstring_map.put(forkName, forkName + "," + allPr_map.get(forkName) + "," + mergedPR + "," + rejectedPR + ","
//                    + f.getAll_PR_commits().size() + ","
//                    + mergedCommits.size() + ","
//                    + rejectedCommits.size() + ","
//                    + f.getAll_PR_commits().toString().replace(",", "/") + ","
//                    + mergedCommits.toString().replace(",", "/") + ","
//                    + rejectedCommits.toString().replace(",", "/") + "\n");
//        }
//
//        io.rewriteFile(sb.toString(), dir + repoUrl + "/forkPR_summary.csv");

                preparedStmt.close();
                conn.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return forkPRstring_map;
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


//    public static void main(String[] args) {
//        AnalyzingPRs ap = new AnalyzingPRs();
//        String repoURL = "django/django";
//        HashMap<String, String> forkPR_map = ap.getPRMap(repoURL);
//        ap.combineGraphResult(repoURL, forkPR_map);
//    }


    public static void main(String[] args) {
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        try {
            String myDriver = "com.mysql.jdbc.Driver";
            String myUrl = "jdbc:mysql://localhost:3306/Fork";

            Connection conn = DriverManager.getConnection(myUrl, "root", "shuruiz");
            PreparedStatement preparedStmt;

            // create a mysql database connection
            Class.forName(myDriver);
            IO_Process io = new IO_Process();

            /*** insert repoList to repository table ***/
            String[] repos = io.readResult("/Users/shuruiz/Box Sync/SPL_Research/ForkBasedDev/statistics/try/repoList.txt").split("\n");
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
            String selectSQL = " SELECT repoURL FROM Fork.repository";
            preparedStmt = conn.prepareStatement(selectSQL);

            //Execute select SQL stetement
            ResultSet rs = preparedStmt.executeQuery();

            int total = 1;
//            while (rs.next() & total <= 2) {
            for (String repoURL : repos) {
                String getrepoID = "SELECT id FROM repository WHERE repoURL = ?";
                System.out.println(repoURL);
                int repoID = -1;
                preparedStmt = conn.prepareStatement(getrepoID);
                preparedStmt.setString(1, repoURL);
                rs = preparedStmt.executeQuery();        // Get the result table from the query  3
                // Get the result table from the query  3
                while (rs.next()) {               // Position the cursor                  4
                    repoID = rs.getInt(1);        // Retrieve the first column valu
                }

//                total++;
//                String repoURL = rs.getString("repoURL");
//                int repoID = rs.getRow();
//                ArrayList<PullRequest> prList = analyzingPRs.createPR(repoURL);
                analyzingPRs.getPRMap(repoURL, repoID);

//                for (int i = 0; i < prList.size(); i++) {
//                    PullRequest pr = prList.get(i);

//                    if (!pr.forkURL.equals("")){
//                        System.out.println("i: " + i +"  "+ pr.forkURL);

//                        String insert_query_1 = " insert into Fork.repo_PR( repoID,pull_request_ID, forkName, authorName,forkURL, created_at, closed, closed_at, commit_List, merged, data_update_at) " +
//                                " values (?,?,?,?,?,?,?,?,?,?,?)";
//
//                        preparedStmt = conn.prepareStatement(insert_query_1);
//                        preparedStmt.setInt(1, repoID);
//                        preparedStmt.setString(2, pr.pr_id);
//                        preparedStmt.setString(3, pr.forkName);
//                        preparedStmt.setString(4, pr.author);
//                        preparedStmt.setString(5, pr.forkURL);
//                        preparedStmt.setString(6, pr.created_at);
//                        preparedStmt.setString(7, pr.closed);
//                        preparedStmt.setString(8, pr.closed_at);
//                        preparedStmt.setString(9, pr.commitSet.toString());
//                        preparedStmt.setString(10, pr.merged);
//                        preparedStmt.setString(11, String.valueOf(now));
//                        preparedStmt.execute();
//
//
//                        String query = "  INSERT into repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo)" +
//                                " SELECT * FROM (SELECT ? as repourl,? as login,? as reponame,? as isf,? as upurl,? as belo) AS tmp" +
//                                " WHERE NOT EXISTS (" +
//                                "SELECT repoURL FROM repository WHERE repoURL = ?" +
//                                ") LIMIT 1";
//
//                        preparedStmt = conn.prepareStatement(query);
//                        preparedStmt.setString(1, pr.forkURL);
//                        preparedStmt.setString(2, pr.author);
//                        preparedStmt.setString(3, pr.forkURL.split("/")[1]);
//                        preparedStmt.setBoolean(4, true);
//                        preparedStmt.setString(5, repoURL);
//                        preparedStmt.setString(6, repoURL);
//                        preparedStmt.setString(7, pr.forkURL);
//                        preparedStmt.execute();


//                        for (String commit : pr.commitSet) {
//                            String insert_query_2 = " insert into Fork.repo_commit (repoID, commitSHA, data_update_at) " +
//                                    " values (?,?,?)";
//                            preparedStmt = conn.prepareStatement(insert_query_2);
//                            preparedStmt.setInt(1, repoID);
//                            preparedStmt.setString(2, commit);
//                            preparedStmt.setString(3, String.valueOf(now));
//                            preparedStmt.execute();
//                        }

//                    }
//                }

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


}
