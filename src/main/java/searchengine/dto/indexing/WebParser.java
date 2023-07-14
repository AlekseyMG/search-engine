package searchengine.dto.indexing;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CharsetEditor;
import org.springframework.data.domain.Example;
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
    private HashSet<String> allLinks;

    public WebParser(String url,
                     Site currentSite,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     HashSet<String> allLinks
    ) {
        this.root = url.replaceAll("www.", "");
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.currentSite = currentSite;
        this.allLinks = allLinks;
        this.htmlDoc = new Document("index");
    }

    @Override
    protected String compute() {
        Set<String> links = getLinks();
        //int level = root.split("/").length - 3;

        if (links.isEmpty() || root.contains(".pdf")) {
//            if (!pageRepository.findAll().stream()
//                    .anyMatch(page1 -> page1.getPath().contains(root))) {
                Page page = new Page();
                page.setPath(root);
                page.setCode(200);
                page.setSite(currentSite);
                page.setContent(htmlDoc.text());
                pageRepository.saveAndFlush(page);
            //}
            currentSite.setStatusTime(LocalDateTime.now());
            siteRepository.saveAndFlush(currentSite);

            return root + "\n";
        }

        StringBuilder paths = new StringBuilder();
        List<WebParser> subTasks = new LinkedList<>();
        for (String link : links) {
            WebParser task = new WebParser(link, currentSite, pageRepository, siteRepository, allLinks);
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
            htmlDoc = Jsoup.connect(root).get();
            Elements htmlLinks = htmlDoc.select("a[href]");
            for (Element link : htmlLinks) {
                String url = link.attr("abs:href");
                if (url.contains(root) &&
                        !url.equals(root) &&
                        !url.contains("#") &&
                        !url.contains("@") &&
                        !url.contains(".com") &&
                        allLinks.stream().noneMatch(s -> s.equals(url))
                ) {
                    links.add(url);
                    allLinks.add(url);
                }

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return links;
    }
}
