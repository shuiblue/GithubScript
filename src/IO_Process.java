import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public  List<String> getPRNumlist(String projectUrl) {
        IO_Process io = new IO_Process();
        String prNumList_filePath = output_dir +"AnalyzePR/"+ projectUrl.replace("/", ".") + ".prNum.txt";
        List<String> prList = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        if (!new File(prNumList_filePath).exists()) {
            System.out.println("generating pr num list");
            String csvFile_dir = output_dir + "shurui.cache/get_prs." + projectUrl.replace("/", ".") + ".csv";
            List<List<String>> prs = io.readCSV(csvFile_dir);
            for (List<String> pr : prs) {
                if (!pr.get(0).equals("")) {
                    int pr_id = Integer.parseInt(pr.get(9));
                    sb.append(pr_id + "\n");
                }

            }
            io.rewriteFile(sb.toString(), output_dir +"AnalyzePR/"+  projectUrl.replace("/", ".") + ".prNum.txt");
            String prListString = "";
            try {
                prListString = io.readResult(prNumList_filePath);

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (!prListString.trim().equals("")) {
                prList = Arrays.asList(prListString.split("\n"));

            }
            return prList;
        }
        return new ArrayList<>();

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
            String line = null;
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
            else
                System.out.println("Execution " + i +
                        "successful: " + numUpdates[i] + " rows updated");
        }
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


    public int getCommitID(String sha) {
        String commitshaID_QUERY = "SELECT id FROM Commit WHERE commitSHA = ?";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY);) {
            preparedStmt.setString(1, sha);
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {               // Position the cursor                  4
                int commitshaID = rs.getInt(1);
                if (commitshaID != -1) {
                    return commitshaID;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public int getRepoId(String repoURL) {
        int repoID = -1;

        String selectRepoID = "SELECT id FROM fork.repository WHERE repoURL = ?";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID)) {
            preparedStmt.setString(1, repoURL);

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

    public HashMap<String, Integer> getCommitIdMap(HashSet<String> commitSet) {
        HashMap<String, Integer> sha_commitID_map = new HashMap<>();
        for (String commit : commitSet) {
            int commitID = getCommitID(commit);
            if (commitID == -1) {
                System.out.println("commit is not in the database !!!");
            } else {
                sha_commitID_map.put(commit, commitID);
            }
        }
        return sha_commitID_map;
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


    public  HashSet<String>  get_un_analyzedCommit() {
        HashSet<String>  unAnalyzedCommit = new HashSet<>();
        String query = "SELECT repo.repoURL, c.commitSHA, c.id \n" +
                "FROM fork.Commit AS c , repository as repo\n" +
                "WHERE NOT EXISTs (SELECT * FROM fork.commit_changedFiles cc WHERE c.id = cc.commitSHA_id) and c.projectID = repo.id;\n";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                unAnalyzedCommit.add(rs.getString(1)+","+rs.getString(2));
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }
        return unAnalyzedCommit;
    }
}



