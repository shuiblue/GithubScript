package misc;

import Util.IO_Process;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class countUpstreamWithHardfork {

    public static void main(String[] args) {
        String[] list = {} ;
        try {
            list = new IO_Process().readResult("/Users/shuruiz/Work/ForkData/hardfork-exploration/EvolutionGraph/hardfork_upstream_pairs_0523_noDup.txt").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        HashMap<String,Set<String>> upstream_hardfork_map = new HashMap<>();
        for(String str:list){
            String[] pair = str.split(",");
            String hardfork = pair[0];
            String upstream = pair[1];

            Set<String> set = new HashSet<>();
            if(upstream_hardfork_map.get(upstream)!=null){
                set = upstream_hardfork_map.get(upstream);
            }
            set.add(hardfork);
            upstream_hardfork_map.put(upstream,set);
        }

        upstream_hardfork_map.forEach((up,set)->{
            if(set.size()>1){
               for(String hf:set){
                   new IO_Process().writeTofile(hf+","+up+"\n","/Users/shuruiz/Work/ForkData/hardfork-exploration/EvolutionGraph/hardfork_upstream_pairs_0531_rerun.txt");
               }
            }
//            new IO_Process().writeTofile(up+","+set.size()+"\n","/Users/shuruiz/Work/ForkData/hardfork-exploration/EvolutionGraph/hardfork_num.txt");
        });
    }
}
