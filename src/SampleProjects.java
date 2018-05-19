import java.io.IOException;
import java.util.*;

public class SampleProjects {
    static List<String> stopWords = new ArrayList<>();
    static List<String> projectList_update = new ArrayList<>();

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        IO_Process io = new IO_Process();
        int number_project = 1500;
        try {
//            List<String> projectList_all = Arrays.asList(io.readResult("/Users/shuruiz/Work/ForkData/bigThan20Forks.csv").split("\n"));
//            stopWords = Arrays.asList(io.readResult("/Users/shuruiz/Work/ForkData/onlineMaterial.txt").split("\n"));
//
//            for (String proName : projectList_all) {
//                if (!isOnlineMaterial(proName)) {
//                    projectList_update.add(proName);
//                }
//            }
//            System.out.println("filter out " + (projectList_all.size() - projectList_update.size()) + " projects.");
//
//            for (String pro : projectList_update) {
//                String[] arr = pro.split(",");
//                String projectURL = arr[1] + "/" + arr[2];
//                sb.append(arr[0] + "," + projectURL + "\n");
//            }
//            io.rewriteFile(sb.toString(), "/Users/shuruiz/Work/ForkData/update_bigThan20Forks.csv");

            projectList_update = Arrays.asList(io.readResult("/Users/shuruiz/Work/ForkData/update_bigThan20Forks.csv").split("\n"));


            List<String> randomPicks = pickNRandom(projectList_update, number_project);
            sb = new StringBuilder();
            for (String pro : randomPicks) {
//                String[] arr = pro.split(",");
//                String projectURL = arr[1] + "/" + arr[2];
//                sb.append(arr[0] + "," + projectURL + "\n");
                sb.append(pro+"\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        io.rewriteFile(sb.toString(), "/Users/shuruiz/Work/ForkData/" + number_project + "_projectList.csv");
    }

    private static boolean isOnlineMaterial(String project) {
        for (String file : stopWords) {
            if (project.toLowerCase().contains(file.toLowerCase())) {
                return true;
            }
        }
        return false;
    }


    public static List<String> pickNRandom(List<String> lst, int n) {
        List<String> copy = new LinkedList<String>(lst);
        Collections.shuffle(copy);
        return copy.subList(0, n);
    }
}
