package searchengine.services;

import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.config.ParserSetting;
import searchengine.dto.ErrorMessages;
import searchengine.dto.statistics.LemmaCount;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

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
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final String siteUrl;
    private final ParserSetting parserSetting;
    private final IndexingServiceImpl indexingServiceImpl;
    private final int siteId;
    List<WebParser> subTasks = new LinkedList<>();
    int statusCode = 0;

    public WebParser(String url,
                     Site currentSite,
                     PageRepository pageRepository,
                     SiteRepository siteRepository,
                     LemmaRepository lemmaRepository,
                     IndexRepository indexRepository,
                     String siteUrl,
                     ParserSetting parserSetting,
                     IndexingServiceImpl indexingServiceImpl
    ) {
        this.root = url.replaceAll("www.", "");
        this.currentSite = currentSite;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
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

        Set<String> links = addCurrentPageAndGetLinks();

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
                    lemmaRepository,
                    indexRepository,
                    siteUrl,
                    parserSetting,
                    indexingServiceImpl
            );
            task.fork();
            subTasks.add(task);
        }
        links.clear();
        for (WebParser task : subTasks) {
            task.join();
        }
        return "";
    }
    public int updateCurrentPage() {
        addCurrentPageAndGetLinks();
//        if (statusCode == 200) {
//            return true;
//        }
        return statusCode;
    }
    private Set<String> addCurrentPageAndGetLinks() {
        Set<String> links = new HashSet<>();
        String url = "";
        Connection.Response response = null;
        //int statusCode = 0;
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
                url = link.attr("abs:href").replaceAll("www.","").toLowerCase();
                //System.out.println(root + " " + url + " " + siteUrl);
//                if (url.contains("?") && url.contains(siteUrl)) {
//                    url = url.substring(0, url.indexOf('?'));
//                }
                if (url.contains(siteUrl) &&
                        !url.equals(root) &&
                        !url.contains("#") &&
                        !url.contains("@") &&
                        !url.contains(".com") &&
                        !url.contains(".pdf") &&
                        !url.contains(".php") &&
                        !url.contains(".png") &&
                        !url.contains(".jpg") &&
                        !url.contains(".jpeg") &&
                        !url.contains(".gif") &&
                        !url.contains("upload") &&
                        !url.contains("img") &&
                        !url.contains("image") //&&
                ) {
                    if (pageRepository.findBySiteIdAndPath(siteId, url.replaceAll(siteUrl,"")) == null) {
                        savePage(0, "", url.replaceAll(siteUrl,""));
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
            errorMessage =  ErrorMessages.connectTimedOut + root;
            if (root.equals(siteUrl + "/")) {
                currentSite.setStatus(StatusType.FAILED);
            }
            System.out.println(errorMessage);
        } catch (InterruptedException ex) {
            errorMessage = ErrorMessages.abortedByUser;
        } catch (IOException ex) {
            errorMessage = ErrorMessages.ioOrNotFound + root;
            currentSite.setStatus(StatusType.FAILED);
        } catch (DataIntegrityViolationException ex) {
            errorMessage = ErrorMessages.errorAddEntityToDB + (ex.toString().contains("Duplicate") ? " (дубликат)" : "");
        }

        String currentPagePath = root.equals(siteUrl + "/") ? "/" : root.replaceAll(siteUrl,"");
        savePage(statusCode, htmlDoc.html(), currentPagePath);

        if (!errorMessage.equals("")) currentSite.setLastError(errorMessage);
        currentSite.setStatusTime(LocalDateTime.now());
        siteRepository.saveAndFlush(currentSite);

        htmlDoc = null;
        response = null;
        errorMessage = "";

        return links;
    }

    private void savePage(int statusCode, String content, String pagePath) {
        Page page = pageRepository.findBySiteIdAndPath(siteId, pagePath);

        if (page == null) {
            page = new Page();
        }

//        if (isNewPage) {
//            currentPage = new Page();
//        } else {
//            currentPage = pageRepository.findBySiteIdAndPath(siteId, pagePath);
//            if (statusCode == 200) {
//                addLemma(currentPage, content);
//            }
//        }

        page.setCode(statusCode);
        page.setSite(currentSite);
        page.setContent(content);
        page.setPath(pagePath);
        pageRepository.saveAndFlush(page);

        if (statusCode == 200) {
            //deleteLemma(page, content);
            addLemma(page, content);
        }
        page = null;
    }

    private void addLemma(Page page, String content) {
        try {
            LemmaFinder lemmaFinder = new LemmaFinder(new RussianLuceneMorphology());
            lemmaFinder.collectLemmasFromHTML(content).forEach((normalWord, integer) -> {
                Lemma lemma = lemmaRepository.findBySiteIdAndLemma(currentSite.getId(), normalWord);
                if (lemma == null) {
                    lemma = new Lemma();
                    lemma.setFrequency(0);
                }
                Index index = indexRepository.findByPageIdAndLemmaId(page.getId(), lemma.getId());
                if (index == null) {
                    index = new Index();
                }
                lemma.setLemma(normalWord);
                lemma.setSite(currentSite);
                lemma.setFrequency(lemma.getFrequency() + 1);

                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(integer);
                LemmaCount.increaseLemmaCountForSite(currentSite.getId(), integer);
//                lemmaRepository.flush();
//                indexRepository.flush();
                lemmaRepository.saveAndFlush(lemma);
                indexRepository.saveAndFlush(index);
            });

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public void deleteLemma(String url) {
        Page page = pageRepository.findBySiteIdAndPath(siteId, url.replaceAll(siteUrl,""));
        System.out.println("Пытаемся удалить: " + url);
        System.out.println("Ищем страницу с путем: " + url.replaceAll(siteUrl,""));
        if (page != null) {
            System.out.println("Удаляем страницу: " + url);
            HashSet<Index> indexes = indexRepository.findByPageId(page.getId());
            indexRepository.deleteByPageId(page.getId());
            indexes.forEach(index -> {
                Lemma lemma = index.getLemma();
                int frequency = lemma.getFrequency() - 1;
                if (frequency < 1) {
                    lemmaRepository.delete(lemma);
                } else {
                    lemma.setFrequency(frequency);
                    lemmaRepository.saveAndFlush(lemma);
                }
            });

        }
    }
    private void stop() {
        currentSite.setStatus(StatusType.FAILED);
        currentSite.setLastError("Прервано пользователем");
        siteRepository.saveAndFlush(currentSite);
//        lemmaRepository.flush();
//        pageRepository.flush();
//        indexRepository.flush();
        savePage(statusCode, "", root.replaceAll(siteUrl,""));
    }
}
