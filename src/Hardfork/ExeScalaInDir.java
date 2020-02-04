package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class ExeScalaInDir {
    public static void main(String[] args) {
//        String dir = "/DATA/shurui/ForkData/ClassifyCommit_new/AllGroup/";
//
//        String cmd[] = {"scala","Visualize.sc",""};
        String dir = "/DATA/shurui/ForkData/analyzed_commitHistory_all-0822/";
//        String dir = "/DATA/shurui/ForkData/bigHistory";
        String cmd[] = {"scala","Category_fast.sc",""};


        IO_Process io =new IO_Process();


        Stream<Path> files = null;
        try {
            files = Files.list(Paths.get(dir));
        } catch (IOException e) {
            e.printStackTrace();
        }

        files.forEach(fileName ->{
            String fn = fileName.getFileName().toString();
            if (fn.endsWith("svg")) return;
            System.out.println(fn);
            cmd[2] =fn;
           String result= io.exeCmd(cmd,dir);


           System.out.println(result);
        });

    }
}
