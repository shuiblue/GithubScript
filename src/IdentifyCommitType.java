import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Created by shuruiz on 12/1/17.
 */
public class IdentifyCommitType {
    static String github_api_repo = "https://api.github.com/repos/";
    static String localDirPath = "/Users/shuruiz/Box Sync/GithubScript-New/input/";
    static String tmpDirPath = "/Users/shuruiz/Box Sync/GithubScript-New/cloneRepos/";
    static String result_dir = "/Users/shuruiz/Box Sync/GithubScript-New/result/";
    public static void main(String[] args) {
        ClassifyCommit cc = new ClassifyCommit();
        IdentifyCommitType identifyCommitType = new IdentifyCommitType();
        IO_Process io = new IO_Process();
        String[] repoList = {};
        /** get repo list **/
        try {
            repoList = io.readResult(localDirPath + "repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String repoUrl : repoList) {
            if (repoUrl.trim().length() > 0) {
                String repoName = repoUrl.split("/")[0];
//                /** get active fork list for given repository **/
//                System.out.println("get all active forks...");
//                String all_activeForkList = cc.getActiveForkList(repoUrl);
//
//                if (all_activeForkList.length() <= maxAnalyzedForkNum) {
//                    io.rewriteFile(all_activeForkList, result_dir + repoUrl + "/ActiveForklist.txt");
//                } else {
//                    io.rewriteFile(all_activeForkList, result_dir + repoUrl + "/all_ActiveForklist.txt");
//                    System.out.println("randomly pick " + randomAnalyzedForkNum + " active forks...");
//                    cc.getRamdomForks(repoUrl, randomAnalyzedForkNum);
//                }

                /** analyze commit history **/
                String[] activeForkList = {};
                try {
                    activeForkList = io.readResult(result_dir + repoUrl + "/ActiveForklist.txt").split("\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                for (String forkInfo : activeForkList) {
                    identifyCommitType.analyzeCommitHistory(forkInfo, true);
                }
            }

        }

    }

    public void analyzeCommitHistory(String forkInfo, boolean compareForkWithUpstream) {
        HashSet<String> fork2Upstream = new HashSet<>();
        HashSet<String> upstream2Fork = new HashSet<>();

        JgitUtility jg = new JgitUtility();
        ClassifyCommit cc = new ClassifyCommit();
        String forkUrl = forkInfo.split(",")[0];
        String forkName = forkUrl.split("/")[0];
        String upstreamUrl = forkInfo.split(",")[1];

        /**clone fork to local*/
        System.out.println("git clone " + forkUrl);
        jg.cloneRepo(forkUrl);

        if (compareForkWithUpstream) {
            System.out.println("git clone " + upstreamUrl);
            jg.cloneRepo(upstreamUrl);
        }

        /** get commit in branch**/
        System.out.println("getting Commits In all Branches of fork: " + forkUrl);
        cc.getCommitInBranch(forkUrl);
        if (compareForkWithUpstream) {
            System.out.println("getting Commits In all Branches of upstream: " + upstreamUrl);
            cc.getCommitInBranch(upstreamUrl);
        }


        IO_Process io = new IO_Process();
        String[] fork_branchArray = {};
        String[] upstream_branchArray = {};
        try {
            System.out.println("getting fork branch commit list..");
            fork_branchArray = io.readResult(tmpDirPath + forkUrl + "/" + forkUrl.split("/")[0] + "_branch_commitList.txt").split("\n");
            if (compareForkWithUpstream) {
                System.out.println("getting upstream branch commit list..");
                upstream_branchArray = io.readResult(tmpDirPath + upstreamUrl + "/" + upstreamUrl.split("/")[0] + "_branch_commitList.txt").split("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }



        HashSet<String> copy_fork2Up = new HashSet<>();
        copy_fork2Up.addAll(fork2Upstream);
        for (String commit : copy_fork2Up) {
            if (upstream2Fork.contains(commit) && fork2Upstream.contains(commit)) {
                upstream2Fork.remove(commit);
                fork2Upstream.remove(commit);
            }
        }



        /** remove local copy of fork**/
        String localDirPath = "/Users/shuruiz/Box Sync/GithubScript-New/cloneRepos/";
        try {
            if (compareForkWithUpstream) {
                io.deleteDir(new File(localDirPath + upstreamUrl));
            }
            io.deleteDir(new File(localDirPath + forkUrl));
        } catch (IOException e) {
            e.printStackTrace();
        }


    }



}
