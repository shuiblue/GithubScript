package Commit;

import Util.IO_Process;
import Util.JgitUtility;

import java.io.File;
import java.io.IOException;

public class Main_analyzeCommitFlow {

    public static void main(String[] args) {

        GraphBasedAnalyzer graphBasedAnalyzer = new GraphBasedAnalyzer();

        IO_Process io = new IO_Process();
        String inputPath = graphBasedAnalyzer.current_dir + "/input/hardfork_upstream_pairs.txt";
        try {
            String[] fork_upstream_pairs = io.readResult(inputPath).split("\n");

            for (String str : fork_upstream_pairs) {
                String forkUrl = str.split(",")[0];
                String projectUrl = str.split(",")[1];
                String classifyCommit_file = graphBasedAnalyzer.graph_dir + projectUrl.replace("/", ".") + "_graph_result_allFork.csv";

                /**  by graph  **/
                System.out.println("graph-based...");
                StringBuilder sb_result = new StringBuilder();
                sb_result.append("fork,upstream,only_F,only_U,F->U,U->F\n");
                io.rewriteFile(sb_result.toString(), classifyCommit_file);

                /** clone project **/
                new JgitUtility().clondForkAndUpstream(forkUrl, projectUrl);
                graphBasedAnalyzer.analyzeCommitHistory(forkUrl, projectUrl, false);

                try {
                    io.deleteDir(new File(graphBasedAnalyzer.clone_dir + projectUrl));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
