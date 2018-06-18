import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/27/18.
 */
public class InsertCommit_graphBasedResult {
    static String working_dir, pr_dir, output_dir, clone_dir, historyDirPath, resultDirPath;
    static String myUrl, user, pwd;
    final int batchSize = 100;
    String[] commitType = {"onlyF", "F2U"};

    public InsertCommit_graphBasedResult() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            historyDirPath = output_dir + "commitHistory/";
            resultDirPath = output_dir + "ClassifyCommit/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public void main(String[] args) {
        InsertCommit_graphBasedResult acc = new InsertCommit_graphBasedResult();
        IO_Process io = new IO_Process();
        String[] repoList = {};
        io.rewriteFile("", resultDirPath + "File_history.csv");

        /** get repo list **/
        String current_dir = System.getProperty("user.dir");
        try {
            repoList = io.readResult(current_dir + "/input/graph_result_repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        JgitUtility jg = new JgitUtility();
        for (String repoUrl : repoList) {

            String[] forkListInfo = new String[0];
            try {
                String forkListInfoString = io.readResult(resultDirPath + repoUrl.replace("/", ".") + "_graph_result_allFork.csv");
                if (forkListInfoString.contains("0,0,0,0,[],[],[],[]")) {
                    io.writeTofile(repoUrl + "\n", output_dir + "graph_redo.txt");
                    continue;
                }
                forkListInfo = forkListInfoString.split("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }

            HashMap<String, HashMap<String, List<String>>> forkOwnCode = getForkOwnCode(forkListInfo);

            HashSet<String> project_forks = new HashSet<>();
            try {
                project_forks.addAll(Arrays.asList(io.readResult(output_dir + "result/" + repoUrl + "/ActiveForklist.txt").split("\n")));
            } catch (IOException e) {
                e.printStackTrace();
            }
            jg.cloneRepo_cmd(project_forks, repoUrl, true);

            for (int i = 1; i < forkListInfo.length; i++) {
                String forkINFO = forkListInfo[i];
                String forkurl = forkINFO.split(",")[0];

                System.out.println("insert commit...of repo " + forkurl);
                acc.insertCommitFromGraphResult(repoUrl, forkOwnCode, forkurl);
            }

            try {
                io.deleteDir(new File(clone_dir + repoUrl));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /***
     * This function calculates modularity for each project by generating support matrix
     * @param repoUrl
     * @throws IOException
     * @throws GitAPIException
     */


    //    private void insertCommitFromGraphResult(String repoUrl, HashMap<String, HashMap<String, List<String>>> forkOwnCode, String forkurl, ArrayList<String> branchList) {
    private void insertCommitFromGraphResult(String repoUrl, HashMap<String, HashMap<String, List<String>>> forkOwnCode, String forkurl) {
        IO_Process io = new IO_Process();
        HashMap<String, List<String>> onlyF_map = forkOwnCode.get("onlyF");
        HashMap<String, List<String>> F2U_map = forkOwnCode.get("F2U");
        int onlyf_boolean, f2u_boolean;
        int repoID = io.getRepoId(forkurl);
        int projectID = io.getRepoId(repoUrl);

        String update_commit_query = " INSERT INTO fork.Commit (commitSHA, projectID, loginID, email, author_name, num_changedFiles, data_update_at, only_f, f2u,created_at,commit_repo_id) " +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS a,? AS b, ? AS c, ? AS d, ? AS e,? AS f, ? AS g ,? AS x, ? AS y,? AS createat,? AS commitrepoid) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT commitSHA" +
                "      FROM fork.Commit AS cc" +
                "      WHERE cc.commitSHA = ?" +
                "  )" +
                "  LIMIT 1";
        int count = 0;
        try (
                Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
                PreparedStatement preparedStmt_insert = conn.prepareStatement(update_commit_query);) {
            conn.setAutoCommit(false);

            /** Get contribution code : onlyF and F2U  **/
            HashSet<String> codeChangeCommits = new HashSet<>();
            LocalDateTime now = LocalDateTime.now();

            for (String type : commitType) {
                if (type.equals("onlyF")) {
                    codeChangeCommits.addAll(onlyF_map.get(forkurl));
                    onlyf_boolean = 1;
                    f2u_boolean = 0;
                } else {
                    codeChangeCommits.addAll(F2U_map.get(forkurl));
                    onlyf_boolean = 0;
                    f2u_boolean = 1;
                }


                for (String sha : codeChangeCommits) {
                    String[] commitInfo = io.getCommitInfoFromCMD(sha, repoUrl);
                    System.out.println(sha+" : size of commit info"+commitInfo.length);
                    String author_fullName = commitInfo[0];
                    String email = commitInfo[1];
                    String created_at = commitInfo[2];


                    ArrayList<String> changedfiles = io.getCommitFromCMD(sha, repoUrl);

                    if (changedfiles == null) {
                        io.writeTofile(sha + "," + repoUrl + "\n", output_dir + "lostCommit.txt");
                        continue;
                    } else if (changedfiles.get(0).trim().equals("")) {
                        io.writeTofile(sha + "," + repoUrl + "\n", output_dir + "noCommit.txt");
                        continue;
                    }

                    if (changedfiles.size() < 100) {
                        String commitid = io.getCommitID(sha);
                        if (commitid.equals("")) {
                            //(commitSHA,
                            preparedStmt_insert.setString(1, sha);

                            // projectID,
                            preparedStmt_insert.setInt(2, projectID);
                            // loginID,
                            preparedStmt_insert.setString(3, forkurl.split("/")[0]);
                            // email,
                            preparedStmt_insert.setString(4, email);
                            // author_name,
                            preparedStmt_insert.setString(5, io.normalize(author_fullName));

                            // num_changedFiles,
                            preparedStmt_insert.setInt(6, changedfiles.size());

                            // data_update_at,
                            preparedStmt_insert.setString(7, String.valueOf(now));
                            // only_f,
                            preparedStmt_insert.setInt(8, onlyf_boolean);

                            // f2u,
                            preparedStmt_insert.setInt(9, f2u_boolean);
                            //created at
                            preparedStmt_insert.setString(10, created_at);
                            // commit repo id

                            preparedStmt_insert.setInt(11, repoID);
                            //SHA
                            preparedStmt_insert.setString(12, sha);
                            preparedStmt_insert.addBatch();
                            System.out.print("add 1 commit ...count " + count + "\n");

                            if (++count % batchSize == 0) {
                                io.executeQuery(preparedStmt_insert);
                                conn.commit();
                            }
                        }
                    }
                }
                if (count > 0) {
                    System.out.println("count " + count);
                    io.executeQuery(preparedStmt_insert);
                    conn.commit();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static HashMap<String, HashMap<String, List<String>>> getForkOwnCode(String[] forkListInfo) {
        HashMap<String, HashMap<String, List<String>>> forkOwnCode = new HashMap<>();
        HashMap<String, List<String>> F2U_map = removeRedundantCommits(forkListInfo, "F2U");
        HashMap<String, List<String>> onlyF_map = removeRedundantCommits(forkListInfo, "onlyF");
        forkOwnCode.put("F2U", F2U_map);
        forkOwnCode.put("onlyF", onlyF_map);
        return forkOwnCode;
    }


    private void updateCommitFromGraphResult(String repoUrl, HashMap<String, HashMap<String, List<String>>> forkOwnCode, String forkurl, ArrayList<String> branchList) {
        IO_Process io = new IO_Process();
        HashMap<String, List<String>> onlyF_map = forkOwnCode.get("onlyF");
        HashMap<String, List<String>> F2U_map = forkOwnCode.get("F2U");

        long start;
        int onlyf_boolean, f2u_boolean;

        try {
            PreparedStatement preparedStmt_update = null;
            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            conn.setAutoCommit(false);

            Repository repo = new FileRepository(clone_dir + ".git");
            Git git = new Git(repo);

            /** Get contribution code : onlyF and F2U  **/
            HashSet<String> codeChangeCommits = new HashSet<>();

            LocalDateTime now = LocalDateTime.now();

            for (String type : commitType) {

                if (type.equals("onlyF")) {
                    codeChangeCommits.addAll(onlyF_map.get(forkurl));
                    onlyf_boolean = 1;
                    f2u_boolean = 0;
                } else {
                    codeChangeCommits.addAll(F2U_map.get(forkurl));
                    onlyf_boolean = 0;
                    f2u_boolean = 1;
                }

                System.out.println("analyzing " + type + " commits...");
                String insert_changedFile_query = " UPDATE commit" +
                        " SET num_changedFiles = ?, authorName = ?, email = ?, data_update_at = ?, only_f = ?, f2u = ? " +
                        " WHERE commitSHA =?";
                preparedStmt_update = conn.prepareStatement(insert_changedFile_query);

                start = System.nanoTime();
                for (String sha : codeChangeCommits) {
                    RevCommit commit = io.getCommit(sha, repo, git, branchList);
                    if (commit != null && commit.getParentCount() > 0) {
                        String author_fullName = io.normalize(commit.getAuthorIdent().getName());
                        String email = commit.getAuthorIdent().getEmailAddress();

                        List<DiffEntry> diffs = io.getCommitDiff(commit, repo);

                        if (diffs.size() < 100) {
                            String commitid = io.getCommitID(sha);
                            if (!commitid.equals("")) {
                                System.out.println("updating existing commit ... ");
                                preparedStmt_update.setInt(1, diffs.size());
                                preparedStmt_update.setString(2, author_fullName);
                                preparedStmt_update.setString(3, email);
                                preparedStmt_update.setString(4, String.valueOf(now));
                                preparedStmt_update.setInt(5, onlyf_boolean);
                                preparedStmt_update.setInt(6, f2u_boolean);
                                preparedStmt_update.setString(7, sha);
                                preparedStmt_update.addBatch();

                            }
                        }
                    }

                }
                long end = System.nanoTime();
                long used = end - start;
                System.out.println("update " + codeChangeCommits.size() + " commit from a fork :" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");

                io.executeQuery(preparedStmt_update);
                conn.commit();
            }

            preparedStmt_update.close();
            conn.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    private static HashMap<String, List<String>> removeRedundantCommits(String[] forkListInfo, String type) {
        HashSet<String> codeChangeCommits_all = new HashSet<>();
        HashSet<String> redundantCommit = new HashSet<>();
        HashMap<String, List<String>> result = new HashMap<>();

        IO_Process io = new IO_Process();
        for (int i = 1; i < forkListInfo.length; i++) {
            String forkINFO = forkListInfo[i];
            String forkurl = forkINFO.split(",")[0];
            String repoCloneDir = clone_dir + forkurl + "/";
            /** Get contribution code : onlyF and F2U  **/
            String[] columns = forkINFO.split(",");
            ArrayList<String> commits;
//            HashSet<String> commitsFromPr = new HashSet<>();
            if (type.equals("onlyF")) {

                commits = new ArrayList<>(Arrays.asList(io.removeBrackets(columns[6]).split("/ ")));
            } else {
                commits = new ArrayList<>(Arrays.asList(io.removeBrackets(columns[8]).split("/ ")));
//                System.out.println("Get .. commit belong to PR  into F2U... repo:" + forkurl);
//                commitsFromPr = getPRcommits(forkurl);
//                System.out.println("commit belong to PR" + commitsFromPr.size());

            }
            commits.removeAll(redundantCommit);
            commits.remove("");
//            System.out.println("commit from Graph" + commits.size());
//            if (type.equals("F2U")) {
//                commits.addAll(commitsFromPr);
//            }
//            System.out.println("ALL commits" + commits.size());

            for (String commit : commits) {
                if (codeChangeCommits_all.contains(commit)) {
                    redundantCommit.add(commit);
                }
            }

            codeChangeCommits_all.addAll(commits);
            result.put(forkurl, commits);
        }

        System.out.println(redundantCommit.size() + " redundanct commits");
        StringBuilder sb = new StringBuilder();
        sb.append(type + "\n");
        result.forEach((k, v) -> {
            v.removeAll(redundantCommit);
            sb.append(k + "," + v.size() + "\n");
        });
        io.writeTofile(sb.toString(), resultDirPath + "tim_graph_result.txt");
        return result;
    }


}
