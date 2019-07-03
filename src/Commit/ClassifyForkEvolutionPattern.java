package Commit;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ClassifyForkEvolutionPattern {
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, result_dir;
    static String myUrl, user, pwd;

    ClassifyForkEvolutionPattern() {
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
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
        new ClassifyForkEvolutionPattern();
        IO_Process io = new IO_Process();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");


        try {
            Files.newDirectoryStream(Paths.get(graph_dir)
//            Files.newDirectoryStream(Paths.get(output_dir + "hardfork-exploration/EvolutionGraph")
                    , path -> path.toFile().getPath().endsWith("category.csv"))
                    .forEach(element -> {
                        String url = element.getFileName().toString().replace("_commit_date_category.csv", "");


                        Date forkingPoint = null;
                        Date latestUpstreamCommitDate = null;
                        Date latestForkCommitDate = null;

                        List<List<String>> csvFile = io.readCSV(element.toAbsolutePath().toString());
                        List<Date> onlyF_set = new ArrayList<>();
                        List<Date> onlyU_set = new ArrayList<>();
                        List<Date> F2U_set = new ArrayList<>();
                        List<Date> U2F_set = new ArrayList<>();

                        int numCommitBeforeForking = 0;
                        for (List<String> row : csvFile) {
                            String category = row.get(1);
                            String date = row.get(3);
                            Date commit_date = null;
                            try {
                                commit_date = sdf.parse(date);
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

                            if (category.equals("beforeForking")) {
                                numCommitBeforeForking++;
                                if (forkingPoint == null) {
                                    forkingPoint = commit_date;
                                } else {
                                    if (commit_date.before(forkingPoint)) {
                                        forkingPoint = commit_date;
                                    }
                                }
                            } else if (category.equals("OnlyF")) {
                                if (latestForkCommitDate == null) {
                                    latestForkCommitDate = commit_date;
                                } else {
                                    if (commit_date.after(latestForkCommitDate)) {
                                        latestForkCommitDate = commit_date;
                                    }
                                }
                                onlyF_set.add(commit_date);
                            } else if (category.equals("OnlyU")) {
                                if (latestUpstreamCommitDate == null) {
                                    latestUpstreamCommitDate = commit_date;
                                } else {
                                    if (commit_date.after(latestUpstreamCommitDate)) {
                                        latestUpstreamCommitDate = commit_date;
                                    }
                                }
                                onlyU_set.add(commit_date);
                            } else if (category.equals("F2U")) {
                                if (latestForkCommitDate == null) {
                                    latestForkCommitDate = commit_date;
                                } else {
                                    if (commit_date.after(latestForkCommitDate)) {
                                        latestForkCommitDate = commit_date;
                                    }
                                }
                                F2U_set.add(commit_date);
                            } else if (category.equals("U2F")) {
                                if (latestUpstreamCommitDate == null) {
                                    latestUpstreamCommitDate = commit_date;
                                } else {
                                    if (commit_date.after(latestUpstreamCommitDate)) {
                                        latestUpstreamCommitDate = commit_date;
                                    }
                                }
                                U2F_set.add(commit_date);
                            }
                        }
                        if(latestForkCommitDate==null ||latestUpstreamCommitDate ==null ||forkingPoint ==null) {
                            return;
                        }
                        System.out.println(url + " forkingPoint:" + forkingPoint);
                        System.out.println(latestForkCommitDate);
                        System.out.println(latestUpstreamCommitDate);

                        long diff = TimeUnit.DAYS.convert(Math.abs(latestForkCommitDate.getTime() - latestUpstreamCommitDate.getTime()), TimeUnit.MILLISECONDS);


                        int num_forkTotal = onlyF_set.size() + F2U_set.size();
                        int num_UpstreamTotal = onlyU_set.size() + U2F_set.size();


                        int num_upstreamCommits_afterForkingPoint = 0;
                        for (Date d : onlyU_set) {
                            if (d.after(forkingPoint)) {
                                num_upstreamCommits_afterForkingPoint++;
                            }
                        }
                        for (Date d : U2F_set) {
                            if (d.after(forkingPoint)) {
                                num_upstreamCommits_afterForkingPoint++;
                            }
                        }
//
//                        System.out.println("");
                        io.writeTofile(url + "," + numCommitBeforeForking + "," + onlyF_set.size() + "," + num_upstreamCommits_afterForkingPoint + "," +
                                F2U_set.size() + "," + U2F_set.size() + "," + latestForkCommitDate + "," + latestUpstreamCommitDate + "," + diff / 365 + "\n",
                                output_dir+"classifyFork.csv");

                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
