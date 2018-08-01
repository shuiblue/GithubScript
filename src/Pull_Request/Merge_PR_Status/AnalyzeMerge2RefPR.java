package Pull_Request.Merge_PR_Status;

import Util.IO_Process;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AnalyzeMerge2RefPR {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir;
    static String myUrl, user, pwd;

    AnalyzeMerge2RefPR() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            timeline_dir = output_dir + "shurui_timeline.cache/";
//            timeline_dir = output_dir + "shurui_crossRef.cache/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new AnalyzeMerge2RefPR();
        GetMergedPR getMergedPR = new GetMergedPR();
        IO_Process io = new IO_Process();
        List<String> PR_list = io.getList_byFilePath(output_dir + "referenceCommit0729.txt");
        String previousRepo = "";
        for (String line : PR_list) {
            String[] arr = line.split(",\\[");
            String projectURL = arr[0].split(",")[0];
            if (!projectURL.equals(previousRepo)) {
                if (!previousRepo.equals("")) {
                    try {
                        io.deleteDir(new File(clone_dir + previousRepo));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                previousRepo = projectURL;
            }
            int prID = Integer.parseInt(arr[0].split(",")[1]);

            String[] referencedSHA = io.removeBrackets(arr[1]).split(",");
            for (String refSHA : referencedSHA) {
                if (getMergedPR.commitIsMerged(refSHA, projectURL)) {
                    io.writeTofile(projectURL + "," + prID + "," + refSHA + "\n", output_dir + "mergedSHA_referenced.txt");
                    System.out.println(projectURL + "," + prID + "," + refSHA);
                }
            }

        }

    }
}
