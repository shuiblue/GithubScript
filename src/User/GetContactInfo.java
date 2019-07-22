package User;

import Util.GithubApiParser;
import Util.IO_Process;

import java.io.IOException;

public class GetContactInfo {


    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, apiResult_dir;
    static String myUrl, user, pwd;

    GetContactInfo() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            apiResult_dir = output_dir + "shurui_timeline.cache/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        String[] forkurl = null;
        try {
            forkurl = new IO_Process().readResult("./input/hardforkList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        GithubApiParser parser = new GithubApiParser();
        for (String fork : forkurl) {
            parser.getUserInfo(fork);
        }
    }

}
