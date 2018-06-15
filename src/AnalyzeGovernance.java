import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class AnalyzeGovernance {
    static String working_dir, pr_dir, output_dir, clone_dir;
    static String myUrl, user, pwd;

    AnalyzeGovernance() {
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

    public static void main(String[] args) {
        AnalyzeGovernance ag = new AnalyzeGovernance();
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (String projectUrl : repos) {
            {
                int projectID = io.getRepoId(projectUrl);

                System.out.println(projectUrl);
                ArrayList<String> prList = io.getPRNumlist(projectUrl);
                if (prList.size() == 0) {
                    System.out.println(projectUrl + " pr api result not available");
                    io.writeTofile(projectUrl + "\n", output_dir + "miss_pr_api.txt");
                    continue;
                }


                for (String pr_id : prList) {

                    int prExistIN_PR_issueMap = io.prExistIN_PR_issueMap(pr_id, projectID);

                    if (prExistIN_PR_issueMap == 0) {
                        System.out.println("parsing pr issue link...");
                        String csvFile_dir = output_dir + "shurui.cache/get_pr_comments." + projectUrl.replace("/", ".") + "_" + pr_id + ".csv";
                        String csvFile_dir_alternative = output_dir + "shurui.cache/get_pr_comments." + projectUrl.replace("/", ".") + "_" + pr_id + ".0.csv";
                        boolean csvFileExist = new File(csvFile_dir).exists();
                        boolean csvFileAlter_Exist = new File(csvFile_dir_alternative).exists();

                        if (csvFileAlter_Exist || csvFileExist) {
                            System.out.println("pr# " + pr_id);
                            io.getIssuePRLink(pr_id, projectUrl, projectID, prList, csvFileExist);
                        } else {
                            System.out.println("pr#" + pr_id + " csv not available.");
                            io.writeTofile(pr_id + "," + projectUrl + "\n", output_dir + "missPR_" + projectUrl + ".txt");
                            break;
                        }
                    }else{
                        System.out.println(projectUrl + " exist in pr issue map");
                    }
                }
            }
        }
    }
}
