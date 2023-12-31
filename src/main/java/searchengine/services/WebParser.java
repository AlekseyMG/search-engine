package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.ParserSetting;
import searchengine.model.*;
import searchengine.model.Index;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;

@Slf4j
public class WebParser extends RecursiveTask<String> {
    private final String absolutePath;
    private final String relativePath;
    private final Site currentSiteEntity;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final String siteUrl;
    private final ParserSetting parserSetting;
    private final IndexingServiceImpl indexingServiceImpl;
    private final int siteId;
    List<WebParser> subTasks = new LinkedList<>();
    ErrorHandler errorHandler = new ErrorHandler();
    private int statusCode = 0;
    private String errorMessage = "";
    private Connection.Response response = null;

    public WebParser(String absolutePath, Site currentSiteEntity, IndexingServiceImpl indexingServiceImpl) {
        this.absolutePath = absolutePath.replaceAll("www.", "");
        this.relativePath = absolutePath
                .replaceAll("www.", "")
                .replaceAll(
                        currentSiteEntity.getUrl().replaceAll("www.", ""), ""
                ).isEmpty() ? "/" :
                absolutePath.replaceAll(
                        currentSiteEntity.getUrl().replaceAll("www.", ""), ""
                );
        this.currentSiteEntity = currentSiteEntity;
        this.pageRepository = indexingServiceImpl.getPageRepository();
        this.siteRepository = indexingServiceImpl.getSiteRepository();
        this.lemmaRepository = indexingServiceImpl.getLemmaRepository();
        this.indexRepository = indexingServiceImpl.getIndexRepository();
        this.siteUrl = currentSiteEntity.getUrl().replaceAll("www.", "");
        this.parserSetting = indexingServiceImpl.getParserSetting();
        this.indexingServiceImpl = indexingServiceImpl;
        this.siteId = currentSiteEntity.getId();
    }

    @Override
    protected String compute() {
        if (indexingServiceImpl.isStoppedByUser()) {
            stop();
            return "";
        }

        Set<String> links = saveCurrentPageAndGetLinks();

        if (links.isEmpty()) {
            return "";
        }

        for (String link : links) {
            WebParser task = new WebParser(link, currentSiteEntity, indexingServiceImpl);
            task.fork();
            subTasks.add(task);
        }
        links.clear();
        for (WebParser task : subTasks) {
            task.join();
        }
        return "";
    }

    public int updateOnePage() {
        saveCurrentPageAndGetLinks();
        return statusCode;
    }

    private Set<String> saveCurrentPageAndGetLinks() {
        Set<String> links = new HashSet<>();
        Document htmlDoc = new Document("");

        if (absolutePath.contains(".pdf") || absolutePath.contains("#")) {
            return new HashSet<>();
        }
        try {
            Thread.sleep((int) (Math.random() *
                    parserSetting.getRandomDelayDeltaBeforeConnection()) +
                    parserSetting.getMinDelayBeforeConnection());

            log.info("Идем по ссылке - " + absolutePath);
            //System.out.println("Идем по ссылке - " + absolutePath);

            Connection connection = Jsoup.connect(absolutePath);
            response = connection
                    .userAgent(parserSetting.getUserAgent())
                    .referrer(parserSetting.getReferrer())
                    .timeout(parserSetting.getConnectionTimeout())
                    .ignoreHttpErrors(true)
                    .execute();

            htmlDoc = connection.get();
            statusCode = response.statusCode();
            links = getLinksFromHTML(htmlDoc);

        } catch (Exception ex) {
            errorHandler.processError(ex, this);
        }

        savePage(statusCode, htmlDoc.html(), relativePath);
        updateSiteStatus(errorMessage);

        return links;
    }

    private void updateSiteStatus(String errorMessage) {
        if (!errorMessage.equals("")) {
            currentSiteEntity.setLastError(errorMessage);
        }
        currentSiteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.saveAndFlush(currentSiteEntity);
    }

    private Set<String> getLinksFromHTML(Document htmlDoc) {
        Set<String> links = new HashSet<>();
        String parsedLinkAbsolutePath;
        String parsedLinkRelativePath;
        Elements htmlLinks = htmlDoc.select("a[href]");

        for (Element link : htmlLinks) {
            parsedLinkAbsolutePath = link.attr("abs:href")
                    .replaceAll("www.","").toLowerCase();
            parsedLinkRelativePath = parsedLinkAbsolutePath.replaceAll(siteUrl,"");

            if (parsedLinkAbsolutePath.contains(siteUrl) &&
                    !parsedLinkAbsolutePath.equals(absolutePath) &&
                    !indexingServiceImpl.isMatchedWithSkipList(parsedLinkAbsolutePath)
            ) {
                if (pageRepository.findBySiteIdAndPath(siteId, parsedLinkRelativePath) == null) {
                    savePage(0, "", parsedLinkRelativePath);
                    links.add(parsedLinkAbsolutePath);
                }
            }
        }
        return links;
    }

    private void savePage(int statusCode, String content, String pagePath) {
        Page page = pageRepository.findBySiteIdAndPath(siteId, pagePath);

        if (page == null) {
            page = new Page();
        }

        page.setCode(statusCode);
        page.setSite(currentSiteEntity);
        page.setContent(content);
        page.setPath(pagePath);
        pageRepository.saveAndFlush(page);

        if (statusCode == 200) {
            saveLemma(page, content);
        }
    }

    private void saveLemma(Page page, String content) {
        try {
            LemmaFinder lemmaFinder = new LemmaFinder(new RussianLuceneMorphology());
            lemmaFinder.collectLemmasFromHTML(content).forEach((normalWord, integer) -> {
                Lemma lemma = lemmaRepository.findBySiteIdAndLemma(currentSiteEntity.getId(), normalWord);
                if (lemma == null) {
                    lemma = new Lemma();
                    lemma.setFrequency(0);
                }

                lemma.setLemma(normalWord);
                lemma.setSite(currentSiteEntity);
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.saveAndFlush(lemma);

                saveIndex(page, lemma, integer);
            });

        } catch (IOException ex) {
            log.info(ex.toString());
            ex.printStackTrace();
        }
    }

    public void saveIndex(Page page, Lemma lemma, int integer) {
        Index index = new Index();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(integer);
        indexingServiceImpl.getBatchIndexWriter().addForSave(index);
    }

    public void deletePageFromRepository(String url) {
        log.info("Пытаемся удалить: " + url);
        log.info("Ищем страницу с путем: " + url.replaceAll(siteUrl,""));

        Page page = pageRepository.findBySiteIdAndPath(siteId, url.replaceAll(siteUrl,""));

//        System.out.println("Пытаемся удалить: " + url);
//        System.out.println("Ищем страницу с путем: " + url.replaceAll(siteUrl,""));

        if (page != null) {

//            System.out.println("Удаляем страницу: " + url);
            log.info("Удаляем страницу: " + url);

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
            log.info("Страница удалена!");
        } else {
            log.info("Страница не найдена!");
        }
    }

    private void stop() {
        currentSiteEntity.setStatus(StatusType.FAILED);
        currentSiteEntity.setLastError("Прервано пользователем");
        siteRepository.saveAndFlush(currentSiteEntity);
        savePage(statusCode, "", relativePath);
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public Connection.Response getResponse() {
        return response;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setCurrentSiteEntityStatus(StatusType statusType) {
        this.currentSiteEntity.setStatus(statusType);
    }
}
