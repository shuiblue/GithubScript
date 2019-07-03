package misc;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FilteroutInactiveForks {
    static String working_dir, output_dir,evolutionVisual_dir;
    static IO_Process io = new IO_Process();

    FilteroutInactiveForks() {
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
            evolutionVisual_dir =  output_dir+"Visual0601/";
//            evolutionVisual_dir =  output_dir+"hardfork-exploration/EvolutionGraph/";
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        new FilteroutInactiveForks();
        Set<String> activeForks = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        try {
            Files.newDirectoryStream(Paths.get(evolutionVisual_dir)
                    , path -> path.toFile().getPath().endsWith("2019.csv"))
                    .forEach(element -> {
                                String filename = element.getFileName().toString();
                                try {
                                    String[] result = new IO_Process().readResult(element.toString()).split("\n");
                                    for (String str : result) {
                                        if (str.contains("only_F")) {
                                            continue;
                                        }
                                            String[] ele = str.split(",");
                                            int onlyF = Integer.parseInt(ele[2]);
                                            int F2U = Integer.parseInt(ele[4]);
                                            if ((onlyF + F2U) > 0) {
                                                activeForks.add(ele[0]);
                                                sb.append(ele[0] + "\n");
                                            }
                                        }

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }
                    );
        } catch (IOException e) {
            e.printStackTrace();
        }

        io.rewriteFile(sb.toString(), evolutionVisual_dir + "activeHardForks.txt");


    }
}
