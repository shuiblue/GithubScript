import java.io.IOException;
import java.util.ArrayList;

public class AnalyzeCommitChangedFile {
    static String working_dir, pr_dir, output_dir, clone_dir;
    static String myUrl, user, pwd;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;

    AnalyzeCommitChangedFile() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static public void main(String[] args) {
        AnalyzeCommitChangedFile accf = new AnalyzeCommitChangedFile();
        IO_Process io = new IO_Process();
        ArrayList<Integer> todo_commits = io.get_un_analyzedCommit();
        for (Integer commit : todo_commits) {

        }
    }


}
