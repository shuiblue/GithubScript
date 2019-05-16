package Hardfork;

public class CompareCommit_main {
    public static void main(String[] args) {
        FindHardFork findHardFork = new FindHardFork();


        String upstream = "lord/slate";
        String fork = "airbrake/slate";
        findHardFork.getCommitDiffForTwoRepos(fork, upstream);
    }
}
