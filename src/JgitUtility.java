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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by shuruiz on 4/7/17.
 */
public class JgitUtility {
    static String github_url = "https://github.com/";
    static String token;
    static String localDirPath;
    static String current_dir;
    static String output_dir = "/Users/shuruiz/Box Sync/ForkData";

    JgitUtility() {
        current_dir = System.getProperty("user.dir");
        try {
            token = new IO_Process().readResult(current_dir + "/input/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void cloneRepo(String repo_uri) {
        System.out.println("cloning repo: " + repo_uri+" ...");

        IO_Process io = new IO_Process();
        localDirPath = output_dir + "/cloneRepos/";
//        localDirPath = current_dir + "/cloneRepos/";
        String clone_url = github_url + repo_uri + ".git";

        String repoName = repo_uri.split("/")[0];
        String pathname = localDirPath + repo_uri + "/";
        File dir = new File(pathname);
        File dir_git = new File(pathname + ".git");

        if (!dir_git.exists()) {
            dir.mkdir();
//            CredentialsProvider cp = new UsernamePasswordCredentialsProvider( "shuiblue", "shuiblue218" );
//            git.pull().setCredentialsProvider( cp ).call();
            try {
                Git git = Git.cloneRepository()
//                        .setCredentialsProvider( cp )
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
                }
                io.rewriteFile(sb_branches.toString(), pathname + repoName + "_branchList.txt");

            } catch (GitAPIException e) {
                e.printStackTrace();
            }

        } else {
            System.out.println(repo_uri + " already exist!");
        }
    }


    public static void main(String[] args) {
        JgitUtility jg = new JgitUtility();
        String[] forkList = {"timscaffidi/ofxVideoRecorder"};
        for (String forkUrl : forkList) {
            jg.cloneRepo(forkUrl);
        }

    }


}
