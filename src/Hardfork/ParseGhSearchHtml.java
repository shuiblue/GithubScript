package Hardfork;

import Util.IO_Process;
import Util.JsonUtility;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Level;


/**
 * Created by shuruiz on 4/5/17.
 */
public class ParseGhSearchHtml {
    static final String FS = File.separator;
    static String github_api = "https://api.github.com/repos/";
    static String github_page = "https://github.com/";
    Document doc, currentDoc;
    static String analysisDir = "";
    static String isJoined_str, isJoined_str_opposite, activity_str = "";
    boolean isJoined;

    HashMap<String, HashSet<Integer>> originalClusterMap = new HashMap<>();
    String originalPage = "original.html";
    int max_numberOfCut;
    int numberOfBiggestClusters;
    private String workingDir = System.getProperty("user.dir");
    HashMap<String, String> nodeId_to_clusterID_Map;
    ArrayList<String> combination_list = new ArrayList<>();
    HashMap<String, String> label_to_id;
    ArrayList<String> forkAddedNodeList;
    HashMap<Integer, HashMap<String, HashSet<Integer>>> clusterResultMap;
    static ArrayList<String> all_splitStep_list = new ArrayList<>();
    HashMap<String, Integer> usedColorIndex = new HashMap<>();
    HashMap<String, String> cluster_color = new HashMap<>();
    static HashMap<Integer, HashMap<Integer, HashMap<String, HashSet<Integer>>>> allSplittingResult;
    static int otherClusterSize = 0;
    String publicToken;


    public ParseGhSearchHtml() {

    }

    public String appendTableTitle(int colspan, String splitStep) {
        return "<table id=\"cluster\">  \n" +
                "  <tr> \n" +
                "    <td colspan=\"2\"> <button class=\"btn\" id=\"btn_hide_non_cluster_rows\" onclick=\"hide_non_cluster_rows()\">Hide non cluster code</button>\n" +
                "    <td><button><a href=\"./" + splitStep + isJoined_str_opposite + ".html\" class=\"button\">" + activity_str + "</a></button></td>" +
                "    </td> \n" +
                "  </tr>\n" +
                "  <tr>\n" +
                "   <th colspan=\"" + colspan + "\">Level</th>\n" +
                "       <th>Cluster</th>\n" +
                "       <th>Navigation</th>\n" +
                "       <th>Keywords</th>\n" +
                "       <th>LOC</th>\n" +
                "       <th>Split Cluster </th>\n" +
                "    </tr>\n";
    }

    public ParseGhSearchHtml(int max_numberOfCut, int numberOfBiggestClusters, String analysisDir, String publicToken) {
        this.max_numberOfCut = max_numberOfCut;
        this.numberOfBiggestClusters = numberOfBiggestClusters;
        this.analysisDir = analysisDir;
        this.publicToken = publicToken;

    }

    public List<String> getSearchPage(String searchPageUrl, String localSourceCodeDirPath) {

        this.analysisDir = localSourceCodeDirPath + "INFOX_output/";
//        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        WebClient webClient = new WebClient();
        // turn off htmlunit warnings
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
        webClient.getOptions().setUseInsecureSSL(true); //ignore ssl certificate
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.setCssErrorHandler(new SilentCssErrorHandler());

        HtmlPage page = null;
        Document currentPage = null;

        try {
            page = webClient.getPage(searchPageUrl);
        } catch (Exception e) {
            System.out.println("Get page error");
        }
        webClient.waitForBackgroundJavaScriptStartingBefore(200);
        webClient.waitForBackgroundJavaScript(5000);

        currentPage = Jsoup.parse(page.asXml());
        return getHardForkInfo(webClient, currentPage);

    }

    private List<String> getHardForkInfo(WebClient webClient, Document currentPage) {
        IO_Process io = new IO_Process();
        io.writeTofile("", "/Users/shuruiz/Work/ForkData/hardfork-exploration/hardfork_upstream_pairs.txt");
        List<String> result = new ArrayList<>();
        Elements repoElements = currentPage.getElementsByClass("repo-list-item");
        for (Element ele : repoElements) {
            String hardfork = ele.getElementsByAttribute("href").get(0).childNode(0).toString().trim();
            String upstream = ele.getElementsByAttribute("href").get(1).childNode(0).toString().trim();
            result.add(hardfork + " " + upstream);
            io.writeTofile(hardfork + " " + upstream + "\n", "/Users/shuruiz/Work/ForkData/hardfork-exploration/hardfork_upstream_pairs.txt");

        }
        System.out.println(result.size());
        return result;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        ParseGhSearchHtml pgs = new ParseGhSearchHtml();
        List<String> hardfork_upstream_pairs = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
            String url = "https://github.com/search?l=&p=" + page + "&q=%22a+fork+of%22+extension%3Amd+fork%3Aonly&ref=advsearch&type=Repositories&utf8=%E2%9C%93";
//            try {
////                Thread.sleep(50000);
//                System.out.println(page);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            hardfork_upstream_pairs.addAll(pgs.getSearchPage(url, ""));
        }

    }

}