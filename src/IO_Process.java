import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
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
        System.out.println(filePath);
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

        String result = "";
        try {
            process.directory(new File(pathname));
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
        System.out.println("delete " + file);
        if (file.isDirectory()) {

            //directory is empty, then delete it
            if (file.list().length == 0) {

                file.delete();
//                System.out.println("Directory is deleted : "
//                        + file.getAbsolutePath());

            } else {

                //list all the directory contents
                String files[] = file.list();

                for (String temp : files) {
                    //construct the file structure
                    File fileDelete = new File(file, temp);
                    if (fileDelete.exists()) {
                        //recursive delete
                        deleteDir(fileDelete);
                    }
                }

                //check the directory again, if empty then delete it
                if (file.list().length == 0) {
                    file.delete();
//                    System.out.println("Directory is deleted : "
//                            + file.getAbsolutePath());
                }
            }

        } else {
            //if file, then delete it
            file.delete();
//            System.out.println("File is deleted : " + file.getAbsolutePath());
        }
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
}
