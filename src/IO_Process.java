import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.json.JSONObject;

import java.io.*;
import java.sql.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shuruiz on 10/19/17.
 */
public class IO_Process {
    static String current_OS = System.getProperty("os.name").toLowerCase();
    String token;
    String user, pwd, myUrl;
    static String github_api_repo = "https://api.github.com/repos/";
    static String github_url = "https://github.com/";
    String current_dir = System.getProperty("user.dir");
    static String working_dir, pr_dir, output_dir, clone_dir;
    final int batchSize = 100;

    public IO_Process() {

        try {
            String[] paramList = readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            token = readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void writeTofile(String content, String filepath) {


        try {
            File file = new File(filepath);
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.getParentFile().mkdir();
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void rewriteFile(String content, String filepath) {

        try {
            File file = new File(filepath);
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsoluteFile(), false);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * this function read the content of the file from filePath, and ready for comparing
     *
     * @param filePath file path
     * @return content of the file
     * @throws IOException e
     */
    public String readResult(String filePath) throws IOException {
        BufferedReader result_br = new BufferedReader(new FileReader(filePath));
        String result;
        try {
            StringBuilder sb = new StringBuilder();
            String line = result_br.readLine();

            while (line != null) {

                sb.append(line);
                sb.append(System.lineSeparator());

                line = result_br.readLine();
            }
            result = sb.toString();
        } finally {
            result_br.close();
        }
        return result;
    }

    public List<List<String>> readCSV(String filePath) {
        List<List<String>> rows = new ArrayList<>();
        File csvFile = new File(filePath);
        try (InputStream in = new FileInputStream(csvFile);) {
            CSV csv = null;
            try {
                csv = new CSV(true, ',', in);
                List<String> colNames = null;
                if (csv.hasNext()) colNames = new ArrayList<String>(csv.next());
                while (csv.hasNext()) {
                    List<String> fields = new ArrayList<String>(csv.next());
                    rows.add(fields);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rows;
    }


    public void deleteDir(File file) throws IOException {
        FileUtils.deleteDirectory(file);
    }


    public List<DiffEntry> getCommitDiff(RevCommit commit, Repository repo) {
//        long start_getdiff = System.nanoTime();

        RevCommit parent;
        DiffFormatter df;
        RevWalk rw = new RevWalk(repo);
        try {
            if (commit.getParentCount() > 0) {
                parent = rw.parseCommit(commit.getParent(0).getId());
                df = getDiffFormat(repo);
//            long end_getdiff = System.nanoTime();
//            System.out.println("get diff:" + TimeUnit.NANOSECONDS.toMillis(end_getdiff - start_getdiff) + " ms");

                return df.scan(parent.getTree(), commit.getTree());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DiffFormatter getDiffFormat(Repository repo) {
        DiffFormatter df;
        df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repo);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);
        return df;
    }


    public RevCommit getCommit(String idString, Repository repository, Git git, ArrayList<String> branchList) {
        long start_getcommitFrombranch = System.nanoTime();
        for (String branchName : branchList) {
            try {

                if (!git.branchList().getRepository().getAllRefs().keySet().contains("refs/heads/" + branchName)) {
                    git.checkout().
                            setCreateBranch(true).
                            setName(branchName).
                            setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).
                            setStartPoint("origin/" + branchName).
                            call();
                }
                ObjectId o1 = repository.resolve(idString);
                if (o1 != null) {
                    RevWalk walk = new RevWalk(repository);
                    RevCommit commit = walk.parseCommit(o1);

                    long end_getcommitFrombranch = System.nanoTime();
                    System.out.println("get commit From branch: " + TimeUnit.NANOSECONDS.toMillis(end_getcommitFrombranch - start_getcommitFrombranch) + " ms");
                    return commit;
                } else {
                    System.err.println("Could not get commit with SHA :" + idString);
                }
            } catch (Exception e) {

                continue;
            }
        }
        return null;
    }

    public ArrayList<String> getPRNumlist(String projectUrl) {

        IO_Process io = new IO_Process();
        String prNumList_filePath = output_dir + "AnalyzePR/" + projectUrl.replace("/", ".") + ".prNum.txt";
        List<String> prList = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
        if (!new File(csvFile_dir).exists()) {
            io.writeTofile(projectUrl + "\n", output_dir + "pr_api_miss.txt");
            System.out.println(projectUrl + " pr api not available yet. ");
            return new ArrayList<>();
        } else {
            System.out.println("get pr list ...");
            try {
                if (!new File(prNumList_filePath).exists() || io.readResult(prNumList_filePath).trim().equals("")) {
                    System.out.println("generating pr num list");
                    List<List<String>> prs = io.readCSV(csvFile_dir);
                    for (List<String> pr : prs) {
                        if (pr.size() == 14) {
                            if (!pr.get(0).equals("")) {
                                int pr_id = Integer.parseInt(pr.get(9));

                                sb.append(pr_id + "\n");
                                System.out.println(pr_id);
                            }
                        } else {
                            System.out.println("csv parsing wrong /..");
                            io.writeTofile(projectUrl + " , " + pr.get(0) + "\n", output_dir + "wrongPRcsv.txt");
                        }

                    }
                    io.rewriteFile(sb.toString(), output_dir + "AnalyzePR/" + projectUrl.replace("/", ".") + ".prNum.txt");

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String prListString = "";
        try {
            prListString = io.readResult(prNumList_filePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!prListString.trim().equals("")) {
            prList = Arrays.asList(prListString.split("\n"));

        }
        return new ArrayList<String>(prList);
    }


    /**
     * process text
     **/
    public String normalize(String str) {
        str = Normalizer.normalize(str, Normalizer.Form.NFD);
        str = str.replaceAll("[^\\p{ASCII}]", "");
        return str;
    }

    public String removeBrackets(String str) {
        return str.replace("\n", "").replace("[", "").replace("]", "");
    }

    public HashSet<HashSet<String>> getAllPairs_string(HashSet<String> set) {
        HashSet<HashSet<String>> allPairs_string = new HashSet<>();
        List<String> list = new ArrayList<>(set);
        String first = list.remove(0);
        for (String node : list) {
            HashSet<String> currentPair = new HashSet<>();
            currentPair.add(first);
            currentPair.add(node);
            allPairs_string.add(currentPair);
        }
        if (set.size() > 2) {
            set.remove(first);
            allPairs_string.addAll(getAllPairs_string(set));
        } else {
            allPairs_string.add(set);
        }

        return allPairs_string;
    }


    /***  Below function are interacting with command line***/
    public String exeCmd(String[] cmd, String pathname) {

        ProcessBuilder process = new ProcessBuilder(cmd);
        process.redirectErrorStream(true);
        String result = "";
        try {
            process.directory(new File(pathname).getAbsoluteFile());
            process.redirectErrorStream(true);
            Process p = process.start();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
//                    System.out.println(line);
                    builder.append(System.getProperty("line.separator"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            result = builder.toString();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result.replace("\"", "");
    }

    public ArrayList<String> getCodeChangeInfo_ofCommit_FromCMD(String sha, String repoUrl) {
        String[] cmd_getline = {"/bin/sh",
                "-c",
                "git log -1 --numstat " + sha + " | egrep ^[[:digit:]]+ | egrep -v ^[\\d]+[[:space:]]+[\\d]+[[:space:]]+"};

        String[] changedfiles = exeCmd(cmd_getline, clone_dir + repoUrl).split("\n");

        ArrayList<String> status = getChangedFileStatus_ofCommit_FromCMD(sha, repoUrl);
        if (status.get(0).contains("fatal: bad object")) {
            return null;
        }
        ArrayList<String> changedFileResult = new ArrayList<>();
        for (int i = 0; i < changedfiles.length; i++) {
            String file = changedfiles[i];
            file += "\t" + status.get(i).split("\t")[0];
            changedFileResult.add(file);
        }

        return changedFileResult;
    }


    public ArrayList<String> getChangedFileStatus_ofCommit_FromCMD(String sha, String projectURL) {
        //git show --pretty="" --name-only b5677907d064d2ebe6c99f3dece1de5390c029cc

        String[] cmd_getline = {"/bin/sh",
                "-c",
                "git show --pretty=\"\" --name-status  " + sha};

        String[] changedfiles = exeCmd(cmd_getline, clone_dir + projectURL).split("\n");

        ArrayList<String> changedFileResult = new ArrayList<>();
        for (int i = 0; i < changedfiles.length; i++) {
            String file = changedfiles[i];
            changedFileResult.add(file);
        }

        return changedFileResult;
    }

    public List<String> getCommitInBranch(String br, String after_Date, String project_cloneDir) {
        //todo git log -1 9b63430f349f7083d09d2db24d24908e1d277379 --pretty="%H" get author date
        String cmd_getCommit = "git log " + br + " --after=\"" + after_Date + "\" --pretty=\"%H\"";
        String commit_list = exeCmd(cmd_getCommit.split(" "), project_cloneDir);
        return Arrays.asList(commit_list.split("\n"));
    }


    /**
     * below function are querying data from database
     **/

    public synchronized void executeQuery(PreparedStatement preparedStmt) {
        int[] numUpdates = new int[0];
        try {
            numUpdates = preparedStmt.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        for (int i = 0; i < numUpdates.length; i++) {
            if (numUpdates[i] == -2)
                System.out.println("Execution " + i +
                        ": unknown number of rows updated");
        }

        System.out.println(numUpdates.length + " rows updated");
    }

    public String getRepoUrlByID(int projectID) {
        String commitshaID_QUERY = "SELECT repoURL FROM repository WHERE id = " + projectID;
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY);) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
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


    public String getForkURL(String originForkurl) {
        if (originForkurl.contains(":")) {
            String[] arr = originForkurl.split("/");
            String[] loginID = arr[0].split(":");
            return loginID[0] + "/" + arr[1];
        } else {
            return originForkurl;
        }
    }


    public String getCommitID(String sha) {
        String commitshaID_QUERY = "SELECT id FROM Commit WHERE commitSHA = ?";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            preparedStmt.setString(1, sha);
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                String commitshaID = rs.getString(1);
                return commitshaID;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return "";
    }

    public int commitExists(String sha) {

        String commitshaID_QUERY = "SELECT 1 from Commit WHERE commitSHA = \'" + sha + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;

    }

    public int commitExitsINChangedFileTable(String commitshaID) {
        String commitshaID_QUERY = "SELECT 1 from commit_changedFiles WHERE commit_uuid = \'" + commitshaID + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int prExistIN_PR_issueMap(String pr_id, int projectID) {
        String commitshaID_QUERY = "SELECT 1 from PR_TO_ISSUE WHERE repoID = \'" + projectID + "\' and pull_request_id = \'" + pr_id + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int prExistIN_PR_commitMap(String pr_id, int projectID) {
        String commitshaID_QUERY = "SELECT 1 from PR_Commit_map WHERE projectID = \'" + projectID + "\' and pull_request_id = \'" + pr_id + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String getIssueCreateatDate(int issueid) {
        String createat = "";
        String selectRepoID = "SELECT created_at FROM fork.ISSUE WHERE issue_id =" + issueid;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID)) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                if (rs.next()) {
                    createat = rs.getString(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return createat;

    }

    private int issueid_Exist(int s, int projectID) {
        String commitshaID_QUERY = "SELECT 1 from ISSUE WHERE projectID = " + projectID + " and issue_id ="+s+" LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }


    public int issueExist(int projectID) {
        String commitshaID_QUERY = "SELECT 1 from ISSUE WHERE projectID = \'" + projectID + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int checkModularityExist(int projectID) {
        String commitshaID_QUERY = "SELECT 1 from fork.Modularity WHERE projectID = \'" + projectID + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }


    public int getRepoId(String repoURL) {
        int repoID = -1;
        String selectRepoID = "SELECT id FROM fork.repository WHERE repoURL = \"" + repoURL + "\"";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID)) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                if (rs.next()) {
                    repoID = rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return repoID;

    }

    public ArrayList<String> getProjectURL() {
        ArrayList<String> repoList = new ArrayList<>();

        String selectRepoID = "SELECT repoURL FROM fork.repository WHERE isFork = FALSE ";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID);) {

            try (ResultSet rs = preparedStmt.executeQuery()) {
                while (rs.next()) {
                    repoList.add(rs.getString("repoURL"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return repoList;

    }


    ArrayList<String> getForkListFromRepoTable(String repoUrl) {
        ArrayList<String> forkList = new ArrayList<>();

        try {

            Connection conn = DriverManager.getConnection(myUrl, user, pwd);
            PreparedStatement preparedStmt;

            String selectRepoID = "SELECT repoURL FROM fork.repository WHERE belongToRepo = ? AND isFork = TRUE ";
            preparedStmt = conn.prepareStatement(selectRepoID);
            preparedStmt.setString(1, repoUrl);
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {
                forkList.add(String.valueOf(rs.getString("repoURL")));
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return forkList;

    }


    ArrayList<Integer> getPRList_hasNo_numOfCommits(int projectID) {
        ArrayList<Integer> PRList = new ArrayList<>();

        try {

            Connection conn = DriverManager.getConnection(myUrl, user, pwd);
            PreparedStatement preparedStmt;

            String selectRepoID = "SELECT pull_request_ID\n" +
                    "FROM fork.Pull_Request\n" +
                    "WHERE  projectID = " + projectID + "   AND num_commit IS NULL; ";
            preparedStmt = conn.prepareStatement(selectRepoID);
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {
                PRList.add((rs.getInt("pull_request_ID")));
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return PRList;

    }


    public HashMap<String, String> getCommitIdMap(HashSet<String> commitSet) {
        HashMap<String, String> sha_commitID_map = new HashMap<>();
        for (String commit : commitSet) {
            String commitID = getCommitID(commit);
            if (commitID.equals("")) {
                System.out.println("commit is not in the database !!!");
            } else {
                sha_commitID_map.put(commit, commitID);
            }
        }
        return sha_commitID_map;
    }

    public HashSet<String> getPRinDataBase(int projectID) {
        String query = "\n" +
                "SELECT pull_request_ID\n" +
                "FROM  Pull_Request\n" +
                "WHERE  projectID = " + projectID;

        HashSet<String> analyzedPR = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                analyzedPR.add(String.valueOf(rs.getInt(1)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return analyzedPR;


    }


    /**
     * These 3 function inserting new repo to database and fetch from github
     *
     * @param projectID
     * @param forkURL
     */


    public void insertNewRepo(int projectID, String forkURL) {
        System.out.println("id = -1!!! fork : " + forkURL + " is not in the database");
        String upstreamUrl = insertRepo_old(forkURL);
        if (upstreamUrl.equals("")) {
            upstreamUrl = getRepoUrlByID(projectID);
            cloneRepo(forkURL, upstreamUrl);
        }
    }

    public void cloneRepo(String forkUrl, String projectURL) {
        String projectName = projectURL.split("/")[0];
        IO_Process io = new IO_Process();
        File upstreamFile = new File(clone_dir + projectURL + "/.git");
        if (!upstreamFile.exists()) {
            String creadDirCMD = "mkdir -p " + clone_dir + projectURL;
            io.exeCmd(creadDirCMD.split(" "), clone_dir);
            System.out.println("mkdir -p " + projectURL);
            String cloneCMD = "git clone " + github_url + projectURL + ".git";
            io.exeCmd(cloneCMD.split(" "), clone_dir + projectName + "/");
        } else {
            System.out.println("upstream exists!");
        }

        forkUrl = io.getForkURL(forkUrl);
        System.out.println(forkUrl);
        String forkName = io.getForkURL(forkUrl.split("/")[0]);
        String cloneForkCmd = "git remote add " + forkName + " " + github_url + forkUrl + ".git";
        io.exeCmd(cloneForkCmd.split(" "), clone_dir + projectURL + "/");

        String fetchAll = "git fetch " + forkName;
        if (io.exeCmd(fetchAll.split(" "), clone_dir + projectURL + "/").contains("fatal: could not read Username")) {
            System.out.println(forkUrl + "is deleted on GitHub.");
        }
    }

    public String insertRepo_old(String repoUrl) {
        GithubApiParser githubApiParser = new GithubApiParser();
        System.out.println("get fork info: " + repoUrl);
        String query = "  INSERT INTO repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo,upstreamID,projectID," +
                "num_of_forks, created_at,pushed_at, size,language,ownerID,public_repos," +
                "public_gists ,followers  , following  ,user_type  )" +
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String upstreamURL = "";
        int upstreamID = -1;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);) {

            String forkUrl = github_api_repo + repoUrl + "?access_token=" + token;
            JsonUtility jsonUtility = new JsonUtility();
            ArrayList<String> fork_info_json = null;
            try {
                fork_info_json = jsonUtility.readUrl(forkUrl);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (fork_info_json.size() > 0) {
                JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));
                String owner_url = (String) ((JSONObject) fork_jsonObj.get("owner")).get("url");
                boolean isFork = (boolean) fork_jsonObj.get("fork");
                if (isFork) {
                    upstreamURL = (String) ((JSONObject) fork_jsonObj.get("parent")).get("full_name");
                    upstreamID = getRepoId(upstreamURL);
                }
                ArrayList<String> owner_info_json = null;
                try {
                    owner_info_json = jsonUtility.readUrl(owner_url + "?access_token=" + token);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                String language = null;
                if (fork_jsonObj.get("language") != null) {
                    language = String.valueOf(fork_jsonObj.get("language"));
                }
                JSONObject owner_jsonObj = new JSONObject(owner_info_json.get(0));
                // repoURL
                preparedStmt.setString(1, repoUrl);
                // loginID
                preparedStmt.setString(2, String.valueOf(owner_jsonObj.get("login")));
                // repoName
                preparedStmt.setString(3, repoUrl.split("/")[1]);
                // isFork
                preparedStmt.setBoolean(4, true);
                // UpstreamURL,
                preparedStmt.setString(5, upstreamURL);

                // belongToRepo,
                preparedStmt.setString(6, upstreamURL);
                // upstreamID,
                preparedStmt.setInt(7, upstreamID);
                // projectID
                preparedStmt.setInt(8, upstreamID);
                // num_of_forks
                preparedStmt.setInt(9, (Integer) fork_jsonObj.get("forks_count"));
                // created_at
                preparedStmt.setString(10, (String) owner_jsonObj.get("created_at"));
                // pushed_at
                preparedStmt.setString(11, (String) fork_jsonObj.get("pushed_at"));
                // size
                preparedStmt.setString(12, String.valueOf(fork_jsonObj.get("size")));
                // language
                preparedStmt.setString(13, language);
                // ownerID
                preparedStmt.setString(14, String.valueOf(owner_jsonObj.get("login")));
                // public_repos
                preparedStmt.setString(15, String.valueOf(owner_jsonObj.get("public_repos")));
                // public_gists
                preparedStmt.setString(16, String.valueOf(owner_jsonObj.get("public_gists")));
                // followers
                preparedStmt.setString(17, String.valueOf(owner_jsonObj.get("followers")));
                // following
                preparedStmt.setString(18, String.valueOf(owner_jsonObj.get("following")));
                // user_type
                preparedStmt.setString(19, String.valueOf(owner_jsonObj.get("type")));

            } else {
                // repoURL
                preparedStmt.setString(1, repoUrl);
                // loginID
                preparedStmt.setString(2, repoUrl.split("/")[1]);
                // repoName
                preparedStmt.setString(3, repoUrl.split("/")[1]);
                // isFork
                preparedStmt.setBoolean(4, true);
                // UpstreamURL,
                preparedStmt.setString(5, "");

                // belongToRepo,
                preparedStmt.setString(6, "");
                // upstreamID,
                preparedStmt.setInt(7, -1);
                // projectID
                preparedStmt.setInt(8, -1);
                // num_of_forks
                preparedStmt.setInt(9, -1);
                // created_at
                preparedStmt.setString(10, "");
                // pushed_at
                preparedStmt.setString(11, "");
                // size
                preparedStmt.setString(12, "-1");
                // language
                preparedStmt.setString(13, "");
                // ownerID
                preparedStmt.setString(14, "");
                // public_repos
                preparedStmt.setString(15, "");
                // public_gists
                preparedStmt.setString(16, "");
                // followers
                preparedStmt.setString(17, "");
                // following
                preparedStmt.setString(18, "");
                // user_type
                preparedStmt.setString(19, "");
            }
            System.out.println("insert " + preparedStmt.executeUpdate() + " repo");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return upstreamURL;

    }

    public void insertRepo(String repoURL) {
        String insertProject = "INSERT INTO fork.repository (repoURL)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS repourl) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT repourl" +
                "      FROM fork.repository AS repo" +
                "      WHERE repo.repoURL= ?" +
                "  )" +
                "  LIMIT 1";
//                " Values(\"" + repoURL + "\")";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insertProject)) {
            preparedStmt.setString(1, repoURL);
            preparedStmt.setString(2, repoURL);
            System.out.println("insert repo " + repoURL + " to database, affected " + preparedStmt.executeUpdate() + " row.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static public void main(String[] args) {
        IO_Process io = new IO_Process();
    }


    public HashSet<String> getForkListFromPRlist(String projectUrl) {
        String filePath = pr_dir + projectUrl + "/pr.txt";
        HashSet<String> forkList = new HashSet<>();
        IO_Process io = new IO_Process();
        File prFile = new File(filePath);
        if (prFile.exists()) {
            String pr_json_string = null;
            try {
                pr_json_string = io.readResult(filePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (pr_json_string.contains("----")) {
                String[] pr_json = pr_json_string.split("\n");
                for (int s = pr_json.length - 1; s >= 0; s--) {
                    String json_string = pr_json[s];
                    String[] arr = json_string.split("----\\[");
                    if (arr.length > 1) {
                        json_string = arr[1];
                        System.out.println(arr[0]);
                        JSONObject pr_info = new JSONObject(json_string.substring(0, json_string.lastIndexOf("]")));
                        String forkUrl = io.getForkURL(pr_info);
                        forkList.add(forkUrl);
                        if (forkUrl.equals("")) {
                            System.out.println("fork url is empty .");
                        }
                    } else {
                        io.writeTofile(projectUrl + " , " + json_string + "\n", output_dir + "incomplete_pr_api.txt");
                    }
                }
            }
        } else {
            System.out.println("pr list does not exist.");
            io.writeTofile(projectUrl + "\n", output_dir + "miss_pr_api.txt");
        }
        return forkList;
    }

    public HashMap<Integer, String> getIssueList(int projectID) {
        String query = "\n" +
                "SELECT issue_id,created_at\n" +
                "FROM  ISSUE\n" +
                "WHERE  projectID = " + projectID;

        HashMap<Integer, String> issues = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                issues.put(rs.getInt(1), rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return issues;

    }


    public void getIssuePRLink(String pr_id, String projectUrl, int projectID, List<String> prList, boolean csvFileExist) {
        String comment_csvFile_dir = output_dir + "shurui.cache/get_pr_comments." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
        String comment_csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_comments." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
        String commit_csvFile_dir = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
        String commit_csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_commits." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";

        IO_Process io = new IO_Process();
        List<List<String>> comments, commits;

        if (csvFileExist) {
            comments = io.readCSV(comment_csvFile_dir);
            commits = io.readCSV(commit_csvFile_dir);
        } else {
            comments = io.readCSV(comment_csvFile_dir_alternative);
            commits = io.readCSV(commit_csvFile_dir_alternative);
        }
        HashSet<Integer> issues = new HashSet<>();
        System.out.println("starting to parse commits and comments... " + projectUrl);
        issues.addAll(getIssueCandidates(comments,projectID));
        issues.addAll(getIssueCandidates(commits,projectID));

        if (issues.size() > 0) {
            System.out.println("issue list size: " + issues.size());
            String insert_PR_issueMap = " INSERT INTO fork.PR_TO_ISSUE(repoID, pull_request_id, issue_id, issue_created_at) " +
                    "  SELECT *" +
                    "  FROM (SELECT" +
                    "          ? AS a,? AS b, ? AS c, ? AS d) AS tmp" +
                    "  WHERE NOT EXISTS(" +
                    "      SELECT *" +
                    "      FROM fork.PR_TO_ISSUE AS ps" +
                    "      WHERE ps.repoID= ? AND" +
                    "         ps.pull_request_id= ? AND" +
                    "         ps.issue_id= ? " +
                    "  )" +
                    "  LIMIT 1";
            int count = 0;
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_PR_issueMap)) {
                conn.setAutoCommit(false);
                for (int issueid : issues) {
                    String issue_created_at = io.getIssueCreateatDate(issueid);
                    //repoID
                    preparedStmt.setInt(1, projectID);
                    // , pull_request_id,
                    preparedStmt.setInt(2, Integer.parseInt(pr_id));
                    // issue_id,
                    preparedStmt.setInt(3, issueid);
                    // issue_created_at
                    preparedStmt.setString(4, issue_created_at);
                    //repoID
                    preparedStmt.setInt(5, projectID);
                    // , pull_request_id,
                    preparedStmt.setInt(6, Integer.parseInt(pr_id));
                    // issue_id,
                    preparedStmt.setInt(7, issueid);
                    preparedStmt.addBatch();
                    if (++count % batchSize == 0) {
                        io.executeQuery(preparedStmt);
                        conn.commit();
                    }
                }

                System.out.println("inserting " + issues.size() + " issue for " + projectUrl + " , pr " + pr_id);
                io.executeQuery(preparedStmt);
                conn.commit();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }


    private HashSet<Integer> getIssueCandidates(List<List<String>> texts,int projectID) {
        Pattern issueLink = Pattern.compile("\\#[1-9][\\d]{1,4}([^0-9]|$)");
        HashSet<Integer> issues = new HashSet<>();
        System.out.println(texts.size() + " texts");
        for (List<String> comment : texts) {

            if (!comment.get(0).equals("")) {
                String text = comment.get(2);
                System.out.println(text);
                Matcher m = issueLink.matcher(text);
                while (m.find()) {
                    int s = Integer.parseInt(m.group().replaceAll("[^\\d]", "").trim());
                    if (new IO_Process().issueid_Exist(s,projectID) == 0) {
                        issues.add(s);
                        System.out.println(s + "issue exist in db ");
                    }
                }
            }
        }
        return issues;
    }



    public void getPRfiles(String projectUrl, int projectID, List<String> prList) {
        System.out.println("analyze pr files of " + projectUrl);
        IO_Process io = new IO_Process();
        io.rewriteFile("", output_dir + "shurui.cache/" + projectUrl.replace("/", ".") + ".prNum.txt");
        LocalDateTime now = LocalDateTime.now();

        String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
        if (new File(csvFile_dir).exists()) {
            List<List<String>> prs = io.readCSV(csvFile_dir);
            String insert_query_1 = " INSERT INTO fork.Pull_Request( " +
                    "projectID,pull_request_ID, authorName,forkID," +
                    "created_at, closed, closed_at, merged, data_update_at,labels,dupPR_label) " +
                    " SELECT * FROM (SELECT ? AS a,? AS b,? AS c,? AS d, ?AS ds,?AS de,?AS d3,?AS d2, ? AS d5,? AS label,? AS labelDup) AS tmp" +
                    " WHERE NOT EXISTS (" +
                    " SELECT projectID FROM Pull_Request WHERE projectID = ? AND pull_request_ID = ?" +
                    ") LIMIT 1";
            int count = 0;
            HashSet<String> existingForks = new HashSet<>();
            for (List<String> pr : prs) {
                if (!pr.get(0).equals("")) {
                    System.out.println();
                    long start = System.nanoTime();
                    try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                         PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                        conn.setAutoCommit(false);
                        int pr_id = Integer.parseInt(pr.get(9));
                        if (prList.contains(pr_id)) {
                            System.out.println("pass " + pr_id);
                            continue;
                        }

                        String author = pr.get(1);

                        /** insert fork to database**/
                        String forkURL = pr.get(7);
                        int forkID = io.getRepoId(forkURL);
                        if (!existingForks.contains(forkURL) || forkID == -1) {
                            io.insertRepo(forkURL);
//                            GithubRepository fork = new GithubRepository().getRepoInfo(forkURL, projectUrl);
//                            analyzeRepository.updateRepoInfo(fork);
                            forkID = io.getRepoId(forkURL);
                        } else {
                            System.out.println(forkURL + "already in database :)");
                        }

                        if (forkID == -1) {
                            System.out.println("fork: " + forkURL + " id is -1");
                        }
                        existingForks.add(forkURL);


                        String created_at = pr.get(6);

                        String closed_at = pr.get(5);
                        String closed = closed_at.equals("") ? "false" : "true";
                        String merged_at = pr.get(11);
                        String merged = merged_at.equals("") ? "false" : "true";

                        String labels = pr.get(10);

                        //projectID
                        preparedStmt.setInt(1, projectID);
                        //pull_request_ID
                        preparedStmt.setInt(2, pr_id);
                        //, authorName,
                        preparedStmt.setString(3, author);
                        //forkID," +
                        preparedStmt.setInt(4, forkID);
                        // created_at,
                        preparedStmt.setString(5, created_at);
                        // closed,
                        preparedStmt.setString(6, closed);
                        // closed_at,
                        preparedStmt.setString(7, closed_at);
                        // merged,
                        preparedStmt.setString(8, merged);
                        //data_update_at
                        preparedStmt.setString(9, String.valueOf(now));
                        //,labels
                        preparedStmt.setString(10, labels);

                        boolean dup_pr = false;
                        if (labels.toLowerCase().contains("duplicate")) {
                            dup_pr = true;
                        }
                        preparedStmt.setBoolean(11, dup_pr);
                        preparedStmt.setInt(12, projectID);
                        preparedStmt.setInt(13, pr_id);
                        preparedStmt.addBatch();

                        long end = System.nanoTime();
                        System.out.println("insert ONE PR :" + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }

                        io.executeQuery(preparedStmt);
                        conn.commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                }
            }
        }
    }


//    public void inserRepoToDB(GithubRepository repo) {
//        System.out.println("get fork info: " + repo.getrepoUr);
//        String query = "  INSERT INTO repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo,upstreamID,projectID," +
//                "num_of_forks, created_at,pushed_at, size,language,ownerID,public_repos," +
//                "public_gists ,followers  , following  ,user_type  )" +
//                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
//        String upstreamURL = "";
//
//        try (
//                Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//                PreparedStatement preparedStmt = conn.prepareStatement(query);)
//
//        {
//
//            String forkUrl = github_api_repo + repoUrl + "?access_token=" + token;
//            JsonUtility jsonUtility = new JsonUtility();
//            ArrayList<String> fork_info_json = null;
//            try {
//                fork_info_json = jsonUtility.readUrl(forkUrl);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));
//            String owner_url = (String) ((JSONObject) fork_jsonObj.get("owner")).get("url");
//            boolean isFork = (boolean) fork_jsonObj.get("fork");
//            if (isFork) {
//                upstreamURL = (String) ((JSONObject) fork_jsonObj.get("parent")).get("full_name");
//                upstreamID = getRepoId(upstreamURL);
//            }
//            ArrayList<String> owner_info_json = null;
//            try {
//                owner_info_json = jsonUtility.readUrl(owner_url + "?access_token=" + token);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            String language = null;
//            if (fork_jsonObj.get("language") != null) {
//                language = String.valueOf(fork_jsonObj.get("language"));
//            }
//            JSONObject owner_jsonObj = new JSONObject(owner_info_json.get(0));
//            // repoURL
//            preparedStmt.setString(1, repoUrl);
//            // loginID
//            preparedStmt.setString(2, String.valueOf(owner_jsonObj.get("login")));
//            // repoName
//            preparedStmt.setString(3, repoUrl.split("/")[1]);
//            // isFork
//            preparedStmt.setBoolean(4, true);
//            // UpstreamURL,
//            preparedStmt.setString(5, upstreamURL);
//
//            // belongToRepo,
//            preparedStmt.setString(6, upstreamURL);
//            // upstreamID,
//            preparedStmt.setInt(7, upstreamID);
//            // projectID
//            preparedStmt.setInt(8, upstreamID);
//            // num_of_forks
//            preparedStmt.setInt(9, (Integer) fork_jsonObj.get("forks_count"));
//            // created_at
//            preparedStmt.setString(10, (String) owner_jsonObj.get("created_at"));
//            // pushed_at
//            preparedStmt.setString(11, (String) fork_jsonObj.get("pushed_at"));
//            // size
//            preparedStmt.setString(12, String.valueOf(fork_jsonObj.get("size")));
//            // language
//            preparedStmt.setString(13, language);
//            // ownerID
//            preparedStmt.setString(14, String.valueOf(owner_jsonObj.get("login")));
//            // public_repos
//            preparedStmt.setString(15, String.valueOf(owner_jsonObj.get("public_repos")));
//            // public_gists
//            preparedStmt.setString(16, String.valueOf(owner_jsonObj.get("public_gists")));
//            // followers
//            preparedStmt.setString(17, String.valueOf(owner_jsonObj.get("followers")));
//            // following
//            preparedStmt.setString(18, String.valueOf(owner_jsonObj.get("following")));
//            // user_type
//            preparedStmt.setString(19, String.valueOf(owner_jsonObj.get("type")));
//
//
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//    }

    public ArrayList<String> getCommitFromCMD(String sha, String repoUrl) {
        String[] cmd_getline = {"/bin/sh",
                "-c",
                "git log -1 --numstat " + sha + " | egrep ^[[:digit:]]+ | egrep -v ^[\\d]+[[:space:]]+[\\d]+[[:space:]]+"};

        String[] changedfiles = exeCmd(cmd_getline, clone_dir + repoUrl).split("\n");

        String[] cmd_getStatus = {"/bin/sh",
                "-c", "git log -1 --name-status " + sha + " --pretty=\"\""};

        String[] status = exeCmd(cmd_getStatus, clone_dir + repoUrl).split("\n");

        if (status[0].contains("fatal: bad object")) {
            return null;
        }
        ArrayList<String> changedFileResult = new ArrayList<>();
        for (int i = 0; i < changedfiles.length; i++) {
            String file = changedfiles[i];
            file += "\t" + status[i].split("\t")[0];
            changedFileResult.add(file);
        }

        return changedFileResult;
    }

    public HashSet<String> getCommitList(int projectID) {
        HashSet<String> allcommits = new HashSet<>();
        String query = "SELECT  commitSHA,  id \n" +
                "FROM fork.Commit    \n" +
                "WHERE projectID = " + projectID;
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allcommits.add(rs.getString(1) + "," + rs.getString(2));
//                System.out.println(rs.getString(1)+","+rs.getString(2)+","+rs.getInt(3));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println(" all commit: #" + allcommits.size());
        return allcommits;
    }


    public HashSet<String> get_un_analyzedCommit(int projectID) {

        HashSet<String> unAnalyzedCommit = new HashSet<>();
        String query = "SELECT  c.commitSHA, c.id \n" +
                "FROM fork.Commit AS c  \n" +
                "WHERE NOT EXISTs (SELECT * FROM fork.commit_changedFiles cc WHERE c.id = cc.commit_uuid) AND c.projectID = " + projectID;
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                unAnalyzedCommit.add(rs.getString(1) + "," + rs.getString(2));
//                System.out.println(rs.getString(1)+","+rs.getString(2)+","+rs.getInt(3));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println(" todo commit: #" + unAnalyzedCommit.size());
        return unAnalyzedCommit;
    }


    public HashSet<String> getProjectForkMap(String projectURL) {
        HashSet<String> forkSet = new HashSet<>();
        String query = "SELECT r2.repoURL AS upstream, r1.repoURL AS fork\n" +
                "FROM repository AS r1, repository AS r2\n" +
                "WHERE  r1.projectID = r2.id AND r2.repoURL = \'" + projectURL + "\'";


        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);) {

            try (ResultSet rs = preparedStmt.executeQuery()) {
                while (rs.next()) {
                    String project = rs.getString("upstream");
                    String fork = rs.getString("fork");
                    forkSet.add(fork);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return forkSet;
    }


    public HashSet<String> getExistCommits(int projectID, int pr_id) {
        HashSet<String> commits = new HashSet<>();
        String query_getExistCommit = "SELECT c.commitSHA\n" +
                "FROM  Pull_Request as pr, PR_Commit_map as prc, Commit as c\n" +
                "WHERE  pr.pull_request_ID = prc.pull_request_id and pr.projectID = prc.projectID " +
                "and prc.commit_uuid = c.id and pr.projectID= " + projectID +
                " and pr.pull_request_ID  = " + pr_id;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query_getExistCommit);) {

            try (ResultSet rs = preparedStmt.executeQuery()) {
                while (rs.next()) {
                    String sha = rs.getString(1);
                    commits.add(sha);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commits;
    }


    public List<String> getActiveForksFromDatabase(String projectUrl) {
        System.out.println("get active fork List for " + projectUrl);
        List<String> activeForks = new ArrayList<>();
        String query = "select r1.repoURL\n" +
                "from repository as r1 ,repository as r2\n" +
                "WHERE r2.projectID = r1.id and r1.repoURL = \"" + projectUrl + "\"";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                activeForks.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println(" active forks in database: #" + activeForks.size());
        return activeForks;

    }

    public HashSet<String> getActiveForksFromPRresult(String projectUrl) {
        System.out.println("read csv of " + projectUrl);
        HashSet<String> forkList = new HashSet<>();
        IO_Process io = new IO_Process();
        List<List<String>> prList = null;
        prList = io.readCSV(output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv");
        System.out.println("prList: " + prList.size());

        for (List<String> pr : prList) {
            if (!pr.get(0).trim().equals("")) {
                String forkURL = pr.get(7);
                if (!forkURL.trim().equals(projectUrl) & !forkURL.trim().equals("")) {
                    forkList.add(forkURL);
                }
            }
        }
        return forkList;
    }


}




