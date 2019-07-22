package misc;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenerateHtmlForImages {

    static String working_dir, output_dir;

    GenerateHtmlForImages() {
        IO_Process io = new IO_Process();
        String current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            output_dir = working_dir + "ForkData/";

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        String dir = "hardfork-exploration/EvolutionGraph/Group-0715/hardfork/ForkLiveLonger/StillActiveNow/NoInteraction/FrequentActivity/";
        boolean RANDOM = true;
        boolean SAMPLELIST = false;
        String outputHTML = "";
        new GenerateHtmlForImages();
        IO_Process io = new IO_Process();
        StringBuilder sb = new StringBuilder();
        String head, end, element_code = "";
        int max_num_hard = 3000;
        final int[] i = {1};
        final int[] y = {20};
        try {
            head = new IO_Process().readResult("./input/evol_htmlhead.txt");
            sb.append(head);




            List<String> svgFiles = new ArrayList<>();
            if (RANDOM) {
                outputHTML = output_dir +dir+ "evolution.html";

                List<String> finalSvgFiles = svgFiles;
                Files.newDirectoryStream(Paths.get(output_dir + dir )
                        , path -> path.toFile().getPath().endsWith("svg"))
                        .forEach(element -> {
                                    finalSvgFiles.add(element.getFileName().toString());
                                }
                        );
            } else if (SAMPLELIST) {
                outputHTML = output_dir + dir + "hardfork-exploration/EvolutionGraph/Visualization0601/evolution-sampled.html";
                svgFiles = Arrays.asList(io.readResult("./input/selectedSvgFiles.txt").split("\n"));
            }


            sb.append("var width = window.innerWidth;\n" +
                    "      var height =" + svgFiles.size() * 102+ ";\n" +
                    " \n" +
                    "      var text = new Konva.Text({\n" +
                    "        x: 10,\n" +
                    "        y: 10,\n" +
                    "        fontFamily: 'Calibri',\n" +
                    "        fontSize: 24,\n" +
                    "        text: '',\n" +
                    "        fill: 'white'\n" +
                    "      });\n" +
                    "\n" +
                    "\n" +
                    "      var stage = new Konva.Stage({\n" +
                    "        container: 'container',\n" +
                    "        width: width,\n" +
                    "        height: height\n" +
                    "      });\n" +
                    "\n" +
                    "      var layer = new Konva.Layer();\n" +
                    "      stage.add(layer);\n");

            element_code = new IO_Process().readResult("./input/htmlCode.txt");

            for (String svg : svgFiles) {
                int box_width = 0;
                try {
                    String svgstr = io.readResult(output_dir + dir + svg);
                    box_width = (int) (Integer.valueOf(svgstr.split("\">")[0].split("width=\"")[1]) * 0.6);
                    if (box_width > 1350) box_width = 1350;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (i[0] < max_num_hard) {
                    sb.append("var image" + i[0] + " = new Konva.Image({\n" +
                            "        x: 20,\n" +
                            "        y: " + y[0] + ",\n" +
                            "        width: " + box_width + ",\n" +
                            "        height: 90,\n" +
                            "        stroke: 'red',\n" +
                            "        strokeWidth: 1,\n" +
                            "        draggable: true\n" +
                            "\n" +
                            "      });\n" +
                            "      layer.add(image" + i[0] + ");\n" +
                            "    \n" +
                            "      var imageObj" + i[0] + " = new Image();\n" +
                            "     imageObj" + i[0] + ".onload = function() {\n" +
                            "        image" + i[0] + ".image(imageObj" + i[0] + ");\n" +
                            "        layer.draw();\n" +
                            "      };\n" +
                            "      imageObj" + i[0] + ".src = '" + svg + "';\n");
                }
                i[0]++;
                y[0] += 100;
            }
//                    );

            end = new IO_Process().readResult("./input/evol_htmlend.txt");
            sb.append(end);

            io.rewriteFile(sb.toString(), outputHTML);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
