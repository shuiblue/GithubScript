package Djikstra;

/**
 * Created by shuruiz on 1/17/18.
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Graph {
//    private final List<Vertex> vertexes;
//    private final List<Edge> edges;

    public HashMap<String, ArrayList<String>> getAll_historyMap() {
        return all_historyMap;
    }

    HashMap<String, ArrayList<String>> all_historyMap ;

    public Graph(HashMap<String, ArrayList<String>> all_historyMap ) {
//    public Graph(List<Vertex> vertexes, List<Edge> edges, HashMap<String, ArrayList<String>> all_historyMap ) {
//        this.vertexes = vertexes;
//        this.edges = edges;
        this.all_historyMap=all_historyMap;
    }

//    public List<Vertex> getVertexes() {
//        return vertexes;
//    }
//
//    public List<Edge> getEdges() {
//        return edges;
//    }



}