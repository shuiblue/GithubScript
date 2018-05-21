import java.io.IOException;

public class MonitorPR {
    public static void main(String[] args) {
        while (true) {
            String latestLine_1 = getLatestProject();

            System.out.println(latestLine_1);
            try {
                Thread.sleep(600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String latestLine_2 = getLatestProject();
            System.out.println(latestLine_2);
            if (latestLine_1.equals(latestLine_2)) {
                System.out.println("restart..");
                new IO_Process().exeCmd("gradle -PmainClass=AnalyzingPRs execute".split(" "), "/home/feature/shuruiz/GithubScript/");
            }
        }
    }

    public static String getLatestProject() {
        String[] finishedProject = new String[0];
        try {
            finishedProject = new IO_Process().readResult("/home/feature/shuruiz/AnalyzePR/finish_PRanalysis.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return finishedProject[finishedProject.length - 1];

    }
}
