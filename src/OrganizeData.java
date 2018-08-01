import Util.IO_Process;

import java.io.IOException;

public class OrganizeData {

    static public void main(String[] args) {
        String output_dir = "/Users/shuruiz/Box Sync/ForkData/commitHistory/";
        IO_Process io = new IO_Process();
        String[] repoList = {};
        try {
            repoList = io.readResult("/Users/shuruiz/Box Sync/GithubScript-New/input/repoList.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String repo : repoList) {

            try {
                String[] result = io.readResult(output_dir + repo + "/changedSize_LOC.csv").split("renameFile\n");
                if(result.length>1) {
                    io.writeTofile(result[1], output_dir + "result_all_repo.csv");
                    System.out.println("+"+repo);
                }else{
                    System.out.println("--"+repo);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
