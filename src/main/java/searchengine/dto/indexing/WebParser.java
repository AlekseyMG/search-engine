package searchengine.dto.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.Query;
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
    private Set<String> rootLinks; //надо удалить!
    private Connection.Response response;
    private int statusCode = 0;
    private String errorMessage = "";
    private final String siteUrl;
    private final String userAgent = "LocoBOT/0.0.2 (indexing and search engine)";
    //"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36";

    public WebParser(String url,
                     Site currentSite,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     String siteUrl,
                     Set<String> rootLinks //надо удалить!
    ) {
        this.root = url.replaceAll("www.", "");
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.currentSite = currentSite;
        this.rootLinks = rootLinks; //надо удалить!
        this.siteUrl = siteUrl.replaceAll("www.", "");
        this.htmlDoc = new Document("index");
    }

    @Override
    protected String compute() {
        Set<String> links = getLinks();

        if (links.isEmpty()) {
            //Page page = new Page();
            Page page = pageRepository.findByPath(root);
            //pageRepository.delete(page);
            //page.setPath(root);
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
            //rootLinks.add(link);
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
            task.join();
            paths.append(root).append("\n").append(task.join());
        }
        return "";
        //return paths.toString();
    }


    private Set<String> getLinks() {
        Set<String> links = new HashSet<>();
        if (root.contains(".pdf") || root.contains("#")) {
            return new HashSet<>();
        }

        try {
            Thread.sleep((int) (Math.random() * 1000) + 1000);
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
            String url = "";
            //boolean pageIsNotExist = false; //pageRepository.findByPath(url) != null;
            //Page emptyPage = new Page();

            for (Element link : htmlLinks) {
//                String url = link.attr("abs:href");
                url = link.attr("abs:href");
                if (url.contains("?") && url.contains(siteUrl)) {
                    url = url.substring(0, url.indexOf('?'));
                }

//                System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAA" + pageRepository.findByPath(siteUrl).getPath());
                //System.out.println(url);
                //emptyPage = pageRepository.findByPath(url) != null ?
//                if (url.contains(siteUrl)) {
//                    pageIsNotExist = pageRepository.findByPath(url) == null || pageRepository.findByPath(url).getPath().equals(url);
//                }
                //System.out.println(pageIsNotExist);
                if (url.contains(siteUrl) &&
                        !url.equals(root) &&
                        !url.contains("#") &&
                        !url.contains("@") &&
                        !url.contains(".com") &&
                        !url.contains(".pdf") &&
                        !url.contains(".php") &&
                        !url.contains("img") &&
                        !url.contains("image") //&&

//                        !rootLinks.contains(url)&&
//                        !rootLinks.contains(url + "/")
                        //pageIsNotExist
                        //pageRepository.findByPath(url) != null
                        //pageRepository.exists(Example.of(emptyPage))

                        //rootLinks.stream().noneMatch(s -> s.equals(url))
                ) {
//                    Page emptyPage = pageRepository.findByPath(url);
//                    System.out.println("\n" + emptyPage + "\n");
                    //if (pageRepository.findByPath(url) == null) {
//                    if (pageRepository.findByPath(url) == null ||
//                            (pageRepository.findByPath(url) != null &&
//                                    !pageRepository.findByPath(url).getPath().equals(url))) {
                    if (pageRepository.findByPath(url) == null) {
                        Page emptyPage = new Page();
                        emptyPage.setCode(0);
                        emptyPage.setSite(currentSite);
                        emptyPage.setPath(url);
                        emptyPage.setContent("");
                        pageRepository.saveAndFlush(emptyPage);
                        links.add(url);
                    }
                    //}
                }

            }
        } catch (Exception ex) {
            System.out.println((response == null ? "" : response.statusMessage()) + ex + " URL: " + root);
            //ex.printStackTrace();
            if (response != null) {
                errorMessage = response.statusMessage().equals("OK") ? "" : response.statusMessage() + " ";
                statusCode = response.statusCode();
            }
            if (ex.toString().contains("Connect timed out"))
                statusCode = 522;
            if (ex.toString().contains("Read timed out"))
                statusCode = 598;
            errorMessage += ex + " URL: " + root;
            //System.out.println(errorMessage);
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
//        System.out.println("-------------------------------------");
//        links.forEach(link -> System.out.println("--- " + link));
//        System.out.println("-------------------------------------");
        return links;
    }

}
