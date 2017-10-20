import java.io.IOException;

/**
 * Created by shuruiz on 10/8/17.
 */
public class TrackCommitHistory {
     static String token;
    /**
     * This function gets a list of forks of a repository
     * input: repo_url
     * output: fork list
     * @param repo_url  e.g. 'shuiblue/INFOX'
     */
    public void getForkList(String repo_url){

    }


    public static void main(String[] args){
        TrackCommitHistory tch = new TrackCommitHistory();
        IO_Process io = new IO_Process();
        String root = "/Users/shuruiz/Work/checkProjectSize/";

        String repo="";


        try {
            token = io.readResult(root + "/token.txt").trim();
        } catch (IOException e) {
            e.printStackTrace();
        }

        tch.getForkList(repo);
    }

}
