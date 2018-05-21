import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

public class CloneRepo {

    static String working_dir, output_dir;
    static public void main(String[] args) {
        IO_Process io =new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
        } catch (IOException e) {
            e.printStackTrace();
        }

        HashMap<String, HashSet<String>> project_forks =io.getProjectForkMap();
        JgitUtility jg = new JgitUtility();

        project_forks.forEach((k, v) -> {
            jg.cloneRepo_cmd(v, k);
            io.writeTofile(k+"\n",output_dir+"finish_clone.txt");
        });

    }

}
