import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by shuruiz on 10/19/17.
 */
public class IO_Process {
    static String current_OS = System.getProperty("os.name").toLowerCase();

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
        String myDriver = "com.mysql.jdbc.Driver";
        String myUrl ,user;
        int repoID = -1;
        if (current_OS.indexOf("mac") >= 0) {
            myUrl = "jdbc:mysql://localhost:3306/Fork";
            user = "root";
        } else {
            myUrl = "jdbc:mysql://localhost:3306/fork";
            user = "shuruiz";
        }
        try {

            Connection    conn = DriverManager.getConnection(myUrl, user, "shuruiz");
            PreparedStatement preparedStmt;

            String selectRepoID = "SELECT id from fork.repository where repoURL = ?";
            preparedStmt = conn.prepareStatement(selectRepoID);
            preparedStmt.setString(1, repoURL);
            ResultSet rs = preparedStmt.executeQuery();

            if (rs.next()) {
                repoID = rs.getInt("id");
            }
//            preparedStmt.close();
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return repoID;

    }
}
