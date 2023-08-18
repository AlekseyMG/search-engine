package searchengine.dto.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class WebParser extends RecursiveTask<String> {
    private final String root;
    private Document htmlDoc;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Site currentSite;
    private Set<String> rootLinks;
    private Connection.Response response;
    private int statusCode = 0;
    private String errorMessage = "";
    private final String siteUrl;
    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                     "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                     "Chrome/114.0.0.0 Safari/537.36 Edg/114.0.1823.58";

    public WebParser(String url,
                     Site currentSite,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     String siteUrl,
                     Set<String> rootLinks
    ) {
        this.root = url.replaceAll("www.", "");
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.currentSite = currentSite;
        this.rootLinks = rootLinks;
        this.siteUrl = siteUrl.replaceAll("www.", "");
        this.htmlDoc = new Document("index");
    }

    @Override
    protected String compute() {
        Set<String> links = getLinks();

        if (links.isEmpty() || root.contains(".pdf")) {
            Page page = new Page();
            page.setPath(root);
            page.setCode(statusCode);
            page.setSite(currentSite);
            page.setContent(htmlDoc.text());
            pageRepository.saveAndFlush(page);
            if (!errorMessage.equals("")) currentSite.setLastError(errorMessage);
            currentSite.setStatusTime(LocalDateTime.now());
            siteRepository.saveAndFlush(currentSite);

            return root + "\n";
        }

        StringBuilder paths = new StringBuilder();
        List<WebParser> subTasks = new LinkedList<>();
        for (String link : links) {
            rootLinks.add(link);
            WebParser task = new WebParser(
                    link,
                    currentSite,
                    pageRepository,
                    siteRepository,
                    siteUrl,
                    rootLinks);
            task.fork();
            subTasks.add(task);
        }
        for (WebParser task : subTasks) {
            paths.append(root).append("\n").append(task.join());
        }
        return paths.toString();
    }


    private Set<String> getLinks() {
        Set<String> links = new HashSet<>();
        if (root.contains(".pdf") || root.contains("#")) {
            return new HashSet<>();
        }
        try {
            Thread.sleep((int) (Math.random() * 200) + 200);
            System.out.println("Идем по ссылке - " + root);
            Connection connection = Jsoup.connect(root);
            response = connection
                    .userAgent(userAgent)
                    .timeout(3000)
                    .ignoreHttpErrors(true)
                    .execute();
            htmlDoc = connection.get();
            statusCode = response == null ? 0 : response.statusCode();

            Elements htmlLinks = htmlDoc.select("a[href]");
            for (Element link : htmlLinks) {
                String url = link.attr("abs:href");
                if (url.contains(siteUrl) &&
                        !url.equals(root) &&
                        !url.contains("#") &&
                        !url.contains("@") &&
                        !url.contains(".com") &&
                        !url.contains("img") &&
                        !rootLinks.contains(url)
                        //rootLinks.stream().noneMatch(s -> s.equals(url))
                ) {
                    links.add(url);
                }

            }
        } catch (Exception ex) {
            System.out.println((response == null ? "" : response.statusMessage()) + ex);
            //ex.printStackTrace();
            if (response != null) {
                errorMessage = response.statusMessage().equals("OK") ? "" : response.statusMessage() + " ";
                statusCode = response.statusCode();
            }
            errorMessage += ex;
            //System.out.println(ex);
//            Page page = new Page();
//            page.setPath(root);
//            page.setCode(statusCode);
//            page.setSite(currentSite);
//            page.setContent("");
//            pageRepository.saveAndFlush(page);
//            //}
//            currentSite.setLastError(statusCode + " - " + (response == null ? "" : response.statusMessage()) + " - " + ex);
//            currentSite.setStatusTime(LocalDateTime.now());
//            siteRepository.saveAndFlush(currentSite);
        }
        return links;
    }
}
