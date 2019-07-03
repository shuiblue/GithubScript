package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class NameChangedHardFork {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, result_dir, hardfork_dir;
    static String myUrl, user, pwd;

    NameChangedHardFork() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            hardfork_dir = output_dir + "hardfork-exploration/";
            result_dir = output_dir + "result0821/";

            clone_dir = output_dir + "clones/";
            graph_dir = output_dir + "ClassifyCommit_new/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new NameChangedHardFork();
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        List<List<String>> forkinfo = io.readCSV(hardfork_dir + "LevenBigThan3.csv");
        int count=0;
        for (List<String> row : forkinfo) {
            String upstreamName = row.get(0);
            String upstreamURL = row.get(1);
            String forkName = row.get(2);
            String forkURL = row.get(3);
            if (!io.isNonDevRepo(upstreamName) && !io.isNonDevRepo(forkName)&&!upstreamURL.equals("")&&!forkURL.equals("")) {
                sb.append(upstreamURL+","+forkURL+"\n");
                count++;
            }
        }
        System.out.println(count);
        io.rewriteFile(sb.toString(),hardfork_dir+"filtered_LevenBigThan3.txt");

    }
}
