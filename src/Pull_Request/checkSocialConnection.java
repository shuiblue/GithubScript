package Pull_Request;

import Util.IO_Process;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class checkSocialConnection {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    static IO_Process io = new IO_Process();

    checkSocialConnection() {
        current_dir = System.getProperty("user.dir");
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
        AnalyzedPRHotness analyzedPRHotness = new AnalyzedPRHotness();
        List<String> repos = io.getList_byFilePath(current_dir + "/input/file_repoList.txt");
        System.out.println(repos.size() + " projects ");

        for (String projectURL : repos) {
            int projectID = io.getRepoId(projectURL);
            System.out.println("projectID: " + projectID + ", "+projectURL);
            System.out.println("get all PR for " + projectURL);
            HashMap<String, HashMap<String, Set<Integer>>>  maintainer_owners = io.getClosedPROwnerMaintainer(projectID);

            maintainer_owners.forEach((maintainer,ownerSet)->{
                Set<String> follower = io.getFollower(maintainer);
            });

        }


    }

}
