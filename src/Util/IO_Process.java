package Util;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    public static String token;
    public static String user, pwd, myUrl, ghtpwd, ghtUrl;
    public static String github_api_repo = "https://api.github.com/repos/";
    public static String github_url = "https://github.com/";
    public static String current_dir = System.getProperty("user.dir");
    public static String working_dir;
    public static String pr_dir;
    public static String output_dir;
    public static String clone_dir;
    public static String graph_dir;
    public static String result_dir;
    public static String hardfork_dir;
    final int batchSize = 500;
    HashSet<String> stopFileSet = new HashSet<>();
    HashSet<String> nonDevRepoSet = new HashSet<>();
    HashSet<String> sourceCodeSuffix = new HashSet<>();

    public IO_Process() {
        try {
            String[] paramList = readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            result_dir = output_dir + "result0821/";
            graph_dir = output_dir + "ClassifyCommit_new/";
            clone_dir = output_dir + "clones/";
            hardfork_dir = output_dir+ "hardfork-exploration/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            ghtpwd = paramList[4];
            ghtUrl = paramList[5];
            token = readResult(current_dir + "/input/token.txt").trim();
            stopFileSet.addAll(Arrays.asList(readResult(current_dir + "/input/StopFiles.txt").split("\n")));
            sourceCodeSuffix.addAll(Arrays.asList(readResult(current_dir + "/input/sourceCode.txt").split("\n")));
            nonDevRepoSet.addAll(Arrays.asList(readResult(current_dir + "/input/NonDevelopmentRepo.txt").split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public void writeTofile(String content, String filepath) {


        try {
            File file = new File(filepath);
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.getParentFile().mkdirs();
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
        File csvFile = new File(filePath);
        return readCSV(csvFile);
    }

    public List<List<String>> readCSV(File csvFile) {
        List<List<String>> rows = new ArrayList<>();
        try (InputStream in = new FileInputStream(csvFile);) {
            CSV csv = new CSV(true, ',', in);
            while (csv.hasNext()) {
                List<String> fields = new ArrayList<>(csv.next());
                rows.add(fields);
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

    public static void fileCopy(String sourceFile, String destinationFile) throws IOException {
        Path source = Paths.get(sourceFile);
        Path destination = Paths.get(destinationFile);

        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
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
            //  e.printStackTrace();
            System.out.println(pathname + "does not exist. return");
            return "noRepo";
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
            if (!file.equals("")) {
                changedFileResult.add(file);
            }
        }

        return changedFileResult;
    }

    public List<String> getCommitInBranch(String br, String after_Date, String project_cloneDir) {
        //todo git log -1 9b63430f349f7083d09d2db24d24908e1d277379 --pretty="%H" get author date
//        String cmd_getCommit = "git log " + br + " --after=\"" + after_Date + "\" --pretty=\"%H\"";
        String cmd_getCommit = "git log --no-merges " + br + " --after=\"" + after_Date + "\" --pretty=\"%H\"";
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


        System.out.print(numUpdates.length + " rows updated....");
    }

    public String getRepoUrlByID(int projectID) {
        String commitshaID_QUERY = "SELECT repoURL_old FROM repository WHERE id = " + projectID;
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


//    public String getCommitID(String sha) {
//        String commitshaID_QUERY = "SELECT id FROM Commit.Commit WHERE SHA = ?";
//        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
//             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
//            preparedStmt.setString(1, sha);
//            ResultSet rs = preparedStmt.executeQuery();
//            if (rs.next()) {               // Position the cursor                  4
//                String commitshaID = rs.getString(1);
//                return commitshaID;
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//
//        return "";
//    }

    public int commitExists(String sha) {

        String commitshaID_QUERY = "SELECT 1 from Commit WHERE SHA = \'" + sha + "\' LIMIT 1";
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


    public HashSet<String> commitChangedFileExists(int projectID) {
        HashSet<String> commits = new HashSet<>();
//        String commitshaID_QUERY = "SELECT DISTINCT c.SHA\n" +
//                "from commit_changedFiles as cc, Commit as c\n" +
//                "WHERE cc.commit_SHA = c.SHA and c.projectID =" + projectID;


        String commitshaID_QUERY = "SELECT DISTINCT prc.SHA\n" +
                "FROM commit_changedFiles AS cc\n" +
                "  LEFT JOIN PR_Commit_map AS prc ON cc.commit_SHA = prc.SHA\n" +
                "WHERE  prc.projectID = " + projectID;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                commits.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return commits;

    }

    public int commitExitsINChangedFileTable(String commitshaID) {
        String commitshaID_QUERY = "SELECT 1 from commit_changedFiles WHERE commit_uuid = \'" + commitshaID + "\' LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {
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

    public HashSet<String> exist_PR_issueMap(int projectID) {
        HashSet<String> result = new HashSet<>();
        String selectRepoID = "SELECT pull_request_id,issue_id FROM fork.PR_TO_ISSUE WHERE repoID =  " + projectID;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID);) {
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {
                result.add(String.valueOf(rs.getInt(1) + "," + String.valueOf(rs.getInt(2))));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;

    }

    public HashMap<Integer, String> getIssueSet(int projectID) {
        HashMap<Integer, String> result = new HashMap<>();
        String selectRepoID = "SELECT issue_id,created_at FROM fork.ISSUE WHERE projectID =  " + projectID;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID);) {
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {
                result.put(rs.getInt(1), rs.getString(2));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }


    public HashSet<String> prListExistIN_PR_commitMap(int projectID) {
        HashSet<String> prList = new HashSet<>();
        String selectRepoID = "SELECT pull_request_id FROM fork.PR_Commit_map WHERE projectID = ? ";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID);) {

            preparedStmt.setInt(1, projectID);
            ResultSet rs = preparedStmt.executeQuery();

            while (rs.next()) {
                prList.add(String.valueOf(rs.getInt("pull_request_id")));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prList;


    }

    public HashSet<String> prList_ExistIN_PR_commitMap(int projectID) {
        HashSet<String> prList = new HashSet<>();
        String commitshaID_QUERY = "SELECT DISTINCT pull_request_id from PR_Commit_map WHERE projectID = \'" + projectID + "\' ";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY)) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {               // Position the cursor                  4
                prList.add(String.valueOf(rs.getInt(1)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return prList;
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

    public String getForkURL_by_loginID(String fork_loginID, int projectID) {
        String query = "select DISTINCT  repoURL\n" +
                "  from repository\n" +
                "where projectID = " + projectID + " and loginID =\'" + fork_loginID + "\';";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }


    public Set<String> getForkIDSet_by_loginID(Set<String> forkLoginIdSet, int projectID) {
        String forkList_str = "";
        for (String fork : forkLoginIdSet) {
            forkList_str += "'" + fork + "',";
        }
        forkList_str = forkList_str.substring(0, forkList_str.length() - 1);


        Set<String> forkSet = new HashSet<>();

        String query = "select DISTINCT  repoURL\n" +
                "  from repository\n" +
                "where projectID = " + projectID + " and loginID IN (" + forkList_str + ");";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                while (rs.next()) {
                    forkSet.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return forkSet;
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

        String selectRepoID = "SELECT repoURL_old FROM fork.repository WHERE isFork = FALSE ";

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


    public HashSet<String> getForkListFromRepoTable(int projectID) {
        HashSet<String> forkList = new HashSet<>();
        String selectRepoID = "SELECT repoURL_old FROM fork.repository WHERE projectID = ? AND isFork = TRUE ";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(selectRepoID)) {

            preparedStmt.setInt(1, projectID);
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                forkList.add(String.valueOf(rs.getString("repoURL")));
            }
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


    public HashSet<Integer> getPRinDataBase(int projectID) {
        String query = "\n" +
                "SELECT pull_request_ID\n" +
                "FROM  Pull_Request\n" +
                "WHERE  projectID = " + projectID;

        HashSet<Integer> analyzedPR = new HashSet<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                analyzedPR.add(rs.getInt(1));
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

    public String cloneRepo(String forkUrl, String projectURL) {
        if (!new File(clone_dir).exists()) {
            new File(clone_dir).mkdirs();
            System.out.println("clones file created");
        }
        String projectName = projectURL.split("/")[0];
        IO_Process io = new IO_Process();
        File upstreamFile = new File(clone_dir + projectURL + "/.git");
        if (!upstreamFile.exists()) {
            String creadDirCMD = "mkdir -p " + clone_dir + projectURL;
            io.exeCmd(creadDirCMD.split(" "), clone_dir);
            System.out.println("mkdir -p " + projectURL);
            String cloneCMD = "git clone " + github_url + projectURL + ".git";
            io.exeCmd(cloneCMD.split(" "), clone_dir + projectName + "/");
        }

        if (!forkUrl.equals("")) {
            forkUrl = io.getForkURL(forkUrl);
            System.out.println(forkUrl);
            String forkName = io.getForkURL(forkUrl.split("/")[0]);
            String cloneForkCmd = "git remote add " + forkName + " " + github_url + forkUrl + ".git";
            String result = io.exeCmd(cloneForkCmd.split(" "), clone_dir + projectURL + "/");
            if (result.equals("noRepo")) return result;

            String fetchAll = "git fetch " + forkName;
            if (io.exeCmd(fetchAll.split(" "), clone_dir + projectURL + "/").contains("fatal: could not read Username")) {
                System.out.println(forkUrl + "is deleted on GitHub.");
            }
        }
        return "success";
    }

    public String insertRepo_old(String repoUrl) {
        GithubApiParser githubApiParser = new GithubApiParser();
        System.out.println("get fork info: " + repoUrl);
        String query = "  INSERT INTO repository ( repoURL_old,loginID,repoName,isFork,UpstreamURL,belongToRepo,upstreamID,projectID," +
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

    public int insertFork(String repoURL, boolean isFork, int projectID, int upstreamID) {
        String insertProject = "INSERT INTO fork.repository (repoURL_old,isFork,projectID,upstreamID,loginID)" +
                "  SELECT *" +
                "  FROM (SELECT" +
                "          ? AS repourl, ? AS B, ? AS C,? AS D,? AS E) AS tmp" +
                "  WHERE NOT EXISTS(" +
                "      SELECT repoURL_old" +
                "      FROM fork.repository AS repo" +
                "      WHERE repo.repoURL_old= ? AND (projectID IS NOT NULL AND projectID != 0) " +
                "  )" +
                "  LIMIT 1";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(insertProject)) {
            preparedStmt.setString(1, repoURL);
            preparedStmt.setBoolean(2, isFork);
            preparedStmt.setInt(3, projectID);
            preparedStmt.setInt(4, upstreamID);
            preparedStmt.setString(5, repoURL.split("/")[0]);
            preparedStmt.setString(6, repoURL);

            System.out.println("insert repo " + repoURL + " to database, affected " + preparedStmt.executeUpdate() + " row.");

            ResultSet rs = preparedStmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
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
            HashSet<String> existingForks = io.getForkListFromRepoTable(projectID);
            System.out.println("there are " + existingForks.size() + " forks of " + projectUrl + " in DB.");
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                conn.setAutoCommit(false);

                for (List<String> pr : prs) {
                    if (!pr.get(0).equals("")) {
                        System.out.println();
                        int pr_id = Integer.parseInt(pr.get(9));
                        if (prList.contains(pr_id)) {
                            System.out.println("pass " + pr_id);
                            continue;
                        }

                        System.out.println("pr " + pr_id + " " + projectUrl);
                        String author = pr.get(1);

                        /** insert fork to database**/
                        String forkURL = pr.get(7);
                        int forkID = io.getRepoId(forkURL);
                        if (!forkURL.trim().equals("") && (!existingForks.contains(forkURL) || forkID == -1)) {
                            boolean isFork = forkURL.equals(projectUrl) ? false : true;
                            io.insertFork(forkURL, isFork, projectID, projectID);
                            forkID = io.getRepoId(forkURL);
                        } else {
                            System.out.println(forkURL + "already in database :)");
                        }

                        if (forkID == -1) {
                            System.out.println("fork: " + forkURL + " id is -1");
                        }
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


                        if (++count % batchSize == 0) {
                            io.executeQuery(preparedStmt);
                            conn.commit();
                        }
                    }
                }
                io.executeQuery(preparedStmt);
                conn.commit();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }


    public String getIssueOwner(Integer projectID, Integer issue_id, String issue_or_pr) {

        String query = "SELECT author\n" +
                "FROM " + issue_or_pr + "\n" +
                "WHERE projectID = " + projectID + " AND issue_id= " + issue_id;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";

    }


    //    public void inserRepoToDB(Repository.GithubRepository repo) {
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
//            Util.JsonUtility jsonUtility = new Util.JsonUtility();
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
    public String[] getCommitInfoFromCMD(String sha, String repoUrl) {
        String[] cmd = {"/bin/sh",
                "-c",
                "git show --format=\"%aN~%ae~%cI\"" + sha};

        String[] changedfiles = exeCmd(cmd, clone_dir + repoUrl).split("\n");
        String[] result = changedfiles[0].split("~");
        return result;
    }


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


    public HashSet<String> getProjectForkMap(String projectURL) {
        HashSet<String> forkSet = new HashSet<>();
        String query = "SELECT r2.repoURL_old AS upstream, r1.repoURL_old AS fork\n" +
                "FROM repository AS r1, repository AS r2\n" +
                "WHERE  r1.projectID = r2.id AND r2.repoURL_old = \'" + projectURL + "\'";


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

    /**
     * get existing commits in database
     */
    public HashSet<String> getExistCommits_inCommit(int projectID) {
        HashSet<String> commits = new HashSet<>();
        String query_getExistCommit = "SELECT DISTINCT SHA\n" +
                "FROM   fork.Commit  "
                + "WHERE  projectID= " + projectID;

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

    public HashSet<String> getExistCommits_inPRCommitMap(int projectID) {
        long start = System.nanoTime();

        HashSet<String> commits = new HashSet<>();
        String query_getExistCommit = "SELECT   prc.sha, prc.pull_request_id\n" +
                "FROM PR_Commit_map AS prc\n" +
                "WHERE  prc.projectID = " + projectID;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query_getExistCommit);) {

            try (ResultSet rs = preparedStmt.executeQuery()) {
                while (rs.next()) {
                    commits.add(rs.getString(1) + "," + rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        long end = System.nanoTime();
        long used = end - start;
        System.out.println("get existing pr commit map :" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");

        return commits;
    }


    public List<String> getActiveForksFromDatabase(String projectUrl) {
        System.out.println("get active fork List for " + projectUrl);
        List<String> activeForks = new ArrayList<>();
        String query = "select r1.repoURL_old\n" +
                "from repository as r1 ,repository as r2\n" +
                "WHERE r2.projectID = r1.id and r1.repoURL_old = \"" + projectUrl + "\"";
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


    public List<String> getListFromFile(String repoList_file) {
        String[] repos = new String[0];
        try {
            repos = readResult(current_dir + "/input/" + repoList_file).split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Arrays.asList(repos);
    }

    public List<String> getList_byFilePath(String filePath) {
        String[] repos = new String[0];
        try {
            repos = readResult(filePath).split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Arrays.asList(repos);
    }

    public int insertUser(String loginID, String full_name, String email, String user_type) {

        IO_Process io = new IO_Process();
        int userId = io.getUserId(loginID);

        if (userId == -1) {
            System.out.println(" user " + loginID + " not exist, add to database...");
            String query = " INSERT INTO user (login_id, full_name, email, user_type)  \n" +
                    "VALUES (?,? ,?,?);";
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);) {
                preparedStmt.setString(1, loginID);
                preparedStmt.setString(2, full_name);
                preparedStmt.setString(3, email);
                preparedStmt.setString(4, user_type);
                int affectedRows = preparedStmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating user failed, no rows affected.");
                }

                ResultSet generatedKeys = preparedStmt.getGeneratedKeys();

                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);

                } else {
                    throw new SQLException("Creating user failed, no generated key obtained.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return userId;

    }

    private int getUserId(String loginID) {
        int userID = -1;
        String query = "SELECT id FROM fork.user WHERE login_id = \"" + loginID + "\"";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {
            try (ResultSet rs = preparedStmt.executeQuery()) {
                if (rs.next()) {
                    userID = rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


        return userID;

    }

    public int insertRepo(String projectURL) {

        String query = " INSERT INTO repository(repoURL_old)  \n" +
                "VALUES (?);";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);) {
            preparedStmt.setString(1, projectURL);
            int affectedRows = preparedStmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

            ResultSet generatedKeys = preparedStmt.getGeneratedKeys();

            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);

            } else {
                throw new SQLException("Creating user failed, no generated key obtained.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;


    }


    public List<Integer> getClosedPRList_unfinishedHotness(int projectID) {
        List<Integer> allPRS = new ArrayList<>();
//        String mergedCommitID_query = "SELECT  pull_request_id\n" +
////                "FROM Pull_Request\n" +
////                "WHERE  projectID = " + projectID +
////                "       AND  closed = 'true' ";

//        String mergedCommitID_query = "SELECT  prc.pull_request_id\n" +
//                "FROM Pull_Request pr LEFT JOIN PR_Commit_map prc\n" +
//                "    ON pr.projectID = prc.projectID AND pr.pull_request_ID = prc.pull_request_id\n" +
//                "  LEFT JOIN Commit c ON prc.sha = c.SHA\n" +
//                "WHERE pr.closed = 'true' AND c.num_hotFiles_touched_fromAPI IS NULL and prc.projectID = " + projectID;

        String mergedCommitID_query = "SELECT DISTINCT prc.pull_request_ID\n" +
                "FROM fork.PR_Commit_map prc\n" +
                "  right JOIN Pull_Request pr ON prc.pull_request_id = pr.pull_request_ID\n" +
                "  right JOIN commit_files_GHAPI cf on prc.sha = cf.sha\n" +
                "WHERE  pr.projectID =" + projectID + " and pr.isClosed and prc.projectID = pr.projectID ";
//                "WHERE  pr.projectID ="+projectID+" and pr.isClosed and prc.projectID = pr.projectID  and pr.num_commits_on_files_touched IS NULL\n ";

//        String mergedCommitID_query = "SELECT pr.pull_request_ID\n" +
//                "FROM fork.Pull_Request pr\n" +
//                " WHERE pr.num_commits_on_files_touched is null and pr.projectID =  "+projectID;

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allPRS.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allPRS;
    }

    public List<Integer> getClosedPRList_partial(int projectID) {
        List<Integer> allPRS = new ArrayList<>();

        String mergedCommitID_query = "SELECT DISTINCT pr.pull_request_ID\n" +
                "FROM Pull_Request pr " +
                "WHERE  pr.projectID =" + projectID + " and pr.isClosed";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allPRS.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allPRS;
    }

    public List<Integer> getClosedPRList(int projectID) {
        List<Integer> allPRS = new ArrayList<>();
        String mergedCommitID_query = "SELECT  pull_request_id\n" +
                "FROM Pull_Request\n" +
                "WHERE  projectID = " + projectID +
                "       AND  closed = 'true' ";
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allPRS.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return allPRS;
    }


    //    public Set<String> getFollower(String maintainer) {
    public HashMap<String, String> getFollower(String maintainer) {
        HashMap<String, String> followerSet = new HashMap<>();
//        Set<String> followerSet = new HashSet<>();
        String mergedCommitID_query = "select  follower.login, f.created_at\n" +
                "from `ghtorrent-2018-03`.followers f\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.users  user on  f.user_id =user.id\n" +
                "  LEFT JOIN `ghtorrent-2018-03`.users follower on f.follower_id =follower.id\n" +
                "WHERE user.login = \'" + maintainer + "\'";

        try (Connection conn = DriverManager.getConnection(ghtUrl, user, ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                String follower = rs.getString(1);
                String date = rs.getString(2);
                followerSet.put(follower, date);


            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return followerSet;
    }


    //    public HashMap<String, HashMap<String, ArrayList<Integer>>> getClosedPROwnerMaintainer(int projectID) {
    public HashMap<String, HashMap<String, ArrayList<String>>> getClosedPROwnerMaintainer(int projectID) {

//        HashMap<String, HashMap<String, ArrayList<Integer>>> maintainer_owners = new HashMap<>();
        HashMap<String, HashMap<String, ArrayList<String>>> maintainer_owners = new HashMap<>();

        String mergedCommitID_query = "SELECT  pull_request_id, authorName,closedBy_loginID,created_at\n" +
                "FROM Pull_Request\n" +
                "WHERE  projectID = " + projectID +
                "       AND  closed = 'true' and authorName!= closedBy_loginID";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(mergedCommitID_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                int prID = rs.getInt(1);
                String owner = rs.getString(2);
                String maintainer = rs.getString(3);
                String created_at = rs.getString(4);
                if (maintainer.equals("")) continue;

//                HashMap<String, ArrayList<Integer>> owner_prSet = new HashMap<>();
                HashMap<String, ArrayList<String>> owner_prSet = new HashMap<>();
                if (maintainer_owners.get(maintainer) != null) {
                    owner_prSet = maintainer_owners.get(maintainer);
                }

//                ArrayList<Integer> prSet = new ArrayList<>();
                ArrayList<String> prSet = new ArrayList<>();
                if (owner_prSet.get(owner) != null) {
                    prSet = owner_prSet.get(owner);
                }
//                prSet.add(prID);
                prSet.add(prID + "," + created_at);
                owner_prSet.put(owner, prSet);
                maintainer_owners.put(maintainer, owner_prSet);

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return maintainer_owners;
    }

    public List<String> getPRList_String(int projectID, boolean merged) {
        String mergedCommitID_query = "SELECT  pull_request_id\n" +
                "FROM Pull_Request\n" +
                "WHERE  projectID = " + projectID +
                "       AND  merge_allType =" + merged +
                "       and fromCoreTeam = false " +
                "       and bot_account = false ;";

        return getFirstStringValueBySQL(mergedCommitID_query);

    }


    public List<Integer> getFirstValueBySQL(String sql) {
        List<Integer> integer_List = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(sql);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                integer_List.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return integer_List;
    }

    public List<String> getFirstStringValueBySQL(String sql) {
        List<String> integer_List = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(sql);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                integer_List.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return integer_List;
    }

    public HashSet<String> getCommitsByPRlist(HashSet<String> sampled_PRs, int projectID) {
        System.out.print("collecting all the commits .. ");
        HashSet<String> allcommits = new HashSet<>();
        String commitList_query = "SELECT\n" +
                "  c.SHA,\n" +
                "  c.loginID\n" +
                "FROM\n" +
                "  Commit c\n" +
                "  INNER JOIN fork.PR_Commit_map prc ON prc.sha = c.SHA\n" +
                "WHERE prc.projectID = " + projectID + " AND prc.pull_request_id IN (" + new IO_Process().removeBrackets(sampled_PRs.toString()) + ");";

        try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
             PreparedStatement preparedStmt = conn.prepareStatement(commitList_query);
        ) {
            ResultSet rs = preparedStmt.executeQuery();
            while (rs.next()) {
                allcommits.add(rs.getString(1) + "," + rs.getString(2));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        System.out.println("sampled " + allcommits.size() + " commits in pr");
        return allcommits;


    }


    public boolean isStopFile(String fileName) {
        for (String file : stopFileSet) {
            if (fileName.toLowerCase().contains(file)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNonDevRepo(String repoURL) {
        for (String word : nonDevRepoSet) {
            if (repoURL.toLowerCase().contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public boolean isSourceCode(String fileName) {
        for (String suffix : sourceCodeSuffix) {
            if (fileName.toLowerCase().endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public List<String> removeStopFile(HashSet<String> added_files) {

        List<String> list = new ArrayList<>();
        for (String f : added_files) {
            if (isSourceCode(f)) {
                list.add(f);
            }
        }
        return list;
    }

    public boolean isDuplicatePR(String comment) {

        Pattern p1 = Pattern.compile("(clos(e|ed|ing)|dup(licate(d)?|e)?|super(c|s)ee?ded?|obsoleted?|replaced?|redundant|better (implementation|solution)" +
                "|solved|fixed|done|going|addressed|already)[ ]{1,}(by|in|with|as|of|in favor of|at|since|via)?[ ]{1,}" +
                "(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)");  // remove merged, landed
        Matcher m1 = p1.matcher(comment);
        if (m1.find()) {
            System.out.println(m1.group());
            System.out.println("match 1.");
            return true;
        }
        Pattern p2 = Pattern.compile("(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+).{1,}" +
                "(better (implementation|solution)|dup(licate(d)?|e)?|(fixe(d|s)?|obsolete(d|s)?|replace(d|s)?))");
        Matcher m2 = p2.matcher(comment);
        if (m2.find()) {
            System.out.println(m2.group());
            System.out.println("match 2.");
            return true;
        }
        Pattern p3 = Pattern.compile("dup(?:licated?)?:?[ ]{1,}(#|http(s)?://github.com/([\\w\\.-]+/){2}(pull|issues)(#(\\w)+)?/)(\\d+)");
        Matcher m3 = p3.matcher(comment);
        if (m3.find()) {
            System.out.println(m3.group());
            System.out.println("match 3.");
            return true;
        }
        return false;
    }

    public boolean isDuplicateComment(String comment) {
        Pattern p1 = Pattern.compile("(dup(licate(d)?|e)?|super(c|s)(ee)?(ded)?(edes)?|obsoleted?|replaced?|redundant|better (implementation|solution)" +
                "|solved|going|addressed|already)[ ]{1,}(by|in|with|as|of|in favor of|at|since|via)?");
        //      "|solved|fixed|done|going|addressed|already)[ ]{1,}(by|in|with|as|of|in favor of|at|since|via)?");// remove merged, landed, fixed, done, clos(e|ed|ing)|
        Matcher m1 = p1.matcher(comment);
        if (m1.find()) {
            System.out.println(m1.group());
            System.out.println("match 1.");
            return true;
        }
        Pattern p2 = Pattern.compile(
//                "(better (implementation|solution)|dup(licate(d)?|e)?|(fixe(d|s)?|obsolete(d|s)?|replace(d|s)?))"); //todo remove fixes for now
                "(better (implementation|solution)|dup(licate(d)?|e)?|obsolete(d|s)?|replace(d|s)?)"); //todo remove fixes for now
        Matcher m2 = p2.matcher(comment);
        if (m2.find()) {
            System.out.println(m2.group());
            System.out.println("match 2.");
            return true;
        }
        Pattern p3 = Pattern.compile("dup(?:licated?)?:?[ ]{1,}");
        Matcher m3 = p3.matcher(comment);
        if (m3.find()) {
            System.out.println(m3.group());
            System.out.println("match 3.");
            return true;
        }
        return false;
    }


    public ArrayList<String> getExternalShaList(List<String> shaList, String projectURL, int num_latestCommit) {
        ArrayList<String> externalCommitList = new ArrayList<>();

        QueryDataFromGithubAPI queryDataFromGithubAPI = new QueryDataFromGithubAPI();

        QueryDatabase qdb = new QueryDatabase();
        List<String> coreTeamList = qdb.getCoreTeamList(projectURL);

        for (String sha : shaList) {
            String loginID = queryDataFromGithubAPI.getGithubLoginID(sha, projectURL);
            if (loginID.equals("authorIsNull")) {
                externalCommitList.add(sha);
                continue;
            }
            if (!coreTeamList.contains(loginID)) {
                if (!loginID.contains("bot")) {
                    externalCommitList.add(sha);
                } else {
                    System.out.println(loginID + " is bot ... ");
                }
            } else {
                System.out.println(projectURL + " " + loginID + " is core ");
            }

            if (externalCommitList.size() > num_latestCommit) {
                return externalCommitList;
            }
        }
        return externalCommitList;
    }

    public boolean isForkAndUpstream(String forkUrl, String upstreamUrl) {
        System.out.println("check if it is fork and upstream..");
        JsonUtility jsonUtility = new JsonUtility();
        ArrayList<String> fork_info_json = null;
        try {
            fork_info_json = jsonUtility.readUrl(forkUrl + "?access_token=" + token);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (fork_info_json.size() > 0) {
            JSONObject fork_jsonObj = new JSONObject(fork_info_json.get(0));

            if (!fork_jsonObj.has("parent")) {
                System.out.println("no parent");
                return false;
            } else {
                JSONObject parentOBJ = (JSONObject) fork_jsonObj.get("parent");
                String parent_url = (String) parentOBJ.get("url");
                if (upstreamUrl.equals(parent_url)) {
                    return true;
                }
            }
        }

        return false;


    }


    public String getUpstream(String repoUrl) {
        JsonUtility jsonUtility = new JsonUtility();
        ArrayList<String> repo_info_json = null;

        try {
            repo_info_json = jsonUtility.readUrl(repoUrl + "?access_token=" + token);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (repo_info_json.size() == 0) {
            return "";
        }
        if (repo_info_json.size() > 0) {
            JSONObject repo_jsonObj = new JSONObject(repo_info_json.get(0));

            if (!repo_jsonObj.has("parent")) {
                System.out.println("no parent");

            } else {
                JSONObject parentOBJ = (JSONObject) repo_jsonObj.get("parent");
                String parent_url = (String) parentOBJ.get("url");
                return parent_url;
            }
        }
        return repoUrl;
    }


}




