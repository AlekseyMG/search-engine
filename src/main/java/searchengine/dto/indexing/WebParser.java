package searchengine.dto.indexing;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.config.ParserSetting;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.StatusType;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;

public class WebParser extends RecursiveTask<String> {
    private final String root;
    private final Site currentSite;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final String siteUrl;
    private final ParserSetting parserSetting;
    private final IndexingServiceImpl indexingServiceImpl;
    private final int siteId;
    List<WebParser> subTasks = new LinkedList<>();

    public WebParser(String url,
                     Site currentSite,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     String siteUrl,
                     ParserSetting parserSetting,
                     IndexingServiceImpl indexingServiceImpl
    ) {
        this.root = url.replaceAll("www.", "");
        this.currentSite = currentSite;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.siteUrl = siteUrl.replaceAll("www.", "");
        this.parserSetting = parserSetting;
        this.indexingServiceImpl = indexingServiceImpl;
        this.siteId = currentSite.getId();
    }

    @Override
    protected String compute() {
        if (!indexingServiceImpl.isRunning) {
            stop();
            return "";
        }
        Set<String> links = getLinks();
        if (!indexingServiceImpl.isRunning) {
            stop();
            return "";
        }

        if (links.isEmpty()) {
            return "";
        }
        for (String link : links) {
            WebParser task = new WebParser(
                    link,
                    currentSite,
                    pageRepository,
                    siteRepository,
                    siteUrl,
                    parserSetting,
                    indexingServiceImpl
            );
            task.fork();
            subTasks.add(task);
        }
            for (WebParser task : subTasks) {
                task.join();
        }
            links.clear();
        return "";
    }

    private Set<String> getLinks() {
        Set<String> links = new HashSet<>();
        Page emptyPage;
        String url = "";
        Connection.Response response = null;
        int statusCode = 0;
        String errorMessage = "";
        Document htmlDoc = new Document("");

        if (root.contains(".pdf") || root.contains("#")) {
            return new HashSet<>();
        }
        try {
            Thread.sleep((int) (Math.random() * 5000) + 1000);
            System.out.println("Идем по ссылке - " + root);
            Connection connection = Jsoup.connect(root);
            response = connection
                    .userAgent(parserSetting.getUserAgent())
                    .referrer(parserSetting.getReferrer())
                    .timeout(3000)
                    .ignoreHttpErrors(true)
                    .execute();
            htmlDoc = connection.get();
            statusCode = response == null ? 0 : response.statusCode();

            Elements htmlLinks = htmlDoc.select("a[href]");
            for (Element link : htmlLinks) {
                url = link.attr("abs:href");
                if (url.contains("?") && url.contains(siteUrl)) {
                    url = url.substring(0, url.indexOf('?'));
                }
                if (url.contains(siteUrl) &&
                        !url.equals(root) &&
                        !url.contains("#") &&
                        !url.contains("@") &&
                        !url.contains(".com") &&
                        !url.contains(".pdf") &&
                        !url.contains(".php") &&
                        !url.contains("img") &&
                        !url.contains("image") //&&
                ) {
                    if (pageRepository.findBySiteIdAndPath(siteId, url.replaceAll(siteUrl,"")) == null) {
                        emptyPage = new Page();
                        emptyPage.setCode(0);
                        emptyPage.setSite(currentSite);
                        emptyPage.setPath(url.replaceAll(siteUrl,""));
                        emptyPage.setContent("");
                        pageRepository.saveAndFlush(emptyPage);
                        links.add(url);
                    }
                }

            }
        } catch (SocketTimeoutException ex) {
            if (response != null) {
                errorMessage = response.statusMessage().equals("OK") ? "" : response.statusMessage() + " ";
                statusCode = response.statusCode();
            }
            if (ex.toString().contains("Connect timed out"))
                statusCode = 522;
            if (ex.toString().contains("Read timed out"))
                statusCode = 598;
            errorMessage = "Превышен интервал ожидания страницы: " + root;
        } catch (InterruptedException ex) {
            errorMessage = "Прервано пользователем";
        } catch (IOException ex) {
            errorMessage = "Ошибка ввода/вывода";
        } catch (DataIntegrityViolationException ex) {
            errorMessage = "Ошибка добавления записи в БД" + (ex.toString().contains("Duplicate") ? " (дубликат)" : "");
        }

        String currentPagePath = root.replaceAll(siteUrl,"").isEmpty() ?
                "/" : root.replaceAll(siteUrl,"");
        emptyPage = pageRepository.findBySiteIdAndPath(siteId, currentPagePath);
        emptyPage.setCode(statusCode);
        emptyPage.setSite(currentSite);
        emptyPage.setContent(htmlDoc.html());
        emptyPage.setPath(root.replaceAll(siteUrl,""));
        pageRepository.saveAndFlush(emptyPage);

        if (!errorMessage.equals("")) currentSite.setLastError(errorMessage);
        currentSite.setStatusTime(LocalDateTime.now());
        siteRepository.saveAndFlush(currentSite);

        htmlDoc = null;
        emptyPage = null;
        response = null;
        errorMessage = "";

        return links;
    }

    private void stop() {
        currentSite.setStatus(StatusType.FAILED);
        currentSite.setLastError("Прервано пользователем");
        siteRepository.saveAndFlush(currentSite);
    }
}
