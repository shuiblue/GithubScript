package misc;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class moveInactiveForkSVG {
    static String working_dir, output_dir, evolutionVisual_dir;
    static IO_Process io = new IO_Process();
    static String current_dir = System.getProperty("user.dir");

    moveInactiveForkSVG() {
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";
//            evolutionVisual_dir = output_dir + "Visual0601/";
            evolutionVisual_dir = output_dir + "hardfork-exploration/EvolutionGraph/Visualization0601/";
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        List<String> arrList = new ArrayList<>();
        new moveInactiveForkSVG();
        try {
            String[] arr = io.readResult(current_dir + "/input/selectedSvgFiles.txt").split("\n");
            arrList = Arrays.asList(arr);

        } catch (IOException e) {
            e.printStackTrace();
        }

        for (String fork : arrList) {
            Path source = Paths.get(evolutionVisual_dir + fork);
            try {
                Files.move(source, source.resolveSibling(evolutionVisual_dir + "InactiveForks/" + fork));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
