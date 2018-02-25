import java.io.File;
import java.io.IOException;

/**
 * Created by shuruiz on 2/20/18.
 */
public class copyPRresult {
static String dir ="/Users/shuruiz/Box Sync/queryGithub/";
    public static void main(String[] args) {
        IO_Process io = new IO_Process();

        io.rewriteFile("", "/Users/shuruiz/Box Sync/queryGithub/allPR.csv");
        File[] files = new File(dir).listFiles();
        showFiles(files);

    }

    public static void showFiles(File[] files) {
        IO_Process io = new IO_Process();
        for (File file : files) {
            if (file.isDirectory()) {
                System.out.println("Directory: " + file.getName());
                showFiles(file.listFiles()); // Calls same method again.
            } else {
                System.out.println("File: " + file.getName());

                if (file.getName().equals("forkPR_summary.csv") ){

                    String repo =file.getPath().replace(dir,"").replace("forkPR_summary.csv","").replaceAll("/$","");
                    try {
                       String[] test= io.readResult(file.getPath()).split("\n");
                       for(String t:test) {
                           io.writeTofile(repo+","+t+"\n", "/Users/shuruiz/Box Sync/queryGithub/allPR.csv");
                       }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
