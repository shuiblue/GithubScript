import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 2/12/18.
 */
public class AnalyzingPRs {

    static String working_dir, output_dir;
    static String myUrl, user, pwd;
    final int batchSize = 100;

    AnalyzingPRs() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) throws IOException {
        IO_Process io = new IO_Process();
        AnalyzingPRs analyzingPRs = new AnalyzingPRs();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }


        String missPRFile = "miss_pr_api_pr7.txt";
        while (true) {
            System.out.println("new loop .....");
            io.rewriteFile("", output_dir + missPRFile);

            /*** insert pr info to  Pull_Request table***/
            for (String projectUrl : repos) {
                System.out.println(projectUrl);

                ArrayList<String> prList = io.getPRNumlist(projectUrl);
                if (prList.size() == 0) {
                    System.out.println(projectUrl + " pr api result not available");
                    io.writeTofile(projectUrl + "\n", output_dir + missPRFile);
                    continue;
                }

                int projectID = io.getRepoId(projectUrl);
                HashSet<String> analyzedPR = io.getPRinDataBase(projectID);

                prList.removeAll(analyzedPR);

                if (prList.size() == 0) {
                    System.out.println(projectUrl + " pr analysis  done :)");
                    continue;
                }
                System.out.println("start : " + projectUrl);
                io.getPRfiles(projectUrl, projectID, prList);
            }
        }
    }


}
