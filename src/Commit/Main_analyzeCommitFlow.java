package Commit;

import Util.IO_Process;
import Util.JgitUtility;

import java.io.File;
import java.io.IOException;

public class Main_analyzeCommitFlow {
    static boolean A_FORK_OF = false;
    static boolean UNMERGED_COMMITS = true;
    static boolean EXTERNAL_PR = false;
    static boolean CHANGENAME = false;
    static boolean test_temp = false;


    public static void main(String[] args) {

        GraphBasedAnalyzer graphBasedAnalyzer = new GraphBasedAnalyzer();

        IO_Process io = new IO_Process();

        String inputPath = null;
        if (CHANGENAME) {
            inputPath = graphBasedAnalyzer.current_dir + "/input/filtered_LevenBigThan3.txt";
        } else if (test_temp) {
            inputPath = graphBasedAnalyzer.current_dir + "/input/text.txt";
        } else if (A_FORK_OF) {
            inputPath = graphBasedAnalyzer.current_dir + "/input/text.txt";
        } else if (UNMERGED_COMMITS) {
            inputPath = graphBasedAnalyzer.current_dir + "/input/unmergeCommits100.csv";
        } else if (EXTERNAL_PR) {
            inputPath = graphBasedAnalyzer.current_dir + "/input/externalPR_new.csv";
        }
        try {
            String[] fork_upstream_pairs = io.readResult(inputPath).split("\n");

            for (String str : fork_upstream_pairs) {

                String forkUrl = "";
                String projectUrl = "";
                if (CHANGENAME || EXTERNAL_PR) {
                    forkUrl = str.split(",")[1];
                    projectUrl = str.split(",")[0];
                } else if (UNMERGED_COMMITS) {
                    if(str.contains("unmergedCommits")) continue;
                    str = str.replaceAll("https://api.github.com/repos/","");
                    System.out.println(str);
                    forkUrl = str.split("\t")[1];
                    projectUrl = str.split("\t")[2];

                }
                /**  by graph  **/
                System.out.println("graph-based...");

                /** clone project **/
                String clondResult = new JgitUtility().clondForkAndUpstream(forkUrl, projectUrl);
                if (clondResult.equals("clone error")) continue;
                String result = graphBasedAnalyzer.analyzeCommitHistory(forkUrl, projectUrl, false);


                try {
                    io.deleteDir(new File(graphBasedAnalyzer.clone_dir + projectUrl));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (result.equals("403")) {
                    io.writeTofile(forkUrl + "\n", graphBasedAnalyzer.current_dir + "/input/403fork-leven-commitEvol.txt");
                    System.out.println("403 " + forkUrl);
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
