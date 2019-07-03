package Commit;

import Util.IO_Process;
import Util.JgitUtility;

import java.io.File;
import java.io.IOException;

public class Main_analyzeCommitFlow {

    public static void main(String[] args) {

        GraphBasedAnalyzer graphBasedAnalyzer = new GraphBasedAnalyzer();

        IO_Process io = new IO_Process();
//        String inputPath = graphBasedAnalyzer.current_dir + "/input/filtered_LevenBigThan3.txt";
        String inputPath = graphBasedAnalyzer.current_dir + "/input/text.txt";
        try {
            String[] fork_upstream_pairs = io.readResult(inputPath).split("\n");

            for (String str : fork_upstream_pairs) {
                String forkUrl = str.split(",")[1];
                String projectUrl = str.split(",")[0];

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
                    System.out.println("403 " + forkUrl);
                    continue;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
