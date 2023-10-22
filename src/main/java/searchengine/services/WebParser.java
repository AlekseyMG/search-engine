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
import searchengine.model.*;
import searchengine.model.Index;
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
    int statusCode = 0;

    public WebParser(String absolutePath, Site currentSiteEntity, IndexingServiceImpl indexingServiceImpl) {
        this.absolutePath = absolutePath.replaceAll("www.", "");
        this.relativePath = absolutePath.replaceAll("www.", "").replaceAll(
                currentSiteEntity.getUrl().replaceAll("www.", ""), "")
                .isEmpty() ? "/" : absolutePath.replaceAll(
                        currentSiteEntity.getUrl().replaceAll("www.", ""), "");
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
        Connection.Response response = null;
        String errorMessage = "";
        Document htmlDoc = new Document("");

        if (absolutePath.contains(".pdf") || absolutePath.contains("#")) {
            return new HashSet<>();
        }
        try {
            Thread.sleep((int) (Math.random() *
                    parserSetting.getRandomDelayDeltaBeforeConnection()) +
                    parserSetting.getMinDelayBeforeConnection());

            System.out.println("Идем по ссылке - " + absolutePath);

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

        } catch (SocketTimeoutException ex) {
            if (response != null) {
                errorMessage = response.statusMessage().equals("OK") ? "" : response.statusMessage() + " ";
                statusCode = response.statusCode();
            }
            if (ex.toString().contains("Connect timed out")) {
                statusCode = 522;
            }
            if (ex.toString().contains("Read timed out")) {
                statusCode = 598;
            }

            errorMessage =  ErrorMessages.CONNECTION_TIMED_OUT + absolutePath;

            if (absolutePath.equals(siteUrl)) {
                currentSiteEntity.setStatus(StatusType.FAILED);
            }

            System.out.println(errorMessage);

        } catch (InterruptedException ex) {
            errorMessage = ErrorMessages.ABORTED_BY_USER;
            System.out.println(errorMessage);

        } catch (IOException ex) {
            errorMessage = ErrorMessages.IO_OR_NOT_FOUND + absolutePath;

            if (absolutePath.equals(siteUrl)) {
                currentSiteEntity.setStatus(StatusType.FAILED);
            }
            System.out.println(errorMessage);

        } catch (DataIntegrityViolationException ex) {
            errorMessage = ErrorMessages.ERROR_ADD_ENTITY_TO_DB + (ex.toString().contains("Duplicate") ? " (дубликат)" : "");
            System.out.println(errorMessage);
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
            parsedLinkAbsolutePath = link.attr("abs:href").replaceAll("www.","").toLowerCase();
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
                //boolean isNewLemma = false;
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
        currentSiteEntity.setStatus(StatusType.FAILED);
        currentSiteEntity.setLastError("Прервано пользователем");
        siteRepository.saveAndFlush(currentSiteEntity);
        savePage(statusCode, "", relativePath);
    }
}
