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
import java.nio.file.Paths;
import java.sql.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by shuruiz on 10/19/17.
 */
public class IO_Process {
    static String current_OS = System.getProperty("os.name").toLowerCase();
    String token;
    String dir, user, pwd, myUrl;
    static String github_api_repo = "https://api.github.com/repos/";
    String current_dir = System.getProperty("user.dir");

    public IO_Process() {

        try {
            String[] paramList = readResult(current_dir + "/input/dir-param.txt").split("\n");
            dir = paramList[0];
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


    ArrayList<String> getForkListFromRepoTable(String repoUrl) {
        ArrayList<String> forkList = new ArrayList<>();

        try {

            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
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


    public ArrayList<String> getProjectURL() {
        ArrayList<String> repoList = new ArrayList<>();

        try {

            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            PreparedStatement preparedStmt;

            String selectRepoID = "SELECT id FROM fork.repository WHERE isFork = FALSE ";
            preparedStmt = conn.prepareStatement(selectRepoID);
            ResultSet rs = preparedStmt.executeQuery();

            if (rs.next()) {
                repoList.add(String.valueOf(rs.getInt("id")));
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return repoList;

    }

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
                    builder.append("aa: " + line);
                    System.out.println(line);
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


    public String removeBrackets(String str) {
        return str.replace("\n", "").replace("[", "").replace("]", "");
    }

    public void deleteDir(File file) throws IOException {
        FileUtils.deleteDirectory(file);
    }


    public List<List<String>> readCSV(String filePath) {
        List<List<String>> values = null;
        try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
            values = lines.map(line -> Arrays.asList(line.split(","))).collect(Collectors.toList());
            values.forEach(value -> System.out.println(value));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return values;
    }


    public int getRepoId(String repoURL) {
        int repoID = -1;
        try {

            Connection conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            PreparedStatement preparedStmt;

            String selectRepoID = "SELECT id FROM fork.repository WHERE repoURL = ?";
            preparedStmt = conn.prepareStatement(selectRepoID);
            preparedStmt.setString(1, repoURL);
            ResultSet rs = preparedStmt.executeQuery();

            if (rs.next()) {
                repoID = rs.getInt("id");
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (repoID == -1) {
            insertRepo(repoURL);
        }

        return repoID;

    }

    private void insertRepo(String repoUrl) {
        GithubApiParser githubApiParser = new GithubApiParser();
        System.out.println("get fork info: " + repoUrl);
        String repoInfo = githubApiParser.getForkInfo(repoUrl).toString();
        Connection conn = null;
        String query = "  INSERT INTO repository ( repoURL,loginID,repoName,isFork,UpstreamURL,belongToRepo,upstreamID,projectID," +
                "num_of_forks, created_at,pushed_at, size,language,ownerID,public_repos," +
                "public_gists ,followers  , following  ,user_type  )" +
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String upstreamURL = "";
        int upstreamID = -1;

        try {
            conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            PreparedStatement preparedStmt = conn.prepareStatement(query);
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
//                repoURL
                preparedStmt.setString(1, repoUrl);
                // loginID
                preparedStmt.setString(2, String.valueOf(owner_jsonObj.get("login")));
                // repoName
                preparedStmt.setString(3, repoUrl.split("/")[1]);
                // isFork
                preparedStmt.setBoolean(4, isFork);
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
                System.out.println("insert " + preparedStmt.executeUpdate() + " repo");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }

    public void executeQuery(PreparedStatement preparedStmt) throws SQLException {
        long start = System.nanoTime();
        int[] numUpdates = preparedStmt.executeBatch();
        for (int i = 0; i < numUpdates.length; i++) {
            if (numUpdates[i] == -2)
                System.out.println("Execution " + i +
                        ": unknown number of rows updated");
            else
                System.out.println("Execution " + i +
                        "successful: " + numUpdates[i] + " rows updated");
        }
        long end = System.nanoTime();
        long used = end - start;
        System.out.println("execute " + numUpdates.length + " query  :" + TimeUnit.NANOSECONDS.toMillis(used) + " ms");
    }

    public int getcommitID(String sha) {
//        long start = System.nanoTime();
        String myUrl, user = "shuruiz";
        if (current_OS.indexOf("mac") >= 0) {
            myUrl = "jdbc:mysql://localhost:3307/fork";
        } else {
            myUrl = "jdbc:mysql://localhost:3306/fork";
        }
        Connection conn;
        try {

            conn = DriverManager.getConnection(myUrl, user, "shuruiz");

            String commitshaID_QUERY = "SELECT id FROM commit WHERE commitSHA = ?";
            PreparedStatement preparedStmt = conn.prepareStatement(commitshaID_QUERY);
            preparedStmt.setString(1, sha);

            ResultSet rs = preparedStmt.executeQuery();
//            rs.setFetchSize(250);
            while (rs.next()) {               // Position the cursor                  4
                int commitshaID = rs.getInt(1);
                if (commitshaID != -1) {
                    conn.close();
//                    long end = System.nanoTime();
//                    System.out.println("get commit id " + TimeUnit.NANOSECONDS.toMillis(end - start) + " ms");

                    return commitshaID;
                }
            }
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public String normalize(String str) {
        str = Normalizer.normalize(str, Normalizer.Form.NFD);
        str = str.replaceAll("[^\\p{ASCII}]", "");
        return str;
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


    static public void main(String[] args) {
        IO_Process io = new IO_Process();
        System.out.print(io.normalize("Michał Putkowski"));
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

    public String getOutputDir(String machineName) {
        String output_dir;
        if (machineName.equals("local")) {
            System.out.println("local machine");
            output_dir = "/Users/shuruiz/Work/ForkData";
        } else if (machineName.equals("featureServer")) {
            System.out.println("feature server");
            output_dir = "/usr0/home/shuruiz/ForkData";
        } else {
            System.out.println("feature 6 or other");
            output_dir = "/home/feature/shuruiz/ForkData";
        }
        return output_dir;
    }

    public String getDBurl(String machineName) {
        String db_url;
        if (machineName.equals("local")) {
            System.out.println("local machine");
            db_url = "jdbc:mysql://localhost:3307/fork";
        } else {
            System.out.println("feature server");
            db_url = "jdbc:mysql://localhost:3306/fork";
        }
        return db_url;
    }
}



