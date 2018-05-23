import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by shuruiz on 4/7/17.
 */
public class JgitUtility {
    static String github_url = "https://github.com/";
    static String token;
    static String localDirPath;
    static String working_dir, pr_dir, output_dir, clone_dir;

    public JgitUtility() {
        String current_dir = System.getProperty("user.dir");
        IO_Process io = new IO_Process();
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            clone_dir = output_dir + "clones/";
            token = io.readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        clone_dir = output_dir + "clones/";
        localDirPath = output_dir + "cloneRepos/";
        current_dir = System.getProperty("user.dir");
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public ArrayList<String> cloneRepo(String repo_uri) {
        ArrayList<String> branchList = new ArrayList<>();
        System.out.println("cloning repo: " + repo_uri + " ...");


        long start = System.nanoTime();

        IO_Process io = new IO_Process();

        String clone_url = github_url + repo_uri + ".git";

        String repoName = repo_uri.split("/")[0];
        String pathname = localDirPath + repo_uri + "/";
        File dir = new File(pathname);
        File dir_git = new File(pathname + ".git");
        System.out.println("pathname: " + pathname);
        if (!dir_git.exists()) {
            dir.mkdir();
            try {
                Git git = Git.cloneRepository()
                        .setURI(clone_url)
                        .setCloneAllBranches(true)
                        .setDirectory(dir)
                        .call();


                /**  get all branches   **/
                System.out.println("get all branches..");
                List<Ref> call = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
                StringBuilder sb_branches = new StringBuilder();
                String default_branch = call.get(0).getName().replace("refs/heads/", "");
                io.rewriteFile(default_branch, pathname + "defaultBranch.txt");
                for (int i = 1; i < call.size(); i++) {
                    Ref ref = call.get(i);
                    String branchName = ref.getName().replace("refs/remotes/origin/", "");
                    sb_branches.append(branchName + "\n");
                    branchList.add(branchName);
                }
                io.rewriteFile(sb_branches.toString(), pathname + repoName + "_branchList.txt");

            } catch (GitAPIException e) {
                e.printStackTrace();
            }

        } else {
            System.out.println(repo_uri + " already exist!");
            try {
                branchList.addAll(Arrays.asList(io.readResult(pathname + repoName + "_branchList.txt").split("\n")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        long end = System.nanoTime();
        long used = end - start;
        System.out.println("clone repo " + TimeUnit.NANOSECONDS.toMillis(used) + " ms");

        return branchList;
    }


    public void cloneRepo_cmd(HashSet<String> forkList, String projectURL) {
        String projectName = projectURL.split("/")[0];
        IO_Process io = new IO_Process();
        String creadDirCMD = "mkdir -p " + clone_dir + projectURL;
        io.exeCmd(creadDirCMD.split(" "), clone_dir);
        System.out.println("mkdir -p " + projectURL);


        String cloneCMD = "git clone " + github_url + projectURL + ".git";
        io.exeCmd(cloneCMD.split(" "), clone_dir + projectName + "/");

        if (new File(clone_dir + projectURL).exists()) {
            HashSet<String> clonedFork = new HashSet<>();
            for (String forkUrl : forkList) {
                forkUrl = io.getForkURL(forkUrl);
                if (!clonedFork.contains(forkUrl)) {
                    clonedFork.add(forkUrl);
                    System.out.println(forkUrl);
                    String forkName = io.getForkURL(forkUrl.split("/")[0]);
                    String cloneForkCmd = "git remote add " + forkName + " " + github_url + forkUrl + ".git";
                    io.exeCmd(cloneForkCmd.split(" "), clone_dir + projectURL + "/");
                }
            }

            String fetchAll = "git fetch --all";
            System.out.println(io.exeCmd(fetchAll.split(" "), clone_dir + projectURL + "/"));
        } else {
            io.writeTofile(projectURL + "\n", output_dir + "404_pro.txt");

        }
    }

    public static void main(String[] args) {
        IO_Process io = new IO_Process();
//        ArrayList<String> projectList = io.getProjectURL();
        String current_dir = System.getProperty("user.dir");
        String[] projectList = {};
        try {
            projectList = io.readResult(current_dir + "/input/cloneRepoList.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String projectUrl : projectList) {
            ArrayList<String> forkList = new ArrayList<>();
            // io.getForkListFromRepoTable(projectUrl);
            System.out.println(projectUrl);
            forkList.addAll(io.getForkListFromPRlist(projectUrl));
            JgitUtility jg = new JgitUtility();
            System.out.println(forkList.size() + "forks on clone list...");
            if (forkList.size() > 0) {
//                jg.cloneRepo_cmd(forkList, projectUrl);
                io.writeTofile(projectUrl + "\n", output_dir + "finish_clone.txt");
            }

        }


    }


}
