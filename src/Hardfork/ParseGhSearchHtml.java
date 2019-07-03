package Hardfork;

import Util.IO_Process;
import Util.JsonUtility;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.util.Hash;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
    static String working_dir, pr_dir, output_dir, clone_dir, current_dir, graph_dir, result_dir,hardfork_dir;
    static String myUrl, user, pwd;

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
        IO_Process io = new IO_Process();
        current_dir = System.getProperty("user.dir");
        try {
            String[] paramList = io.readResult(current_dir + "/input/dir-param.txt").split("\n");
            working_dir = paramList[0];
            pr_dir = working_dir + "queryGithub/";
            output_dir = working_dir + "ForkData/";
            hardfork_dir  = output_dir + "hardfork-exploration/";
            result_dir = output_dir + "result0821/";

            clone_dir = output_dir + "clones/";
            graph_dir = output_dir + "ClassifyCommit_new/";

            myUrl = paramList[1];
            user = paramList[2];
            pwd = paramList[3];

        } catch (IOException e) {
            e.printStackTrace();
        }
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
        System.setProperty("http.agent", "");
        this.analysisDir = localSourceCodeDirPath + "INFOX_output/";
//        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        WebClient webClient = new WebClient();
        // turn off htmlunit warnings
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
        webClient.getOptions().setUseInsecureSSL(false); //ignore ssl certificate
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        HtmlPage page = null;
        Document currentPage;

        page = requestPage(searchPageUrl, webClient);
        if (page == null) {
            return new ArrayList<>();
        }

        webClient.waitForBackgroundJavaScriptStartingBefore(200);
        webClient.waitForBackgroundJavaScript(5000);

        currentPage = Jsoup.parse(page.asXml());
        return getHardForkInfo(webClient, currentPage);

    }

    private HtmlPage requestPage(String searchPageUrl, WebClient webClient) {
        HtmlPage page = null;
        try {
            page = webClient.getPage(searchPageUrl);

            //abuse detection
            while (page.getWebResponse().getStatusCode() == 429) {
                System.out.println("github abuse detection, sleep 500 seconds");
                System.out.println(searchPageUrl);
                Thread.sleep(500000);
                page = requestPage(searchPageUrl, webClient);
            }
        } catch (Exception e) {
            System.out.println("Get page error");
        }
        return page;
    }

    private List<String> getHardForkInfo(WebClient webClient, Document currentPage) {
        IO_Process io = new IO_Process();
        io.writeTofile("", hardfork_dir+"hardfork_upstream_pairs_0522.txt");
        List<String> result = new ArrayList<>();
        Elements repoElements = currentPage.getElementsByClass("repo-list-item");
        for (Element ele : repoElements) {
            String hardfork = ele.getElementsByAttribute("href").get(0).childNode(0).toString().trim();
            String upstream = ele.getElementsByAttribute("href").get(1).childNode(0).toString().trim();
            result.add(hardfork + " " + upstream);
            io.writeTofile(hardfork + "," + upstream + "\n", hardfork_dir+"hardfork_upstream_pairs_0522.txt");

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

//        https://github.com/search?q=%22a+fork+of%22+fork%3Aonly+created%3A2008-03-01..2008-04-01&type=Repositories
//        appears the very first repo
//        String date_string = "2008-01-01";
        String date_string = "2017-03-01";


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd");
        Date date = null;
        try {
            date = sdf.parse(date_string);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Calendar c1 = Calendar.getInstance();
        c1.setTime(date);
        for (int year = 2017; year <= 2020; year++) {
            for (int i = 1; i < 12; i++) {
                String currentMonth = c1.getTime().toInstant().toString().split("T")[0];
                c1.add(Calendar.MONTH, 1);
                String nextMonth = c1.getTime().toInstant().toString().split("T")[0];

                System.out.println(currentMonth + "..." + nextMonth);
                String query = "q=%22a+fork+of%22+fork%3Aonly+created%3A" + currentMonth + ".." + nextMonth + "&type=Repositories";
                for (int page = 1; page <= 100; page++) {
                    String url = "https://github.com/search?l=&p=" + page + "&" + query;
                    try {
                        Thread.sleep(10000);
                        System.out.println(url);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    List<String> result = pgs.getSearchPage(url, "");
                    if (result.size() > 0) {
                        hardfork_upstream_pairs.addAll(result);
                    } else {
                        System.out.println("0 result between:" + currentMonth + "..." + nextMonth);
                        break;
                    }
                }


            }

        }


    }

}
