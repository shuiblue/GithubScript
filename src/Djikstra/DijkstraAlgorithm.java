package Djikstra; /**
 * Created by shuruiz on 1/17/18.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class DijkstraAlgorithm {

//    private final List<Vertex> nodes;
//    private final List<Edge> edges;
    HashMap<String, ArrayList<String>> all_historyMap ;
    private Set<Vertex> settledNodes;
    private Set<Vertex> unSettledNodes;
    private Map<Vertex, Vertex> predecessors;

    public Map<Vertex, Integer> getDistance() {
        return distance;
    }

    private Map<Vertex, Integer> distance;

    public DijkstraAlgorithm(Graph graph) {
        // create a copy of the array so that we can operate on this array
//        this.nodes = new ArrayList<Vertex>(graph.getVertexes());
//        this.edges = new ArrayList<Edge>(graph.getEdges());
        this.all_historyMap=graph.getAll_historyMap();
    }

    public void execute(Vertex source) {
        settledNodes = new HashSet<Vertex>();
        unSettledNodes = new HashSet<Vertex>();
        distance = new HashMap<Vertex, Integer>();
        predecessors = new HashMap<Vertex, Vertex>();
        distance.put(source, 0);
        unSettledNodes.add(source);
        while (unSettledNodes.size() > 0) {
            Vertex node = getMinimum(unSettledNodes);
            settledNodes.add(node);
            unSettledNodes.remove(node);
            findMinimalDistances(node);
        }
    }

    private void findMinimalDistances(Vertex node) {
        List<Vertex> adjacentNodes = getNeighbors(node);
        for (Vertex target : adjacentNodes) {
            if (getShortestDistance(target) > getShortestDistance(node)
                    + getDistance(node, target)) {
                distance.put(target, getShortestDistance(node)
                        + getDistance(node, target));
                predecessors.put(target, node);
                unSettledNodes.add(target);
            }
        }

    }

    private int getDistance(Vertex node, Vertex target) {
//        for (Edge edge : edges) {
//            if (edge.getSource().equals(node)
//                    && edge.getDestination().equals(target)) {
//                return edge.getWeight();
//            }
//        }

        ArrayList<String> neighbors_ids =  all_historyMap.get(node.getId());
        if(target.getId().equals(neighbors_ids.get(0))){
            return 0;
        }else if(target.getId().equals(neighbors_ids.get(1))){
            return 1;
        }

        throw new RuntimeException("Should not happen");
    }

    private List<Vertex> getNeighbors(Vertex node) {
        List<Vertex> neighbors = new ArrayList<Vertex>();
//        for (Edge edge : edges) {
//            if (edge.getSource().equals(node)
//                    && !isSettled(edge.getDestination())) {
//                neighbors.add(edge.getDestination());
//            }
//        }
        ArrayList<String> neighbors_ids = all_historyMap.get(node.getId());
       for(String id:neighbors_ids){
           neighbors.add(new Vertex(id));
       }

        return neighbors;
    }

    private Vertex getMinimum(Set<Vertex> vertexes) {
        Vertex minimum = null;
        for (Vertex vertex : vertexes) {
            if (minimum == null) {
                minimum = vertex;
            } else {
                if (getShortestDistance(vertex) < getShortestDistance(minimum)) {
                    minimum = vertex;
                }
            }
        }
        return minimum;
    }

    private boolean isSettled(Vertex vertex) {
        return settledNodes.contains(vertex);
    }

    private int getShortestDistance(Vertex destination) {
        Integer d = distance.get(destination);
        if (d == null) {
            return Integer.MAX_VALUE;
        } else {
            return d;
        }
    }

    /*
     * This method returns the path from the source to the selected target and
     * NULL if no path exists
     */
    public LinkedList<Vertex> getPath(Vertex target) {
        LinkedList<Vertex> path = new LinkedList<Vertex>();
        Vertex step = target;
        // check if a path exists
        if (predecessors.get(step) == null) {
            return null;
        }
        path.add(step);
        while (predecessors.get(step) != null) {
            step = predecessors.get(step);
            path.add(step);
        }
        // Put it into the correct order
        Collections.reverse(path);
        return path;
    }

}