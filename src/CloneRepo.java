import java.io.File;
import java.io.IOException;
import java.util.HashSet;

public class CloneRepo {

    static String working_dir, output_dir, clone_dir;

    static public void main(String[] args) {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(repos.length + " projects ");
        while (true) {
            for (String projectURL : repos) {
                    HashSet<String> project_forks = io.getProjectForkMap(projectURL);
                    JgitUtility jg = new JgitUtility();
                    jg.cloneRepo_cmd(project_forks, projectURL,false);
                    io.writeTofile(projectURL + "\n", output_dir + "finish_clone.txt");
            }
        }
    }

}
