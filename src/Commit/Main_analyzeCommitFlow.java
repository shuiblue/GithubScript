package Commit;

import Util.IO_Process;
import Util.JgitUtility;

import java.io.File;
import java.io.IOException;

public class Main_analyzeCommitFlow {
    static boolean A_FORK_OF = false;
    static boolean UNMERGED_COMMITS = false;
    static boolean EXTERNAL_PR = false;
    static boolean CHANGENAME = true;
    static boolean test_temp = false;
    static boolean ONEYEAR = false;


    public static void main(String[] args) {

        GraphBasedAnalyzer graphBasedAnalyzer = new GraphBasedAnalyzer();

        IO_Process io = new IO_Process();
        String criteria ;
        String inputPath = null;
        if (CHANGENAME) {
            inputPath = io.current_dir + "/input/LevenBigThan3_3stars.csv";

        } else if (test_temp) {
            inputPath = io.current_dir + "/input/text.txt";
        } else if (A_FORK_OF) {
            inputPath = io.current_dir + "/input/aforkof_description.txt";

        } else if (UNMERGED_COMMITS) {
            inputPath = io.current_dir + "/input/unmergeCommits100_3star.csv";

        } else if (EXTERNAL_PR) {
            inputPath = io.current_dir + "/input/externalPR_new_3star.csv";

        }else if (ONEYEAR) {
            inputPath = io.current_dir + "/input/1yr_activity_3stars.csv";

        }
        criteria = "AllGroup";
        try {
            String[] fork_upstream_pairs = io.readResult(inputPath).split("\n");

            for (String str : fork_upstream_pairs) {
                if(str.toLowerCase().contains("linux")){
                    System.out.println("skip linux project");
                    continue;
                }

                String forkUrl = "";
                String projectUrl = "";
                str = str.replaceAll("https://api.github.com/repos/", "");
                if (CHANGENAME) {
                    forkUrl = str.split(",")[1];
                    projectUrl = str.split(",")[0];

                } else if (UNMERGED_COMMITS) {
                    if (str.contains("unmergedCommits")) continue;
                    System.out.println(str);
                    forkUrl = str.split("\t")[1];
                    projectUrl = str.split("\t")[2];

                } else if (EXTERNAL_PR) {
                    if (str.contains("num_exteral_contributors")) continue;

                    System.out.println(str);
                    forkUrl = str.split("\t")[0];
                    projectUrl = str.split("\t")[1];

                } else if (test_temp) {
                    forkUrl = str.split(",")[0];
                    projectUrl = str.split(",")[1];
                } else if (A_FORK_OF) {
                    forkUrl = str.split(",")[0];
                    projectUrl = str.split(",")[1];
                } else if (ONEYEAR) {
                    forkUrl = str.split("\t")[0];
                    projectUrl = str.split("\t")[2];
                }
                if(new File(io.graph_dir + criteria + "/" + forkUrl.replace("/", ".") + "_commit_date_category.csv").exists()){
                    System.out.println("analyzed, skip");
                    continue;
                }

                /** clone project **/
                String clondResult = new JgitUtility().clondForkAndUpstream(forkUrl, projectUrl);
                if (clondResult.equals("clone error")) continue;
                String result = graphBasedAnalyzer.analyzeCommitHistory(forkUrl, projectUrl, false, criteria,io.token);


                try {
                    io.deleteDir(new File(io.clone_dir + projectUrl));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (result.equals("403")) {
                    io.writeTofile(forkUrl + "," + projectUrl + "\n", io.current_dir + "/input/403fork-leven-commitEvol.txt");
                    System.out.println("403,,, sleep 5000");
                    Thread.sleep(5000);
                    continue;
                }
                Thread.sleep(100);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
