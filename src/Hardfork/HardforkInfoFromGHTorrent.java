package Hardfork;

import Util.IO_Process;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class HardforkInfoFromGHTorrent {

    static IO_Process io = new IO_Process();

    public static void insertStarInfO(String[] args) {
        System.out.println("start");
        HardforkInfoFromGHTorrent hardforkInfoFromGHTorrent = new HardforkInfoFromGHTorrent();

        LineIterator it = null;
        int count = 0;
        String hardfork_dir = io.hardfork_dir;
        try {
            System.out.println(hardfork_dir + "hardforkList.txt");
            it = FileUtils.lineIterator(new File(hardfork_dir + "hardforkList.txt"), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (it.hasNext()) {
                System.out.println("count: " + count++);
                String line = it.nextLine();
                if (!line.equals("url\turl")) {
                    String[] arr = line.split(",");
                    String repo1 = arr[0];
                    String repo2 = arr[1];
                    int star_repo1 = hardforkInfoFromGHTorrent.getStar(repo1);
                    int star_repo2 = hardforkInfoFromGHTorrent.getStar(repo2);
                    System.out.println(repo1 + "\n" + repo2);
                    System.out.println(star_repo1 + "   " + star_repo2);


                    io.writeTofile(repo1 + "," + repo2 + "," + star_repo1 + "," + star_repo2 + "\n", hardfork_dir + "hardfork_Star.txt");

                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            LineIterator.closeQuietly(it);
        }
    }

    public static void main(String[] args) {
        System.out.println("start");
        HardforkInfoFromGHTorrent hardforkInfoFromGHTorrent = new HardforkInfoFromGHTorrent();

        LineIterator it = null;
        int count = 0;
        String hardfork_dir = io.hardfork_dir;
        try {
            System.out.println(hardfork_dir + "updateAt_fork_upstream.csv");
            it = FileUtils.lineIterator(new File(hardfork_dir + "updateAt_fork_upstream.csv"), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (it.hasNext()) {
                System.out.println("count: " + count++);
                String line = it.nextLine();
                if (!line.contains("hardfork_url")) {
                    String[] arr = line.split(",");
                    String repo1 = arr[0];
                    String repo2 = arr[1];
                    Timestamp date_repo1 = hardforkInfoFromGHTorrent.getUpdatedAt(repo1);
                    Timestamp date_repo2 = hardforkInfoFromGHTorrent.getUpdatedAt(repo2);
                    String date1 = "";
                    String date2 = "";

                    if (date_repo1 != null) {
                        date1 = date_repo1.toString();
                    }
                    if (date_repo2 != null) {
                        date2 = date_repo2.toString();
                    }


                    System.out.println(repo1 + "\n" + repo2);
                    System.out.println(date1 + "   " + date2);


                    io.writeTofile(repo1 + "," + repo2 + "," + date1 + "," + date2 + "\n", hardfork_dir + "updatedAt_0821.txt");

                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            LineIterator.closeQuietly(it);
        }
    }

    public static void insertLanguageInfo(String[] args) {
        System.out.println("start");
        HardforkInfoFromGHTorrent hardforkInfoFromGHTorrent = new HardforkInfoFromGHTorrent();

        LineIterator it = null;
        int count = 0;
        String hardfork_dir = io.hardfork_dir;
        try {
            System.out.println(hardfork_dir + "upstream_list_language.csv");
            it = FileUtils.lineIterator(new File(hardfork_dir + "inputData/upstream_list_language.csv"), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            while (it.hasNext()) {
                System.out.println("count: " + count++);
                String line = it.nextLine();
                if (!line.contains("upstream_url")) {
                    String repo1 = line;
                    String language = hardforkInfoFromGHTorrent.getLanguage(repo1);
                    System.out.println(repo1 + "，" + language);
                    io.writeTofile(repo1 + "," + language + "\n", hardfork_dir + "upstream_language.txt");
                }
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        } finally {
            LineIterator.closeQuietly(it);
        }
    }


    public String getLanguage(String repoUrl) {
        String query = "select p.language\n" +
                "  from `ghtorrent-2018-03`.projects p \n" +
                "WHERE p.url = 'https://api.github.com/repos/" + repoUrl + "'";
        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
//            System.out.println(query);
            if (rs.next()) {
                String result = (String) rs.getObject("language");
                if (result == null || result.equals("")) {
                    return "";
                } else {
                    return result;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public Timestamp getUpdatedAt(String repoUrl) {
        String query = "select p.updated_at\n" +
                "  from `ghtorrent-2018-03`.projects p \n" +
                "WHERE p.url = 'https://api.github.com/repos/" + repoUrl + "'";
        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
             PreparedStatement preparedStmt = conn.prepareStatement(query)) {

            ResultSet rs = preparedStmt.executeQuery();
//            System.out.println(query);
            if (rs.next()) {
                Timestamp result = (Timestamp) rs.getObject("updated_at");
                if (result == null || result.equals("")) {
                    return null;
                } else {
                    return result;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public int getStar(String repoUrl) {
        String query = "select ps.num_stars\n" +
                "  from `ghtorrent-2018-03`.projects p, `ghtorrent-2018-03`.project_stars ps\n" +
                "WHERE p.id = ps.project_id and p.url = 'https://api.github.com/repos/" + repoUrl + "'";
        try (Connection conn = DriverManager.getConnection(io.ghtUrl, io.user, io.ghtpwd);
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
