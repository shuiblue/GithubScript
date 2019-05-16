package Hardfork;

import Util.IO_Process;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class HardforkInfoFromGHTorrent {

    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, PR_ISSUE_dir, timeline_dir, hardfork_dir, checkedCandidates;
    static String myUrl, user, pwd, pwd_ght, url_ght;


    HardforkInfoFromGHTorrent() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            PR_ISSUE_dir = output_dir + "crossRef/";
            timeline_dir = output_dir + "shurui_crossRef.cache_pass/";
            hardfork_dir = output_dir + "/hardfork-exploration/";
            checkedCandidates = hardfork_dir + "checked_repo_pairs.txt";
            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];
            pwd_ght = paramList[4];
            url_ght = paramList[5];

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        HardforkInfoFromGHTorrent hardforkInfoFromGHTorrent = new HardforkInfoFromGHTorrent();
        IO_Process io = new IO_Process();
        LineIterator it = null;
        int count = 0;
        try {
//            it = FileUtils.lineIterator(new File("/usr0/home/shuruiz/hardfork/hardfork.csv"), "UTF-8");
//            it = FileUtils.lineIterator(new File(hardfork_dir+"hardfork-filter.csv"), "UTF-8");
            it = FileUtils.lineIterator(new File(hardfork_dir + "hardfork_fork_origin-complete.csv"), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (it.hasNext()) {
                System.out.println("count: " +count++);
                String line = it.nextLine();
                if (!line.equals("url\turl")) {
                    String[] arr = line.split("\t");
                    String repo1 = arr[0];
                    String repo2 = arr[1];
                    int star_repo1 = hardforkInfoFromGHTorrent.getStar(repo1);
                    int star_repo2 = hardforkInfoFromGHTorrent.getStar(repo2);
                    System.out.println(repo1 + "\n" + repo2);
                    System.out.println(star_repo1 + "   " + star_repo2);
                    if (star_repo1 > 5 && star_repo2 > 5) {

                        io.writeTofile(repo1 + "," + repo2 + "\n", hardfork_dir + "withStarPairs.txt");

                    }
                }
            }
        } finally {
            LineIterator.closeQuietly(it);
        }
    }


    public int getStar(String repoUrl) {
        String query = "select ps.num_stars\n" +
                "  from `ghtorrent-2018-03`.projects p, `ghtorrent-2018-03`.project_stars ps\n" +
                "WHERE p.id = ps.project_id and p.url = '" + repoUrl + "'";
        try (Connection conn = DriverManager.getConnection(url_ght, user, pwd_ght);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
//            System.out.println(query);
            if (rs.next()) {
                Integer result = (Integer) rs.getObject("num_stars");
                if (result == null || result.equals("")) {
                    return 0;
                } else {
                    return result;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

}
