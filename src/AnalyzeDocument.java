import Util.IO_Process;

import java.io.IOException;

public class AnalyzeDocument {
    static String output_dir;
    static String current_OS = System.getProperty("os.name").toLowerCase();

    static public void main(String[] args) {
        if (current_OS.indexOf("mac") >= 0) {
            output_dir = "/Users/shuruiz/Box Sync/ForkData";
        } else {
            output_dir = "/home/feature/shuruiz/ForkData";
        }

        IO_Process io = new IO_Process();
        String[] repoList={};
        try {
            repoList = io.readResult(output_dir + "/activeForkRatio.csv").split("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(String repo:repoList){
            String repoUrl = repo.split(",")[0];
            AnalyzeDocument analyzeDocument = new AnalyzeDocument();
            analyzeDocument.parseMarkDownFiles(repoUrl);
        }
    }

    private void parseMarkDownFiles(String repoUrl) {
//        String urlString = "http://localhost:8983/solr/bigboxstore";
//        HttpSolrClient solr = new HttpSolrClient.Builder(urlString).build();
//        solr.setParser(new XMLResponseParser());
//        SolrQuery query = new SolrQuery();
//        query.setQuery(mQueryString);
//        query.set("fl", "category,title,price");
//        try {
//            QueryResponse response = solr.query(query);
//            SolrDocumentList list = response.getResults();
//        } catch (SolrServerException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
    }
}
