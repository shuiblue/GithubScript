import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzePRComment {

    static String working_dir, output_dir;
    static String myUrl, user, pwd;

    AnalyzePRComment() {
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

    public static void main(String[] args) {
        IO_Process io = new IO_Process();
        new AnalyzePRComment();
        String current_dir = System.getProperty("user.dir");
        /*** insert repoList to repository table ***/
        String[] repos = new String[0];
        try {
            repos = io.readResult(current_dir + "/input/prComment_repoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        final int[] count = {0};

        /*** insert pr info to  Pull_Request table***/
        for (String projectUrl : repos) {
            int projectID = io.getRepoId(projectUrl);
            System.out.println("start : " + projectUrl);
            if (projectID == -1) {
                System.out.println(projectUrl + "projectID = -1");
            }

            String insert_query_1 = " UPDATE fork.Pull_Request" +
                    " SET dupPR_comment = ? , dup_related_comments = ? " +
                    "WHERE projectID = ? AND pull_request_ID=? ";
            try (Connection conn = DriverManager.getConnection(myUrl, user, pwd);
                 PreparedStatement preparedStmt = conn.prepareStatement(insert_query_1)) {
                conn.setAutoCommit(false);

                Files.newDirectoryStream(Paths.get(output_dir + "shurui.cache/"), path -> path.toFile().isFile())
                        .forEach(file -> {
                                    String file_name = file.getFileName().toString();
                                    if ((file_name.startsWith("get_pr_comments"))
                                            && file_name.contains(projectUrl.replace("/", ".") + "_")) {
                                        System.out.println(file_name);
                                        String[] arr = file_name.split("_");
                                        int pr_id = Integer.parseInt(arr[arr.length - 1].replace(".csv", "").replace(".0", ""));
                                        ArrayList<String> dup_comments = new ArrayList<>();
                                        List<List<String>> rows = io.readCSV(file.toFile());
                                        boolean isDup = false;
                                        for (List<String> r : rows) {
                                            if (!r.get(0).equals("")) {
                                                String comment = r.get(2);
                                                isDup = isDuplicatePR(comment.toLowerCase());
                                                dup_comments.add(comment);
                                                System.out.println(comment);

                                            }
                                        }
                                        if (dup_comments.size() > 0) {
                                            try {
                                                preparedStmt.setBoolean(1, isDup);
                                                preparedStmt.setString(2, io.normalize(dup_comments.toString()));
                                                preparedStmt.setInt(3, projectID);
                                                preparedStmt.setInt(4, pr_id);
                                                preparedStmt.addBatch();
                                                System.out.println("add comments of pr  " + pr_id + "from repo" + projectUrl);

                                                int batchSize = 100;
                                                if (++count[0] % batchSize == 0) {
                                                    System.out.println("update  " + count[0] + "comments from repo" + projectUrl);
                                                    io.executeQuery(preparedStmt);
                                                    conn.commit();
                                                }
                                            } catch (SQLException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                        );
                if (count[0]  > 0) {
                    io.executeQuery(preparedStmt);
                    conn.commit();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }


        }

    }

    private static boolean isDuplicatePR(String comment) {

        Pattern p1 = Pattern.compile("(?:clos(?:e|ed|ing)|dup(?:licated?)?|super(?:c|s)ee?ded?|obsoleted?|replaced?|redundant|better (?:implementation|solution)" +
                "|solved|fixed|done|going|merged|addressed|already|land(?:ed|ing)) (?:by|in|with|as|of|in favor of|at|since|via):? " +
                "(?:#|https://github.com/(?:[\\w\\.-]+/)+(pull|issues))?");
        Matcher m1 = p1.matcher(comment);
        if (m1.find()) {
            return true;
        }
        Pattern p2 = Pattern.compile("(?:#|https://github.com/(?:[\\w\\.-]+/)+(pull|issues))?" +
                "(?:better (?:implementation|solution)|dup(?:licate)?|(?:fixe(?:d|s)|obsolete(?:d|s)|replace(?:d|s))')");
        Matcher m2 = p2.matcher(comment);
        if (m2.find()) {
            return true;
        }
        Pattern p3 = Pattern.compile("dup(?:licated?)?:? (?:#|https://github.com/(?:[\\w\\.-]+/)+(pull|issues))?");
        Matcher m3 = p3.matcher(comment);
        if (m3.find()) {
            return true;
        }
        return false;
    }

}
