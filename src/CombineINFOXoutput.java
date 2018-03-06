import java.io.IOException;
import java.util.HashMap;

/**
 * Created by shuruiz on 2/25/18.
 */
public class CombineINFOXoutput {


    public void combineINFOX(String repo) {
        String infox_dir = "/Users/shuruiz/Box Sync/GithubScript-New/infox-output/";
        String input_dir = "/Users/shuruiz/Box Sync/GithubScript-New/result/";
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String[] infoxResult = {};
        String[] forksResult = {};
        try {
            infoxResult = io.readResult(infox_dir + repo + "/infox.csv").split("\n");
            forksResult = io.readResult(input_dir + repo + "/pr_graph_info.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        HashMap<String, String[]> infoxMap = new HashMap<>();
        for (int i = 1; i < infoxResult.length; i++) {
            String[] info = infoxResult[i].split(",");
            String forkName = info[0].replace("\"", "");
            infoxMap.put(forkName, info);
        }

        sb.append(forksResult[0] + "infox_changed_Commits,infox_changed_files,infox_changed_total_loc\n");
        for (int i = 1; i < forksResult.length; i++) {
            String[] fork = forksResult[i].split(",");
            String forkName = fork[1];
            if (infoxMap.get(forkName) != null) {
                String[] infox = infoxMap.get(forkName);
                sb.append(forksResult[i] + "," + infox[1].replace("\"", "") +"," + infox[2].replace("\"", "")+"," + infox[3].replace("\"", "")+"\n");
            }else{
                sb.append(forksResult[i] + ",,,\n");
            }
        }


        io.rewriteFile(sb.toString(),input_dir+repo+"/plusINFOX.csv");
    }

    public static void main(String[] args) {
        CombineINFOXoutput combineINFOXoutput = new CombineINFOXoutput();
        String repoURL = "gitlabhq/gitlabhq";
        combineINFOXoutput.combineINFOX(repoURL);

    }


}
