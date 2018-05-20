import java.io.IOException;

public class AnalyzeRepository {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd,token;
    static String myDriver = "com.mysql.jdbc.Driver";
    final int batchSize = 100;


    AnalyzeRepository() {
        IO_Process io = new IO_Process();
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
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


    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        String[] projectList = null;
        current_dir = System.getProperty("user.dir");
        try {
            projectList = io.readResult(current_dir + "/input/repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String projectUrl : projectList) {
            GithubRepository repo = new GithubRepository().getForkInfo(projectUrl,"");
//            io.inserRepoToDB(repo);


        }
    }
}
