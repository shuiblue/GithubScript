import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Created by shuruiz on 2/12/18.
 */
public class AnalyzingPRs {

    String dir = "/Users/shuruiz/Box Sync/queryGithub/";

    public HashMap<String, String> getPRMap(String repoUrl) {
        String[] pr_json = {};
        IO_Process io = new IO_Process();

        try {
            pr_json = io.readResult(dir + repoUrl + "/pr.txt").split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        HashMap<String, Fork> forkPR_map = new HashMap<>();
        HashMap<String, String> forkPRstring_map = new HashMap<>();
        HashMap<String, Integer> allPr_map = new HashMap<>();
        HashMap<String, Integer> mergedPr_map = new HashMap<>();
        HashMap<String, Integer> rejectedPr_map = new HashMap<>();
        HashSet<Fork> forkSet = new HashSet<>();
        int i =1;
        for (String json_string : pr_json) {
            System.out.println(i++);
            JSONObject pr_info = new JSONObject(json_string.substring(1, json_string.lastIndexOf("]")));

            String forkName = (String) pr_info.get("forkName");


            String author = pr_info.get("author").toString();
            String created_at = pr_info.get("created_at").toString();
            String closed_at = pr_info.get("closed_at").toString();
            String closed = pr_info.get("closed").toString();
            String merged = pr_info.get("merged").toString();


            PullRequest pr = new PullRequest(forkName, author, created_at, closed_at, closed, merged);

            HashSet<String> commitSet = new HashSet<>();
            String[] commitArray = (io.removeBrackets(pr_info.get("commit_list").toString().replaceAll("\"", ""))).split(",");
            for (String commit : commitArray) {
                if (!commit.equals("")) {
                    commitSet.add(commit);
                }
            }

            Fork fork;
            if (forkPR_map.get(forkName) == null) {
                fork = new Fork(forkName);
            } else {
                fork = forkPR_map.get(forkName);
            }

            HashSet<String> mergedCommit = new HashSet<>();
            HashSet<String> rejectedCommit = new HashSet<>();
            HashSet<String> pendingCommit = new HashSet<>();
            if (merged.equals("true")) {

                int mergePr = 0;
                if (mergedPr_map.get(forkName) != null) {
                    mergePr = mergedPr_map.get(forkName);
                    mergedCommit = fork.getMergedCommits();
                }
                mergedCommit.addAll(commitSet);
                fork.setMergedCommits(mergedCommit);
                mergedPr_map.put(forkName, mergePr + 1);

            } else if (merged.equals("false") && closed.equals("true")) {

                int rejectPr = 0;
                if (rejectedPr_map.get(forkName) != null) {
                    rejectPr = rejectedPr_map.get(forkName);
                    rejectedCommit = fork.getRejectedCommits();
                }
                rejectedCommit.addAll(commitSet);
                fork.setRejectedCommits(rejectedCommit);
                rejectedPr_map.put(forkName, rejectPr + 1);
            } else {
                pendingCommit.addAll(commitSet);
                fork.setPendingCommits(pendingCommit);
            }


            pr.setCommitSet(commitSet);
            forkPR_map.put(forkName, fork);
            fork.getAll_PR_commits().addAll(commitSet);
            int numberOfPr = 0;
            if (allPr_map.get(forkName) != null) {
                numberOfPr = allPr_map.get(forkName);
            } else {
                forkSet.add(fork);
            }
            allPr_map.put(forkName, numberOfPr + 1);
        }
        StringBuilder sb = new StringBuilder();
        /** forkname,allPR,mergedPR,rejectedPR,#allPRcommit,#mergedPRcommits,#rejectedPRcommits ,allPRcommit,mergedPRcommits,rejectedPRcommits **/
        for (Fork f : forkSet) {
            String forkName = f.getForkName();
            int mergedPR = mergedPr_map.get(forkName) == null ? 0 : mergedPr_map.get(forkName);
            int rejectedPR = rejectedPr_map.get(forkName) == null ? 0 : rejectedPr_map.get(forkName);
            HashSet<String> mergedCommits = f.getMergedCommits();
            HashSet<String> rejectedCommits = f.getRejectedCommits();
            rejectedCommits.removeAll(mergedCommits);

            sb.append(forkName + "," + allPr_map.get(forkName) + "," + mergedPR + "," + rejectedPR + ","
                    + f.getAll_PR_commits().size() + ","
                    + mergedCommits.size() + ","
                    + rejectedCommits.size() + ","
                    + f.getAll_PR_commits().toString().replace(",", "/") + ","
                    + mergedCommits.toString().replace(",", "/") + ","
                    + rejectedCommits.toString().replace(",", "/") + "\n");

            forkPRstring_map.put(forkName, forkName + "," + allPr_map.get(forkName) + "," + mergedPR + "," + rejectedPR + ","
                    + f.getAll_PR_commits().size() + ","
                    + mergedCommits.size() + ","
                    + rejectedCommits.size() + ","
                    + f.getAll_PR_commits().toString().replace(",", "/") + ","
                    + mergedCommits.toString().replace(",", "/") + ","
                    + rejectedCommits.toString().replace(",", "/") + "\n");
        }

        io.rewriteFile(sb.toString(), dir + repoUrl + "/forkPR_summary.csv");

        return forkPRstring_map;
    }



    public void combineGraphResult(String repoURL, HashMap<String, String> forkPR_map) {
        String current_dir = System.getProperty("user.dir");
        String resultPath = current_dir + "/result/" + repoURL + "/graph_result.txt";
        String graph_info_csv_Path = current_dir + "/result/" + repoURL + "/graph_info.csv";
        IO_Process io = new IO_Process();


        StringBuilder sb = new StringBuilder();
        sb.append("repo,fork,upstream,only_F,only_U,F2U,U2F,"
                + "fork_num,created_at,pushed_at,size,language,ownerID,public_repos,public_gists,followers,following,sign_up_time,user_type,fork_age,lastCommit_age,"
                + "allPR,mergedPR,rejectedPR,num_allPRcommit, num_mergedPRcommits, num_rejectedPRcommits,allPRcommit,mergedPRcommits,rejectedPRcommits,"
                + "final_OnlyF,final_F2U,num_mergedCommitsNotThroughPR,OnlyF_list,F2U_list,mergedCommitsNotThroughPR_list"
                + "\n");

        String[] graphResult = {};
        String[] graph_info_csv = {};
        try {
            graphResult = io.readResult(resultPath).split("\n");
            graph_info_csv = io.readResult(graph_info_csv_Path).split("\n");

        } catch (IOException e) {
            e.printStackTrace();
        }

        HashMap<String, String> graph_info_map = new HashMap<>();
        for (String fork : graph_info_csv) {
            String info_fork_name = fork.split(",")[1].split("/")[0];
            graph_info_map.put(info_fork_name, fork);
        }


        for (int i = 1; i < graphResult.length; i++) {
            String forkResult = graphResult[i];
            String result[] = forkResult.split(",");
            String forkName = result[0].split("/")[0];

            System.out.println(forkName);
            List<String> OnlyF_list = Arrays.asList(io.removeBrackets(result[6]).split("/ "));
            List<String> F2U_list = Arrays.asList(io.removeBrackets(result[8]).split("/ "));
            HashSet<String> OnlyF = new HashSet<>();
            for(String c:OnlyF_list){
                if(!c.trim().equals("")){
                    OnlyF.add(c);
                }
            }

            HashSet<String> F2U = new HashSet<>();
            for(String c:F2U_list){
                if(!c.trim().equals("")){
                    F2U.add(c);
                }
            }

            String forkPRinfo;
            if (graph_info_map.get(forkName) != null) {
                if (forkPR_map.get(forkName) == null) {

                    /** forkname,allPR,mergedPR,rejectedPR,#allPRcommit, #mergedPRcommits, #rejectedPRcommits, allPRcommit,  mergedPRcommits,  rejectedPRcommits **/
                    sb.append(graph_info_map.get(forkName) + ",0,0,0,0,0,0,[],[],[],"+OnlyF.size()+","+F2U.size()+",0,"+
                            OnlyF.toString().replace(",", "/")+"," + F2U.toString().replace(",", "/")+",[]\n");

                } else {

                    forkPRinfo = forkPR_map.get(forkName);
                    String[] prResult = forkPRinfo.split(",");
                    List<String> mergedPRCommits = Arrays.asList(io.removeBrackets(prResult[8].trim()).split("/ "));
                    List<String> rejectedPRCommits_list = Arrays.asList(io.removeBrackets(prResult[9].trim()).split("/ "));
                    HashSet<String> rejectedPRCommits = new HashSet<>();
                    rejectedPRCommits.addAll(rejectedPRCommits_list);

                    HashSet<String> mergeThroughOtherMechanisum = new HashSet();

                    for (String c : F2U) {
                        if (!c.trim().equals("")) {
                            if (!mergedPRCommits.contains(c)) {
                                mergeThroughOtherMechanisum.add(c);
                            }
                            if (rejectedPRCommits.contains(c)) {
                                mergeThroughOtherMechanisum.add(c);
                            }
                        }
                    }

                    F2U.remove("");
                    F2U.addAll(mergedPRCommits);
                    rejectedPRCommits.removeAll(F2U);
                    OnlyF.removeAll(F2U);

                    sb.append(graph_info_map.get(forkName) + "," + forkPRinfo.replace("\n", "").replaceAll(forkName + ",", "") + ","
                            + OnlyF.size() + "," + F2U.size() + "," + mergeThroughOtherMechanisum.size() + ","
                            + OnlyF.toString().replace(",", "/")+"," + F2U.toString().replace(",", "/") + "," + mergeThroughOtherMechanisum.toString().replace(",", "/") + "\n");

                }
            }

        }
        io.rewriteFile(sb.toString(), current_dir + "/result/" + repoURL + "/pr_graph_info.csv");


    }




    public static void main(String[] args) {
        AnalyzingPRs ap = new AnalyzingPRs();
        String repoURL = "airbnb/javascript";
        HashMap<String, String> forkPR_map = ap.getPRMap(repoURL);
        ap.combineGraphResult(repoURL, forkPR_map);


    }
 }
