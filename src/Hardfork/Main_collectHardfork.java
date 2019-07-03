package Hardfork;

import Util.IO_Process;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Main_collectHardfork {
    static String working_dir, output_dir;

    final int batchSize = 200;
    Main_collectHardfork(){
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



        IO_Process  io = new IO_Process();
        String[] hardforks = {};
        try {
           hardforks = io.readResult(output_dir+"/hardfork-exploration/hardfork_upstream_pairs_complete.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(hardforks.length +" in the list");
        Set<String> hardfork_set = new HashSet<>();
        for(String s: hardforks){
            hardfork_set .add(s);
        }
        System.out.println(hardfork_set.size() +" in the set");


        for(String s: hardfork_set){
            io.writeTofile(s + "\n","/Users/shuruiz/Work/ForkData/hardfork-exploration/hardfork_upstream_pairs_0523_noDup.txt");
        }
    }
}
